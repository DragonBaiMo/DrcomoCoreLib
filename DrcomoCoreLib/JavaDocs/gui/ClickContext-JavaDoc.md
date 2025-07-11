### `ClickContext.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.gui.ClickContext`
  * **核心职责:** 作为 GUI 点击事件的统一数据载体，封装玩家、会话及点击相关信息。

**2. 如何实例化 (Initialization)**

  * **核心思想:** 该类是 Java 17 `record`，应通过其静态方法 `from(InventoryClickEvent, GUISessionManager)` 创建。
  * **代码示例:**
    ```java
    ClickContext ctx = ClickContext.from(event, sessionManager);
    dispatcher.handleClick(ctx, event);
    ```

**3. 公共API方法 (Public API Methods)**

  * #### `from(InventoryClickEvent e, GUISessionManager sessionMgr)`

      * **返回类型:** `ClickContext`
      * **功能描述:** 根据事件与会话管理器构建新的上下文实例。
      * **参数说明:**
          * `e` (`InventoryClickEvent`): 原始事件。
          * `sessionMgr` (`GUISessionManager`): 会话管理器。

  * #### `isShift()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断点击是否为 Shift + 点击。

  * #### `isLeftClick()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断是否按下左键。

  * #### `isRightClick()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断是否按下右键。

  * #### `isMiddleClick()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断是否按下中键。

  * #### `isNumberKey()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断点击是否来自数字键。

  * #### `isDrop()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断是否按下丢弃键。

  * #### `isControlDrop()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断是否按下 Ctrl+丢弃。

  * #### `isSwapOffhand()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断是否与副手物品交换。

  * #### `isDangerous()`

      * **返回类型:** `boolean`
      * **功能描述:** 判断该点击是否被视为危险操作。
