package globalquake.plum;

import globalquake.ui.globe.Point2D;

/**
 * PLUM 网格单元（h3 六边形）。
 * pga 为「覆盖该网格的测站实测烈度最大值」与「点源预估」融合后的最终值，
 * 由 PlumService 每帧刷新。sourcePga 记录纯实测值，供ハイブリッド融合取较大值。
 */
public record PlumCell(long id, Point2D center, double pga, double sourcePga, int stationCount) {
}
