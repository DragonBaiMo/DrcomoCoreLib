### `SlotPredicate.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.gui.interfaces.SlotPredicate`
  * **核心职责:** 用于判断某个槽位是否应触发对应的 `ClickAction` 的函数式接口。

**2. 如何实例化 (Initialization)**

  * **核心思想:** 作为函数式接口，通常以 lambda 表达式实现，例如 `slot -> slot == 0`。

**3. 公共API方法 (Public API Methods)**

  * #### `test(int slot)`

      * **返回类型:** `boolean`
      * **功能描述:** 判断给定槽位是否匹配。
      * **参数说明:**
          * `slot` (`int`): 事件中的槽位序号。
