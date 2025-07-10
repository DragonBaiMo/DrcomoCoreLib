package cn.drcomo.corelib.nbt;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBTList;
import de.tr7zw.nbtapi.iface.ReadableNBTList;
import de.tr7zw.nbtapi.iface.ReadWriteNBTCompoundList;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import org.bukkit.inventory.ItemStack;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.util.DebugUtil.LogLevel;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 通用 NBT 工具类，用于操作 Bukkit ItemStack 的 NBT 数据。
 * 替换旧版实现，兼容 NBT-API v2.15.0，支持读取、写入、复制等操作。
 *
 * <p>依赖外部包：
 * - NBT-API v2.15.0：提供 NBT、NBTType、ReadableItemNBT、ReadWriteItemNBT 等接口和类。
 * - Bukkit API：ItemStack 类（无需额外处理）。
 * - 内部包：DebugUtil、NbtKeyHandler。
 *
 * @author [Your Name or Drcomo Team]
 * @version 1.0
 * @since 2025-07-10
 */
public class NBTUtil {

    private final DebugUtil logger;

    /**
     * 构造 NBTUtil 实例，基于指定的键处理策略。
     *
     * @param handler 键处理策略实例，不能为 null。
     * @param logger  DebugUtil 实例，用于日志输出
     * @throws NullPointerException 若 handler 为 null。
     */
    public NBTUtil(NbtKeyHandler handler, DebugUtil logger) {
        this.keyHandler = Objects.requireNonNull(handler, "NbtKeyHandler 不能为空");
        this.logger = logger;
    }

    /* ===== 通用方法：不依赖前缀 ===== */

    /**
     * 从 ItemStack 的 NBT 中读取整数值。
     *
     * @param item ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @param def 默认值，若读取失败或不存在则返回。
     * @return NBT 中的整数值或默认值。
     * @throws Exception 若 NBT 操作异常，记录日志并返回默认值。
     */
    public int getInt(ItemStack item, String key, int def) {
        if (item == null) return def;
        try {
            // Consumer 重载：安全读取
            final int[] result = {def};
            NBT.get(item, (ReadableItemNBT nbt) -> {
                if (nbt.hasTag(key)) result[0] = nbt.getInteger(key);
            });
            return result[0];
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "getInt 异常: " + ex.getMessage());
            return def;
        }
    }

    /**
     * 向 ItemStack 的 NBT 中写入整数值，返回克隆后的 ItemStack。
     *
     * @param item 原 ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @param val 要写入的整数值。
     * @return 修改后的 ItemStack 克隆，或原 item 若操作失败。
     * @throws Exception 若 NBT 操作异常，记录日志并返回原 item。
     */
    public ItemStack setInt(ItemStack item, String key, int val) {
        if (item == null) return null;
        try {
            ItemStack clone = item.clone();
            NBT.modify(clone, (ReadWriteItemNBT nbt) -> {
                nbt.setInteger(key, val);  // 写入整数
            });
            return clone;
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "setInt 异常: " + ex.getMessage());
            return item;
        }
    }

    /**
     * 从 ItemStack 的 NBT 中读取字符串值。
     *
     * @param item ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @param def 默认值，若读取失败或不存在则返回。
     * @return NBT 中的字符串值或默认值。
     * @throws Exception 若 NBT 操作异常，记录日志并返回默认值。
     */
    public String getString(ItemStack item, String key, String def) {
        if (item == null) return def;
        try {
            final String[] result = {def};
            NBT.get(item, (ReadableItemNBT nbt) -> {
                if (nbt.hasTag(key)) result[0] = nbt.getString(key);
            });
            return result[0];
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "getString 异常: " + ex.getMessage());
            return def;
        }
    }

    /**
     * 向 ItemStack 的 NBT 中写入字符串值，返回克隆后的 ItemStack。
     *
     * @param item 原 ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @param val 要写入的字符串值。
     * @return 修改后的 ItemStack 克隆，或原 item 若操作失败。
     * @throws Exception 若 NBT 操作异常，记录日志并返回原 item。
     */
    public ItemStack setString(ItemStack item, String key, String val) {
        if (item == null) return null;
        try {
            ItemStack clone = item.clone();
            NBT.modify(clone, (ReadWriteItemNBT nbt) -> {
                nbt.setString(key, val);
            });
            return clone;
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "setString 异常: " + ex.getMessage());
            return item;
        }
    }

    /**
     * 检查 ItemStack 的 NBT 中是否包含指定键。
     *
     * @param item ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @return true 若键存在，否则 false。
     * @throws Exception 若 NBT 操作异常，记录日志并返回 false。
     */
    public boolean hasKey(ItemStack item, String key) {
        if (item == null) return false;
        try {
            final boolean[] exists = {false};
            NBT.get(item, (ReadableItemNBT nbt) -> {
                exists[0] = nbt.hasTag(key);  // hasTag 已替代 hasKey
            });
            return exists[0];
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "hasKey 异常: " + ex.getMessage());
            return false;
        }
    }

    /**
     * 从 ItemStack 的 NBT 中移除指定键，返回克隆后的 ItemStack。
     *
     * @param item 原 ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @return 修改后的 ItemStack 克隆，或原 item 若操作失败。
     * @throws Exception 若 NBT 操作异常，记录日志并返回原 item。
     */
    public ItemStack removeKey(ItemStack item, String key) {
        if (item == null) return null;
        try {
            ItemStack clone = item.clone();
            NBT.modify(clone, (ReadWriteItemNBT nbt) -> {
                nbt.removeKey(key);  // 新增接口 removeKey(String)
            });
            return clone;
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "removeKey 异常: " + ex.getMessage());
            return item;
        }
    }

    /* ===== 实例方法：基于前缀策略 ===== */

    private final NbtKeyHandler keyHandler;

    /**
     * 扫描 ItemStack 的 NBT 中所有符合插件前缀的键。
     *
     * @param item ItemStack 实例，可能为 null。
     * @return 符合前缀的键集合，若 item 为 null 或异常则返回空集合。
     * @throws Exception 若 NBT 操作异常，记录日志并返回空集合。
     */
    public Set<String> scanPluginNBTKeys(ItemStack item) {
        Set<String> keys = new HashSet<>();
        if (item == null) return keys;
        try {
            NBT.get(item, (ReadableItemNBT nbt) -> {
                for (String k : nbt.getKeys()) {
                    if (keyHandler.isValidKey(k)) keys.add(k);
                }
            });
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "scanPluginNBTKeys 异常: " + ex.getMessage());
        }
        return keys;
    }

    /**
     * 检查 ItemStack 的 NBT 中是否包含指定插件自定义键（加前缀）。
     *
     * @param item ItemStack 实例，可能为 null。
     * @param customKey 自定义键名（不含前缀）。
     * @return true 若键存在，否则 false。
     */
    public boolean hasPluginKey(ItemStack item, String customKey) {
        return hasKey(item, keyHandler.addPrefix(customKey));
    }

    /**
     * 检查 ItemStack 的 NBT 中指定自定义键集合（加前缀）中哪些存在。
     *
     * @param item ItemStack 实例，可能为 null。
     * @param customKeys 自定义键集合（不含前缀）。
     * @return 存在的完整键集合（含前缀），若 item 为 null 则返回空集合。
     */
    public Set<String> checkPluginKeys(ItemStack item, Set<String> customKeys) {
        Set<String> present = new HashSet<>();
        if (item == null) return present;
        for (String ck : customKeys) {
            String full = keyHandler.addPrefix(ck);
            if (hasKey(item, full)) present.add(full);
        }
        return present;
    }

    /**
     * 批量保留源 ItemStack 中指定自定义键的 NBT 数据到目标 ItemStack。
     *
     * @param src 源 ItemStack，可能为 null。
     * @param dst 目标 ItemStack，可能为 null。
     * @param customKeys 自定义键集合（不含前缀）。
     * @return 修改后的目标 ItemStack 克隆，或原 dst 若操作失败。
     * @throws Exception 若 NBT 操作异常，记录日志并返回原 dst。
     */
    public ItemStack batchPreserve(ItemStack src, ItemStack dst, Set<String> customKeys) {
        if (src == null || dst == null) return dst;
        ItemStack clone = dst.clone();
        try {
            // 先读取 source 的所有符合前缀的标签
            NBT.get(src, (ReadableItemNBT srcNbt) -> {
                // 再对 clone 执行写入
                NBT.modify(clone, (ReadWriteItemNBT tgtNbt) -> {
                    for (String ck : customKeys) {
                        String full = keyHandler.addPrefix(ck);
                        if (srcNbt.hasTag(full)) {
                            copyNBTTag(srcNbt, tgtNbt, full);
                        }
                    }
                });
            });
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "batchPreserve 异常: " + ex.getMessage());
            return dst;
        }
        return clone;
    }

    /**
     * 保留源 ItemStack 中单个自定义键的 NBT 数据到目标 ItemStack。
     *
     * @param src 源 ItemStack，可能为 null。
     * @param dst 目标 ItemStack，可能为 null。
     * @param customKey 自定义键名（不含前缀）。
     * @return 修改后的目标 ItemStack 克隆，或原 dst 若操作失败。
     */
    public ItemStack preserveSingle(ItemStack src, ItemStack dst, String customKey) {
        Set<String> one = new HashSet<>();
        one.add(customKey);
        return batchPreserve(src, dst, one);
    }

    /**
     * 从键集合中清理不符合插件前缀的无效键。
     *
     * @param keys 键集合。
     * @return 无效键集合。
     */
    public Set<String> cleanupInvalidKeys(Set<String> keys) {
        Set<String> invalid = new HashSet<>();
        for (String k : keys) {
            if (!keyHandler.isValidKey(k)) invalid.add(k);
        }
        return invalid;
    }

    /**
     * 为自定义键添加插件前缀。
     *
     * @param customKey 自定义键名。
     * @return 完整键名（含前缀）。
     */
    public String addPrefix(String customKey)  { return keyHandler.addPrefix(customKey); }

    /**
     * 从完整键中移除插件前缀。
     *
     * @param fullKey 完整键名（含前缀）。
     * @return 自定义键名（不含前缀）。
     */
    public String removePrefix(String fullKey) { return keyHandler.removePrefix(fullKey); }

    /* ===== 私有：根据类型复制 NBT 标签 ===== */

    /**
     * 【内部方法】将源 {@code src} 中指定 {@code key} 的 NBT 标签按照实际类型深拷贝到目标 {@code dst}。
     * <p>
     * 核心功能：
     * <ul>
     *     <li>首先通过 {@link NBTType} 获取标签类型，并使用 {@code switch} 分支为每种基础类型调用对应的 <code>setXxx/getXxx</code> 方法完成复制。</li>
     *     <li>当类型为 {@link NBTType#NBTTagCompound}（复合标签）时，通过 <code>getOrCreateCompound(key).mergeCompound(...)</code> 方式递归合并，实现深拷贝。</li>
     *     <li>当类型为 {@link NBTType#NBTTagList}（列表）时，先检查元素子类型，再按子类型遍历复制到目标列表；空列表或未知类型会创建空列表并记录警告。</li>
     * </ul>
     * <p>
     * 异常与不支持类型处理：
     * <ul>
     *     <li>如遇到未实现的标签类型，方法会通过 {@link DebugUtil} 记录 WARN 日志后跳过，确保调用方不会因异常导致流程中断。</li>
     *     <li>复制过程中若捕获到异常，将记录 DEBUG 日志并继续执行，以保证整体稳定性。</li>
     * </ul>
     *
     * @param src 源 NBT 容器（只读）
     * @param dst 目标 NBT 容器（可写）
     * @param key 需要复制的 NBT 键名
     */
    private void copyNBTTag(ReadableItemNBT src, ReadWriteItemNBT dst, String key) {
        try {
            NBTType type = src.getType(key);  // 获取标签类型
            switch (type) {
                case NBTTagByte:
                    dst.setByte(key, src.getByte(key));
                    break;
                case NBTTagShort:
                    dst.setShort(key, src.getShort(key));
                    break;
                case NBTTagInt:
                    dst.setInteger(key, src.getInteger(key));
                    break;
                case NBTTagLong:
                    dst.setLong(key, src.getLong(key));
                    break;
                case NBTTagFloat:
                    dst.setFloat(key, src.getFloat(key));
                    break;
                case NBTTagDouble:
                    dst.setDouble(key, src.getDouble(key));
                    break;
                case NBTTagString:
                    dst.setString(key, src.getString(key));
                    break;
                case NBTTagByteArray:
                    dst.setByteArray(key, src.getByteArray(key));
                    break;
                case NBTTagIntArray:
                    dst.setIntArray(key, src.getIntArray(key));  // 修正方法名：getIntArray 而非 getIntegerArray
                    break;
                case NBTTagLongArray:
                    dst.setLongArray(key, src.getLongArray(key));
                    break;
                case NBTTagCompound:
                    // 深拷贝复合标签
                    dst.getOrCreateCompound(key).mergeCompound(src.getCompound(key));
                    break;
                case NBTTagList:
                    // 处理列表拷贝
                    NBTType contentType = src.getListType(key);  // 使用 getListType 获取内容类型
                    if (contentType == null || contentType == NBTType.NBTTagEnd) {
                        // 空列表：创建空列表（使用字符串列表作为默认，API 会处理为空列表）
                        dst.getStringList(key);
                    } else {
                        switch (contentType) {
                            case NBTTagString:
                                ReadableNBTList<String> srcStrList = src.getStringList(key);
                                ReadWriteNBTList<String> dstStrList = dst.getStringList(key);
                                for (String item : srcStrList) {
                                    dstStrList.add(item);
                                }
                                break;
                            case NBTTagInt:
                                ReadableNBTList<Integer> srcIntList = src.getIntegerList(key);
                                ReadWriteNBTList<Integer> dstIntList = dst.getIntegerList(key);
                                for (Integer item : srcIntList) {
                                    dstIntList.add(item);
                                }
                                break;
                            case NBTTagLong:
                                ReadableNBTList<Long> srcLongList = src.getLongList(key);
                                ReadWriteNBTList<Long> dstLongList = dst.getLongList(key);
                                for (Long item : srcLongList) {
                                    dstLongList.add(item);
                                }
                                break;
                            case NBTTagFloat:
                                ReadableNBTList<Float> srcFloatList = src.getFloatList(key);
                                ReadWriteNBTList<Float> dstFloatList = dst.getFloatList(key);
                                for (Float item : srcFloatList) {
                                    dstFloatList.add(item);
                                }
                                break;
                            case NBTTagDouble:
                                ReadableNBTList<Double> srcDblList = src.getDoubleList(key);
                                ReadWriteNBTList<Double> dstDblList = dst.getDoubleList(key);
                                for (Double item : srcDblList) {
                                    dstDblList.add(item);
                                }
                                break;
                            case NBTTagCompound:
                                ReadableNBTList<ReadWriteNBT> srcCmpList = src.getCompoundList(key);
                                ReadWriteNBTCompoundList dstCmpList = dst.getCompoundList(key);
                                for (ReadWriteNBT comp : srcCmpList) {
                                    ReadWriteNBT newComp = dstCmpList.addCompound();
                                    newComp.mergeCompound(comp);
                                }
                                break;
                            case NBTTagIntArray:  // 支持 UUID 列表
                                ReadableNBTList<UUID> srcUUIDList = src.getUUIDList(key);
                                ReadWriteNBTList<UUID> dstUUIDList = dst.getUUIDList(key);
                                for (UUID item : srcUUIDList) {
                                    dstUUIDList.add(item);
                                }
                                break;
                            default:
                                logger.log(LogLevel.WARN, "不支持的列表内容类型拷贝: " + contentType + " for key: " + key);
                                break;
                        }
                    }
                    break;
                default:
                    logger.log(LogLevel.WARN, "不支持的 NBT 类型拷贝: " + type + " for key: " + key);
                    break;
            }
        } catch (Exception ex) {
            logger.log(LogLevel.DEBUG, "copyNBTTag 失败 [" + key + "]: " + ex.getMessage());
        }
    }
}