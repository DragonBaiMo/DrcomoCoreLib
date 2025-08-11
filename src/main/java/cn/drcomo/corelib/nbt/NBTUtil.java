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
 * <p>
 * 本类多数写入方法在写入前会先读取原值：若值未变化则直接返回传入的对象，
 * 仅当需要修改时才克隆并写入，以减少不必要的对象创建。
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
            logger.debug("getInt 异常: " + ex.getMessage(), ex);
            return def;
        }
    }

    /**
     * 向 ItemStack 的 NBT 中写入整数值。
     * 若原值与待写入值相同，则直接返回原对象；
     * 仅在需要写入时才克隆并修改。
     *
     * @param item 原 ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @param val 要写入的整数值。
     * @return 原对象或修改后的克隆。
     * @throws Exception 若 NBT 操作异常，记录日志并返回原 item。
     */
    public ItemStack setInt(ItemStack item, String key, int val) {
        if (item == null) return null;
        try {
            final boolean[] needWrite = {true};
            NBT.get(item, (ReadableItemNBT nbt) -> {
                if (nbt.hasTag(key) && nbt.getInteger(key) == val) {
                    needWrite[0] = false;
                }
            });
            if (!needWrite[0]) return item;

            ItemStack clone = item.clone();
            NBT.modify(clone, (ReadWriteItemNBT nbt) -> {
                nbt.setInteger(key, val);
            });
            return clone;
        } catch (Exception ex) {
            logger.debug("setInt 异常: " + ex.getMessage(), ex);
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
            logger.debug("getString 异常: " + ex.getMessage(), ex);
            return def;
        }
    }

    /**
     * 向 ItemStack 的 NBT 中写入字符串值。
     * 若原值与待写入值一致则返回原对象，避免额外克隆。
     *
     * @param item 原 ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @param val 要写入的字符串值。
     * @return 原对象或修改后的克隆。
     * @throws Exception 若 NBT 操作异常，记录日志并返回原 item。
     */
    public ItemStack setString(ItemStack item, String key, String val) {
        if (item == null) return null;
        try {
            final boolean[] needWrite = {true};
            NBT.get(item, (ReadableItemNBT nbt) -> {
                if (nbt.hasTag(key)) {
                    String old = nbt.getString(key);
                    if (Objects.equals(old, val)) needWrite[0] = false;
                }
            });
            if (!needWrite[0]) return item;

            ItemStack clone = item.clone();
            NBT.modify(clone, (ReadWriteItemNBT nbt) -> {
                nbt.setString(key, val);
            });
            return clone;
        } catch (Exception ex) {
            logger.debug("setString 异常: " + ex.getMessage(), ex);
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
            logger.debug("hasKey 异常: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 从 ItemStack 的 NBT 中移除指定键。
     * 若键不存在则直接返回原对象。
     *
     * @param item 原 ItemStack 实例，可能为 null。
     * @param key NBT 键名。
     * @return 原对象或修改后的克隆。
     * @throws Exception 若 NBT 操作异常，记录日志并返回原 item。
     */
    public ItemStack removeKey(ItemStack item, String key) {
        if (item == null) return null;
        try {
            final boolean[] exists = {false};
            NBT.get(item, (ReadableItemNBT nbt) -> exists[0] = nbt.hasTag(key));
            if (!exists[0]) return item;

            ItemStack clone = item.clone();
            NBT.modify(clone, (ReadWriteItemNBT nbt) -> nbt.removeKey(key));
            return clone;
        } catch (Exception ex) {
            logger.debug("removeKey 异常: " + ex.getMessage(), ex);
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
            logger.debug("scanPluginNBTKeys 异常: " + ex.getMessage(), ex);
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
            logger.debug("batchPreserve 异常: " + ex.getMessage(), ex);
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
                    NBTType subType = src.getListType(key);
                    if (subType == null || subType == NBTType.NBTTagEnd) {
                        dst.getStringList(key); // 创建空列表
                    } else {
                        switch (subType) {
                            case NBTTagString:
                                ReadWriteNBTList<String> dstStr = dst.getStringList(key);
                                for (String s : src.getStringList(key)) dstStr.add(s);
                                break;
                            case NBTTagInt:
                                ReadWriteNBTList<Integer> dstInt = dst.getIntegerList(key);
                                for (Integer i : src.getIntegerList(key)) dstInt.add(i);
                                break;
                            case NBTTagLong:
                                ReadWriteNBTList<Long> dstLong = dst.getLongList(key);
                                for (Long l : src.getLongList(key)) dstLong.add(l);
                                break;
                            case NBTTagFloat:
                                ReadWriteNBTList<Float> dstF = dst.getFloatList(key);
                                for (Float f : src.getFloatList(key)) dstF.add(f);
                                break;
                            case NBTTagDouble:
                                ReadWriteNBTList<Double> dstD = dst.getDoubleList(key);
                                for (Double d : src.getDoubleList(key)) dstD.add(d);
                                break;
                            case NBTTagCompound:
                                ReadWriteNBTCompoundList dstCmp = dst.getCompoundList(key);
                                for (ReadWriteNBT cmp : src.getCompoundList(key)) {
                                    ReadWriteNBT newCmp = dstCmp.addCompound();
                                    newCmp.mergeCompound(cmp);
                                }
                                break;
                            default:
                                logger.log(LogLevel.WARN, "copyTagGeneric 不支持的列表子类型: " + subType);
                        }
                    }
                    break;
                default:
                    logger.log(LogLevel.WARN, "不支持的 NBT 类型拷贝: " + type + " for key: " + key);
                    break;
            }
        } catch (Exception ex) {
            logger.debug("copyNBTTag 失败 [" + key + "]: " + ex.getMessage(), ex);
        }
    }

    /**
     * 获取 ItemStack 的完整 NBT 数据并序列化为字符串（SNBT 格式）。
     * <p>
     * 该方法内部通过 {@link NBT#itemStackToNBT(ItemStack)} 获取物品的 NBT
     * 数据，再调用其 {@code toString()} 返回标准 SNBT 字符串，便于日志打印、问题排查与人工比对。
     * <pre>
     * String raw = nbtUtil.toRawString(item);
     * plugin.getLogger().info(raw);
     * </pre>
     * 当 {@code item} 为 {@code null} 或发生异常时，将返回空 JSON "{}"。
     *
     * @param item 目标物品，可为 {@code null}
     * @return 物品完整 NBT 的 SNBT 字符串，异常或空物品时返回 "{}"
     */
    public String toRawString(ItemStack item) {
        if (item == null) return "{}";
        try {
            // NBT.itemStackToNBT 会返回包含 id 与 Count 的完整 NBT
            ItemStack clone = item.clone();
            ReadWriteNBT nbt = NBT.itemStackToNBT(clone);
            return nbt.toString();
        } catch (Exception ex) {
            logger.debug("toRawString 异常: " + ex.getMessage(), ex);
            return "{}";
        }
    }

    /**
     * 获取 ItemStack 的原始 NBT Compound 对象，便于高级自定义操作。
     * <p>
     * 返回值为 NBT-API 提供的 {@link ReadWriteNBT}（或其运行时实现）。
     * 调用方可在不破坏本工具封装的前提下，进行更复杂的读取/写入或自定义序列化。
     * 当 {@code item} 为 {@code null} 或发生异常时，返回 {@code null}。
     *
     * <strong>⚠ 注意：</strong> 返回的对象与物品 NBT 引用绑定，若需离线操作请自行克隆。
     *
     * @param item 目标物品，可为 {@code null}
     * @return 物品的 {@code ReadWriteNBT} 复合对象，异常或空物品时返回 {@code null}
     */
    public ReadWriteNBT getRawCompound(ItemStack item) {
        if (item == null) return null;
        try {
            final ReadWriteNBT[] result = {null};
            // 使用只读访问，获取内部 NBT Compound 引用
            NBT.get(item, (ReadableItemNBT nbt) -> {
                if (nbt instanceof ReadWriteNBT) {
                    result[0] = (ReadWriteNBT) nbt; // 直接返回实现引用
                }
            });
            return result[0];
        } catch (Exception ex) {
            logger.debug("getRawCompound 异常: " + ex.getMessage(), ex);
            return null;
        }
    }

    /* =====================================
     * 进阶功能区：序列化/反序列化 & 批量操作
     * ===================================== */

    /**
     * 从 SNBT/JSON 字符串反序列化并构造 {@link ItemStack}。
     * <p>
     * 底层通过 <code>NBT.parseNBT</code> 将字符串解析为 {@link ReadWriteNBT}，
     * 随后借助 <code>NBT.itemStackFromNBT</code> 还原为物品。
     *
     * @param nbtString 完整 NBT 字符串（SNBT/JSON 格式）
     * @return 还原得到的 {@link ItemStack}
     * @throws ParseException 当字符串格式不合法或解析失败时抛出
     */
    public ItemStack fromRawString(String nbtString) throws cn.drcomo.corelib.hook.placeholder.parse.ParseException {
        if (nbtString == null || nbtString.isEmpty()) {
            throw new cn.drcomo.corelib.hook.placeholder.parse.ParseException("NBT 字符串为空");
        }
        try {
            ReadWriteNBT nbt = NBT.parseNBT(nbtString);
            return NBT.itemStackFromNBT(nbt);
        } catch (Exception ex) {
            logger.debug("fromRawString 解析失败: " + ex.getMessage(), ex);
            throw new cn.drcomo.corelib.hook.placeholder.parse.ParseException("NBT 解析失败: " + ex.getMessage());
        }
    }

    /**
     * 获取物品全部 NBT 数据展开为 {@code Map<String, Object>}（递归）。
     * <p>返回的 Map 中：
     * <ul>
     *     <li>基础类型（Number、String、byte[] 等）保持原样；</li>
     *     <li>复合标签对应嵌套 {@link java.util.Map}；</li>
     *     <li>列表标签对应 {@link java.util.List}，其中元素类型与实际类型保持一致。</li>
     * </ul>
     * 此方法仅用于<strong>读取</strong>，写入请使用 {@link #setAllNbt(ItemStack, Map)}。
     *
     * @param item 目标物品
     * @return 递归展开后的键值 Map，若 item 为 null 返回空 Map
     */
    public java.util.Map<String, Object> getAllNbt(ItemStack item) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        if (item == null) return result;
        try {
            ReadWriteNBT nbt = NBT.itemStackToNBT(item);
            convertNBTToMap(nbt, result);
        } catch (Exception ex) {
            logger.debug("getAllNbt 异常: " + ex.getMessage(), ex);
        }
        return result;
    }

    /** 递归辅助：将 ReadableNBT 转换为 Map */
    private void convertNBTToMap(ReadWriteNBT nbt, java.util.Map<String, Object> target) {
        for (String key : nbt.getKeys()) {
            NBTType type = nbt.getType(key);
            switch (type) {
                case NBTTagCompound:
                    java.util.Map<String, Object> child = new java.util.HashMap<>();
                    convertNBTToMap(nbt.getCompound(key), child);
                    target.put(key, child);
                    break;
                case NBTTagList:
                    NBTType sub = nbt.getListType(key);
                    java.util.List<Object> list = new java.util.ArrayList<>();
                    if (sub != null) {
                        switch (sub) {
                            case NBTTagString:
                                for (String s : nbt.getStringList(key)) list.add(s);
                                break;
                            case NBTTagInt:
                                for (Integer i : nbt.getIntegerList(key)) list.add(i);
                                break;
                            case NBTTagLong:
                                for (Long l : nbt.getLongList(key)) list.add(l);
                                break;
                            case NBTTagFloat:
                                for (Float f : nbt.getFloatList(key)) list.add(f);
                                break;
                            case NBTTagDouble:
                                for (Double d : nbt.getDoubleList(key)) list.add(d);
                                break;
                            case NBTTagCompound:
                                for (ReadWriteNBT cmp : nbt.getCompoundList(key)) {
                                    java.util.Map<String, Object> cmpMap = new java.util.HashMap<>();
                                    convertNBTToMap(cmp, cmpMap);
                                    list.add(cmpMap);
                                }
                                break;
                            default:
                                list.add("UnsupportedListType:" + sub);
                        }
                    }
                    target.put(key, list);
                    break;
                case NBTTagByte:       target.put(key, nbt.getByte(key)); break;
                case NBTTagShort:      target.put(key, nbt.getShort(key)); break;
                case NBTTagInt:        target.put(key, nbt.getInteger(key)); break;
                case NBTTagLong:       target.put(key, nbt.getLong(key)); break;
                case NBTTagFloat:      target.put(key, nbt.getFloat(key)); break;
                case NBTTagDouble:     target.put(key, nbt.getDouble(key)); break;
                case NBTTagString:     target.put(key, nbt.getString(key)); break;
                case NBTTagByteArray:  target.put(key, nbt.getByteArray(key)); break;
                case NBTTagIntArray:   target.put(key, nbt.getIntArray(key)); break;
                case NBTTagLongArray:  target.put(key, nbt.getLongArray(key)); break;
                default:
                    target.put(key, "UnsupportedType:" + type);
            }
        }
    }

    /**
     * 使用 Map 批量覆盖/写入物品 NBT。
     * <p>仅支持基础类型及嵌套 Map，若遇到不支持的值类型将被忽略。</p>
     * @param item  目标物品
     * @param nbtMap 键值 Map
     * @return 修改后的 ItemStack 克隆
     */
    public ItemStack setAllNbt(ItemStack item, java.util.Map<String, Object> nbtMap) {
        if (item == null || nbtMap == null) return item;
        ItemStack clone = item.clone();
        try {
            NBT.modify(clone, (ReadWriteItemNBT nbt) -> applyMapToNBT(nbt, nbtMap));
        } catch (Exception ex) {
            logger.debug("setAllNbt 异常: " + ex.getMessage(), ex);
            return item;
        }
        return clone;
    }

    /** 递归辅助：将 Map 数据写入 NBT */
    @SuppressWarnings("unchecked")
    private void applyMapToNBT(ReadWriteNBT dst, java.util.Map<String, Object> map) {
        for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null) continue;
            if (val instanceof String) {
                dst.setString(key, (String) val);
            } else if (val instanceof Integer) {
                dst.setInteger(key, (Integer) val);
            } else if (val instanceof Long) {
                dst.setLong(key, (Long) val);
            } else if (val instanceof Short) {
                dst.setShort(key, (Short) val);
            } else if (val instanceof Byte) {
                dst.setByte(key, (Byte) val);
            } else if (val instanceof Float) {
                dst.setFloat(key, (Float) val);
            } else if (val instanceof Double) {
                dst.setDouble(key, (Double) val);
            } else if (val instanceof byte[]) {
                dst.setByteArray(key, (byte[]) val);
            } else if (val instanceof int[]) {
                dst.setIntArray(key, (int[]) val);
            } else if (val instanceof long[]) {
                dst.setLongArray(key, (long[]) val);
            } else if (val instanceof java.util.Map) {
                ReadWriteNBT child = dst.getOrCreateCompound(key);
                applyMapToNBT(child, (java.util.Map<String, Object>) val);
            } else if (val instanceof java.util.List) {
                java.util.List<?> listVal = (java.util.List<?>) val;
                if (listVal.isEmpty()) {
                    dst.getStringList(key); // 创建空字符串列表占位
                    continue;
                }
                Object first = null;
                for (Object o : listVal) {
                    if (o != null) {
                        first = o;
                        break;
                    }
                }
                if (first == null) {
                    dst.getStringList(key);
                    continue;
                }
                if (first instanceof String) {
                    ReadWriteNBTList<String> l = dst.getStringList(key);
                    l.clear();
                    for (Object o : listVal) l.add(o == null ? "" : o.toString());
                } else if (first instanceof Integer) {
                    ReadWriteNBTList<Integer> l = dst.getIntegerList(key);
                    l.clear();
                    for (Object o : listVal) if (o != null) l.add(((Number) o).intValue());
                } else if (first instanceof Long) {
                    ReadWriteNBTList<Long> l = dst.getLongList(key);
                    l.clear();
                    for (Object o : listVal) if (o != null) l.add(((Number) o).longValue());
                } else if (first instanceof Float) {
                    ReadWriteNBTList<Float> l = dst.getFloatList(key);
                    l.clear();
                    for (Object o : listVal) if (o != null) l.add(((Number) o).floatValue());
                } else if (first instanceof Double) {
                    ReadWriteNBTList<Double> l = dst.getDoubleList(key);
                    l.clear();
                    for (Object o : listVal) if (o != null) l.add(((Number) o).doubleValue());
                } else if (first instanceof java.util.Map) {
                    ReadWriteNBTCompoundList l = dst.getCompoundList(key);
                    l.clear();
                    for (Object o : listVal) {
                        if (o instanceof java.util.Map) {
                            ReadWriteNBT newCmp = l.addCompound();
                            //noinspection unchecked
                            applyMapToNBT(newCmp, (java.util.Map<String, Object>) o);
                        }
                    }
                } else {
                    logger.log(LogLevel.WARN, "applyMapToNBT 未支持的列表元素类型 key=" + key + " type=" + first.getClass());
                }
            } else {
                logger.log(LogLevel.WARN, "未支持的值类型, key=" + key + ", type=" + val.getClass());
            }
        }
    }

    /**
     * 读取原生 NBT 键（不加前缀）。
     */
    public Object getRaw(ItemStack item, String key) {
        if (item == null) return null;
        try {
            return NBT.get(item, nbt -> {
                if (nbt.hasTag(key)) {
                    NBTType t = nbt.getType(key);
                    switch (t) {
                        case NBTTagString: return nbt.getString(key);
                        case NBTTagInt:    return nbt.getInteger(key);
                        case NBTTagByte:   return nbt.getByte(key);
                        case NBTTagShort:  return nbt.getShort(key);
                        case NBTTagLong:   return nbt.getLong(key);
                        case NBTTagFloat:  return nbt.getFloat(key);
                        case NBTTagDouble: return nbt.getDouble(key);
                        case NBTTagByteArray: return nbt.getByteArray(key);
                        case NBTTagIntArray:  return nbt.getIntArray(key);
                        case NBTTagLongArray: return nbt.getLongArray(key);
                        default: return null;
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            logger.debug("getRaw 异常: " + ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 写入原生 NBT 键（不加前缀）。
     */
    public ItemStack setRaw(ItemStack item, String key, Object value) {
        if (item == null) return null;
        ItemStack clone = item.clone();
        try {
            NBT.modify(clone, nbt -> {
                if (value instanceof String) nbt.setString(key, (String) value);
                else if (value instanceof Integer) nbt.setInteger(key, (Integer) value);
                else if (value instanceof Long) nbt.setLong(key, (Long) value);
                else if (value instanceof Short) nbt.setShort(key, (Short) value);
                else if (value instanceof Byte) nbt.setByte(key, (Byte) value);
                else if (value instanceof Float) nbt.setFloat(key, (Float) value);
                else if (value instanceof Double) nbt.setDouble(key, (Double) value);
                else if (value instanceof byte[]) nbt.setByteArray(key, (byte[]) value);
                else if (value instanceof int[]) nbt.setIntArray(key, (int[]) value);
                else if (value instanceof long[]) nbt.setLongArray(key, (long[]) value);
                else {
                    logger.log(LogLevel.WARN, "setRaw 未支持的值类型: " + value.getClass());
                }
            });
            return clone;
        } catch (Exception ex) {
            logger.debug("setRaw 异常: " + ex.getMessage(), ex);
            return item;
        }
    }

    /**
     * 返回带缩进、换行的可读性更高的 NBT 字符串。
     * <p>当前实现基于简单缩进算法，不保证完美格式化，但足够人工阅读。</p>
     */
    public String toPrettyString(ItemStack item) {
        String raw = toRawString(item);
        StringBuilder pretty = new StringBuilder();
        int indent = 0;
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '{':
                case '[':
                    pretty.append(c).append('\n');
                    indent++;
                    pretty.append("  ".repeat(indent));
                    break;
                case '}':
                case ']':
                    pretty.append('\n');
                    indent = Math.max(0, indent - 1);
                    pretty.append("  ".repeat(indent)).append(c);
                    break;
                case ',':
                    pretty.append(c).append('\n').append("  ".repeat(indent));
                    break;
                default:
                    pretty.append(c);
            }
        }
        return pretty.toString();
    }

    /**
     * 从 {@code toPrettyString} 生成的字符串恢复 {@link ItemStack} 实例。
     * <p>方法会在解析前移除所有不在引号中的空白字符，然后复用
     * {@link #fromRawString(String)} 进行反序列化。</p>
     *
     * @param pretty 美化后的 SNBT 字符串
     * @return 解析得到的 {@link ItemStack}
     * @throws cn.drcomo.corelib.hook.placeholder.parse.ParseException
     *         当解析失败或字符串为空时抛出
     */
    public ItemStack fromPrettyString(String pretty)
            throws cn.drcomo.corelib.hook.placeholder.parse.ParseException {
        if (pretty == null || pretty.isEmpty()) {
            throw new cn.drcomo.corelib.hook.placeholder.parse.ParseException("NBT 字符串为空");
        }
        StringBuilder raw = new StringBuilder(pretty.length());
        boolean inQuote = false;
        for (char c : pretty.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
            }
            if (!inQuote && Character.isWhitespace(c)) {
                continue;
            }
            raw.append(c);
        }
        return fromRawString(raw.toString());
    }

    /**
     * 仅导出符合本插件前缀的所有 NBT 数据（SNBT 字符串）。
     */
    public String exportPluginNbt(ItemStack item) {
        if (item == null) return "{}";
        try {
            java.util.Set<String> keys = scanPluginNBTKeys(item);
            if (keys.isEmpty()) return "{}";
            ReadWriteNBT out = NBT.createNBTObject();
            NBT.get(item, src -> {
                for (String k : keys) {
                    copyTagGeneric(src, out, k);
                }
            });
            return out.toString();
        } catch (Exception ex) {
            logger.debug("exportPluginNbt 异常: " + ex.getMessage(), ex);
            return "{}";
        }
    }

    /** 通用复制：支持 ReadableItemNBT -> ReadWriteNBT */
    private void copyTagGeneric(ReadableItemNBT src, ReadWriteNBT dst, String key) {
        try {
            NBTType type = src.getType(key);
            switch (type) {
                case NBTTagCompound:
                    dst.getOrCreateCompound(key).mergeCompound(src.getCompound(key));
                    break;
                case NBTTagList:
                    NBTType subType = src.getListType(key);
                    if (subType == null || subType == NBTType.NBTTagEnd) {
                        dst.getStringList(key); // 创建空列表
                    } else {
                        switch (subType) {
                            case NBTTagString:
                                ReadWriteNBTList<String> dstStr = dst.getStringList(key);
                                for (String s : src.getStringList(key)) dstStr.add(s);
                                break;
                            case NBTTagInt:
                                ReadWriteNBTList<Integer> dstInt = dst.getIntegerList(key);
                                for (Integer i : src.getIntegerList(key)) dstInt.add(i);
                                break;
                            case NBTTagLong:
                                ReadWriteNBTList<Long> dstLong = dst.getLongList(key);
                                for (Long l : src.getLongList(key)) dstLong.add(l);
                                break;
                            case NBTTagFloat:
                                ReadWriteNBTList<Float> dstF = dst.getFloatList(key);
                                for (Float f : src.getFloatList(key)) dstF.add(f);
                                break;
                            case NBTTagDouble:
                                ReadWriteNBTList<Double> dstD = dst.getDoubleList(key);
                                for (Double d : src.getDoubleList(key)) dstD.add(d);
                                break;
                            case NBTTagCompound:
                                ReadWriteNBTCompoundList dstCmp = dst.getCompoundList(key);
                                for (ReadWriteNBT cmp : src.getCompoundList(key)) {
                                    ReadWriteNBT newCmp = dstCmp.addCompound();
                                    newCmp.mergeCompound(cmp);
                                }
                                break;
                            default:
                                logger.log(LogLevel.WARN, "copyTagGeneric 不支持的列表子类型: " + subType);
                        }
                    }
                    break;
                case NBTTagString:
                    dst.setString(key, src.getString(key));
                    break;
                case NBTTagInt:
                    dst.setInteger(key, src.getInteger(key));
                    break;
                case NBTTagByte:
                    dst.setByte(key, src.getByte(key));
                    break;
                case NBTTagShort:
                    dst.setShort(key, src.getShort(key));
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
                case NBTTagByteArray:
                    dst.setByteArray(key, src.getByteArray(key));
                    break;
                case NBTTagIntArray:
                    dst.setIntArray(key, src.getIntArray(key));
                    break;
                case NBTTagLongArray:
                    dst.setLongArray(key, src.getLongArray(key));
                    break;
                default:
                    logger.log(LogLevel.WARN, "copyTagGeneric 未支持类型: " + type);
            }
        } catch (Exception ex) {
            logger.debug("copyTagGeneric 异常:" + ex.getMessage(), ex);
        }
    }

    /**
     * 将 SNBT 字符串中的数据（仅限本插件前缀）导入到目标物品。
     */
    public ItemStack importPluginNbt(ItemStack item, String pluginNbtString) throws cn.drcomo.corelib.hook.placeholder.parse.ParseException {
        if (item == null) return null;
        try {
            ReadWriteNBT src = NBT.parseNBT(pluginNbtString);
            java.util.Set<String> keys = src.getKeys();
            ItemStack clone = item.clone();
            NBT.modify(clone, target -> {
                for (String k : keys) {
                    if (keyHandler.isValidKey(k)) {
                        // 将 src 的 key 写入 target
                        writeValueToTarget(target, k, src);
                    }
                }
            });
            return clone;
        } catch (Exception ex) {
            logger.debug("importPluginNbt 失败: " + ex.getMessage(), ex);
            throw new cn.drcomo.corelib.hook.placeholder.parse.ParseException("导入插件 NBT 失败: " + ex.getMessage());
        }
    }

    private void writeValueToTarget(ReadWriteItemNBT target, String key, ReadWriteNBT src) {
        NBTType type = src.getType(key);
        switch (type) {
            case NBTTagString: target.setString(key, src.getString(key)); break;
            case NBTTagInt: target.setInteger(key, src.getInteger(key)); break;
            case NBTTagByte: target.setByte(key, src.getByte(key)); break;
            case NBTTagShort: target.setShort(key, src.getShort(key)); break;
            case NBTTagLong: target.setLong(key, src.getLong(key)); break;
            case NBTTagFloat: target.setFloat(key, src.getFloat(key)); break;
            case NBTTagDouble: target.setDouble(key, src.getDouble(key)); break;
            case NBTTagByteArray: target.setByteArray(key, src.getByteArray(key)); break;
            case NBTTagIntArray: target.setIntArray(key, src.getIntArray(key)); break;
            case NBTTagLongArray: target.setLongArray(key, src.getLongArray(key)); break;
            case NBTTagCompound: target.getOrCreateCompound(key).mergeCompound(src.getCompound(key)); break;
            default: logger.log(LogLevel.WARN, "writeValueToTarget 未支持类型: " + type);
        }
    }
}