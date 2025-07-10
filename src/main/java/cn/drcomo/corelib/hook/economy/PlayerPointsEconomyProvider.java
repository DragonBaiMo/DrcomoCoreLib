package cn.drcomo.corelib.hook.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import cn.drcomo.corelib.hook.EconomyProvider; // 或者你自定义的接口包

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.util.DebugUtil.LogLevel;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI; // 新增导入：PlayerPointsAPI 类
import cn.drcomo.corelib.hook.economy.EconomyResponse;

/**
 * PlayerPoints 经济提供者 —— 前置工具类（动态检测 PlayerPoints 插件）。
 */
public class PlayerPointsEconomyProvider implements EconomyProvider {

    private final Plugin plugin;
    private final DebugUtil logger;
    private PlayerPoints playerPoints;
    private PlayerPointsAPI api; // 改动：类型修正为 PlayerPointsAPI（原为 PlayerPoints，导致类型不兼容）

    /**
     * 构造函数：动态检测 PlayerPoints 插件与 API 接口。
     *
     * @param plugin   你的主插件实例
     * @param logLevel 日志级别
     */
    public PlayerPointsEconomyProvider(Plugin plugin, LogLevel logLevel) {
        this.plugin = plugin;
        this.logger = new DebugUtil(plugin, logLevel);

        // 检测 PlayerPoints 插件是否启用
        if (!Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            logger.warn("PlayerPoints 插件未启用，PlayerPointsEconomyProvider 将不可用");
            return;
        }

        // 获取 PlayerPoints 主实例
        org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pl instanceof PlayerPoints) {
            this.playerPoints = (PlayerPoints) pl;
            this.api = playerPoints.getAPI();
            logger.info("PlayerPoints API 已连接");
        } else {
            logger.warn("无法获取 PlayerPoints 实例");
        }
    }

    @Override
    public boolean isEnabled() {
        return api != null;
    }

    @Override
    public String getName() {
        return isEnabled() ? playerPoints.getName() : "PlayerPoints (不可用)";
    }

    @Override
    public boolean hasBalance(Player player, double amount) {
        return isEnabled() && api.look(player.getUniqueId()) >= amount;
    }

    @Override
    public EconomyResponse withdraw(Player player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(false, "PlayerPoints 未启用");
        }
        if (!hasBalance(player, amount)) {
            return new EconomyResponse(false, "余额不足");
        }
        api.take(player.getUniqueId(), (int) Math.ceil(amount));
        return new EconomyResponse(true, null);
    }

    @Override
    public EconomyResponse deposit(Player player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(false, "PlayerPoints 未启用");
        }
        api.give(player.getUniqueId(), (int) Math.ceil(amount));
        return new EconomyResponse(true, null);
    }

    @Override
    public double getBalance(Player player) {
        return isEnabled() ? api.look(player.getUniqueId()) : 0;
    }

    @Override
    public String format(double amount) {
        int pts = (int) Math.ceil(amount);
        return pts + " 点数";
    }
}