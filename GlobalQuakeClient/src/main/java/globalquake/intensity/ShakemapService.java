package globalquake.intensity;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import globalquake.core.GlobalQuake;
import globalquake.core.Settings;
import globalquake.core.earthquake.data.Earthquake;
import globalquake.core.earthquake.data.Hypocenter;
import globalquake.core.events.GlobalQuakeEventListener;
import globalquake.core.events.specific.QuakeArchiveEvent;
import globalquake.core.events.specific.QuakeCreateEvent;
import globalquake.core.events.specific.QuakeRemoveEvent;
import globalquake.core.events.specific.QuakeUpdateEvent;
import globalquake.core.intensity.CityIntensity;
import globalquake.core.intensity.IntensityScales;
import globalquake.events.specific.ShakeMapsUpdatedEvent;
import globalquake.client.GlobalQuakeLocal;
import globalquake.core.intensity.CityLocation;
import globalquake.plum.PlumService;
import globalquake.utils.GeoUtils;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShakemapService {

    private final Map<UUID, ShakeMap> shakeMaps = new ConcurrentHashMap<>();

    private final ExecutorService shakemapService = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService checkService = Executors.newSingleThreadScheduledExecutor();
    // 上次重建时的 PLUM 实测版本号（refreshShakemaps 增量重建依据）
    private volatile long lastMeasuredVersion = -1;

    private static final List<CityLocation> cities = new ArrayList<>();

    static {
        load();
    }

    private static void load() {
        int errors = 0;
        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource("cities/worldcities.csv")).openStream())).withSkipLines(1).build()) {
            String[] fields;
            while ((fields = reader.readNext()) != null) {
                String cityName = fields[1];
                double lat = Double.parseDouble(fields[2]);
                double lon = Double.parseDouble(fields[3]);

                int population;

                try {
                    population = Integer.parseInt(fields[9]);
                } catch (Exception e) {
                    population = -1;
                    errors++;
                }

                cities.add(new CityLocation(cityName, lat, lon, population));
            }
        } catch (IOException | CsvValidationException e) {
            Logger.error(e);
        }

        Logger.warn("%d cities have unknown population!".formatted(errors));
    }

    public ShakemapService() {
        GlobalQuake.instance.getEventHandler().registerEventListener(new GlobalQuakeEventListener() {
            @Override
            public void onQuakeCreate(QuakeCreateEvent event) {
                updateShakemap(event.earthquake());
            }

            @Override
            public void onQuakeArchive(QuakeArchiveEvent event) {
                removeShakemap(event.archivedQuake().getUuid());
            }

            @Override
            public void onQuakeUpdate(QuakeUpdateEvent event) {
                updateShakemap(event.earthquake());
            }

            @Override
            public void onQuakeRemove(QuakeRemoveEvent event) {
                removeShakemap(event.earthquake().getUuid());
            }
        });

        checkService.scheduleAtFixedRate(this::checkShakemaps, 0, 1, TimeUnit.MINUTES);
        // 模拟模式：周期重建活跃地震的 ShakeMap，让 PLUM 实测烈度变化即时反映到预估烈度六边形
        // （ShakeMap 本身是快照，仅 Create/Update 时重建一次，实测持续变化不触发重建）
        checkService.scheduleAtFixedRate(this::refreshShakemaps, 0, 1, TimeUnit.SECONDS);
    }

    private void refreshShakemaps() {
        try {
            if (!GlobalQuake.instance.isSimulation() || !Settings.plumEnabled || PlumService.getInstance() == null) {
                return;
            }
            // 实测烈度未变化（版本号相同）则跳过整轮重建：ShakeMap 生成（BFS+海洋判定）成本高，
            // 静止/清空状态无需每秒全量重建
            long version = PlumService.getInstance().getMeasuredVersion();
            if (version == lastMeasuredVersion) {
                return;
            }
            lastMeasuredVersion = version;
            List<Earthquake> quakes = new ArrayList<>(GlobalQuake.instance.getEarthquakeAnalysis().getEarthquakes());
            boolean changed = false;
            Set<UUID> active = new HashSet<>();
            for (Earthquake earthquake : quakes) {
                if (earthquake.getCluster() == null || earthquake.getCluster().getPreviousHypocenter() == null) {
                    continue;
                }
                active.add(earthquake.getUuid());
                ShakeMap updated = createShakemap(earthquake);
                ShakeMap old = shakeMaps.get(earthquake.getUuid());
                if (old == null || !sameHexes(old.getHexList(), updated.getHexList())) {
                    shakeMaps.put(earthquake.getUuid(), updated);
                    changed = true;
                }
            }
            // 清理残留快照（如修订时被移除的真实地震报）
            Iterator<UUID> it = shakeMaps.keySet().iterator();
            while (it.hasNext()) {
                UUID uuid = it.next();
                if (!active.contains(uuid)) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                GlobalQuakeLocal.instance.getLocalEventHandler().fireEvent(new ShakeMapsUpdatedEvent());
            }
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    /** 六边形集合是否完全一致（id + pga）。hexList 无序，按 id 映射比较。 */
    private boolean sameHexes(List<IntensityHex> a, List<IntensityHex> b) {
        if (a.size() != b.size()) {
            return false;
        }
        Map<Long, Double> map = new HashMap<>();
        for (IntensityHex h : a) {
            map.put(h.id(), h.pga());
        }
        for (IntensityHex h : b) {
            Double v = map.get(h.id());
            if (v == null || Double.compare(v, h.pga()) != 0) {
                return false;
            }
        }
        return true;
    }

    private void checkShakemaps() {
        try {
            for (Iterator<Map.Entry<UUID, ShakeMap>> iterator = shakeMaps.entrySet().iterator(); iterator.hasNext(); ) {
                var kv = iterator.next();
                UUID uuid = kv.getKey();
                if (GlobalQuake.instance.getEarthquakeAnalysis().getEarthquake(uuid) == null) {
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    private void removeShakemap(UUID uuid) {
        shakemapService.submit(() -> {
            try {
                shakeMaps.remove(uuid);
                GlobalQuakeLocal.instance.getLocalEventHandler().fireEvent(new ShakeMapsUpdatedEvent());
            } catch (Exception e) {
                Logger.error(e);
            }
        });
    }

    private void updateShakemap(Earthquake earthquake) {
        shakemapService.submit(() -> {
            try {
                shakeMaps.put(earthquake.getUuid(), createShakemap(earthquake));
                GlobalQuakeLocal.instance.getLocalEventHandler().fireEvent(new ShakeMapsUpdatedEvent());
                updateCities(earthquake);
            } catch (Exception e) {
                Logger.error(e);
            }
        });
    }

    private void updateCities(Earthquake earthquake) {
        List<CityIntensity> result = new ArrayList<>();
        double threshold = IntensityScales.getIntensityScale().getLevels().get(0).getPga();

        cities.forEach(cityLocation -> {
            double pga = calculatePGA(cityLocation, earthquake);
            if (pga >= threshold) {
                result.add(new CityIntensity(cityLocation, pga));
            }
        });

        result.sort(Comparator.comparing(cityIntensity -> -cityIntensity.pga()));

        earthquake.cityIntensities = result;
    }

    private double calculatePGA(CityLocation cityLocation, Earthquake earthquake) {
        double dist = GeoUtils.geologicalDistance(earthquake.getLat(), earthquake.getLon(), -earthquake.getDepth(),
                cityLocation.lat(), cityLocation.lon(), 0);
        return GeoUtils.pgaFunction(earthquake.getMag(), dist, earthquake.getDepth());
    }

    private ShakeMap createShakemap(Earthquake earthquake) {
        Hypocenter hyp = earthquake.getCluster().getPreviousHypocenter();
        double mag = hyp.magnitude + hyp.depth / 200.0;
        mag += Settings.shakemapQualityOffset;
        // 模拟模式 + PLUM 激活：用 PLUM 网格分辨率（大六边形），实测烈度驱动（假定报阶段理论 pga 极小）
        if (GlobalQuake.instance.isSimulation() && Settings.plumEnabled && PlumService.getInstance() != null) {
            return new ShakeMap(hyp, Settings.plumResolution);
        }
        return new ShakeMap(hyp, mag <= 4.9 ? 6 : mag < 6.4 ? 5 : mag < 8.5 ? 4 : 3);
    }

    public void stop() {
        GlobalQuake.instance.stopService(shakemapService);
        GlobalQuake.instance.stopService(checkService);
    }

    public Map<UUID, ShakeMap> getShakeMaps() {
        return shakeMaps;
    }

    public void clear() {
        shakeMaps.clear();
        GlobalQuakeLocal.instance.getLocalEventHandler().fireEvent(new ShakeMapsUpdatedEvent());
    }
}
