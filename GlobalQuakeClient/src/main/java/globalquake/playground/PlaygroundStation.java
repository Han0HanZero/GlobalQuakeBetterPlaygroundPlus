package globalquake.playground;

import globalquake.core.analysis.Event;
import globalquake.core.station.AbstractStation;
import gqserver.api.packets.station.InputType;

public class PlaygroundStation extends AbstractStation {

    public static final double SAMPLE_RATE = 50;
    public long lastSampleTime = -1;
    private final StationWaveformGenerator generator;
    public static final double DEFAULT_SENSITIVITY = 7E10;
    public PlaygroundStation(String networkCode, String stationCode, String channelName, String locationCode, double lat, double lon, double alt, int id, double sensitivity) {
        super(networkCode, stationCode, channelName, locationCode, lat, lon, alt, id, null,
                sensitivity);
        getAnalysis().setSampleRate(SAMPLE_RATE);
        this.generator = new StationWaveformGenerator(this, id);
    }

    public PlaygroundStation(String stationCode, double lat, double lon, double alt, int id, double sensitivity) {
        this("", stationCode, "", "", lat, lon, alt, id, sensitivity);
    }

    @Override
    public void second(long time) {
        super.second(time);
        generator.second();
    }

    @Override
    public InputType getInputType() {
        return InputType.VELOCITY;
    }

    @Override
    public boolean hasData() {
        return lastSampleTime != -1;
    }

    @Override
    public boolean hasDisplayableData() {
        return hasData();
    }

    public int getNoise(long time) {
        return generator.getValue(time);
    }

    @Override
    public boolean isInEventMode() {
        Event event = getAnalysis() == null ? null : getAnalysis().getLatestEvent();
        return event != null && event.isValid() && !event.hasEnded();
    }

    public long getDelay() {
        return generator.getDelay();
    }

    /**
     * 重置 StationWaveformGenerator 的地震距离缓存 + 重置波形分析状态。
     * 对应「清除地震后再创建新地震时无法拾取」的修复：
     *   1) earthquakeDistancesMap 不清会导致新地震的 Distances 不被重新计算，
     *   2) ratioHistory 不清导致颜色不蓝，
     *   3) lastSampleTime=-1 让 WaveformGenerator 从当前时间-2分钟重新发样本。
     */
    public void resetPlaygroundStation() {
        generator.reset();
        this.reset();              // AbstractStation.reset() -> 清 ratioHistory
        getIntervals().clear();    // 清 ACTIVE/EVENT 状态区间
        clear();                   // 清 nearbyStations 缓存
        getAnalysis().setSampleRate(SAMPLE_RATE); // 重建 WaveformBuffer
        getAnalysis().fullReset(); // INIT 状态 + _maxRatio/_maxVelocity 归零
        getAnalysis().getDetectedEvents().clear();
        lastSampleTime = -1;
    }
}
