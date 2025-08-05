package cn.drcomo.corelib.gui.session;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 通用的玩家会话管理器。
 *
 * <p>通过泛型形式保存玩家会话数据，可设置超时并在玩家离线时自动清理。</p>
 * <p>无需自行注册事件，构造时会自动向插件管理器注册监听器。</p>
 */
public class PlayerSessionManager<T> implements Listener {

    /** 默认会话过期时间：5 分钟 */
    private static final long DEFAULT_SESSION_TIMEOUT = 5 * 60 * 1000L;

    private final Plugin plugin;
    private final DebugUtil debug;
    private final Map<UUID, Session<T>> sessions = new HashMap<>();
    private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    /**
     * 构造函数，使用默认 5 分钟超时。
     *
     * @param plugin 插件实例
     * @param debug  日志工具
     */
    public PlayerSessionManager(Plugin plugin, DebugUtil debug) {
        this(plugin, debug, DEFAULT_SESSION_TIMEOUT);
    }

    /**
     * 构造函数，可自定义会话过期时间。
     *
     * @param plugin         插件实例
     * @param debug          日志工具
     * @param sessionTimeout 会话超时时间（毫秒），<=0 表示不过期
     */
    public PlayerSessionManager(Plugin plugin, DebugUtil debug, long sessionTimeout) {
        this.plugin = plugin;
        this.debug = debug;
        this.sessionTimeout = sessionTimeout;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 创建或替换玩家的会话数据。
     *
     * @param player 玩家
     * @param data   会话数据
     */
    public void createSession(Player player, T data) {
        if (player == null) {
            debug.warn("createSession player null");
            return;
        }
        cleanExpired();
        sessions.put(player.getUniqueId(), new Session<>(data, sessionTimeout));
    }

    /**
     * 获取玩家会话数据。
     *
     * @param player 玩家
     * @return 会话数据，若不存在或已过期则返回 null
     */
    public T getSession(Player player) {
        if (player == null) return null;
        cleanExpired();
        Session<T> s = sessions.get(player.getUniqueId());
        return s == null ? null : s.data;
    }

    /**
     * 销毁玩家会话。
     *
     * @param player 玩家
     */
    public void destroySession(Player player) {
        if (player == null) return;
        sessions.remove(player.getUniqueId());
    }

    /**
     * 判断玩家是否存在活跃会话。
     *
     * @param player 玩家
     * @return true 表示存在
     */
    public boolean hasSession(Player player) {
        if (player == null) return false;
        cleanExpired();
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * 调整全局会话超时设置。
     *
     * @param sessionTimeout 超时时间，<=0 表示禁用过期检查
     */
    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        destroySession(event.getPlayer());
    }

    // ================== 私有助手 ==================
    private void cleanExpired() {
        if (sessionTimeout <= 0) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Session<T>>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session<T>> e = it.next();
            if (e.getValue().isExpired(now)) {
                it.remove();
            }
        }
    }

    private static class Session<T> {
        final T data;
        final long createTime;
        final long timeout;

        Session(T data, long timeout) {
            this.data = data;
            this.timeout = timeout;
            this.createTime = System.currentTimeMillis();
        }

        boolean isExpired(long now) {
            return timeout > 0 && now - createTime > timeout;
        }
    }
}
