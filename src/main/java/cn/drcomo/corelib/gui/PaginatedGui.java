package cn.drcomo.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * <h2>可分页 GUI 抽象基类</h2>
 *
 * <p>内部维护页码、导航槽位和页面总数，并通过
 * {@link GUISessionManager} 与 {@link GuiActionDispatcher}
 * 完成会话注册及点击事件分发。</p>
 */
public abstract class PaginatedGui {

    private final GUISessionManager sessions;
    private final GuiActionDispatcher dispatcher;
    private final int prevSlot;
    private final int nextSlot;
    private final int pageSize;

    private int pageCount;
    private int currentPage;
    private String sessionId;

    /**
     * 构造函数。
     *
     * @param sessions   会话管理器
     * @param dispatcher 点击事件分发器
     * @param pageSize   每页展示槽位数量
     * @param prevSlot   上一页按钮槽位
     * @param nextSlot   下一页按钮槽位
     */
    public PaginatedGui(GUISessionManager sessions,
                        GuiActionDispatcher dispatcher,
                        int pageSize,
                        int prevSlot,
                        int nextSlot) {
        this.sessions = sessions;
        this.dispatcher = dispatcher;
        this.pageSize = pageSize;
        this.prevSlot = prevSlot;
        this.nextSlot = nextSlot;
    }

    /**
     * 打开 GUI 并渲染第一页。
     *
     * @param player    目标玩家
     * @param sessionId 会话标识
     */
    public void open(Player player, String sessionId) {
        this.sessionId = sessionId;
        this.pageCount = calcPageCount(getTotalItemCount(player));
        this.currentPage = 0;
        sessions.openSession(player, sessionId, p -> {
            Inventory inv = createInventory(p);
            renderPage(p, inv, currentPage, pageCount);
            return inv;
        });
        registerNavigation();
    }

    /**
     * 显示指定页码。
     *
     * @param player 目标玩家
     * @param page   页码（从 0 开始）
     */
    public void showPage(Player player, int page) {
        if (page < 0 || page >= pageCount) return;
        currentPage = page;
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!sessions.validateSessionInventory(player, inv)) return;
        renderPage(player, inv, page, pageCount);
    }

    /** 跳转到下一页。 */
    public void showNext(Player player) {
        showPage(player, currentPage + 1);
    }

    /** 跳转到上一页。 */
    public void showPrev(Player player) {
        showPage(player, currentPage - 1);
    }

    /**
     * 计算总页数。
     *
     * @param total 条目总数
     * @return 页数
     */
    public int calcPageCount(int total) {
        if (pageSize <= 0) return 1;
        return (total + pageSize - 1) / pageSize;
    }

    /**
     * 关闭 GUI 会话并注销相关回调。
     *
     * @param player 目标玩家
     */
    public void close(Player player) {
        dispatcher.unregister(sessionId);
        sessions.closeSession(player);
    }

    /** @return 当前页码（从 0 开始） */
    public int getCurrentPage() {
        return currentPage;
    }

    /** @return 总页数 */
    public int getPageCount() {
        return pageCount;
    }

    /** @return 每页显示槽位数量 */
    protected int getPageSize() {
        return pageSize;
    }

    /** @return 上一页按钮槽位 */
    protected int getPrevSlot() {
        return prevSlot;
    }

    /** @return 下一页按钮槽位 */
    protected int getNextSlot() {
        return nextSlot;
    }

    //================ protected hooks =================

    /**
     * 获取要展示的条目总数，用于计算页数。
     */
    protected abstract int getTotalItemCount(Player player);

    /**
     * 创建新的 Inventory 供展示使用。
     */
    protected abstract Inventory createInventory(Player player);

    /**
     * 渲染指定页的内容。
     *
     * @param player     玩家
     * @param inv        要渲染的界面
     * @param page       页码（从 0 开始）
     * @param totalPages 总页数
     */
    protected abstract void renderPage(Player player,
                                       Inventory inv,
                                       int page,
                                       int totalPages);

    //================ private helpers =================
    private void registerNavigation() {
        // 热路径优化：直接按槽位注册，避免每次点击时遍历谓词
        dispatcher.registerForSlot(sessionId, prevSlot, ctx -> showPrev(ctx.player()));
        dispatcher.registerForSlot(sessionId, nextSlot, ctx -> showNext(ctx.player()));
    }
}
