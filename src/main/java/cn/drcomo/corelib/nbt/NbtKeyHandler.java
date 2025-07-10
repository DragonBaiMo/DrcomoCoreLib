package cn.drcomo.corelib.nbt;

/**
 * 子插件自行实现的前缀策略。
 */
public interface NbtKeyHandler {

    /** 判断完整键名是否属于本插件 */
    boolean isValidKey(String fullKey);

    /** 给用户自定义键添加前缀，返回完整键名 */
    String addPrefix(String key);

    /** 从完整键名去除前缀，返回用户自定义部分 */
    String removePrefix(String fullKey);
}
