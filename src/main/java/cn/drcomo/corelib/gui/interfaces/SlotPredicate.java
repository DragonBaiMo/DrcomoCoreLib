package cn.drcomo.corelib.gui.interfaces;

/**
 * 槽位过滤函数式接口，用于匹配点击槽位。
 */
@FunctionalInterface
public interface SlotPredicate {
    /**
     * 判断指定槽位是否匹配。
     *
     * @param slot 槽位序号
     * @return true 表示匹配
     */
    boolean test(int slot);
}
