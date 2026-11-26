# 开发与验证

## 基于 Occultism 1.227.0 的行为

- `DimensionalMineshaftBlockEntity`、`DimensionalMineshaftContainer`、`MinerRecipe` 和 `WeightedRecipeResult` 确定了原版配方的匹配、滚动次数、抽取权重和输出数量。使用 `OccultismRecipes.MINER_TYPE` 和原版配方结果，重新加载数据包时使查询缓存失效。[上游矿井源码](https://github.com/klikli-dev/occultism/blob/1ff75fa91769ad7fa3b66ec0400dd008414b2bfb/src/main/java/com/klikli_dev/occultism/common/blockentity/DimensionalMineshaftBlockEntity.java)
- `MinerSpiritItem`、`OccultismStartupConfig` 和原版配置写入的原生数据组件提供矿工工作时间和每次产量。诡奇远古矿工默认配置 15 个工作 tick、每次 1 抽。无附魔时原版还需 1 个准备 tick，实际循环 16 tick。深渊远古矿工每个物理 tick 推进 4 个原版虚拟 tick，保留效率的随机推进、时运额外抽取和精准采集的产量逻辑。原版矿井的兼容 Mixin 只接管深渊远古矿工的工作，且使用原版库存及提取器接口。[上游矿工配置](https://github.com/klikli-dev/occultism/blob/1ff75fa91769ad7fa3b66ec0400dd008414b2bfb/src/main/java/com/klikli_dev/occultism/config/OccultismStartupConfig.java)
- `DimensionalBattlefieldBlockEntity`、`DimensionalBattlefieldContainer`、`SoulGemItem` 确定了实体数据、武器属性、灵魂燃料组件、Boss 单抽、附身实体映射、失败/消耗概率和战利品上下文。用服务端临时实体、FakePlayer、Occultism 战场专用 Loot Table、Minecraft 实体 Loot Table 和 NeoForge `LivingDropsEvent` 执行。相同生物在不同来源槽各自计时。[上游战场源码](https://github.com/klikli-dev/occultism/blob/1ff75fa91769ad7fa3b66ec0400dd008414b2bfb/src/main/java/com/klikli_dev/occultism/common/blockentity/DimensionalBattlefieldBlockEntity.java)
- `ChalkItem` 默认放置的是对应原版颜色的 Glyph。`GoldenSacrificialBowlBlockEntity` 内有对两种圣杯的专用完成路径，也有玩家击杀献祭逻辑。附属使用独立注册的核心、配方和服务端状态，从实体死亡接收玩家击杀，专属法阵的符文必须带有授权粉笔绘制标记。[上游圣杯源码](https://github.com/klikli-dev/occultism/blob/1ff75fa91769ad7fa3b66ec0400dd008414b2bfb/src/main/java/com/klikli_dev/occultism/common/blockentity/GoldenSacrificialBowlBlockEntity.java)

## 1.2.3

重绘两台设备的顶面、侧面与五件物品图标，采用 32×32 游戏贴图。统一中英文物品提示，清理冗余注释。

## 1.2.2

ME 按批导出，服务端配置 machines.meExportIntervalTicks 默认为 10，范围 1–1200 tick。容量按原生上限乘以 1、16、256、4096，设备界面共用同一容量定义。实际库存拆成两个 16 位字段传输，避免原版容器同步截断超过 65535 的数量。

## 1.2.1

凋零仪式启动后供品留在外围碗，献祭后按原生间隔消耗。移除 /abyssalcovenant、对应的客户端屏幕与粉笔提示。魔灵全典的投影继续提供布局。

ME 改为每 tick 有界轮询输出槽。矿工按原有工作周期计时，红石暂停有独立的进度条提示。新增立即抽空库存与保留库存的对照测试。

## 1.2.0

使用独立的对称 19×19 Modonomicon 结构。献祭之碗可放在同层、范围内的空格，十件供品用十只碗。通过 Occultism 原生圣杯完成六种仪式。GameTest 验证新版法阵没有匹配原版配方的结构。

两种设备采用紧凑布局。InvTweaks 的玩家背包整理按钮移至背包标题行，大容量产物区的整理按钮移除。武器槽检查主手攻击力，矿工槽在客户端与服务端都使用矿工配方校验。

AE2 19.2.19 与 Jade 15.10.6 环境中的 12 项 GameTest 全部通过。用真实供电的 AE2 驱动器接收了矿井的 513 个铁锭和战场的 257 个金锭，并核对本机扣减。整合包内界面与整理按钮的视觉效果尚未实测。

## 1.1.0

六种配方由 Occultism 原生圣杯执行，魔灵全典新增深渊章节与两套投影。测试通过十一项，包含原生圣杯启动与产出、凋零献祭与返还、章节六个配方条目及 Jade 数字。模拟客户端显示转换验证了 4096。整合包内 HUD 和投影视觉尚未实测。下面保留早期版本的历史记录。

## 1.0.2 补丁

输出格只绘制服务端同步的实际数量，客户端保留合法物品栈供容器交互使用。为已有的仓库测试加入实际库存 4096 与物品栈 64 分离的断言。两种机器增加仪式结构按钮，客户端命令 /abyssalcovenant 也能独立打开搭建图。网格取自服务端法阵验证定义。

## 1.0.1 补丁

将最低 NeoForge 版本调整到 21.1.265。本次用 Minecraft 1.21.1、Occultism 1.227.0 和 NeoForge 21.1.265 重新编译，10 项服务端 GameTest 全部通过。Impostor Syndrome - Reimagined 已通过 PCL 在原有 NeoForge 21.1.265 上进入实际客户端主菜单。原版 1.0.0 的专用服存档和 JEI 验证记录保留在下文。

## 已执行的验证

实测环境为 Windows 11、Java 21.0.11、Minecraft 1.21.1、NeoForge 21.1.267 和 Occultism 1.227.0。Gradle 构建生成主 Jar 和源码 Jar。专用 GameTest 服务端进入世界、运行测试并保存世界后正常退出。游戏测试使用真实注册表及配方管理器，测试中按需直接推进方块实体 tick 并执行 NBT 往返。

| GameTest | 验证范围 |
| --- | --- |
| `warehouseAndMenu` | 4096 容量、降级后超容保护、不堆叠物品组件、NBT 往返、两位测试玩家分别拾取和数字键提取、Shift、Capability 提取上限 |
| `minerSpeedSafetyAndStock` | 混合矿工、深渊矿工在原版矿井中产出、普通矿工扣耐久、深渊矿工不毁、8/16 并行设置、加载后不毁 |
| `miningBackpressure` | 输出满后保存缓存，恢复空间后入库且没有重复扣耐久 |
| `battlefieldInputsFuelAndLoot` | 九种刷怪蛋、三种填充宝石、玩家实体拒绝、72 理论任务、燃料不足、消耗后出货与 NBT 保持 |
| `covenantRealWitherAndRecovery` | 10 件材料暂存、诡奇圣杯、实际凋零死亡事件、范围外无效、12000 tick 起计、献祭状态载入、破坏结构后返还 |
| `covenantChalkChaliceAndCompletion` | 拒绝白粉笔与普通中心碗、虚空和彩虹混用、天辉圣杯计时、2400 tick 正常完结、领取及六条配方加载 |
| `allFiveNonSacrificeRecipes` | 五种无献祭仪式按各自默认时长完成，检查提前一 tick 尚未结算 |
| `oneWitherChoosesNearestPentacle` | 两个候选法阵接收真实凋零死亡近邻判定，同一个事件重复发送也只消费一次 |
| `realHoppers` | 真实漏斗输入矿工、拒绝泥土等非法输入、产物输出与数量守恒 |
| `lootDataPackAndWeaponLooting` | 真正加载数据包 Loot Table、Looting III 只应用一次、×1–4、黑名单、经验瓶排除、不可堆叠装备场景 |

## 专用服与客户端实测

- 以普通专用服开发启动器加载最终发布 Jar。实际停止、保存并重新启动后，强化矿井的两批输出分别保留 36 与 45 件，未完成进度保留 8 个虚拟 tick。
- 对运行中的普通专用服执行数据包重载后，已运行矿井从石灰色羊毛配方切换到品红色羊毛配方，旧库存仍在。受控的一源矿井在 16 tick 内产出 36 件、20 tick 内产出 45 件。发布 Jar 在默认预算下测试九个深渊矿工、三个并行卡和三个堆叠卡，在 43 tick 内累计完成 1,440 次独立抽取，输出数量与十轮工作相符。
- 在剔除 Occultism 的独立启动检查中，NeoForge 在载入前明确报告缺少 Occultism 1.227.0。
- Windows 客户端携带 JEI 19.25.0.322 启动到主菜单，资源加载日志未发现本模组的缺图或缺模型。已执行服务端菜单交互测试。游戏内 GUI 视觉确认和 JEI 词条打开仍未实测。

## 兼容边界

- 独立战斗模拟会派发 `LivingDropsEvent` 并走 Loot Table 和 Global Loot Modifiers。依赖实体真实生成、AI、`LivingDeathEvent`、真实玩家攻击生命周期和第三方额外生成物的效果没有通用兼容保证。Boss 和非法容器按目标版本语义检查。燃料按 `1 + floor(生命 / 单件燃料值)` 实际到位才开工。
- 出于单操作上限考虑，极端模组属性下，模拟生命取 1–1024、击打次数取 1–65536、击打间隔取 1–12000 tick，单次模拟额外掉落受配置上限约束。工作预算按每台方块计算，无法代表整台服务器的全局限流或压力测试结果。
- 正常保存和加载的持续状态通过 NBT 往返测试。仪式启动可能横跨多个区块存档和玩家背包文件，Minecraft 的磁盘写入缺少跨文件原子事务，进程崩溃或断电时无法保证所有文件在同一事务点恢复。
- 尚未完成章节 18 的全组合验证。缺少第三方管道与 EMI 专属集成实测、真实多客户端联机操作、仪式进行中跨进程重启与卸载组合测试、凋零实际生成爆炸场景。仓库并发测试使用两位服务端模拟玩家。
- JEI 使用独立仪式类别，Occultism 魔灵辞典提供结构和操作说明。未开发 EMI 原生插件。
