package globalquake.intensity;

import com.uber.h3core.H3Core;
import com.uber.h3core.LengthUnit;
import com.uber.h3core.util.LatLng;
import globalquake.core.GlobalQuake;
import globalquake.core.earthquake.data.Hypocenter;
import globalquake.core.intensity.IntensityScale;
import globalquake.core.intensity.IntensityScales;
import globalquake.core.intensity.Level;
import globalquake.core.regions.Regions;
import globalquake.plum.PlumService;
import globalquake.ui.globe.Point2D;
import globalquake.utils.GeoUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ShakeMap {

    private static H3Core h3;
    private final int res;
    private double maxPGA;

    public static void init() throws IOException{
        h3 = H3Core.newInstance();
    }

    private List<IntensityHex> hexList = new ArrayList<>();

    public ShakeMap(Hypocenter hypocenter, int res) {
        this.res = res;
        generate(hypocenter, res);
    }

    private void generate(Hypocenter hypocenter, int res) {
        IntensityScale intensityScale = IntensityScales.getIntensityScale();
        // 模拟模式 + PLUM 激活：用 PLUM 实测修正预估烈度（实测驱动，禁止纯理论 PGA 预测）
        PlumService plum = (GlobalQuake.instance.isSimulation() && globalquake.core.Settings.plumEnabled)
                ? PlumService.getInstance() : null;
        double pga = GeoUtils.pgaFunction(hypocenter.magnitude, hypocenter.depth, hypocenter.depth);
        double startLat = hypocenter.lat;
        double startLon = hypocenter.lon;
        if (plum != null && plum.isMeasuredActive()) {
            double maxMeasured = plum.getMaxMeasuredPga();
            Point2D maxPos = plum.getMaxMeasuredPos();
            // 实测烈度高于理论震中时，以实测最大位置作为 BFS 起点（假定报阶段理论 pga 极小，全靠实测驱动）
            if (maxMeasured > pga && maxPos != null) {
                startLat = maxPos.x;
                startLon = maxPos.y;
                pga = maxMeasured;
            }
        }
        Level level = intensityScale.getLevel(pga);
        if(level == null){
            return;
        }

        long id = h3.latLngToCell(startLat, startLon, res);

        LatLng latLng = h3.cellToLatLng(id);
        IntensityHex intensityHex = new IntensityHex(id, pga,
                new Point2D(latLng.lat, latLng.lng));
        hexList = new ArrayList<>(bfs(intensityHex, hypocenter, intensityScale, res, plum));
        maxPGA = hexList.stream().map(IntensityHex::pga).max(Double::compareTo).orElse(0.0);
    }

    private Set<IntensityHex> bfs(IntensityHex intensityHex, Hypocenter hypocenter, IntensityScale intensityScale, int res, PlumService plum) {
        Set<IntensityHex> result = new HashSet<>();
        Set<Long> visited = new HashSet<>();

        Queue<IntensityHex> pq = new PriorityQueue<>();
        pq.add(intensityHex);

        while(!pq.isEmpty()) {
            IntensityHex current = pq.remove();
            result.add(current);

            // 只扩展直接邻居（1 环 7 格）。gridDisk 的 k 是环数，误传 res（4~8）会每次生成 61~217 个候选格，
            // 使一次 ShakeMap 生成产生数万次几何计算（refreshShakemaps 每秒重建一次 → 持续高 CPU）
            for (long neighbor : h3.gridDisk(current.id(), 1)) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                LatLng latLng = h3.cellToLatLng(neighbor);
                double dist = GeoUtils.geologicalDistance(hypocenter.lat, hypocenter.lon, -hypocenter.depth, latLng.lat, latLng.lng, 0);
                dist = Math.max(0, dist - h3.getHexagonEdgeLengthAvg(res, LengthUnit.km) * 0.5);
                double pga = GeoUtils.pgaFunction(hypocenter.magnitude, dist, hypocenter.depth);
                if (plum != null) {
                    // ハイブリッド：与 PLUM 实测取较大值（cell 聚合查表，避免每格全站遍历导致大区域刷新卡顿）
                    pga = Math.max(pga, plum.getMeasuredPgaAtCell(latLng.lat, latLng.lng, res));
                }

                Level level = intensityScale.getLevel(pga);
                if (level == null) {
                    continue;
                }


                IntensityHex neighborHex = new IntensityHex(neighbor, pga, new Point2D(latLng.lat, latLng.lng));

                visited.add(neighbor);

                pq.add(neighborHex);
            }
        }

        boolean uhd = res >= 6;
        return result.parallelStream().filter(intensityHex1 -> !isOcean(intensityHex1.id(), uhd)).collect(Collectors.toSet());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isOcean(long id, boolean uhd) {
        List<LatLng> coords = h3.cellToBoundary(id);
        coords.add(h3.cellToLatLng(id));
        return coords.stream().allMatch(coord -> Regions.isOcean(coord.lat, coord.lng, uhd));
    }

    public List<IntensityHex> getHexList() {
        return hexList;
    }

    public double getMaxPGA() {
        return maxPGA;
    }

    public int getRes() {
        return res;
    }
}
