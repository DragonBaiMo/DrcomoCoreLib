### `ConfigValidator.java`

**1. 概述 (Overview)**

  * **完整路径:** `cn.drcomo.corelib.config.ConfigValidator`
  * **核心职责:** 一个通用的配置校验器，允许开发者以链式方式声明配置项的类型、是否必填以及自定义校验规则，最终给出校验结果。

**2. 如何实例化 (Initialization)**

  * **构造函数:** `public ConfigValidator(YamlUtil yamlUtil, DebugUtil logger)`
  * **代码示例:**
    ```java
    YamlUtil yamlUtil = new YamlUtil(plugin, logger);
    yamlUtil.loadConfig("config");
    ConfigValidator validator = new ConfigValidator(yamlUtil, logger);
    ```

**3. 公共API方法 (Public API Methods)**

  * #### `validateString(String path)`

      * **返回类型:** `ValidatorBuilder`
      * **功能描述:** 声明一个字符串类型的配置项，返回的 `ValidatorBuilder` 可继续设置 `required()` 或 `custom()` 约束。
      * **参数说明:**
          * `path` (`String`): 配置路径。

  * #### `validateNumber(String path)`

      * **返回类型:** `ValidatorBuilder`
      * **功能描述:** 声明一个数字类型的配置项，允许校验整数或浮点数字符串。
      * **参数说明:**
          * `path` (`String`): 配置路径。

  * #### `validateEnum(String path, Class<E> enumClass)`

      * **返回类型:** `ValidatorBuilder`
      * **功能描述:** 声明一个枚举类型的配置项，值将按不区分大小写的方式解析为指定枚举。
      * **参数说明:**
          * `path` (`String`): 配置路径。
          * `enumClass` (`Class<E>`): 期望的枚举类型。

  * #### `required()` *(ValidatorBuilder)*

      * **返回类型:** `ValidatorBuilder`
      * **功能描述:** 标记当前配置项为必填项，若缺失则校验失败。
      * **参数说明:** 无。

  * #### `custom(Predicate<Object> rule, String message)` *(ValidatorBuilder)*

      * **返回类型:** `ValidatorBuilder`
      * **功能描述:** 为当前配置项添加自定义校验规则，当 `rule.test(value)` 返回 `false` 时，校验失败并记录 `message`。
      * **参数说明:**
          * `rule` (`Predicate<Object>`): 自定义断言。
          * `message` (`String`): 失败时的提示信息。

  * #### `validate(Configuration config)`

      * **返回类型:** `ValidationResult`
      * **功能描述:** 针对给定的 `Configuration` 执行所有已声明的校验，并返回结果对象，包含是否通过及所有错误信息。
      * **参数说明:**
          * `config` (`Configuration`): 要校验的配置实例。

