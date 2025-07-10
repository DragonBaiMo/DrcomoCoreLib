package cn.drcomo.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.util.DebugUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<Player, GUISession> sessions = new HashMap<>();

    /**
     * 构造方法，不自动注册任何事件。
     *
     * @param plugin         主插件实例
     * @param debug          日志工具
     * @param messageService 可选信息服务
     */
    public GUISessionManager(Plugin plugin, DebugUtil debug, MessageService messageService) {
        this.plugin = plugin;
        this.debug = debug;
        this.msgSvc = messageService;
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
            GUISession session = new GUISession(sessionId, inv);
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
        GUISession session = sessions.get(player);
        if (session != null) {
            safeClose(player, session);
        }
    }

    /**
     * 关闭并清理所有活跃会话。
     */
    public void closeAllSessions() {
        List<Player> targets = new ArrayList<>(sessions.keySet());
        for (Player p : targets) {
            GUISession s = sessions.get(p);
            if (s != null) {
                safeClose(p, s);
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
        GUISession s = sessions.get(player);
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
        return sessions.containsKey(player);
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
        GUISession s = sessions.get(player);
        return s != null && s.inventory.equals(inv);
    }

    // ================== 内部类 ==================
    private static class GUISession {
        final String sessionId;
        final Inventory inventory;
        final long createTime;
        GUISession(String id, Inventory inv) {
            this.sessionId = id;
            this.inventory = inv;
            this.createTime = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - createTime > SESSION_TIMEOUT;
        }
    }

    private static final long SESSION_TIMEOUT = 5 * 60 * 1000L;

    // ================== 私有助手 ==================
    private void registerSession(Player player, GUISession session) {
        sessions.put(player, session);
    }

    private void unregisterSession(Player player) {
        sessions.remove(player);
    }

    private void cleanExpiredSessions() {
        List<Player> expired = new ArrayList<>();
        for (Map.Entry<Player, GUISession> e : sessions.entrySet()) {
            if (e.getValue().isExpired()) {
                expired.add(e.getKey());
            }
        }
        for (Player p : expired) {
            GUISession s = sessions.get(p);
            if (s != null) {
                safeClose(p, s);
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
}
