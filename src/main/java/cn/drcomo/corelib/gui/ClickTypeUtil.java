package cn.drcomo.corelib.gui;

import org.bukkit.event.inventory.ClickType;

/**
 * <h2>ClickType 工具类</h2>
 * 提供对 {@link ClickType} 的常用判定方法，便于在无完整 {@link ClickContext} 信息的情况下
 * 直接基于点击类型判断玩家的操作行为。
 * <p>所有方法都对入参进行 <code>null</code> 检查，若传入值为 <code>null</code> 则返回 <code>false</code>
 * （或在某些聚合判定方法中返回 <code>true</code>，详见各方法注释）。</p>
 *
 * <p>该类设计为纯静态工具类，构造函数被显式私有化。</p>
 */
public final class ClickTypeUtil {

    /** 私有化构造函数，防止实例化。 */
    private ClickTypeUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    // region 单项判定

    /** 是否为 Shift 点击 */
    public static boolean isShift(ClickType type) {
        return type != null && type.isShiftClick();
    }

    /** 是否为左键点击 */
    public static boolean isLeftClick(ClickType type) {
        return type != null && type.isLeftClick();
    }

    /** 是否为右键点击 */
    public static boolean isRightClick(ClickType type) {
        return type != null && type.isRightClick();
    }

    /** 是否为中键点击 */
    public static boolean isMiddleClick(ClickType type) {
        return type == ClickType.MIDDLE;
    }

    /** 是否使用数字键 */
    public static boolean isNumberKey(ClickType type) {
        return type == ClickType.NUMBER_KEY;
    }

    /** 是否按下丢弃键 */
    public static boolean isDrop(ClickType type) {
        return type == ClickType.DROP;
    }

    /** 是否为 Ctrl+丢弃 */
    public static boolean isControlDrop(ClickType type) {
        return type == ClickType.CONTROL_DROP;
    }

    /** 是否与副手交换 */
    public static boolean isSwapOffhand(ClickType type) {
        return type == ClickType.SWAP_OFFHAND;
    }

    // endregion

    // region 聚合判定

    /**
     * 是否为快捷键触发的点击操作。
     * 包括：Shift、与副手交换、数字键、双击、窗口边缘点击。
     */
    public static boolean isKeyboardTriggerClick(ClickType type) {
        return isShift(type)
                || isSwapOffhand(type)
                || isNumberKey(type)
                || type == ClickType.DOUBLE_CLICK
                || type == ClickType.WINDOW_BORDER_LEFT
                || type == ClickType.WINDOW_BORDER_RIGHT;
    }

    /**
     * 是否属于非普通点击 (除 Left 和 Right 点击外)。
     * 若 <code>type</code> 为 <code>null</code>，视为危险点击，返回 <code>true</code>。
     */
    public static boolean isDangerous(ClickType type) {
        if (type == null) {
            return true;
        }
        return type.isShiftClick()
                || type.isKeyboardClick()
                || type.isCreativeAction()
                || type == ClickType.DOUBLE_CLICK
                || type == ClickType.SWAP_OFFHAND
                || type == ClickType.UNKNOWN
                || type == ClickType.CONTROL_DROP
                || type == ClickType.NUMBER_KEY
                || type == ClickType.DROP;
    }

    // endregion
} 