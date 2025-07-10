package cn.drcomo.corelib.sound;

import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 音效管理类，用于加载、缓存和播放音效。
 */
public class SoundManager {

    // ===== 字段 =====
    public final Plugin plugin;
    public final YamlUtil yamlUtil;
    public final DebugUtil logger;
    private final Map<String, SoundEffectData> soundCache = new HashMap<>();
    /** 配置文件名（不含 .yml） */
    public final String configName;
    /** 全局音量倍率，用于统一调节音量 */
    public final float volumeMultiplier;
    /** 是否在找不到音效键时输出警告 */
    public final boolean warnOnMissingKeys;

    // ===== 构造函数 =====

    /**
     * 构造音效管理器
     *
     * @param plugin   Bukkit 插件实例
     * @param yamlUtil YamlUtil 实例，用于配置管理
     * @param logger DebugUtil 实例，用于日志输出
     * @param configName 配置文件名（不含 .yml）
     * @param volumeMultiplier 全局音量倍率，用于统一调节音量
     * @param warnOnMissingKeys 是否在找不到音效键时输出警告
     */
    public SoundManager(Plugin plugin, YamlUtil yamlUtil, DebugUtil logger, String configName, float volumeMultiplier, boolean warnOnMissingKeys) {
        this.plugin = plugin;
        this.yamlUtil = yamlUtil;
        this.logger = logger;
        this.configName = configName;
        this.volumeMultiplier = volumeMultiplier;
        this.warnOnMissingKeys = warnOnMissingKeys;
    }

    // ===== 加载与重载 =====

    /**
     * 从 sound.yml 加载音效配置并缓存
     */
    public void loadSounds() {
        // 根据调用方指定的配置文件名加载音效
        yamlUtil.ensureDirectory("");
        yamlUtil.copyDefaults(configName, "");
        yamlUtil.loadConfig(configName);
        var cfg = yamlUtil.getConfig(configName);

        soundCache.clear();
        for (String key : cfg.getKeys(false)) {
            String soundString = cfg.getString(key);
            if (soundString == null) continue;

            try {
                SoundEffectData data = parseSoundString(soundString);
                if (data != null) {
                    soundCache.put(key, data);
                }
            } catch (Exception e) {
                logger.error("解析音效配置时发生异常: " + soundString, e);
            }
        }
        logger.info("从 '" + configName + ".yml' 加载完成，共缓存 " + soundCache.size() + " 个音效");
    }

    /**
     * 重载音效配置
     */
    public void reloadSounds() {
        logger.info("开始重载音效配置");
        loadSounds();
        logger.info("完成重载音效配置");
    }

    // ===== 查询方法 =====

    /**
     * 检查指定键的音效是否存在
     *
     * @param key 音效键
     * @return 是否存在
     */
    public boolean hasSound(String key) {
        return soundCache.containsKey(key);
    }

    /**
     * 获取缓存的音效数量
     *
     * @return 缓存数量
     */
    public int getCachedSoundCount() {
        return soundCache.size();
    }

    /**
     * 获取所有可用的音效键
     *
     * @return 键集合
     */
    public Set<String> getAvailableSoundKeys() {
        return soundCache.keySet();
    }

    // ===== 播放方法 =====

    /**
     * 为玩家播放音效
     *
     * @param player 玩家
     * @param key    音效键
     */
    public void playSound(Player player, String key) {
        SoundEffectData data = soundCache.get(key);
        if (data != null) {
            playSoundForPlayer(player, player.getLocation(), data);
            logger.debug("为玩家 " + player.getName() + " 播放音效: " + key);
        } else if (warnOnMissingKeys) {
            logger.warn("找不到音效键: " + key);
        }
    }

    /**
     * 在指定位置播放音效
     *
     * @param loc 位置
     * @param key 音效键
     */
    public void playSoundAtLocation(Location loc, String key) {
        SoundEffectData data = soundCache.get(key);
        if (data != null) {
            playToWorld(loc, data);
            logger.debug("在位置 " + loc + " 播放音效: " + key);
        } else if (warnOnMissingKeys) {
            logger.warn("找不到音效键: " + key);
        }
    }

    /**
     * 在半径范围内播放音效
     *
     * @param center 中心位置
     * @param key    音效键
     * @param radius 半径（方块数）
     */
    public void playSoundInRadius(Location center, String key, double radius) {
        SoundEffectData data = soundCache.get(key);
        if (data != null) {
            playToPlayersInRadius(center, data, radius);
            logger.debug("在 " + center + " 半径 " + radius + " 范围内播放音效: " + key);
        } else if (warnOnMissingKeys) {
            logger.warn("找不到音效键: " + key);
        }
    }

    /**
     * 按字符串定义播放音效给玩家
     *
     * @param player      玩家
     * @param soundString 格式 "name-volume-pitch"
     */
    public void playSoundFromString(Player player, String soundString) {
        SoundEffectData data = parseSoundString(soundString);
        if (data != null) {
            playSoundForPlayer(player, player.getLocation(), data);
            logger.debug("为玩家 " + player.getName() + " 播放音效字符串: " + soundString);
        }
    }

    /**
     * 按字符串定义在半径范围内播放音效
     *
     * @param center      中心位置
     * @param soundString 格式 "name-volume-pitch"
     * @param radius      半径（方块数）
     */
    public void playSoundFromStringInRadius(Location center, String soundString, double radius) {
        SoundEffectData data = parseSoundString(soundString);
        if (data != null) {
            playToPlayersInRadius(center, data, radius);
            logger.debug("在 " + center + " 半径 " + radius + " 范围内播放音效字符串: " + soundString);
        } else if (warnOnMissingKeys) {
            logger.warn("音效字符串格式无效: " + soundString);
        }
    }

    /**
     * 根据世界名称和坐标，在指定位置播放音效。
     *
     * @param worldName 世界名称
     * @param x         X 坐标
     * @param y         Y 坐标
     * @param z         Z 坐标
     * @param key       音效键
     */
    public void playSound(String worldName, double x, double y, double z, String key) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            logger.warn("找不到世界: " + worldName);
            return;
        }
        playSoundAtLocation(new Location(world, x, y, z), key);
    }

    /**
     * 根据世界名称和坐标，在半径范围内播放音效。
     *
     * @param worldName 世界名称
     * @param x         X 坐标
     * @param y         Y 坐标
     * @param z         Z 坐标
     * @param key       音效键
     * @param radius    半径（方块数）
     */
    public void playSoundInRadius(String worldName, double x, double y, double z, String key, double radius) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            logger.warn("找不到世界: " + worldName);
            return;
        }
        playSoundInRadius(new Location(world, x, y, z), key, radius);
    }

    /**
     * 根据世界名称和坐标，直接根据字符串定义播放音效。
     *
     * @param worldName   世界名称
     * @param x           X 坐标
     * @param y           Y 坐标
     * @param z           Z 坐标
     * @param soundString 音效字符串，格式 "name-volume-pitch"
     */
    public void playSoundFromString(String worldName, double x, double y, double z, String soundString) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            logger.warn("找不到世界: " + worldName);
            return;
        }
        SoundEffectData data = parseSoundString(soundString);
        if (data != null) {
            playToWorld(new Location(world, x, y, z), data);
        } else if (warnOnMissingKeys) {
            logger.warn("音效字符串格式无效: " + soundString);
        }
    }

    /**
     * 根据世界名称和坐标，在半径范围内播放字符串定义的音效。
     *
     * @param worldName   世界名称
     * @param x           X 坐标
     * @param y           Y 坐标
     * @param z           Z 坐标
     * @param soundString 音效字符串，格式 "name-volume-pitch"
     * @param radius      半径（方块数）
     */
    public void playSoundFromStringInRadius(String worldName, double x, double y, double z, String soundString, double radius) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            logger.warn("找不到世界: " + worldName);
            return;
        }
        playSoundFromStringInRadius(new Location(world, x, y, z), soundString, radius);
    }

    // ===== 私有辅助方法 =====

    /**
     * 在世界指定位置播放音效（不针对玩家）
     */
    private void playToWorld(Location loc, SoundEffectData data) {
        World world = loc.getWorld();
        if (world == null) return;
        if (data.sound != null) {
            world.playSound(loc, data.sound, data.volume, data.pitch);
        } else {
            world.playSound(loc, data.name, data.volume, data.pitch);
        }
    }

    /**
     * 为玩家播放音效
     */
    private void playSoundForPlayer(Player player, Location location, SoundEffectData data) {
        if (data.sound != null) {
            player.playSound(location, data.sound, data.volume, data.pitch);
        } else {
            player.playSound(location, data.name, data.volume, data.pitch);
        }
    }

    /**
     * 在指定半径范围内向所有玩家播放音效
     */
    private void playToPlayersInRadius(Location center, SoundEffectData data, double radius) {
        World world = center.getWorld();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(center) <= radius) {
                playSoundForPlayer(player, center, data);
            }
        }
    }

    /**
     * 解析 "name-volume-pitch" 格式的音效字符串
     */
    private SoundEffectData parseSoundString(String soundString) {
        try {
            String[] parts = soundString.split("-");
            if (parts.length != 3) {
                logger.warn("音效字符串格式无效: " + soundString);
                return null;
            }
            String name = parts[0];
            float volume = Float.parseFloat(parts[1]) * volumeMultiplier;
            float pitch = Float.parseFloat(parts[2]);

            Sound enumSound = null;
            try {
                enumSound = Sound.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 非枚举项，保留原名播放
            }
            return new SoundEffectData(enumSound, name, volume, pitch);
        } catch (Exception e) {
            logger.error("解析音效字符串时发生异常: " + soundString, e);
            return null;
        }
    }

    // ===== 内部静态类：音效数据 =====

    /**
     * 音效数据类，封装 Sound 枚举、原始名称、音量及音调
     */
    private static class SoundEffectData {
        private final Sound sound;
        private final String name;
        private final float volume;
        private final float pitch;

        SoundEffectData(Sound sound, String name, float volume, float pitch) {
            this.sound = sound;
            this.name = name;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
}
