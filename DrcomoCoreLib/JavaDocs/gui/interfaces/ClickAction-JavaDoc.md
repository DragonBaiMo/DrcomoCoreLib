### `ClickAction.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.gui.interfaces.ClickAction`
  * **核心职责:** 代表一次 GUI 点击行为的处理逻辑，是一个可被 lambda 表达式实现的函数式接口。

**2. 如何实例化 (Initialization)**

  * **核心思想:** 作为函数式接口，通常以 lambda 或方法引用的形式实现，例如 `ctx -> { ... }`。

**3. 公共API方法 (Public API Methods)**

  * #### `execute(ClickContext ctx)`

      * **返回类型:** `void`
      * **功能描述:** 当满足槽位条件时由分发器调用，执行业务逻辑。
      * **参数说明:**
          * `ctx` (`ClickContext`): 点击上下文。
  