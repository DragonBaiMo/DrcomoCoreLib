package cn.drcomo.corelib.gui.interfaces;

import cn.drcomo.corelib.gui.ClickContext;

/**
 * GUI 点击回调函数式接口。
 */
@FunctionalInterface
public interface ClickAction {
    /**
     * 执行点击逻辑。
     *
     * @param ctx 点击上下文
     */
    void execute(ClickContext ctx);
}
