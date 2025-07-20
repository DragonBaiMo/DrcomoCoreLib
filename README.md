# DrcomoCoreLib 开发法典与用户指南

### **核心哲学：成为一个纯粹的“军火库”，而非“预制营房”**

`DrcomoCoreLib` 的唯一使命，是为所有依赖它的子插件提供**高复用、零耦合、完全可控**的工具集。它是一个抽象的逻辑工具箱，而非一个具象的功能插件。

我们不创造功能，我们只**赋能创造**。

## **第一章：贡献者开发法典 (Contributor's Codex)**

**本章是为所有 `DrcomoCoreLib` 的开发者和贡献者制定的最高准则。任何代码的提交都必须严格遵守以下原则。**

### **第一条：三大基石（The Three Pillars）**

这是构建 `DrcomoCoreLib` 可靠代码宇宙的三大定律，不可违背。

1.  **零硬编码原则 (The Zero Hardcoding Principle)**

      * **定义：** 禁止在代码中出现任何写死的配置项（文件名、路径、URL、配置键等）。所有可变值都必须作为参数传入。

2.  **控制反转原则 (The Inversion of Control Principle)**

      * **定义：** 自身不创建、不管理依赖的生命周期。所有外部依赖都必须通过**构造函数注入 (Constructor Injection)** 的方式传入。

3.  **单一职责原则 (The Single Responsibility Principle)**

      * **定义：** 每个模块只解决一个通用的开发痛点，严禁将特定插件的业务逻辑渗透进核心库。

### **第二条：如何新增一个模块（How to Add a New Module）**

当为本库新增一个工具类（如 `XxxUtil`, `XxxManager`）时，必须遵循以下流程：

1.  **设计为可实例化类：** 新的类必须是一个标准的 `public` 类，拥有一个 `public` 构造函数。严禁将其设计为静态工具类（即`private`构造函数+静态方法）。
2.  **注入所有依赖：** 在构造函数中声明并接收所有该类需要的外部依赖（如 `Plugin`, `DebugUtil` 等）。
3.  **使用实例方法：** 类的所有核心功能都应作为实例方法提供，并使用通过构造函数注入的依赖。
4.  **命名规范：** 如果该类需要被实例化并持有状态或依赖，应使用 `Manager`, `Service`, `Provider` 等后缀，而非 `Util`。

### **第三条：黄金法则（The Golden Rules）**

如果你的修改违背了以下任何一条，它将被无情地驳回。

  - ❌ **严禁写死**任何配置值。
  - ✅ **必须注入**所有外部依赖。
  - ✅ **必须保持**模块功能的纯粹与独立。

### **第四条：代码与文档同步 (Code and Documentation Synchronization)**

所有对公开（`public`）API 的修改，都必须在 `DrcomoCoreLib/JavaDocs` 同步更新其对应的 Javadoc 注释。确保代码的行为、参数、返回值与文档描述完全一致。新增的公开类与方法，同样需要提供清晰的 Javadoc。

**贡献代码，即是认同此约。**

## **第二章：子插件用户指南 (Sub-Plugin User Guide)**

**本章面向所有希望使用 `DrcomoCoreLib` 的外部插件开发者。**

### **安装与依赖**

1.  将 `DrcomoCoreLib.jar` 放入服务器的 `plugins` 文件夹。
2.  在你的插件 `plugin.yml` 中添加依赖：
    ```yaml
    depend: [DrcomoCoreLib]
    ```

### **核心使用范例**

`DrcomoCoreLib` 的所有工具类都不能直接使用，你必须在你的插件中通过 `new` 关键字创建它们的实例，并将依赖注入。

```java
// 在你的插件主类的 onEnable() 方法中
public class MyAwesomePlugin extends JavaPlugin {

    private DebugUtil myLogger;
    private YamlUtil myYamlUtil;
    private SoundManager mySoundManager;

    @Override
    public void onEnable() {
        // 1. 为你的插件创建独立的日志工具
        myLogger = new DebugUtil(this, DebugUtil.LogLevel.INFO);

        // 2. 为你的插件创建独立的 Yaml 配置工具，并注入日志实例
        myYamlUtil = new YamlUtil(this, myLogger);
        myYamlUtil.loadConfig("config");
        myYamlUtil.watchConfig("config", updated ->
                myLogger.info("配置文件已重新加载！"));

        // 3. 实例化 SoundManager，注入所有需要的依赖
        mySoundManager = new SoundManager(
            this,
            myYamlUtil,
            myLogger,
            "mySounds.yml", // 自定义配置文件名
            1.0f,           // 全局音量
            true            // 找不到音效时警告
        );
        mySoundManager.loadSounds(); // 手动加载音效

        // 4. 使用类型安全的方式读取配置
        boolean autoSave = myYamlUtil.getValue("settings.auto-save", Boolean.class, true);
        if (autoSave) {
            myLogger.info("自动保存已启用");
        }

        // 5. 备份数据并清理旧归档
        ArchiveUtil archiveUtil = new ArchiveUtil(myLogger);
        String zip = archiveUtil.archiveByDate("plugins/MyPlugin/data", "backups");
        archiveUtil.cleanupOldArchives("backups", 30);

        myLogger.info("我的插件已成功加载，并配置好了核心库工具！");
    }
}
```

### **核心模块一览**

本库提供以下核心工具类，所有类都需通过 `new` 关键字实例化使用：

  * `DebugUtil`: 分级日志工具。
  * `YamlUtil`: YAML 配置文件管理器，可一次性加载目录内的多份配置。
  * `MessageService`: 支持多语言和 PlaceholderAPI 的消息管理器。
  * `SoundManager`: 音效管理器。
  * `NBTUtil`: 物品 NBT 数据操作工具。
  * `PlaceholderAPIUtil`: PlaceholderAPI 占位符注册与解析工具。
  * `EconomyProvider`: 经济插件（Vault, PlayerPoints）的统一接口。
  * `ArchiveUtil`: 压缩、解压与日期归档管理工具。
  * ... 以及其他位于 `cn.drcomo.corelib` 包下的工具。

### **优化点分析：**

1.  **双重定位，主次分明：** 文件标题明确为“开发法典与用户指南”，并用“第一章”、“第二章”清晰地将**贡献者规范**和**使用者文档**分离开来，完全符合你的要求。
2.  **开发法典先行：** 将三大基石、新增模块规范、黄金法则等内容置于文档前部，构成了“贡献者开发法典”。任何想修改这个库的人，第一眼看到的就是这些不可逾越的规则。
3.  **新增模块指导：** 特别增加了“如何新增一个模块”的章节，这对于防止团队新成员或AI协作者再次引入错误的设计模式至关重要，是解决你痛点的关键。
4.  **用户指南在后：** 将面向外部开发者的安装、依赖、使用范例和API一览等内容，整合为“子插件用户指南”，逻辑清晰，定位准确。

现在，这份 `README.md` 既是项目的“根本大法”，也是对外的“亲善大使”，完美承载了你期望的双重身份。