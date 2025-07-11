package cn.drcomo.corelib.hook.economy.provider;

import cn.drcomo.corelib.hook.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import cn.drcomo.corelib.hook.economy.EconomyProvider;

import cn.drcomo.corelib.util.DebugUtil;

/**
 * Vault 经济提供者 —— 前置工具类（动态检测 Vault 与经济服务）。
 */
public class VaultEconomyProvider implements EconomyProvider {

    private final Plugin plugin;
    private final DebugUtil logger;
    private net.milkbowl.vault.economy.Economy economy;

    /**
     * 构造函数：动态检测 Vault 插件与 Economy 服务注册情况。
     *
     * @param plugin  你的主插件实例
     * @param logger  已实例化的 DebugUtil，用于日志输出
     */
    public VaultEconomyProvider(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;

        // 检测 Vault 插件是否启用
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            logger.warn("Vault 插件未启用，VaultEconomyProvider 将不可用");
            return;
        }

        // 检测 Economy 服务提供者
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
            Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) {
            logger.warn("未找到注册的 Economy 服务（请安装如 EssentialsX、CMI 等经济插件）");
            return;
        }

        this.economy = rsp.getProvider();
        logger.info("Vault 经济服务已连接: " + economy.getName());
    }

    @Override
    public boolean isEnabled() {
        return economy != null;
    }

    @Override
    public String getName() {
        return isEnabled() ? economy.getName() : "Vault (不可用)";
    }

    @Override
    public boolean hasBalance(Player player, double amount) {
        return isEnabled() && economy.has(player, amount);
    }

    @Override
    public EconomyResponse withdraw(Player player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(false, "Vault 未启用");
        }
        net.milkbowl.vault.economy.EconomyResponse resp = economy.withdrawPlayer(player, amount);
        if (!resp.transactionSuccess()) {
            logger.warn("Vault 提取失败: " + resp.errorMessage);
            return new EconomyResponse(false, resp.errorMessage);
        }
        return new EconomyResponse(true, null);
    }

    @Override
    public EconomyResponse deposit(Player player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(false, "Vault 未启用");
        }
        net.milkbowl.vault.economy.EconomyResponse resp = economy.depositPlayer(player, amount);
        if (!resp.transactionSuccess()) {
            logger.warn("Vault 存入失败: " + resp.errorMessage);
            return new EconomyResponse(false, resp.errorMessage);
        }
        return new EconomyResponse(true, null);
    }

    @Override
    public double getBalance(Player player) {
        return isEnabled() ? economy.getBalance(player) : 0;
    }

    @Override
    public String format(double amount) {
        return isEnabled() ? economy.format(amount) : String.valueOf(amount);
    }
}
