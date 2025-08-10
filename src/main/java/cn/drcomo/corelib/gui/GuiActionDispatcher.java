package cn.drcomo.corelib.gui;

import cn.drcomo.corelib.gui.interfaces.ClickAction;
import cn.drcomo.corelib.gui.interfaces.SlotPredicate;
import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
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
     * @param debug      日志工具
     * @param sessions   会话管理器
     * @param guiManager GUI 管理器
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
     * 为指定槽位注册点击回调（热路径优化）。
     * 该方法避免在分发时遍历所有谓词，实现 O(1) 槽位直达。
     *
     * @param sessionId 会话 ID
     * @param slot      槽位编号
     * @param action    回调
     */
    public void registerForSlot(String sessionId, int slot, ClickAction action) {
        if (sessionId == null || action == null) return;
        ActionRegistry reg = registries.computeIfAbsent(sessionId, k -> new ActionRegistry());
        reg.addForSlot(slot, action);
    }

    /**
     * 注册单次执行的点击回调，首次触发后自动注销。
     *
     * @param sessionId 会话ID
     * @param where     槽位条件
     * @param action    回调
     */
    public void registerOnce(String sessionId, SlotPredicate where, ClickAction action) {
        if (sessionId == null || where == null || action == null) return;
        ActionRegistry reg = registries.computeIfAbsent(sessionId, k -> new ActionRegistry());
        reg.addOnce(where, action);
    }

    /**
     * 为指定槽位注册单次执行的点击回调。
     * @param sessionId 会话 ID
     * @param slot      槽位编号
     * @param action    回调
     */
    public void registerOnceForSlot(String sessionId, int slot, ClickAction action) {
        if (sessionId == null || action == null) return;
        ActionRegistry reg = registries.computeIfAbsent(sessionId, k -> new ActionRegistry());
        reg.addOnceForSlot(slot, action);
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
        private static class Entry {
            final SlotPredicate predicate;
            final ClickAction action;

            Entry(SlotPredicate predicate, ClickAction action) {
                this.predicate = predicate;
                this.action = action;
            }
        }

        /** 通用（基于谓词）注册集合 */
        private final List<Entry> actions = new ArrayList<>();
        /** 指定槽位的直达回调集合（热路径） */
        private final Map<Integer, List<ClickAction>> slotActions = new HashMap<>();

        void add(SlotPredicate p, ClickAction a) {
            actions.add(new Entry(p, a));
        }

        void addForSlot(int slot, ClickAction a) {
            slotActions.computeIfAbsent(slot, k -> new ArrayList<>()).add(a);
        }

        void addOnce(SlotPredicate p, ClickAction a) {
            Entry[] ref = new Entry[1];
            ClickAction wrapper = ctx -> {
                try {
                    a.execute(ctx);
                } finally {
                    actions.remove(ref[0]);
                }
            };
            ref[0] = new Entry(p, wrapper);
            actions.add(ref[0]);
        }

        void addOnceForSlot(int slot, ClickAction a) {
            List<ClickAction> list = slotActions.computeIfAbsent(slot, k -> new ArrayList<>());
            final ClickAction[] ref = new ClickAction[1];
            ClickAction wrapper = ctx -> {
                try {
                    a.execute(ctx);
                } finally {
                    // 移除自身
                    list.remove(ref[0]);
                }
            };
            ref[0] = wrapper;
            list.add(wrapper);
        }

        void dispatch(ClickContext ctx) {
            // 1) 先处理槽位直达的回调（热路径）
            List<ClickAction> direct = slotActions.get(ctx.slot());
            if (direct != null && !direct.isEmpty()) {
                // 复制一份避免回调过程中修改集合导致并发修改
                List<ClickAction> snapshot = new ArrayList<>(direct);
                for (ClickAction fn : snapshot) {
                    fn.execute(ctx);
                }
            }

            // 2) 再处理通用谓词回调
            for (int i = 0; i < actions.size(); i++) {
                Entry e = actions.get(i);
                if (e.predicate.test(ctx.slot())) {
                    int before = actions.size();
                    e.action.execute(ctx);
                    if (actions.size() < before) {
                        i--;
                    }
                }
            }
        }
    }
}
