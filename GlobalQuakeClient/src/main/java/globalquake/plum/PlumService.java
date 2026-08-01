package globalquake.plum;

import com.uber.h3core.H3Core;
import com.uber.h3core.LengthUnit;
import com.uber.h3core.util.LatLng;
import globalquake.core.GlobalQuake;
import globalquake.core.Settings;
import globalquake.core.earthquake.data.Cluster;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.earthquake.data.Hypocenter;
import globalquake.core.earthquake.interval.DepthConfidenceInterval;
import globalquake.core.events.GlobalQuakeEventListener;
import globalquake.core.events.specific.QuakeCreateEvent;
import globalquake.core.events.specific.QuakeRemoveEvent;
import globalquake.core.geo.taup.TauPTravelTimeCalculator;
import globalquake.core.intensity.IntensityScales;
import globalquake.core.intensity.Level;
import globalquake.core.station.AbstractStation;
import globalquake.events.specific.PlumUpdatedEvent;
import globalquake.client.GlobalQuakeLocal;
import globalquake.playground.PlaygroundStation;
import globalquake.ui.globe.Point2D;
import globalquake.ui.globalquake.feature.FeatureGlobalStation;
import globalquake.ui.i18n.I18n;
import globalquake.utils.GeoUtils;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 简易 PLUM 法（Playground Mode）。
 *
 * 链路：测站实测烈度（FeatureGlobalStation.computePGA，禁止理论 PGA）
 *   → h3 网格（大六边形，默认分辨率 4，约 45km 直径）——路径2：
 *     对每个网格中心，搜索 30km 半径内的测站实测烈度取最大值作为该网格预报烈度
 *   → ハイブリッド融合：PLUM 实测值与点源预估（GeoUtils.pgaFunction）逐网格取较大值
 *   → レベル法：首个测站实测超标 → 创建「PLUM法假定震源要素」正式地震报
 *     （震中 = 第一个超标测站，深度 10km，发震时刻反推，震级 1.0），经
 *     QuakeCreateEvent 走程序正式发报；程序定位出真实震源后自动修订该报。
 *
 * 工作流约束：本服务只读测站实测数据（自然计算产生），不绕过任何计算直接发 EEW。
 */
public class PlumService {

    private static final long ACTIVE_TIMEOUT_MS = 30_000;
    private static final long TICK_INTERVAL_MS = 250;
    /** 网格探测半径（km）：对每个网格中心搜索该半径内的测站，取最大实测烈度。 */
    private static final double SEARCH_RADIUS_KM = 30.0;
    /** 假定震源深度（km），按 JMA 予報固定要素。 */
    private static final double ASSUMED_DEPTH = 10.0;
    /** 假定震级（予報阶段震级未知，固定 1.0）。 */
    private static final double ASSUMED_MAGNITUDE = 1.0;

    private static PlumService instance;

    private final ScheduledExecutorService executor;
    private final H3Core h3;

    // 渲染线程安全：不可变快照，每次 tick 后整体替换
    private volatile List<PlumCell> cellsView = new ArrayList<>();

    private final Object stateLock = new Object();
    private Earthquake assumedQuake;               // 假定震源正式地震报（QuakeCreateEvent 发报）
    private AbstractStation firstTriggerStation;   // 第一个检测到超标的测站
    private long firstExceedTime;                  // 首个超标时刻（模拟时钟）
    private final Map<AbstractStation, Long> stationFirstExceed = new ConcurrentHashMap<>();
    private long lastActiveTime;
    private double maxPGA;
    private int forecastCellCount;

    // 实测烈度快照（computePGA ≥ 0.5 gal 的测站），供系统 ShakeMap 六边形修正查询
    private volatile Map<AbstractStation, Double> measuredPga = Collections.emptyMap();
    private volatile double maxMeasuredPga = 0;
    private volatile Point2D maxMeasuredPos;
    // 按 h3 cell 聚合的实测 max（当前 Settings.plumResolution 分桶），供 ShakeMap 快速查表（O(7~127) 替代全站遍历）
    private volatile Map<Long, Double> cellMeasuredPga = Collections.emptyMap();
    // 强制重算：假定报生命周期内，同一个地震当且仅当有 X 个站（发报最低测站数）检测到晃动时触发一次，
    // 之后由程序自然决定是否修订（不再手动触发）
    private volatile boolean forcedOnce = false;

    private static final class MutableCell {
        final long id;
        final Point2D center;
        double maxMeasured = 0;
        int stationCount = 0;

        MutableCell(long id, Point2D center) {
            this.id = id;
            this.center = center;
        }
    }

    public PlumService() {
        instance = this;
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new RuntimeException("Unable to init H3Core for PLUM!", e);
        }
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::tick, 0, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // 监听程序正式地震创建/移除：真实定位出现后修订假定报；假定报被外部移除时清理引用
        GlobalQuake.instance.getEventHandler().registerEventListener(new GlobalQuakeEventListener() {
            @Override
            public void onQuakeCreate(QuakeCreateEvent event) {
                onRealQuakeCreated(event.earthquake());
            }

            @Override
            public void onQuakeRemove(QuakeRemoveEvent event) {
                synchronized (stateLock) {
                    if (event.earthquake() == assumedQuake) {
                        assumedQuake = null;
                        firstTriggerStation = null;
                        firstExceedTime = 0;
                        stationFirstExceed.clear();
                    }
                }
            }
        });

        Logger.info("PLUM service started (simplified, playground)");
    }

    public static PlumService getInstance() {
        return instance;
    }

    public void stop() {
        executor.shutdownNow();
        instance = null;
    }

    /** 清除地震/重置波形时调用：撤销假定报、清空网格与状态。 */
    public void reset() {
        releaseAssumedQuake();
        synchronized (stateLock) {
            maxPGA = 0;
            forecastCellCount = 0;
            forcedOnce = false;
            // 无条件清空首台超标记录：假定报可能已让位（assumedQuake==null）而 releaseAssumedQuake
            // 直接返回，残留记录会让波形重置后的下一个 tick 误触发创建新的假定报
            firstTriggerStation = null;
            firstExceedTime = 0;
            lastActiveTime = 0;
            stationFirstExceed.clear();
        }
        cellsView = new ArrayList<>();
        measuredPga = Collections.emptyMap();
        cellMeasuredPga = Collections.emptyMap();
        maxMeasuredPga = 0;
        maxMeasuredPos = null;
        fireUpdated();
    }

    /** 非 Playground（无 PlumService）时安全调用。 */
    public static void resetIfPresent() {
        if (instance != null) {
            instance.reset();
        }
    }

    /** 撤销假定震源报：从正式地震列表移除并 fire QuakeRemoveEvent。 */
    private void releaseAssumedQuake() {
        synchronized (stateLock) {
            if (assumedQuake == null) {
                return;
            }
            Earthquake q = assumedQuake;
            assumedQuake = null;
            firstTriggerStation = null;
            firstExceedTime = 0;
            stationFirstExceed.clear();
            // 假定 cluster 从未进入聚类集合、也没有事件，无需清理
            GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes().remove(q);
            GlobalQuake.instance.getEventHandler().fireEvent(new QuakeRemoveEvent(q));
            Logger.info("PLUM assumed quake released");
        }
    }

    private void tick() {
        try {
            if (!Settings.plumEnabled) {
                if (!cellsView.isEmpty() || assumedQuake != null) {
                    releaseAssumedQuake();
                    cellsView = new ArrayList<>();
                    synchronized (stateLock) {
                        maxPGA = 0;
                        forecastCellCount = 0;
                    }
                    fireUpdated();
                }
                return;
            }

            if (GlobalQuake.instance.getStationManager() == null) {
                return;
            }

            int res = clamp(Settings.plumResolution, 3, 8);
            double threshold = getThreshold();
            long now = GlobalQuake.instance.currentTimeMillis();

            // 1) 测站实测烈度 → 按所在 cell 分桶；记录首台超标（首次时刻）
            Map<Long, List<AbstractStation>> stationByCell = new HashMap<>();
            Map<AbstractStation, Double> pgaByStation = new HashMap<>();
            Map<AbstractStation, Double> newMeasuredPga = new HashMap<>();
            double newMaxMeasured = 0;
            Point2D newMaxPos = null;
            for (AbstractStation station : GlobalQuake.instance.getStationManager().getStations()) {
                if (station.disabled) {
                    continue;
                }
                double pga = FeatureGlobalStation.computePGA(station);
                if (pga < 0.5) {
                    continue;
                }
                // 实测烈度快照（供系统 ShakeMap 六边形修正）
                newMeasuredPga.put(station, pga);
                if (pga > newMaxMeasured) {
                    newMaxMeasured = pga;
                    newMaxPos = new Point2D(station.getLatitude(), station.getLongitude());
                }
                long id = h3.latLngToCell(station.getLatitude(), station.getLongitude(), res);
                stationByCell.computeIfAbsent(id, k -> new ArrayList<>()).add(station);
                pgaByStation.put(station, pga);
                if (pga >= threshold) {
                    stationFirstExceed.putIfAbsent(station, now);
                }
            }
            measuredPga = newMeasuredPga;
            maxMeasuredPga = newMaxMeasured;
            maxMeasuredPos = newMaxPos;

            // 按 cell 聚合实测 max（供系统 ShakeMap 快速查表修正六边形）
            Map<Long, Double> newCellMeasured = new HashMap<>();
            for (Map.Entry<Long, List<AbstractStation>> e : stationByCell.entrySet()) {
                double max = 0;
                for (AbstractStation s : e.getValue()) {
                    double p = pgaByStation.getOrDefault(s, 0.0);
                    if (p > max) {
                        max = p;
                    }
                }
                if (max >= 0.5) {
                    newCellMeasured.put(e.getKey(), max);
                }
            }
            cellMeasuredPga = newCellMeasured;

            // 2) 候选网格 = 所有含站 cell ∪ 其 gridDisk(1) 邻居（保证任何测站都落在至少一个网格的 30km 内）
            Set<Long> candidateCells = new HashSet<>();
            for (Long cellId : stationByCell.keySet()) {
                candidateCells.addAll(h3.gridDisk(cellId, 1));
            }

            // 3) 路径2：对每个网格中心搜索 30km 内测站，取最大实测烈度作为该网格预报
            List<Earthquake> quakes = GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes();
            boolean hybrid = Settings.plumHybridEnabled && !quakes.isEmpty();
            int minStations = clamp(Settings.plumCellMinStations, 1, 100);

            List<PlumCell> newCells = new ArrayList<>(candidateCells.size());
            double newMaxPGA = 0;
            int newForecastCount = 0;

            for (Long cid : candidateCells) {
                LatLng center = h3.cellToLatLng(cid);
                double maxMeasured = 0;
                int count = 0;
                for (Long nid : h3.gridDisk(cid, 1)) {
                    List<AbstractStation> bucket = stationByCell.get(nid);
                    if (bucket == null) {
                        continue;
                    }
                    for (AbstractStation s : bucket) {
                        if (GeoUtils.greatCircleDistance(center.lat, center.lng,
                                s.getLatitude(), s.getLongitude()) <= SEARCH_RADIUS_KM) {
                            count++;
                            double pga = pgaByStation.getOrDefault(s, 0.0);
                            if (pga > maxMeasured) {
                                maxMeasured = pga;
                            }
                        }
                    }
                }
                if (count < minStations) {
                    continue;
                }

                double sourcePga = maxMeasured;
                double finalPga = sourcePga;
                if (hybrid) {
                    for (Earthquake q : quakes) {
                        double dist = GeoUtils.geologicalDistance(
                                q.getLat(), q.getLon(), -q.getDepth(),
                                center.lat, center.lng, 0);
                        finalPga = Math.max(finalPga, GeoUtils.pgaFunction(q.getMag(), dist, q.getDepth()));
                    }
                }
                if (finalPga >= threshold) {
                    newForecastCount++;
                }
                if (finalPga > newMaxPGA) {
                    newMaxPGA = finalPga;
                }
                newCells.add(new PlumCell(cid, new Point2D(center.lat, center.lng), finalPga, sourcePga, count));
            }

            // 4) レベル法：首台超标 → 创建假定震源正式地震报（定位后修订）
            boolean anyExceed = !stationFirstExceed.isEmpty();

            synchronized (stateLock) {
                maxPGA = newMaxPGA;
                forecastCellCount = newForecastCount;

                if (Settings.plumLevelMethodEnabled && anyExceed) {
                    lastActiveTime = now;
                    if (assumedQuake == null) {
                        createAssumedQuake();
                    }
                } else if (assumedQuake != null && now - lastActiveTime > ACTIVE_TIMEOUT_MS) {
                    // 误报超时：30s 无实测超标则撤销假定报
                    releaseAssumedQuake();
                }

                // ⑤ 强制重算：假定报生命周期内，同一个地震当且仅当有 X 个站（设置中的发报最低测站数）检测到晃动时，
                //    触发一次重算（催促尚未发正式报的真实 cluster 立即重算定位→尽快发第2报）；
                //    之后更多站检测到晃动则不再手动触发，由程序自然决定是否发报/修订
                if (assumedQuake != null && !forcedOnce) {
                    int minEewStations = Settings.minimumStationsForEEW == null ? 4 : clamp(Settings.minimumStationsForEEW, 1, 100);
                    if (stationFirstExceed.size() >= minEewStations) {
                        forcedOnce = true;
                        int forced = 0;
                        for (Cluster c : GlobalQuake.instance.getClusterAnalysis().getClusters()) {
                            if (c.getEarthquake() == null && !c.getAssignedEvents().isEmpty()) {
                                c.lastEpicenterUpdate = 0;
                                forced++;
                            }
                        }
                        Logger.info("PLUM 强制重算触发：%d 个站检测到晃动（最低 %d 站），催促 %d 个未发报聚类簇立即重算定位"
                                .formatted(stationFirstExceed.size(), minEewStations, forced));
                    }
                }
            }

            cellsView = newCells;
            fireUpdated();
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    /** 创建「PLUM法假定震源要素」正式地震报（经 QuakeCreateEvent 走程序正式发报）。 */
    private void createAssumedQuake() {
        // 震中 = 第一个检测到超标的测站（时刻最早）
        AbstractStation first = null;
        long earliest = Long.MAX_VALUE;
        for (Map.Entry<AbstractStation, Long> e : stationFirstExceed.entrySet()) {
            if (e.getValue() < earliest) {
                earliest = e.getValue();
                first = e.getKey();
            }
        }
        if (first == null) {
            return;
        }
        firstTriggerStation = first;
        firstExceedTime = earliest;

        double lat = first.getLatitude();
        double lon = first.getLongitude();

        // 守卫：程序已发出真实正式报（同一地震）时，PLUM 不再创建假定报，避免假定报把正式报吞并掉
        for (Earthquake q : GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes()) {
            if (q != null && !q.isPlumAssumed() && q.getCluster() != null && q.getCluster().getPreviousHypocenter() != null) {
                double dist = GeoUtils.greatCircleDistance(q.getLat(), q.getLon(), lat, lon);
                if (dist < 150.0) {
                    Logger.info("PLUM skip assumed quake: real EEW already issued at %.3f, %.3f (dist %.1f km)"
                            .formatted(q.getLat(), q.getLon(), dist));
                    return;
                }
            }
        }

        // 发震时刻反推：origin = 首台超标时刻 - P波走时(震源=该站, 深度10km) - bias - delay
        // （站实际看到波形时刻 = origin + P走时 + bias + delay，见 FeatureGlobalStation.calcPhase 注释）
        double pTravel = TauPTravelTimeCalculator.getPWaveTravelTime(ASSUMED_DEPTH, 0.0);
        long offset = 0;
        if (first instanceof PlaygroundStation ps) {
            offset = ps.getBias() + ps.getDelay();
        }
        long origin = pTravel <= 0 ? earliest : earliest - (long) (pTravel * 1000) - offset;

        Cluster cluster = new Cluster();
        cluster.updateRoot(lat, lon);
        // 新假定报：允许下一次"4 个站触发一次重算"
        forcedOnce = false;
        Hypocenter hyp = new Hypocenter(lat, lon, ASSUMED_DEPTH, origin, 0.0, 0,
                new DepthConfidenceInterval(0, 0), new ArrayList<>());
        hyp.magnitude = ASSUMED_MAGNITUDE;
        cluster.setPreviousHypocenter(hyp);

        Earthquake quake = new Earthquake(cluster);
        quake.setRegion(I18n.get("plum.assumed.region"));
        quake.setPlumAssumed(true);
        cluster.setEarthquake(quake);

        GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes().add(quake);
        // 假定 cluster 不入聚类集合：PLUM 假定报仅作预警占位（第1报），不参与程序定位/合并/修订流程，
        // 真实 cluster 保持独立、按原程序逻辑积累事件并发正式报（第2报，4~10 站），两者互不干扰。
        // （mergeClusters 对假定报跳过合并，见 ClusterAnalysis.mergeClusters）
        GlobalQuake.instance.getEventHandler().fireEvent(new QuakeCreateEvent(quake));

        assumedQuake = quake;
        lastActiveTime = GlobalQuake.instance.currentTimeMillis();
        Logger.info("PLUM レベル法 EEW issued: assumed source at %.3f, %.3f (station %s, %.1f gal)"
                .formatted(lat, lon, first.getStationCode(), FeatureGlobalStation.computePGA(first)));
    }

    /**
     * 程序正式定位出真实地震（QuakeCreateEvent）时触发：正式报独立存活、完全由程序管理，
     * 假定报完成第1报预警使命后让位（撤销），两者互不干扰。
     */
    private void onRealQuakeCreated(Earthquake real) {
        if (real == null) {
            return;
        }
        synchronized (stateLock) {
            if (assumedQuake == null || real == assumedQuake) {
                return;
            }
            if (real.getCluster() == null || real.getCluster().getAssignedEvents().isEmpty()) {
                // 非程序定位产物，忽略
                return;
            }
            // 仅当正式报与假定报属同一地震（震中相近）时让位，避免远处独立地震撤销当前假定报
            double dist = GeoUtils.greatCircleDistance(
                    real.getLat(), real.getLon(), assumedQuake.getLat(), assumedQuake.getLon());
            if (dist < 150.0) {
                Logger.info("PLUM assumed quake yields to real EEW M%.1f at %.3f, %.3f (dist %.1f km)"
                        .formatted(real.getMag(), real.getLat(), real.getLon(), dist));
                releaseAssumedQuake();
            }
        }
    }

    private void fireUpdated() {
        if (GlobalQuakeLocal.instance != null && GlobalQuakeLocal.instance.getLocalEventHandler() != null) {
            GlobalQuakeLocal.instance.getLocalEventHandler().fireEvent(new PlumUpdatedEvent());
        }
    }

    /** PLUM 预报阈值（gal）：设置的烈度标准 + 级别。 */
    public static double getThreshold() {
        int scaleIdx = clamp(Settings.plumThresholdScale, 0, IntensityScales.INTENSITY_SCALES.length - 1);
        var scale = IntensityScales.INTENSITY_SCALES[scaleIdx];
        int levelIdx = clamp(Settings.plumThresholdLevel, 0, scale.getLevels().size() - 1);
        return scale.getLevels().get(levelIdx).getPga();
    }

    public static Level getThresholdLevel() {
        int scaleIdx = clamp(Settings.plumThresholdScale, 0, IntensityScales.INTENSITY_SCALES.length - 1);
        var scale = IntensityScales.INTENSITY_SCALES[scaleIdx];
        int levelIdx = clamp(Settings.plumThresholdLevel, 0, scale.getLevels().size() - 1);
        return scale.getLevels().get(levelIdx);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------- 渲染/状态读取接口 ----------------

    public List<PlumCell> getCells() {
        return cellsView;
    }

    /** 是否已有实测烈度（≥0.5 gal 的测站）。 */
    public boolean isMeasuredActive() {
        return !measuredPga.isEmpty();
    }

    /** 某点 30km 半径内测站实测烈度最大值（PLUM 实测，用于修正系统 ShakeMap 六边形预估烈度）。 */
    public double getMeasuredPgaAt(double lat, double lon) {
        double max = 0;
        for (Map.Entry<AbstractStation, Double> e : measuredPga.entrySet()) {
            AbstractStation s = e.getKey();
            if (GeoUtils.greatCircleDistance(lat, lon, s.getLatitude(), s.getLongitude()) <= SEARCH_RADIUS_KM) {
                double v = e.getValue();
                if (v > max) {
                    max = v;
                }
            }
        }
        return max;
    }

    /**
     * 某点附近实测烈度最大值（cell 聚合查表版）：按 h3 cell + 30km 折算环数查询，
     * O(7~127) 替代 getMeasuredPgaAt 的全站遍历，避免六边形数量多时（每格遍历所有测站）刷新卡顿。
     * res 需与 cellMeasuredPga 的分桶分辨率一致（Settings.plumResolution）。
     */
    public double getMeasuredPgaAtCell(double lat, double lon, int res) {
        long cell = h3.latLngToCell(lat, lon, res);
        double edgeKm = h3.getHexagonEdgeLengthAvg(res, LengthUnit.km);
        int k = Math.max(1, (int) Math.ceil(SEARCH_RADIUS_KM / (edgeKm * 1.5)));
        double max = 0;
        for (long n : h3.gridDisk(cell, k)) {
            Double v = cellMeasuredPga.get(n);
            if (v != null && v > max) {
                max = v;
            }
        }
        return max;
    }

    /** 全图实测烈度最大值（gal）。 */
    public double getMaxMeasuredPga() {
        return maxMeasuredPga;
    }

    /** 实测烈度最大值所在位置（x=lat, y=lon），无实测时为 null。 */
    public Point2D getMaxMeasuredPos() {
        return maxMeasuredPos;
    }

    /** 假定震源报是否活跃（已发报且未被撤销）。 */
    public boolean isActive() {
        synchronized (stateLock) {
            return assumedQuake != null;
        }
    }

    public Earthquake getAssumedQuake() {
        synchronized (stateLock) {
            return assumedQuake;
        }
    }

    public double getMaxPGA() {
        synchronized (stateLock) {
            return maxPGA;
        }
    }

    public int getForecastCellCount() {
        synchronized (stateLock) {
            return forecastCellCount;
        }
    }
}
