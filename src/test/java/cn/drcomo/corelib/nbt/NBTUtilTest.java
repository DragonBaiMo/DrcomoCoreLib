package cn.drcomo.corelib.nbt;

import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.util.DebugUtil.LogLevel;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Basic tests for {@link NBTUtil#toRawString(ItemStack)} and
 * {@link NBTUtil#fromRawString(String)}.
 */
public class NBTUtilTest {

    private final NbtKeyHandler handler = new NbtKeyHandler() {
        @Override
        public boolean isValidKey(String fullKey) {
            return true;
        }
        @Override
        public String addPrefix(String key) {
            return key;
        }
        @Override
        public String removePrefix(String fullKey) {
            return fullKey;
        }
    };

    private final DebugUtil logger = new DebugUtil(new org.bukkit.plugin.Plugin() {
        private final java.util.logging.Logger l = java.util.logging.Logger.getLogger("dummy");
        @Override public java.util.logging.Logger getLogger() { return l; }
        // the remaining methods are irrelevant for this test and left empty
        @Override public org.bukkit.plugin.PluginDescriptionFile getDescription() { return null; }
        @Override public void onDisable() {}
        @Override public void onEnable() {}
        @Override public void onLoad() {}
        @Override public boolean isNaggable() { return false; }
        @Override public void setNaggable(boolean canNag) {}
        @Override public org.bukkit.configuration.file.FileConfiguration getConfig() { return null; }
        @Override public void reloadConfig() {}
        @Override public void saveConfig() {}
        @Override public void saveDefaultConfig() {}
        @Override public void saveResource(String resourcePath, boolean replace) {}
        @Override public java.io.File getDataFolder() { return null; }
        @Override public org.bukkit.plugin.PluginLoader getPluginLoader() { return null; }
        @Override public org.bukkit.Server getServer() { return null; }
        @Override public boolean isEnabled() { return true; }
        @Override public boolean isLoaded() { return true; }
        @Override public void reloadDataFolder() {}
    }, LogLevel.DEBUG);

    @Test
    public void roundTripShouldKeepNbt() throws Exception {
        NBTUtil util = new NBTUtil(handler, logger);
        ItemStack item = new ItemStack(Material.DIAMOND, 2);
        String raw = util.toRawString(item);
        ItemStack clone = util.fromRawString(raw);
        assertEquals(util.toRawString(item), util.toRawString(clone));
    }
}
