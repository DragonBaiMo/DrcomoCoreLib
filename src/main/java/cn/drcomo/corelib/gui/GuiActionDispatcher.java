package cn.drcomo.corelib.gui;

import cn.drcomo.corelib.gui.interfaces.ClickAction;
import cn.drcomo.corelib.gui.interfaces.SlotPredicate;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.gui.GuiManager;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI 操作分发器，统一处理点击事件并分派到对应回调。
 */
public class GuiActionDispatcher {

    private final DebugUtil debug;
    private final GUISessionManager sessions;
    private final GuiManager guiManager;
    private final Map<String, ActionRegistry> registries = new ConcurrentHashMap<>();

    /**
     * 构造分发器。
     *
     * @param debug    日志工具
     * @param sessions 会话管理器
     * @param guiManager GUI管理器
     */
    public GuiActionDispatcher(DebugUtil debug, GUISessionManager sessions, GuiManager guiManager) {
        this.debug = debug;
        this.sessions = sessions;
        this.guiManager = guiManager;
    }

    /**
     * 注册点击回调。
     *
     * @param sessionId 会话ID
     * @param where     槽位条件
     * @param action    回调
     */
    public void register(String sessionId, SlotPredicate where, ClickAction action) {
        if (sessionId == null || where == null || action == null) return;
        ActionRegistry reg = registries.computeIfAbsent(sessionId, k -> new ActionRegistry());
        reg.add(where, action);
    }

    /**
     * 注销会话的所有回调。
     *
     * @param sessionId 会话ID
     */
    public void unregister(String sessionId) {
        if (sessionId == null) return;
        registries.remove(sessionId);
    }

    /**
     * 统一处理点击事件。
     *
     * @param ctx   上下文
     * @param event 原始事件
     */
    public void handleClick(ClickContext ctx, InventoryClickEvent event) {
        try {
            if (!sessions.validateSessionInventory(ctx.player(), event.getInventory())) {
                return;
            }
            guiManager.clearCursor(ctx.player(), event);
            event.setCancelled(true);
            if (ctx.isDangerous()) {
                return;
            }
            ActionRegistry reg = registries.get(ctx.sessionId());
            if (reg != null) {
                reg.dispatch(ctx);
            }
        } catch (Exception e) {
            debug.error("handleClick error", e);
        }
    }

    /**
     * 内部回调注册表。
     */
    private static class ActionRegistry {
        private final List<Map.Entry<SlotPredicate, ClickAction>> actions = new ArrayList<>();

        void add(SlotPredicate p, ClickAction a) {
            actions.add(Map.entry(p, a));
        }

        void dispatch(ClickContext ctx) {
            for (Map.Entry<SlotPredicate, ClickAction> e : actions) {
                if (e.getKey().test(ctx.slot())) {
                    e.getValue().execute(ctx);
                }
            }
        }
    }
}
