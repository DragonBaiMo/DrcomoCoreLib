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
        return clickType.isShiftClick();
    }

    /**
     * 是否使用数字键。
     */
    public boolean isNumberKey() {
        return clickType == ClickType.NUMBER_KEY;
    }

    /**
     * 是否属于危险点击。
     */
    public boolean isDangerous() {
        return GuiUtil.isDangerousClick(clickType);
    }
}
