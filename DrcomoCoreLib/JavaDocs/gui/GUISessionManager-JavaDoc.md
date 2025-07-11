### `GUISessionManager.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.gui.GUISessionManager`
  * **核心职责:** 统一管理所有由子插件创建的 GUI 会话。负责在玩家打开界面时记录会话、在关闭时注销，并提供会话验证与批量清理能力。

**2. 如何实例化 (Initialization)**

  * **核心思想:** 该管理器遵循控制反转原则，构造时需要传入主插件实例、调试工具以及可选的 `MessageService`。它本身不注册事件，事件应由外部绑定。
  * **构造函数:** `public GUISessionManager(Plugin plugin, DebugUtil debug, MessageService messageService)`
  * **代码示例:**
    ```java
    Plugin plugin = this; // 你的插件主类
    DebugUtil logger = new DebugUtil(plugin, DebugUtil.LogLevel.INFO);
    MessageService msgSvc = null; // 如无需要可传入 null

    GUISessionManager manager = new GUISessionManager(plugin, logger, msgSvc);
    ```

**3. 公共API方法 (Public API Methods)**

  * #### `openSession(Player player, String sessionId, GUICreator creator)`
 
      * **返回类型:** `boolean`
      * **功能描述:** 创建并打开一个新的 GUI，会自动登记会话并在之后的验证中使用。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。
          * `sessionId` (`String`): 会话标识，由调用方保证唯一。
          * `creator` (`GUICreator`): 用于构建界面的回调。

  * #### `closeSession(Player player)`

      * **返回类型:** `void`
      * **功能描述:** 关闭并注销指定玩家的当前会话。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。

  * #### `closeAllSessions()`

      * **返回类型:** `void`
      * **功能描述:** 关闭并注销所有已记录的会话。
      * **参数说明:** 无。

  * #### `getCurrentSessionId(Player player)`

      * **返回类型:** `String`
      * **功能描述:** 获取玩家当前会话的标识，如果不存在则返回 `null`。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。

  * #### `hasSession(Player player)`

      * **返回类型:** `boolean`
      * **功能描述:** 判断指定玩家是否存在活跃会话。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。

  * #### `validateSessionInventory(Player player, Inventory inv)`

      * **返回类型:** `boolean`
      * **功能描述:** 检查给定界面是否属于玩家当前会话，用于拦截非法或过期的操作。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。
          * `inv` (`Inventory`): 要验证的界面实例。
