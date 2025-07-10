### `GuiUtil.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.gui.GuiUtil`
  * **核心职责:** 提供与 GUI 操作相关的静态辅助方法，包括危险点击判断、光标清理以及安全播放音效。

**2. 如何实例化 (Initialization)**

  * **核心思想:** 该类为纯静态工具类，构造函数被私有化，不应被实例化，所有方法均通过类名直接调用。

**3. 公共API方法 (Public API Methods)**

  * #### `isDangerousClick(ClickType click)`

      * **返回类型:** `boolean`
      * **功能描述:** 根据 `ClickType` 判断一次点击是否可能造成物品移动等危险操作。
      * **参数说明:**
          * `click` (`ClickType`): Bukkit 定义的点击类型。

  * #### `clearCursor(Player player, InventoryClickEvent event)`

      * **返回类型:** `void`
      * **功能描述:** 清空玩家鼠标上的物品堆，避免拖拽造成异常。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。
          * `event` (`InventoryClickEvent`): 原始事件对象。

  * #### `safePlaySound(Player player, Sound sound, float volume, float pitch)`

      * **返回类型:** `void`
      * **功能描述:** 在指定玩家处播放音效，若播放过程中发生异常会被捕获并记录日志。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。
          * `sound` (`Sound`): 音效枚举。
          * `volume` (`float`): 音量。
          * `pitch` (`float`): 音调。

-----
