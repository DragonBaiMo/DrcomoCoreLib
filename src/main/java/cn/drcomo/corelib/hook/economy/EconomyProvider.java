package cn.drcomo.corelib.hook.economy;

import org.bukkit.entity.Player;

/**
 * 经济服务提供者接口（前置工具）。<br>
 * 子插件或核心库可通过实现此接口来桥接不同的经济插件（如 Vault、PlayerPoints 等）。
 */
public interface EconomyProvider {

    /**
     * 判断当前经济服务是否可用（插件已加载且服务正常）。
     *
     * @return 如果可用则返回 true，否则返回 false
     */
    boolean isEnabled();

    /**
     * 获取该经济服务的名称，用于日志或界面显示。
     *
     * @return 经济服务名称
     */
    String getName();

    /**
     * 检查玩家是否拥有至少指定数量的货币。
     *
     * @param player 玩家实例
     * @param amount 金额
     * @return 如果玩家余额 ≥ amount 且服务可用，则返回 true，否则返回 false
     */
    boolean hasBalance(Player player, double amount);

    /**
     * 从玩家账户中提取指定数量的货币。
     *
     * @param player 玩家实例
     * @param amount 金额
     * @return 如果提取成功且服务可用，则返回 true，否则返回 false
     */
    EconomyResponse withdraw(Player player, double amount);

    /**
     * 向玩家账户存入指定数量的货币。
     *
     * @param player 玩家实例
     * @param amount 金额
     * @return 如果存入成功且服务可用，则返回 true，否则返回 false
     */
    EconomyResponse deposit(Player player, double amount);

    /**
     * 获取玩家当前的货币余额。
     *
     * @param player 玩家实例
     * @return 如果服务可用返回玩家余额，否则返回 0
     */
    double getBalance(Player player);

    /**
     * 将数字金额格式化为显示字符串（包含货币单位或分隔符）。
     *
     * @param amount 要格式化的金额
     * @return 格式化后的字符串
     */
    String format(double amount);
}
