package globalquake.ui.globalquake.feature;

import globalquake.core.GQFonts;

import globalquake.client.GlobalQuakeClient;
import globalquake.core.analysis.AnalysisStatus;
import globalquake.core.analysis.Event;
import globalquake.core.earthquake.data.Cluster;
// ==================== 调试日志备用 import（默认保留，取消注释调试代码后直接可用） ====================
import globalquake.core.GlobalQuake;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.geo.taup.TauPTravelTimeCalculator;
import globalquake.playground.PlaygroundStation;
// ==============================================================================================
import globalquake.core.station.AbstractStation;
import globalquake.ui.globe.GlobeRenderer;
import globalquake.ui.globe.Point2D;
import globalquake.ui.globe.Polygon3D;
import globalquake.ui.globe.RenderProperties;
import globalquake.ui.globe.feature.RenderElement;
import globalquake.ui.globe.feature.RenderEntity;
import globalquake.ui.globe.feature.RenderFeature;
import globalquake.core.Settings;
import globalquake.core.intensity.IntensityScales;
import globalquake.core.intensity.Level;
import globalquake.core.intensity.LevelPalette;
import globalquake.ui.settings.StationsShape;
import globalquake.ui.stationselect.FeatureSelectableStation;
import globalquake.utils.GeoUtils; // 调试日志备用 import（与上面调试代码配套）
import globalquake.utils.Scale;
import gqserver.api.packets.station.InputType;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.awt.*;
import java.io.FileWriter; // 调试日志备用 import
import java.io.PrintWriter; // 调试日志备用 import
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong; // 调试日志备用 import
import java.util.stream.Collectors;

public class FeatureGlobalStation extends RenderFeature<AbstractStation> {

    private final Collection<AbstractStation> globalStations;

    public static final double RATIO_YELLOW = 2000.0;
    public static final double RATIO_RED = 20000.0;

    public FeatureGlobalStation(Collection<AbstractStation> globalStations) {
        super(2);
        this.globalStations = globalStations;
    }

    public static double computePGA(AbstractStation station) {
        double ratio = station.getMaxRatio60S();
        if (ratio < 5.0) return 0;
        double ratioGain = Math.pow(ratio / 10.0, 0.5);

        double result;
        if (station.isSensitivityValid()) {
            double velCounts = station.getMaxVelocity60S();
            if (velCounts <= 0) {
                result = Math.min(1.0, ratioGain * 0.5);
            } else {
                double physV = velCounts / station.getSensitivity();
                double pga = physV * 2.0 * Math.PI * 1.2 * 100.0;
                // ratioGain=(ratio/10)^0.5 在 ratio 千万级时放大 ~291 倍，是 M9 中距离(100-200km)
                // 虚高到 2000+ gal(7度) 的根因。对放大因子饱和封顶(等效对数压缩)：
                //   cap=45：M9 近场 20km→427gal(7度)、109km→385(6+)、137km→80(5-)，
                //           M4 12km→13.5(3度)、26km→3.3(2-3度)；
                //   cap=22 时全部偏低 1-2 度（M4 中距离原放大 43-75 也被误压）。
                result = Math.max(pga, pga * Math.pow(ratio / 1000.0, 0.35)) * Math.max(1.0, Math.min(ratioGain * 0.25, 45.0));
            }
        } else {
            // Unknown sensitivity (Seedlink remote stations): ratio-only empirical fit
            result = 0.040 * Math.pow(ratio, 0.74);
        }

        // ==================== 临时调试日志（备用，默认注释） ====================
        // 需要时取消注释：写入 intensity_debug.log，含地震信息、震中距与 P/S 波阶段。
        // 取消注释前请确认下方 debugCounter/DEBUG_FILE/calcPhase 及文件头部 import 已恢复。
        /*
        try {
            long idx = debugCounter.incrementAndGet();
            // 阶段提前计算（需第一个地震的震中距）：P后S前窗口短暂，该阶段强制逐条记录
            String phase = "?";
            Earthquake q0 = null;
            if (GlobalQuake.instance != null && GlobalQuake.instance.getEarthquakeAnalysis() != null
                    && !GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes().isEmpty()) {
                q0 = GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes().get(0);
                double dist0 = GeoUtils.greatCircleDistance(
                        station.getLatitude(), station.getLongitude(), q0.getLat(), q0.getLon());
                phase = calcPhase(station, q0, dist0);
            }
            if ("P".equals(phase) || idx % 10 == 1) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("[DEBUG] %s | ratio=%.1f vel=%.4f sens=%.2E pga=%.2f",
                        station.getStationCode(), ratio,
                        station.getMaxVelocity60S(), station.getSensitivity(), result));
                if (GlobalQuake.instance != null && GlobalQuake.instance.getEarthquakeAnalysis() != null) {
                    var quakes = GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes();
                    if (quakes.isEmpty()) {
                        sb.append(" | 无活动地震");
                    } else {
                        sb.append(" | 地震[");
                        for (int i = 0; i < quakes.size(); i++) {
                            Earthquake q = quakes.get(i);
                            double dist = GeoUtils.greatCircleDistance(
                                    station.getLatitude(), station.getLongitude(), q.getLat(), q.getLon());
                            if (i > 0) sb.append("; ");
                            sb.append(String.format("M%.1f 距%.0fkm", q.getMag(), dist));
                        }
                        sb.append("]");
                        sb.append(" | 阶段=").append(phase);
                    }
                }
                Level level = IntensityScales.getIntensityScale().getLevel(result);
                sb.append(" | 烈度=").append(level == null ? "N/A" : level.toString());
                try (PrintWriter pw = new PrintWriter(new FileWriter(DEBUG_FILE, true))) {
                    pw.println(sb);
                }
            }
        } catch (Exception ignored) {
        }
        */

        return result;
    }

    /*
    // ==================== 调试用字段与方法（备用，默认注释） ====================
    private static final AtomicLong debugCounter = new AtomicLong();
    private static final String DEBUG_FILE = "intensity_debug.log";

    private static String calcPhase(AbstractStation station, Earthquake q, double dist) {
        // 判断当前时刻处于哪个波相阶段：前=P波未到、P=P波已到S波未到、S=S波已到。
        // 三点必须一致，否则阶段会系统性错乱（曾出现全 S / 全 P）：
        //   1) 时钟：用 GlobalQuake.instance.currentTimeMillis()（Playground 为模拟时钟，
        //      origin 也是模拟时钟；若用 System.currentTimeMillis() 会差一个固定偏移导致永远 S）；
        //   2) bias+delay：站实际看到波形的时刻 = origin + 走时 + bias（波形时间偏移）
        //      + delay（采样延迟，见 WaveformGenerator.now = currentTimeMillis - delay）；
        //   3) 走时：TauP 理论走时。
        try {
            double pTravel = TauPTravelTimeCalculator.getPWaveTravelTime(q.getDepth(), TauPTravelTimeCalculator.toAngle(dist));
            double sTravel = TauPTravelTimeCalculator.getSWaveTravelTime(q.getDepth(), TauPTravelTimeCalculator.toAngle(dist));
            if (pTravel == TauPTravelTimeCalculator.NO_ARRIVAL || sTravel == TauPTravelTimeCalculator.NO_ARRIVAL) {
                return "?";
            }
            long now = GlobalQuake.instance.currentTimeMillis();
            long offset = 0;
            if (station instanceof PlaygroundStation ps) {
                offset = ps.getBias() + ps.getDelay();
            }
            long pArrival = q.getOrigin() + (long) (pTravel * 1000) + offset;
            long sArrival = q.getOrigin() + (long) (sTravel * 1000) + offset;
            if (now < pArrival) return "前";
            if (now < sArrival) return "P";
            return "S";
        } catch (Exception e) {
            return "?";
        }
    }
    */

    public static Level computeIntensityLevel(AbstractStation station) {
        double pga = computePGA(station);
        if (pga < 0.5) return null;
        return IntensityScales.getIntensityScale().getLevel(pga);
    }

    @Override
    public Collection<AbstractStation> getElements() {
        return globalStations;
    }

    @Override
    public void createPolygon(GlobeRenderer renderer, RenderEntity<AbstractStation> entity, RenderProperties renderProperties) {
        RenderElement elementStationCircle = entity.getRenderElement(0);
        RenderElement elementStationSquare = entity.getRenderElement(1);
        if (elementStationCircle.getPolygon() == null) {
            elementStationCircle.setPolygon(new Polygon3D());
        }
        if (elementStationSquare.getPolygon() == null) {
            elementStationSquare.setPolygon(new Polygon3D());
        }

        double size = Math.min(36, renderer.pxToDeg(7.0, renderProperties)) * Settings.stationsSizeMul;

        if(Math.abs(size - entity.getOriginal()._lastRenderSize) < 0.1){
            return;
        }

        entity.getOriginal()._lastRenderSize = size;

        InputType inputType = entity.getOriginal().getInputType();

        StationsShape shape = StationsShape.values()[Settings.stationsShapeIndex];

        if(shape == StationsShape.CIRCLE){
            inputType = InputType.UNKNOWN;
        } else if(shape == StationsShape.TRIANGLE){
            inputType = InputType.VELOCITY;
        }

        switch (inputType){
            case UNKNOWN ->
                    renderer.createCircle(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size, 0, 30);
            case VELOCITY ->
                    renderer.createTriangle(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size * 1.41, 0, 0);
            case ACCELERATION ->
                    renderer.createTriangle(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size * 1.41, 0, 180);
            case DISPLACEMENT ->
                    renderer.createSquare(elementStationCircle.getPolygon(),
                            entity.getOriginal().getLatitude(),
                            entity.getOriginal().getLongitude(),
                            size * 1.41, 0);
        }

        renderer.createSquare(elementStationSquare.getPolygon(),
                entity.getOriginal().getLatitude(),
                entity.getOriginal().getLongitude(),
                size * 2.0, 0);
    }

    @Override
    public boolean needsUpdateEntities() {
        return true;
    }

    @Override
    public boolean needsCreatePolygon(RenderEntity<AbstractStation> entity, boolean propertiesChanged) {
        return propertiesChanged;
    }

    @Override
    public boolean needsProject(RenderEntity<AbstractStation> entity, boolean propertiesChanged) {
        return propertiesChanged;
    }

    @Override
    public void project(GlobeRenderer renderer, RenderEntity<AbstractStation> entity, RenderProperties renderProperties) {
        RenderElement elementStationCircle = entity.getRenderElement(0);
        elementStationCircle.getShape().reset();
        elementStationCircle.shouldDraw = renderer.project3D(elementStationCircle.getShape(), elementStationCircle.getPolygon(), true, renderProperties);

        RenderElement elementStationSquare = entity.getRenderElement(1);
        elementStationSquare.getShape().reset();
        elementStationSquare.shouldDraw = renderer.project3D(elementStationSquare.getShape(), elementStationSquare.getPolygon(), true, renderProperties);
    }

    @Override
    public boolean isEntityVisible(RenderEntity<?> entity) {
        AbstractStation station = (AbstractStation) entity.getOriginal();

        if(Settings.hideDeadStations && !station.hasDisplayableData()){
            return false;
        }

        return !station.disabled;
    }

    @Override
    public void renderAll(GlobeRenderer renderer, Graphics2D graphics, RenderProperties properties) {
        // 烈度大的测站浮在上面：先画无烈度站，再按烈度升序画有烈度站（后画的盖在先画的上面）
        List<RenderEntity<AbstractStation>> visible = getEntities().stream()
                .filter(this::isEntityVisible)
                .collect(Collectors.toList());

        Map<RenderEntity<AbstractStation>, Integer> levelIdxMap = new HashMap<>();
        List<Level> levels = IntensityScales.getIntensityScale().getLevels();
        for (RenderEntity<AbstractStation> entity : visible) {
            Level level = computeIntensityLevel((AbstractStation) entity.getOriginal());
            levelIdxMap.put(entity, level == null ? -1 : levels.indexOf(level));
        }

        visible.stream()
                .sorted(Comparator.comparingInt(levelIdxMap::get))
                .forEach(entity -> {
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    render(renderer, graphics, entity, properties);
                });
    }

    @Override
    public void render(GlobeRenderer renderer, Graphics2D graphics, RenderEntity<AbstractStation> entity, RenderProperties renderProperties) {
        RenderElement elementStationCircle = entity.getRenderElement(0);

        if(!elementStationCircle.shouldDraw){
            return;
        }

        AbstractStation station = entity.getOriginal();
        Level level = computeIntensityLevel(station);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                Settings.antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

        Vector3D point3D = GlobeRenderer.createVec3D(getCenterCoords(entity));
        Point2D centerPoint = renderer.projectPoint(point3D, renderProperties);

        RenderElement elementStationSquare = entity.getRenderElement(1);

        if (level != null && centerPoint != null) {
            List<Level> levels = IntensityScales.getIntensityScale().getLevels();
            int levelIdx = levels.indexOf(level);
            double radiusPx = (8.0 + Math.max(0, levelIdx) * 0.6) * Settings.stationsSizeMul;
            int r = (int) Math.round(radiusPx);
            int cx = (int) Math.round(centerPoint.x);
            int cy = (int) Math.round(centerPoint.y);
            graphics.setColor(LevelPalette.bg(level));
            graphics.fillOval(cx - r, cy - r, r * 2, r * 2);
            graphics.setColor(LevelPalette.border(level));
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.drawOval(cx - r, cy - r, r * 2, r * 2);
            graphics.setStroke(new BasicStroke(1f));
            int fontSize = Math.max(12, Math.min(r * 2 / Math.max(1, level.getFullName().length()), r + 4));
            graphics.setFont(GQFonts.font(Font.BOLD, fontSize));
            FontMetrics fm = graphics.getFontMetrics();
            String label = level.getFullName();
            int tw = fm.stringWidth(label);
            int th = fm.getAscent();
            if (LevelPalette.noShadow()) {
                // 非默认配色方案：数字不加阴影
                graphics.setColor(LevelPalette.fg(level));
                graphics.drawString(label, cx - tw / 2, cy + th / 2 - 2);
            } else {
                graphics.setColor(Color.black);
                graphics.drawString(label, cx - tw / 2 + 1, cy + th / 2 - 2 + 1);
                graphics.setColor(Color.white);
                graphics.drawString(label, cx - tw / 2, cy + th / 2 - 2);
            }
        } else {
            graphics.setColor(getDisplayColor(station));
            graphics.fill(elementStationCircle.getShape());
        }

        boolean mouseNearby = renderer.getLastMouse() != null && renderer.hasMouseMovedRecently() && elementStationCircle.getShape().contains(renderer.getLastMouse());

        if (mouseNearby && renderProperties.scroll < 1) {
            graphics.setColor(Color.yellow);
            graphics.setStroke(new BasicStroke(2f));
            graphics.draw(elementStationCircle.getShape());
        }

        graphics.setStroke(new BasicStroke(1f));

        graphics.setFont(GQFonts.font(Font.PLAIN, 13));

        if(Settings.displayClusters){
            for(Event event2 : station.getAnalysis().getDetectedEvents()){
                Cluster cluster = event2.assignedCluster;
                if(cluster != null){
                    Color c = !event2.isValid() ? Color.gray : cluster.color;

                    int _y = (int) centerPoint.y + 4;
                    _y += 16;

                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

                    graphics.setColor(c);
                    graphics.draw(elementStationSquare.getShape());
                    graphics.drawString("Cluster #"+cluster.id, (int) centerPoint.x + 12, _y);
                }
            }
        } else if (station.isInEventMode() && ((System.currentTimeMillis() / 500) % 2 == 0)) {
            Color c = Color.green;

            double maxRatio = station.getMaxRatio60S();

            if (maxRatio >= RATIO_YELLOW) {
                c = Color.yellow;
            }

            if (maxRatio >= RATIO_RED) {
                c = Color.red;
            }

            graphics.setColor(c);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, Settings.antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.draw(elementStationSquare.getShape());
        }


        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawDetails(mouseNearby, renderProperties.scroll, centerPoint, graphics, station, renderer, entity, renderProperties, level);
    }

    private void drawDetails(boolean mouseNearby, double scroll, Point2D centerPoint, Graphics2D g, AbstractStation station, GlobeRenderer renderer,
                             RenderEntity<AbstractStation> entity, RenderProperties renderProperties, Level level) {
        int _y = (int) (7 + 6 * Settings.stationsSizeMul);
        if (mouseNearby && scroll < 1) {
            g.setColor(Color.white);
            String str = station.toString();

            if(centerPoint == null) {
                var point3D = GlobeRenderer.createVec3D(getCenterCoords(entity));
                centerPoint = renderer.projectPoint(point3D, renderProperties);
            }

            int x = (int) centerPoint.x;
            int y = (int) centerPoint.y;

            g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y - _y);
            str = station.getSeedlinkNetwork() == null ? "" : station.getSeedlinkNetwork().getName();
            g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y - _y - 15);

            if(!station.hasDisplayableData()){
                str = "No data";
                g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y + _y + 22);
            } else {
                long delay = station.getDelayMS();
                if (delay == Long.MIN_VALUE) {
                    g.setColor(Color.magenta);
                    str = "Replay";
                    g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y + _y + 22);
                } else {
                    FeatureSelectableStation.drawDelay(g, x, y + 33, delay, "Delay");
                }
            }
        }
        if (scroll < Settings.stationIntensityVisibilityZoomLevel || (mouseNearby && scroll < 1)) {
            if (level == null) {
                String str = !station.hasDisplayableData() ? "-.-" : "%.1f".formatted(station.getMaxRatio60S());
                g.setFont(GQFonts.font(Font.PLAIN, 13));
                g.setColor(station.getAnalysis().getStatus() == AnalysisStatus.EVENT ? Color.green : Color.LIGHT_GRAY);
                if(centerPoint == null) {
                    var point3D = GlobeRenderer.createVec3D(getCenterCoords(entity));
                    centerPoint = renderer.projectPoint(point3D, renderProperties);
                }

                int x = (int) centerPoint.x;
                int y = (int) centerPoint.y;
                g.drawString(str, x - g.getFontMetrics().stringWidth(str) / 2, y + _y + 9);
            }
        }
    }

    private Color getDisplayColor(AbstractStation station) {
        if(station.disabled){
            return Color.DARK_GRAY;
        }
        if (!station.hasData()) {
            return Color.gray;
        }

        if ((GlobalQuakeClient.instance == null && station.getAnalysis().getStatus() == AnalysisStatus.INIT) || !station.hasDisplayableData()) {
            return Color.lightGray;
        } else {
            return Scale.getColorRatio(station.getMaxRatio60S());
        }

    }

    @Override
    public Point2D getCenterCoords(RenderEntity<?> entity) {
        return new Point2D(((AbstractStation) (entity.getOriginal())).getLatitude(), ((AbstractStation) (entity.getOriginal())).getLongitude());
    }
}
