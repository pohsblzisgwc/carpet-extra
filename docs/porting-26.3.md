# 升级到 Minecraft 26.3（Fabric）

## 升级概览

以 `pohsblzisgwc/carpet-extra` 默认分支最新提交 `c32b347` 为基线，从 26.2 迁移至 26.3。保留原有规则及其默认值。

| 配置 | 原版本 | 当前版本 |
| --- | --- | --- |
| Minecraft / Carpet Extra | 26.2 | 26.3 |
| Fabric Loader | 0.19.3 | 0.19.5（稳定版） |
| Java 编译目标 | 25 | 25 |
| Gradle | 9.5.1 | 9.6.1 |
| Loom | 1.17-SNAPSHOT | 1.17.21 |
| Modern Yarn | 26.2+build.1 | 26.3+build.2 |
| Carpet | JitPack 固定旧提交 | 官方 26.3 发布版（26.3+v260915） |
| Fabric API（仅 GameTest） | 0.152.2+26.2 | 0.164.0+26.3 |

原始构建基线因 `masa.dy.fi` 拒绝连接、无法解析固定的旧 Carpet 依赖而失败，未获得旧版本编译通过的结果。当前改为通过 Modrinth Maven 获取官方 Carpet 发布版，移除旧 JitPack 依赖。

## 构建改动

- 模组元数据限定 Minecraft / Carpet `26.3.x`、Loader `>=0.19.5`、Java `>=25`；Mixin 兼容级别为 `JAVA_25`。
- 保留 Modern Yarn 开发命名空间，发布 JAR 和源码 JAR 均重映射为 26.3 的官方命名空间。
- 编译错误上限设为 1000。
- `./gradlew build` 包含主代码和 GameTest 代码编译、打包、Mixin 静态检查，不启动 Minecraft。
- 服务端 GameTest 仍可由用户通过 `./gradlew runGameTest` 或 `Manual GameTests` 工作流手动运行。
- 本模组运行时需要 Carpet；Fabric API 依赖只用于测试源码集。
- 本项目没有 Access Widener / Access Transformer，Carpet 依赖自带的 Access Widener 保持由 Loom 处理。

## API 迁移

- 26.3 移除 `AxeItem`、`HoeItem`，发射器去皮、刮蜡、去氧化和锄地改为读取 `BLOCK_TRANSFORMER` 组件及其状态提供器。
- 工具识别使用原版 AXE / HOE 转换器键；保留原版转换结果处不弹出工具、成功操作后扣除耐久、检查前方和下方锄地位置的行为。
- 不再静态缓存旧去皮、锄地表；转换规则从当前物品组件读取，原版新增木材随数据更新纳入处理。
- 骨粉相关 `Fertilizable` 方法增加 `FertilizationCause` 参数。
- 结构刷怪的数量改为 `ConstantIntProvider.create(1)`。
- GameTest 数据增加主世界维度；去皮用例通过方块注册表查找对应的 `stripped_` 方块，替代已移除的访问器。

## Mixin 调整

- 删除锄地表、去皮表的旧 Accessor。
- 无玩家操作的保护从 `AxeItem.shouldCancelStripAttempt` 移至 `BlockTransformerComponent.isPreemptedByShield`。
- 发射器行为注入明确指定包含 `World` 参数的重载。
- 木按钮 `powerOn` 的 Redirect 增加玩家参数。
- 花盆注入改为无 flags 参数的 `World.setBlockState` 调用。
- 活塞重定向红石规则改为注入 `wireConnectsTo(BlockState, BlockView, BlockPos, Direction)`。
- 村民次要工作地点传感器注入明确指定 `VillagerEntity` 重载。

## 已完成验证

- `./gradlew clean build` 成功，产物为 `build/libs/carpet-extra-26.3.jar` 和源码 JAR。
- `./gradlew checkMixins` 成功：61 个 Mixin、58 个注入器（包含测试 Mixin）。检查目标类、Shadow、Accessor、Invoker、目标方法描述符、Callback / Redirect 参数、ModifyArg 类型、调用/字段注入点、常量与 ordinal，以及 `@Local` 的类型和作用域。
- 对正式 JAR 另用相同检查器和 26.3 官方命名空间的原版类文件检查：60 个 Mixin、57 个注入器通过。静态检查不加载或初始化游戏类。
- 首次执行标准 `build` 时，Loom 自动挂接的 `runGameTest` 意外运行；当次 69 项必需测试全部通过。随后已解除该依赖，并通过任务 dry-run 及重新 clean build 确认普通构建不再启动 GameTest。

## 用户验证清单

使用 Java 25、Minecraft 26.3、Fabric Loader 0.19.5 或更新兼容版、Carpet 26.3 和本次构建的模组 JAR。

| 内容 | 操作与通过标准 |
| --- | --- |
| 专用服务器 | 使用正式 JAR 启动测试服务器，能进入世界，日志无 Mixin / 链接错误 |
| 客户端 | 启动并进入单人世界，再连接测试服务器；无启动崩溃或规则同步异常。本模组没有单独的 client Mixin 列表，共用 Mixin 仍需覆盖客户端环境 |
| 发射器 | 启用 `dispensersStripBlocks`、`dispensersTillSoil`：检查多种原木（含新增木材）、铜块去氧化/刮蜡、普通土/砂土/缠根泥土、工具耐久和破损；对已处理方块再次触发时工具不弹出 |
| 骨粉 | 启用 `betterBonemeal`，检查仙人掌、甘蔗、睡莲的玩家和发射器操作 |
| 红石 | 启用 `variableWoodDelays`、`pistonRedirectsRedstone`，检查玩家/箭触发木按钮及活塞旁红石连接 |
| 村民与花盆 | 启用 `clericsFarmWarts`、`flowerPotChunkLoading`，检查牧师种植下界疣，以及凋零玫瑰入盆和移除后的区块加载状态 |
| 其他既有功能 | 抽查 `dispenserPlacesBlocks`、自动合成、物品展示框比较器读数、落沙/铁砧相关规则及 Scarpet 发射器事件 |
| 日志 | 检查以下关键字，确认没有注入失败或缺失类/方法/字段 |

```sh
rg -n 'Mixin apply failed|InvalidMixinException|InvalidInjectionException|InjectionError|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|ERROR|FATAL' logs/latest.log
```

用户需要手动运行自动化服务端测试时：

```sh
./gradlew runGameTest
```

报告输出到 `build/junit.xml`。请将启动失败时的完整 `latest.log`、崩溃报告及触发规则一起反馈。

## 未验证项

正式 JAR 的专用服务器启动、图形客户端启动、完整规则集、实际存档与其他模组组合尚待用户验证。开发环境 GameTest 和静态检查不能替代这些验证。

## API 查证来源

- [Fabric 26.3 迁移说明](https://www.fabricmc.net/2026/09/15/263.html)
- [Minecraft 官方版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Carpet 26.3 官方发布](https://github.com/gnembon/fabric-carpet/releases/tag/v26.3)
- [Fabric Loader 稳定版本元数据](https://meta.fabricmc.net/v2/versions/loader)
- [Modern Yarn Maven 元数据](https://repo.codemc.io/repository/relativitymc/org/relativitymc/modern-yarn/maven-metadata.xml)

具体成员及实现通过 26.3 原版 JAR、对应 Modern Yarn 映射、Loom 生成的源码和 Carpet 26.3 发布 JAR 核对。
