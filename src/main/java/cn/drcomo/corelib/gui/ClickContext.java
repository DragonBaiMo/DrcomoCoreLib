package cn.drcomo.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * GUI 点击上下文，封装点击事件的关键信息。
 *
 * <p>该记录类用于在分发器和业务回调之间传递统一的数据。</p>
 */
public record ClickContext(
        Player player,
        String sessionId,
        int slot,
        ClickType clickType,
        ItemStack cursor,
        ItemStack currentItem,
        Inventory inventory) {

    /**
     * 根据事件与会话管理器创建上下文。
     *
     * @param e         原始事件
     * @param sessionMgr 会话管理器
     * @return 构建得到的上下文
     */
    public static ClickContext from(InventoryClickEvent e, GUISessionManager sessionMgr) {
        Player p = (Player) e.getWhoClicked();
        String sid = sessionMgr.getCurrentSessionId(p);
        return new ClickContext(p, sid, e.getSlot(), e.getClick(), e.getCursor(), e.getCurrentItem(), e.getInventory());
    }

    /**
     * 是否为 Shift 点击。
     */
    public boolean isShift() {
        return ClickTypeUtil.isShift(clickType);
    }

    /**
     * 是否为左键点击。
     */
    public boolean isLeftClick() {
        return ClickTypeUtil.isLeftClick(clickType);
    }

    /**
     * 是否为右键点击。
     */
    public boolean isRightClick() {
        return ClickTypeUtil.isRightClick(clickType);
    }

    /**
     * 是否为中键点击。
     */
    public boolean isMiddleClick() {
        return ClickTypeUtil.isMiddleClick(clickType);
    }

    /**
     * 是否使用数字键。
     */
    public boolean isNumberKey() {
        return ClickTypeUtil.isNumberKey(clickType);
    }

    /**
     * 是否按下丢弃键。
     */
    public boolean isDrop() {
        return ClickTypeUtil.isDrop(clickType);
    }

    /**
     * 是否为 Ctrl+丢弃。
     */
    public boolean isControlDrop() {
        return ClickTypeUtil.isControlDrop(clickType);
    }

    /**
     * 是否为与副手交换。
     */
    public boolean isSwapOffhand() {
        return ClickTypeUtil.isSwapOffhand(clickType);
    }

    /**
     * 是否为快捷键操作点击。
     * 包括 Shift+点击、副手切换、数字键点击、双击、窗口边缘点击
     */
    public boolean isKeyboardTriggerClick() {
        return ClickTypeUtil.isKeyboardTriggerClick(clickType);
    }

    /**
     * 是否属于非普通点击 (除 Left 和 Right 点击外)。
     */
    public boolean isDangerous() {
        return ClickTypeUtil.isDangerous(clickType);
    }
}
