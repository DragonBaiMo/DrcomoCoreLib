package cn.drcomo.corelib.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import cn.drcomo.corelib.util.DebugUtil;

/**
 * GUI 辅助工具类。
 * <p>提供危险点击判断、清理光标以及安全播放音效等静态方法。</p>
 */
public final class GuiUtil {

    /** DrcomoCoreLib 专用日志工具 */
    private static final DebugUtil DEBUG = new DebugUtil(
            JavaPlugin.getProvidingPlugin(GuiUtil.class),
            DebugUtil.LogLevel.INFO);

    /** 私有构造，禁止实例化 */
    private GuiUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断点击类型是否危险，需拦截。
     *
     * @param click 点击类型
     * @return true 表示危险
     */
    public static boolean isDangerousClick(ClickType click) {

        if (click == null) {
            return true;
        }
        return click.isShiftClick()
                || click.isKeyboardClick()
                || click.isCreativeAction()
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.UNKNOWN;

        if (click == null) return true;
        return click.isShiftClick() || click.isKeyboardClick()
                || click.isCreativeAction() || click == ClickType.DOUBLE_CLICK;

    }

    /**
     * 清空玩家的鼠标光标物品。
     *
     * @param player 玩家实例
     * @param event  事件对象
     */
    public static void clearCursor(Player player, InventoryClickEvent event) {
        if (player == null || event == null) return;
        try {
            event.setCursor(null);
            player.updateInventory();
        } catch (Exception e) {
            DEBUG.error("clear cursor error", e);
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
    public static void safePlaySound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            DEBUG.error("play sound fail: " + sound, e);
        }
    }
}
