## 项目概述

DrcomoCoreLib 是一个 Minecraft 服务器插件核心库，为子插件提供高复用、零耦合、完全可控的工具集。基于 Paper/Spigot API 1.18.2+，使用 Java 17 和 Maven 构建。

## 架构原则

### 三大基石

1. **零硬编码原则** - 禁止写死配置值（文件名、路径、URL、配置键）。所有可变值通过参数传入
2. **控制反转原则** - 通过构造函数注入所有外部依赖（`Plugin`, `DebugUtil` 等），不创建也不管理依赖生命周期
3. **单一职责原则** - 每个模块只解决一个通用开发痛点，禁止渗透特定插件业务逻辑

### 类设计规范

- 新类必须是可实例化的 `public` 类，拥有 `public` 构造函数
- 严禁设计为静态工具类（`private` 构造函数 + 静态方法）
- 需实例化并持有状态的类使用 `Manager`, `Service`, `Provider` 后缀
- 纯无状态辅助功能使用 `Util` 后缀

## 代码与文档同步规范

**关键规则：代码更新必须同步更新文档，这是强制性要求。**

### 新增功能时

1. 在 `./DrcomoCoreLib/JavaDocs/` 目录下创建对应的 `-JavaDoc.md` 文件
2. 在 `./DrcomoCoreLib/README.md` 的 **API文档查询规则** 部分添加新模块条目
3. 格式参考现有文档，包含：
   - 功能描述
   - 包类路径
   - 查询文档链接

### 修改 public API 时

更新 `./DrcomoCoreLib/JavaDocs/` 中对应的文档，确保：
- 方法签名一致
- 参数说明一致
- 返回值描述一致
- 代码示例有效

### 废弃功能时

**重要：不要删除废弃的代码，按以下步骤处理：**

1. 在代码中添加 `@Deprecated` 注解，保持 `public` 可见性
2. 添加 Javadoc 说明废弃原因和替代方案
3. 从 `./DrcomoCoreLib/README.md` 中**删除**该功能的使用说明条目
4. 在对应的 JavaDoc 文件中标注已废弃状态

示例：
```java
/**
 * @deprecated 自 1.x 版本起废弃，请使用 {@link NewClass#newMethod()} 替代
 */
@Deprecated
public void oldMethod() {
    // 保留实现，不删除
}
```

## 包结构

```
cn.drcomo.corelib
├── archive/        # 文件归档与压缩
├── async/          # 异步任务管理
├── color/          # 颜色代码处理
├── config/         # YAML配置与校验
├── database/       # SQLite/MySQL数据库操作
├── gui/            # GUI创建与交互
│   ├── interfaces/ # 点击回调接口
│   └── session/    # 玩家会话管理
├── hook/           # 第三方插件集成
│   ├── economy/    # Vault/PlayerPoints经济
│   └── placeholder/# PlaceholderAPI集成
├── json/           # JSON序列化
├── math/           # 数学公式计算
├── message/        # 消息服务与本地化
├── nbt/            # NBT数据操作
├── net/            # HTTP网络请求
├── performance/    # 性能监控
├── sound/          # 音效管理
└── util/           # 通用工具（DebugUtil, SkullUtil）
```

## 文档结构

```
DrcomoCoreLib/
├── README.md           # 子插件开发者指南（含API索引）
└── JavaDocs/
    ├── DrcomoCoreLib-JavaDoc.md
    ├── async/
    ├── color/
    ├── config/
    ├── database/
    ├── gui/
    ├── hook/
    ├── json/
    ├── math/
    ├── message/
    ├── nbt/
    ├── net/
    ├── performance/
    ├── sound/
    └── util/
```

## 依赖注入模式

所有工具类遵循构造函数注入：

```java
// 正确示例
public class MyManager {
    private final Plugin plugin;
    private final DebugUtil logger;

    public MyManager(Plugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.logger = logger;
    }
}

// 子插件使用方式
DebugUtil logger = new DebugUtil(this, DebugUtil.LogLevel.INFO);
MyManager manager = new MyManager(this, logger);
```

## 外部依赖

| 依赖 | 用途 | 作用域 |
|------|------|--------|
| Paper API 1.18.2 | 服务器API | provided |
| PlaceholderAPI | 占位符集成 | provided |
| Vault API | 经济系统 | provided |
| PlayerPoints | 点数经济 | provided |
| NBTAPI | NBT操作 | provided |
| HikariCP | 数据库连接池 | provided |
| exp4j | 数学表达式解析 | provided |
| Gson | JSON处理 | provided |
