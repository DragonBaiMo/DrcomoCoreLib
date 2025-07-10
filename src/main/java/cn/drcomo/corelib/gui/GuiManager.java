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
                || click == ClickType.UNKNOWN;
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
} 