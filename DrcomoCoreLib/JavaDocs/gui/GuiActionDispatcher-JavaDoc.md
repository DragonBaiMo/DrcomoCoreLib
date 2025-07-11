### `GuiActionDispatcher.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.gui.GuiActionDispatcher`
  * **核心职责:** 在验证会话和拦截危险操作后，将玩家的点击事件分发到已注册的回调函数。

**2. 如何实例化 (Initialization)**

  * **核心思想:** 分发器需要外部提供 `DebugUtil` 与 `GUISessionManager` 实例，以遵循控制反转原则。
  * **构造函数:** `public GuiActionDispatcher(DebugUtil debug, GUISessionManager sessions)`

**3. 公共API方法 (Public API Methods)**

  * #### `register(String sessionId, SlotPredicate where, ClickAction action)`

      * **返回类型:** `void`
      * **功能描述:** 为指定会话注册一个点击回调及其槽位过滤条件。
      * **参数说明:**
          * `sessionId` (`String`): 会话标识。
          * `where` (`SlotPredicate`): 槽位判断条件。
          * `action` (`ClickAction`): 点击回调。

  * #### `unregister(String sessionId)`

      * **返回类型:** `void`
      * **功能描述:** 移除指定会话的所有回调。
      * **参数说明:**
          * `sessionId` (`String`): 会话标识。

  * #### `handleClick(ClickContext ctx, InventoryClickEvent event)`

      * **返回类型:** `void`
      * **功能描述:** 统一处理事件，验证会话并分派到符合条件的回调。
      * **参数说明:**
          * `ctx` (`ClickContext`): 点击上下文。
          * `event` (`InventoryClickEvent`): 原始事件。

**4. 使用示例 (Usage Example)**

```java
public class MyListener implements Listener {
    private final GuiActionDispatcher dispatcher;
    private final GUISessionManager sessionMgr;

    public MyListener(Plugin plugin) {
        DebugUtil logger = new DebugUtil(plugin, DebugUtil.LogLevel.INFO);
        this.sessionMgr = new GUISessionManager(plugin, logger, null);
        this.dispatcher = new GuiActionDispatcher(logger, sessionMgr);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        ClickContext ctx = ClickContext.from(event, sessionMgr);
        dispatcher.handleClick(ctx, event);
    }
}
```
