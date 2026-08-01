package globalquake.events.specific;

import globalquake.events.GlobalQuakeLocalEventListener;

/**
 * PLUM 状态刷新事件：每次 PlumService 计算完一轮后触发（当前无渲染监听，
 * PLUM 预报烈度已融入系统 ShakeMap 六边形）。
 */
public class PlumUpdatedEvent implements GlobalQuakeLocalEvent {

    @Override
    public void run(GlobalQuakeLocalEventListener eventListener) {
        eventListener.onPlumUpdated(this);
    }

    @Override
    public String toString() {
        return "PlumUpdatedEvent{}";
    }
}
