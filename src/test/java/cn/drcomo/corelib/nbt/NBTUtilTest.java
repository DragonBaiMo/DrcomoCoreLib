package cn.drcomo.corelib.nbt;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 用于验证 NBTUtil 在写入相同值时不会创建新实例。
 */
public class NBTUtilTest {

    private final NbtKeyHandler handler = new NbtKeyHandler() {
        @Override
        public boolean isValidKey(String fullKey) { return true; }

        @Override
        public String addPrefix(String key) { return key; }

        @Override
        public String removePrefix(String fullKey) { return fullKey; }
    };

    private NBTUtil newUtil() {
        return new NBTUtil(handler, null);
    }

    @Test
    public void 同值写入整数返回原实例() {
        ItemStack item = new ItemStack(Material.STONE);
        NBTUtil util = newUtil();

        ItemStack first = util.setInt(item, "intKey", 1);
        ItemStack second = util.setInt(first, "intKey", 1);

        assertSame(first, second, "写入相同整数值应返回原实例");
    }

    @Test
    public void 同值写入字符串返回原实例() {
        ItemStack item = new ItemStack(Material.STONE);
        NBTUtil util = newUtil();

        ItemStack first = util.setString(item, "strKey", "abc");
        ItemStack second = util.setString(first, "strKey", "abc");

        assertSame(first, second, "写入相同字符串应返回原实例");
    }
}

