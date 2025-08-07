# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

DrcomoCoreLib 是一个面向 Minecraft 插件开发者的核心工具库，遵循"军火库"而非"预制营房"的设计哲学。它为子插件提供高复用、零耦合、完全可控的工具集，不创造功能，只赋能创造。

### 技术栈
- Java 17
- Maven 构建系统 
- Bukkit/Spigot/Paper API (1.18.2)
- 支持 PlaceholderAPI、Vault、PlayerPoints 等常见插件

## 构建与开发命令

### 基本构建
```bash
# Maven 标准构建（默认目标：clean package）
mvn clean package

# 或使用项目根目录的快捷方式
mvn
```

### 安装到本地仓库
```bash
# 使用自定义 Python 脚本安装生成的 JAR 到本地 Maven 仓库
python mvn.py
```

### 测试
```bash
# 运行测试（使用 Maven Surefire 插件）
mvn test
```

## 核心架构原则

### 三大基石（不可违背）
1. **零硬编码原则**: 禁止在代码中写死任何配置项，所有可变值必须作为参数传入
2. **控制反转原则**: 不创建不管理依赖生命周期，所有外部依赖通过构造函数注入
3. **单一职责原则**: 每个模块只解决一个通用开发痛点，严禁特定插件业务逻辑渗透

### 设计模式
- **实例化设计**: 所有工具类必须是可实例化的 public 类，严禁静态工具类
- **依赖注入**: 通过构造函数注入所有依赖（Plugin 实例、DebugUtil 等）
- **命名约定**: Manager/Service/Provider 后缀表示有状态类，Util 后缀用于无状态工具

## 代码组织结构

### 核心模块
- `config/`: 配置管理（YamlUtil, ConfigValidator）
- `gui/`: GUI 交互（GuiManager, PaginatedGui）
- `message/`: 消息系统（MessageService - 支持多语言/占位符）
- `database/`: 数据库操作（SQLiteDB）
- `hook/`: 外部插件集成（economy, placeholder）
- `util/`: 通用工具（DebugUtil, SkullUtil）
- `math/`: 数学计算（FormulaCalculator, NumberUtil）
- `sound/`: 声音管理（SoundManager）
- `nbt/`: NBT 数据处理
- `net/`: 网络工具（HttpUtil）

### 使用模式示例
```java
// 正确的使用方式 - 在子插件中实例化
DebugUtil logger = new DebugUtil(this, LogLevel.DEBUG);
YamlUtil yamlUtil = new YamlUtil(this, logger);
SoundManager soundManager = new SoundManager(this, yamlUtil, logger, "sounds", 1.0f, true);
MessageService messageService = new MessageService(this, logger, yamlUtil, papiUtil, "lang/zh_cn", "msg.");
```

## 开发规范

### 代码质量要求
- **中文注释**: 所有沟通内容、注释、日志必须使用中文
- **JavaDoc 同步**: 所有 public API 修改必须同步更新 `DrcomoCoreLib/JavaDocs/` 下的对应文档
- **方法长度**: 单类/方法控制在 30-200 行，文件超 500 行需拆分并补充文档
- **命名规范**: 变量、方法、类名必须清晰表达业务含义

### 安全与性能
- **输入验证**: 外部输入严格校验，使用白名单策略
- **异步处理**: IO、网络等操作必须异步，确保线程安全
- **资源管理**: 文件、数据库连接等必须主动释放

### 扩展性设计
- 模块开发时优先使用接口、事件、回调等低耦合方式
- 发现通用逻辑必须提取至 util 工具类并强制复用
- 按需引入依赖，禁止冗余导入

## 文档维护

每次修改 public API 时，必须：
1. 更新对应的 JavaDoc 文件（在 `DrcomoCoreLib/JavaDocs/` 下）
2. 确保代码行为、参数、返回值与文档完全一致
3. 新增的 public 类和方法需要提供清晰的 JavaDoc

## 重要提醒

- 本库是工具箱而非功能插件，不注册事件监听或命令
- 遵循用户指定的 `.cursor/rules/开发规则.mdc` 中的详细规范
- 任何违背三大基石原则的修改将被驳回
- 配置文件仅在缺失时自动生成，样例配置须与实际逻辑同步