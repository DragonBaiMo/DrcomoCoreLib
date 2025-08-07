package cn.drcomo.corelib.message;

import org.bukkit.entity.Player;

/**
 * 内部占位符解析器接口。
 * <p>
 * 用于处理形如 {key[:args]} 的占位符，冒号后的参数将按顺序传递。
 * </p>
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /**
     * 解析占位符。
     *
     * @param player  当前玩家，可为 null
     * @param args    冒号分隔的参数列表，若无参数则为空数组
     * @return 解析后的字符串
     */
    String resolve(Player player, String[] args);
}

