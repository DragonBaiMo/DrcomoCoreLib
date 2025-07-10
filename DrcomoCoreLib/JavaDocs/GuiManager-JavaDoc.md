### `GuiManager.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.gui.GuiManager`
  * **核心职责:** 提供与 GUI 操作相关的辅助能力，包括危险点击检测、光标清理以及安全播放音效；

**2. 如何实例化 (Initialization)**

  * **核心思想:** 这是一个<em>可实例化</em>的服务类，每个子插件应为其创建独立实例，并通过构造函数注入 `DebugUtil` 以获得符合自身日志级别的输出。
  * **构造函数:** `public GuiManager(DebugUtil logger)`
  * **代码示例:**
    ```java
    // 在你的子插件 onEnable() 方法中
    DebugUtil logger = new DebugUtil(this, DebugUtil.LogLevel.DEBUG);

    GuiManager guiManager = new GuiManager(logger);
    ```

**3. 公共API方法 (Public API Methods)**

  * #### `boolean isDangerousClick(ClickType click)`

      * **功能描述:** 根据 Bukkit 的 `ClickType` 判断一次点击是否属于风险操作（如 Shift、数字键、创意模式点击等）。
      * **参数说明:**
          * `click` (`ClickType`): Bukkit 定义的点击类型。

  * #### `void clearCursor(Player player, InventoryClickEvent event)`

      * **功能描述:** 清空玩家鼠标光标上的物品堆，更新背包视图；若出现异常将记录到日志。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。
          * `event` (`InventoryClickEvent`): 原始事件对象。

  * #### `void safePlaySound(Player player, Sound sound, float volume, float pitch)`

      * **功能描述:** 在指定玩家位置播放音效；如播放失败（版本兼容或其他异常），异常将被捕获并记录。
      * **参数说明:**
          * `player` (`Player`): 目标玩家。
          * `sound` (`Sound`): 音效枚举。
          * `volume` (`float`): 音量 (0.0 ~ 1.0)。
          * `pitch` (`float`): 音调 (0.5 ~ 2.0)。

----- 