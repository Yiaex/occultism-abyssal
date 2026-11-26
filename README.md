# Occultism: Abyss · 神秘学：深渊

[English](README.en.md)

这是 Occultism 的扩展模组，当前版本为 **1.3.0**，Mod ID 为 `occultism_abyssal`。适用于 Minecraft **1.21.1**、NeoForge **21.1.265 至 21.2 之前**、Java **21** 和 Occultism **1.227.0**。开发环境使用 NeoForge **21.1.265**。

## 主要功能

- 强化维度矿井和高级维度战场各有九个工作槽，支持堆叠、并行、抢夺或武器维护等与设备对应的升级。
- 深渊契约法阵提供六种 Occultism 原生仪式，可制作设备、升级卡和深渊远古矿工。
- 支持红石控制、侧面自动化和可选的 AE2 ME 网络输出。JEI 可查看仪式配方，Jade 可查看设备库存。

## 安装

将 `occultism_abyssal-1.3.0.jar` 放入客户端和服务端各自的 `mods` 目录。同一实例安装 Occultism 及其必需前置。下列依赖版本用于本项目构建和测试。

| 必需模组 | 版本 |
| --- | --- |
| [Occultism](https://modrinth.com/mod/occultism/version/SFDVXWYt) | 1.227.0 |
| [Modonomicon](https://modrinth.com/mod/modonomicon/version/B0Pncoub) | 1.123.1 |
| [Curios API](https://modrinth.com/mod/curios/version/KJsZIQrT) | 9.6.0+1.21.1 |
| [GeckoLib](https://modrinth.com/mod/geckolib/version/nxAPSFAR) | 4.9.4 |
| [SmartBrainLib](https://modrinth.com/mod/smartbrainlib/version/O5EpeqI3) | 1.16.11 |

打开 Occultism 的魔灵全典，在「深渊」章节查看六种原生仪式和可投影的法阵预览。JEI、Jade 15.10.6 和 AE2 均为可选集成。使用 AE2 集成时，请一并安装所用 AE2 版本要求的前置（开发环境使用 GuideME）。

从 1.2.3 或更早版本升级时，请先从 mods 目录移出旧版 JAR。如果使用自定义配置，请将 `config/occultism_abyssal_mining-server.toml` 复制为 `config/occultism_abyssal-server.toml`。自定义数据包中的配方路径及引用需改用新命名空间。

## 布置深渊契约法阵

打开魔灵全典的「深渊 → 深渊契约法阵」查看并投影布局。

在同一高度布置对称的 19×19 法阵。按图绘制符文，中央放诡奇圣杯或天辉圣杯，空位可自由摆放献祭之碗。

```text
·········符·········
·····符··符·符··符·····
····符··符符符符符··符····
···符··符··符··符··符···
··符·符符···符···符符·符··
·符··符符···符···符符··符·
···符··符··符··符··符···
··符····符·符·符····符··
·符符·····符符符·····符符·
符·符符符符符符符杯符符符符符符符·符
·符符·····符符符·····符符·
··符····符·符·符····符··
···符··符··符··符··符···
·符··符符···符···符符··符·
··符·符符···符···符符·符··
···符··符··符··符··符···
····符··符符符符符··符····
·····符··符·符··符·····
·········符·········
```

- **符**用虚空粉笔或彩虹粉笔绘制。两种粉笔可混用。升级后按新投影补画并调整位置。
- 碗和符文放在同一高度，距离中心在 X、Z 两轴各不超过 9 格。深渊远古矿工仪式需要十只碗，十件供品分别放入。
- 把配方材料分别放进外围碗。主手拿「已绑定的魔灵之书：马里德」，直接右击中央圣杯启动。启动用书按原生合成仪式消耗。
- 深渊远古矿工仪式启动后，由玩家在默认半径 8 格内击杀凋零。等待献祭期间供品留在各碗内。献祭后开始 600 秒倒计时，圣杯随仪式进度逐件消耗供品。
- 仪式完成后产物从圣杯掉出，也支持 Occultism 的倒扣献祭之碗接收方式。破坏必要结构会中断并返还材料。升级前已经启动的旧仪式仍由旧核心结算。

## 六种仪式

产物和配方均使用 `occultism_abyssal` 命名空间，表内 ID 是各自的路径。每种仪式产出 1 件。

| 产物 / ID | 默认时长 | 材料 |
| --- | ---: | --- |
| 强化维度矿井 / `abyssal_mineshaft` | 300 秒 | `occultism:dimensional_mineshaft` ×1，`occultism:dimensional_matrix` ×1，`occultism:spirit_attuned_gem` ×1，`occultism:iesnium_ingot` ×2，`minecraft:shulker_box` ×1，`minecraft:obsidian` ×1 |
| 堆叠升级卡 / `stack_upgrade` | 120 秒 | `occultism:dimensional_matrix` ×1，`occultism:iesnium_ingot` ×1，`minecraft:shulker_shell` ×2，`minecraft:ender_chest` ×1 |
| 并行升级卡 / `parallel_upgrade` | 180 秒 | `occultism:spirit_attuned_gem` ×1，`occultism:dimensional_matrix` ×1，`occultism:otherworld_essence` ×1，`occultism:iesnium_ingot` ×1，`minecraft:echo_shard` ×1 |
| 深渊远古矿工 / `miner_ancient_abyssal` | 600 秒 | `occultism:miner_ancient_eldritch` ×8、`occultism:dimensional_matrix` ×1、`minecraft:netherite_ingot` ×1，另献祭 `minecraft:wither` ×1 |
| 高级维度战场 / `advanced_battlefield` | 360 秒 | `occultism:dimensional_battlefield` ×1，`occultism:spirit_attuned_gem` ×1，`occultism:soul_gem` ×1，`occultism:iesnium_ingot` ×1，`minecraft:diamond_sword` ×1，`minecraft:crying_obsidian` ×1 |
| 抢夺升级卡 / `looting_upgrade` | 240 秒 | `occultism:spirit_attuned_gem` ×1，`occultism:soul_gem` ×1，`occultism:otherworld_essence` ×1，`minecraft:diamond` ×1，`minecraft:ender_pearl` ×1 |

制作矿工时，将八个诡奇远古矿工、维度矩阵和下界合金锭分别放进十只碗里。六个仪式都另需一本已绑定的魔灵之书：马里德启动。重复材料分开放入不同容器。数据包可覆盖 `data/occultism_abyssal/recipe/<id>.json`，配方类型为 `occultism:ritual`，执行类型为 `occultism_abyssal:covenant_craft`。`duration` 使用秒。保留配方默认秒数时，服务端配置可覆盖实际 tick 数。

## 操作设备

- 强化矿井支持九个各自计时的矿工。高级战场支持九个生物来源并共用一把武器和一个燃料槽。填入的灵魂宝石按原版拒绝标签验证。刷怪蛋作为生物模板，击杀仍消耗燃料与武器耐久。有效攻击伤害和主手攻击速度越高，命中间隔越短，最短为 1 tick。以未附魔钻石剑为基准，伤害计算也计入针对目标生效的攻击附魔。
- 武器维护升级卡由抢夺升级卡、附魔之瓶和神秘学的灵魂宝石无序合成。装入高级维度战场的维护槽后，击杀经验优先修补带有经验修补的武器。每点经验最多修复 2 点耐久，每 10 点剩余经验仍会装成 1 瓶。
- 从顶部插入矿工、生物来源或战场合格的武器和燃料。底部仅提取输出。横向接口默认为输入与输出，可配置关闭。升级槽由玩家安装。
- 堆叠卡按物品原生容量乘以 1、16、256、4096。原生上限为 64 时，四档容量为 64、1024、16384、262144。输出槽只允许提取。右击取半组，Shift 转移一组，数字键提取至空栏或合并到兼容的快捷栏。输出格显示真实库存数量，悬停同时显示该物品的库存和单格上限。拆卡后超容物品保留且可继续提取。
- 并行卡按安装数量增加 0、1、3、7 个独立任务。深渊远古矿工基础并行为 9。高级战场的抢夺卡在战利品已完成原版附魔结算后应用 ×1 至 ×4。
- 接入红石信号可暂停机器。服务端可关闭矿井的红石控制。卸载的区块暂停，加载后继续。禁用区块卸载期间的工作补算。

## 接入 ME 网络

装有 AE2 时，使用一张并行升级卡、一个 ME 输入总线和一个工程处理器合成 ME 输出升级。装入任一设备的 ME 槽，再用线缆连接。设备使用一个频道；只有在网络具备供电、频道和存储空间时才输出。默认每 10 个游戏 tick 导出一批（20 TPS 时约 0.5 秒），可在服务端配置中修改 machines.meExportIntervalTicks。网络拒收的数量保留在本机仓库。

## 配置与构建

NeoForge 生成 `config/occultism_abyssal-server.toml`。安装包中的 [默认配置](config/occultism_abyssal-server.toml) 可供整合包预设。槽位数量、四档升级倍率、矿工永久不毁和四倍速度属于固定机制。服务端同步设备进度、升级数量和真实库存，仪式时长以服务端为准。

构建需要 **JDK 21**。在仓库根目录运行自带的 **Gradle 9.2.1 Wrapper**，首次构建需要联网下载 Gradle 和依赖。

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat -PwithJei runClient
```

Linux 或 macOS 使用 `./gradlew` 执行相同任务。启动开发客户端时可用 `-PwithAe2` 或 `-PwithJade` 加载对应的可选模组。

Gradle 构建输出在 `build/libs`。使用 `occultism_abyssal-1.3.0.jar` 安装模组，`-sources.jar` 供浏览源码。主源码和发布资源位于 `src/main`，GameTest 和测试用 Loot Table 位于 `src/gameTest`。

[源码依据、测试矩阵和兼容边界](DEVELOPMENT.md)
