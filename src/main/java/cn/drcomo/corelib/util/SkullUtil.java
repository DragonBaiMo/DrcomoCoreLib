package cn.drcomo.corelib.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.UUID;

/**
 * 玩家头像工具类，用于生成带自定义纹理的头颅物品。
 */
public class SkullUtil {

    private final DebugUtil logger;

    /**
     * 构造 SkullUtil。
     *
     * @param logger DebugUtil 实例
     */
    public SkullUtil(DebugUtil logger) {
        this.logger = logger;
    }

    /**
     * 根据纹理 URL 创建玩家头颅。
     *
     * @param url 纹理地址
     * @return 带自定义纹理的 ItemStack
     */
    public ItemStack fromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes());
        return fromBase64(base64);
    }

    /**
     * 根据 Base64 字符串创建玩家头颅。
     *
     * @param base64 纹理 Base64
     * @return 带自定义纹理的 ItemStack
     */
    public ItemStack fromBase64(String base64) {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            GameProfile profile = new GameProfile(UUID.randomUUID(), null);
            profile.getProperties().put("textures", new Property("textures", base64));
            Field field = meta.getClass().getDeclaredField("profile");
            field.setAccessible(true);
            field.set(meta, profile);
            skull.setItemMeta(meta);
            return skull;
        } catch (Exception e) {
            logger.error("生成自定义头像失败", e);
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }
}
