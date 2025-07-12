package cn.drcomo.corelib.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import cn.drcomo.corelib.util.DebugUtil;

/**
 * GUI 管理器。
 * <p>替代过去的静态 {@code GuiUtil} 工具类，采用依赖注入方式持有日志工具，
 * 并以实例方法形式提供 GUI 相关的辅助能力。</p>
 */
public class GuiManager {

    /** 日志工具，由调用方注入 */
    private final DebugUtil logger;

    /**
     * 构造函数。
     *
     * @param logger 日志工具实例
     */
    public GuiManager(DebugUtil logger) {
        this.logger = logger;
    }

    /**
     * 判断点击类型是否危险，需拦截。
     *
     * @param click 点击类型
     * @return true 表示危险
     */
    public boolean isDangerousClick(ClickType click) {
        if (click == null) {
            return true;
        }
        return click.isShiftClick()
                || click.isKeyboardClick()
                || click.isCreativeAction()
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.UNKNOWN
                || click == ClickType.CONTROL_DROP
                || click == ClickType.NUMBER_KEY
                || click == ClickType.DROP;
    }

    /**
     * 清空玩家的鼠标光标物品。
     *
     * @param player 玩家实例
     * @param event  事件对象
     */
    public void clearCursor(Player player, InventoryClickEvent event) {
        if (player == null || event == null) return;
        try {
            event.setCursor(null);
            player.updateInventory();
        } catch (Exception e) {
            logger.error("clear cursor error", e);
        }
    }

    /**
     * 安全播放音效，内部捕获异常并记录。
     *
     * @param player 玩家
     * @param sound  音效枚举
     * @param volume 音量
     * @param pitch  音调
     */
    public void safePlaySound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            logger.error("play sound fail: " + sound, e);
        }
    }

    /**
     * 判断此次点击是否发生在 GUI 顶部容器（自定义界面）区域 —— 基于 rawSlot 的方式。
     * <p>原理：Bukkit 约定 rawSlot 小于顶部容器大小时即处于 GUI 区域。</p>
     *
     * @param event 事件对象
     * @return true 表示点击发生在 GUI 区域
     */
    public boolean isGuiAreaByRawSlot(InventoryClickEvent event) {
        if (event == null) return false;
        return event.getRawSlot() < event.getInventory().getSize();
    }

    /**
     * 判断此次点击是否发生在 GUI 顶部容器（自定义界面）区域 —— 基于 clickedInventory 引用比较方式。
     * <p>原理：只有当 {@code event.getClickedInventory()} 与 {@code event.getInventory()} 指向相同实例时，
     * 才视为玩家点击了自定义 GUI；若为玩家背包槽位，则两者不同。</p>
     *
     * @param event 事件对象
     * @return true 表示点击发生在 GUI 区域
     */
    public boolean isGuiAreaByInventory(InventoryClickEvent event) {
        if (event == null) return false;
        return event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getInventory());
    }
} 