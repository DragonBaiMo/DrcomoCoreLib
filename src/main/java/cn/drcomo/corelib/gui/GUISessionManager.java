package cn.drcomo.corelib.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.inventory.InventoryClickEvent;

import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.util.DebugUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI 会话管理器。
 *
 * <p>只管理会话的创建、打开、关闭与验证，不主动监听任何事件，保持纯粹的单一职责。</p>
 * <p>所有依赖都通过构造方法注入，遵循零硬编码与控制反转原则。</p>
 */
public class GUISessionManager {

    /**
     * GUI 构建器回调接口，子插件负责实现其 GUI 构建逻辑。
     */
    @FunctionalInterface
    public interface GUICreator {
        /**
         * 根据给定玩家构建并返回一个新的 {@link Inventory}。
         *
         * @param player 玩家实例
         * @return 构建好的界面实例，若返回 {@code null} 将视为创建失败
         */
        Inventory create(Player player);
    }

    private final Plugin plugin;
    private final DebugUtil debug;
    private final MessageService msgSvc;
    /**
     * 使用 UUID 作为 Key，避免因 Player 实例更替导致的键不一致，并减少强引用导致的潜在泄漏。
     */
    private final Map<UUID, GUISession> sessions = new HashMap<>();
    private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    /**
     * 构造方法，不自动注册任何事件。
     *
     * @param plugin         主插件实例
     * @param debug          日志工具
     * @param messageService 可选信息服务
     */
    public GUISessionManager(Plugin plugin, DebugUtil debug, MessageService messageService) {
        this(plugin, debug, messageService, DEFAULT_SESSION_TIMEOUT);
    }

    /**
     * 构造方法，可自定义会话过期时间。
     *
     * @param plugin         主插件实例
     * @param debug          日志工具
     * @param messageService 可选信息服务
     * @param sessionTimeout 会话过期毫秒数
     */
    public GUISessionManager(Plugin plugin, DebugUtil debug, MessageService messageService, long sessionTimeout) {
        this.plugin = plugin;
        this.debug = debug;
        this.msgSvc = messageService;
        this.sessionTimeout = sessionTimeout;
    }

    /**
     * 公开只读访问：返回注入的 {@link Plugin} 实例。
     * 该方法仅用于诊断/集成场景，避免外部直接依赖字段。
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * 公开只读访问：返回注入的 {@link MessageService}，可能为 null。
     */
    public MessageService getMessageService() {
        return msgSvc;
    }

    /**
     * 创建并打开一个新的 GUI 会话。
     *
     * @param player    目标玩家
     * @param sessionId 会话标识，由调用方保证唯一
     * @param creator   界面构建回调
     * @return 创建并打开成功返回 {@code true}
     */
    public boolean openSession(Player player, String sessionId, GUICreator creator) {
        if (player == null || sessionId == null || creator == null) {
            debug.warn("openSession 参数不能为空");
            return false;
        }
        cleanExpiredSessions();
        try {
            Inventory inv = creator.create(player);
            if (inv == null) {
                debug.warn("GUICreator 返回 null");
                return false;
            }
            GUISession session = new GUISession(sessionId, inv, sessionTimeout);
            registerSession(player, session);
            player.openInventory(inv);
            debug.debug("open gui session: " + sessionId + " for " + player.getName());
            return true;
        } catch (Exception e) {
            debug.error("openSession error: " + sessionId, e);
            return false;
        }
    }

    /**
     * 关闭并注销玩家的当前会话。
     *
     * @param player 目标玩家
     */
    public void closeSession(Player player) {
        GUISession session = sessions.get(player.getUniqueId());
        if (session != null) {
            safeClose(player, session);
        }
    }

    /**
     * 关闭并清理所有活跃会话。
     */
    public void closeAllSessions() {
        List<UUID> targets = new ArrayList<>(sessions.keySet());
        for (UUID uid : targets) {
            GUISession s = sessions.get(uid);
            if (s != null) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    safeClose(p, s);
                } else {
                    // 玩家不在线，仅移除会话条目
                    unregisterSession(uid);
                }
            }
        }
    }

    /**
     * 获取玩家当前会话的标识。
     *
     * @param player 目标玩家
     * @return 会话 ID，若不存在则返回 {@code null}
     */
    public String getCurrentSessionId(Player player) {
        cleanExpiredSessions();
        GUISession s = sessions.get(player.getUniqueId());
        return s == null ? null : s.sessionId;
    }

    /**
     * 判断给定玩家是否拥有活跃会话。
     *
     * @param player 目标玩家
     * @return 若存在会话返回 {@code true}
     */
    public boolean hasSession(Player player) {
        cleanExpiredSessions();
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * 验证给定界面是否属于玩家当前会话。
     *
     * @param player 玩家
     * @param inv    待验证的界面
     * @return true 表示验证通过
     */
    public boolean validateSessionInventory(Player player, Inventory inv) {
        if (player == null || inv == null) return false;
        GUISession s = sessions.get(player.getUniqueId());
        // 引用比较以确保确为同一实例，避免不同实例但内容相同的误判
        return s != null && s.inventory == inv;
    }

    /**
     * 判断此次点击是否作用于玩家当前会话的 GUI 区域（顶部容器）。
     * <p>通过比较 {@code event.getClickedInventory()} 与会话记录的 Inventory 是否相同来判定，
     * 能准确区分玩家背包槽位。</p>
     *
     * @param player 玩家实例
     * @param event  点击事件
     * @return true 若点击的容器即为当前会话 GUI
     */
    public boolean isSessionInventoryClick(Player player, InventoryClickEvent event) {
        if (player == null || event == null) return false;
        GUISession s = sessions.get(player.getUniqueId());
        if (s == null) return false;
        Inventory clicked = event.getClickedInventory();
        return clicked != null && clicked == s.inventory;
    }

    /**
     * 判断此次点击是否发生在玩家背包区域（即不属于当前会话 GUI）。
     *
     * @param player 玩家实例
     * @param event  点击事件
     * @return true 当玩家存在会话且点击的容器不是会话 GUI 时返回 true
     */
    public boolean isPlayerInventoryClick(Player player, InventoryClickEvent event) {
        if (player == null || event == null) return false;
        GUISession s = sessions.get(player.getUniqueId());
        if (s == null) return false;
        Inventory clicked = event.getClickedInventory();
        return clicked == null || clicked != s.inventory;
    }

    /**
     * 插件关闭时强制回收所有 GUI 物品并返还给玩家。
     * <p>调度器已不可用，必须同步调用；若背包已满则自然掉落到玩家脚下。</p>
     */
    public void flushOnDisable() {
        List<UUID> targets = new ArrayList<>(sessions.keySet());
        for (UUID uid : targets) {
            GUISession s = sessions.get(uid);
            if (s != null) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    flushSessionItems(p, s);
                    safeClose(p, s);
                } else {
                    unregisterSession(uid);
                }
            }
        }
    }

    /**
     * 设置新的会话过期时间（毫秒）。
     *
     * @param sessionTimeout 过期时间，单位毫秒
     */
    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    // ================== 内部类 ==================
    private static class GUISession {
        final String sessionId;
        final Inventory inventory;
        final long createTime;
        final long timeout;

        GUISession(String id, Inventory inv, long timeout) {
            this.sessionId = id;
            this.inventory = inv;
            this.timeout = timeout;
            this.createTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createTime > timeout;
        }
    }

    private static final long DEFAULT_SESSION_TIMEOUT = 5 * 60 * 1000L;

    // ================== 私有助手 ==================
    private void registerSession(Player player, GUISession session) {
        sessions.put(player.getUniqueId(), session);
    }

    private void unregisterSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private void unregisterSession(UUID uuid) {
        sessions.remove(uuid);
    }

    private void cleanExpiredSessions() {
        Iterator<Map.Entry<UUID, GUISession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GUISession> entry = it.next();
            if (entry.getValue().isExpired()) {
                UUID uid = entry.getKey();
                GUISession s = entry.getValue();
                it.remove();
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    safeClose(p, s);
                }
            }
        }
    }

    private void safeClose(Player player, GUISession session) {
        try {
            player.closeInventory();
        } catch (Exception e) {
            debug.error("close inventory error", e);
        }
        unregisterSession(player);
    }

    private void flushSessionItems(Player player, GUISession session) {
        if (player == null || session == null) return;
        try {
            Inventory guiInv = session.inventory;
            if (guiInv == null) return;
            for (int i = 0; i < guiInv.getSize(); i++) {
                var item = guiInv.getItem(i);
                if (item == null) continue;
                Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item);
                for (org.bukkit.inventory.ItemStack lf : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), lf);
                }
                guiInv.setItem(i, null);
            }
            player.updateInventory();
        } catch (Exception e) {
            debug.error("flushSessionItems error", e);
        }
    }
}
