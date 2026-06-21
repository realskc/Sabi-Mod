# Sabi Mod 技术设计说明

本文档面向后续接手项目的 Agent，记录当前实现、设计约束、关键文件和容易踩坑的地方。本文描述的是当前仓库状态，不是最终玩法蓝图。

## 项目概览

Sabi 是一个基于 NeoForge 的 Minecraft Java 版 Mod。

- Mod ID：`sabi`
- Java 包名：`top.sabi`
- Minecraft：`26.1.2`
- NeoForge：`26.1.2.41-beta`
- Java/JDK：`25`
- 当前 Mod 版本：`1.0.0`
- 主题：撒币经济系统，以及后续计划加入的德州扑克玩法

当前已经实现：

- 四档实体货币：小撒币、中撒币、大撒币、巨撒币
- 个人撒币账户：绑定到玩家，不依赖钱包物品
- 玩家背包界面里的账户入口
- 撒币机方块：可典当配置允许的物品，把收益存入账户，之后用账户余额赎回或购买
- 撒币机完整物品价格配置：按 group 维护，可人工调价
- 交互式价格编辑脚本：`tools/edit_sabi_prices.py`

尚未实现：

- 德州扑克玩法
- 撒币机价格按物品组件、耐久、附魔等细分
- 撒币机库存浏览中按 NBT/组件区分同一物品的不同栈

## 构建与运行

构建命令：

```powershell
.\gradlew.bat build --no-daemon
```

`build.gradle` 中配置了 Java 25 toolchain，并在 `build` 任务结束后自动执行 `copyModToPcl`。复制目标来自 `gradle.properties`：

```properties
pcl_mods_dir=D:/Portable Softwares/PCL/.minecraft/versions/26.1.2-NeoForge_26.1.2.73/mods
```

如果其他机器没有这个目录，需要修改 `gradle.properties` 或临时调整该属性。

## 顶层注册

核心入口是 `src/main/java/top/sabi/Sabi.java`。

注册内容：

- `DeferredRegister.Blocks BLOCKS`
- `DeferredRegister.Items ITEMS`
- `DeferredRegister<MenuType<?>> MENUS`
- `DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS`
- 玩家附件由 `SabiAccount.register(modEventBus)` 内部注册

创造模式物品栏：

- 所有撒币
- 高级红石核心
- 撒币机

都加入 `CreativeModeTabs.INGREDIENTS`。

## 实体货币

相关文件：

- `CurrencyDenomination.java`
- `CurrencyItem.java`
- `SabiCurrencyExchange.java`
- `CurrencyStackUpgradeRecipe.java`
- `src/main/resources/data/sabi/recipe/*.json`

四档货币及价值：

| 物品 ID | 中文名 | 账户价值 |
| --- | --- | ---: |
| `sabi:small_sabi` | 小撒币 | 1 |
| `sabi:medium_sabi` | 中撒币 | 64 |
| `sabi:big_sabi` | 大撒币 | 4096 |
| `sabi:giant_sabi` | 巨撒币 | 262144 |

设计规则：

- 相邻汇率是 `64`
- 堆叠上限是 `64`
- 实体货币只负责展示、交易和掉落
- 大额长期余额放在个人账户里
- 撒币本身不可被撒币机典当

`CurrencyItem#getMaxStackSize` 返回 `Sabi.CURRENCY_STACK_SIZE`，当前是 `64`。

### 货币互换

降级兑换是普通无序配方：

- 1 中撒币 -> 64 小撒币
- 1 大撒币 -> 64 中撒币
- 1 巨撒币 -> 64 大撒币

升级兑换使用自定义 recipe serializer：

- `sabi:small_sabi_to_medium_sabi`
- `sabi:medium_sabi_to_big_sabi`
- `sabi:big_sabi_to_giant_sabi`

对应 JSON 只有 type，例如：

```json
{ "type": "sabi:small_sabi_to_medium_sabi" }
```

`CurrencyStackUpgradeRecipe#matches` 要求合成格中只有一个非空 stack，并且这个 stack 是低一级撒币，数量至少 `64`。`assemble` 输出 1 个高一级撒币。

注意：原版合成事件默认只消耗 1 个输入物品，所以 `SabiCurrencyExchange` 监听 `PlayerEvent.ItemCraftedEvent`，在升级合成发生后额外 shrink `63` 个低级撒币。这个实现依赖合成事件，后续如果换更复杂的合成逻辑要重新审计。

## 个人撒币账户

相关文件：

- `SabiAccount.java`
- `SabiClientState.java`
- `SabiClient.java`
- `client/SabiAccountScreen.java`
- `SabiNetwork.java`

账户不是物品，而是玩家附件：

```java
AttachmentType.serializable(AccountData::new)
    .copyOnDeath()
    .sync(AccountData.STREAM_CODEC)
    .build()
```

账户数据：

- 字段：`long balance`
- 下限：0
- 加法：`saturatingAdd`，溢出时饱和到 `Long.MAX_VALUE`
- 死亡复制：开启 `copyOnDeath`
- 同步：登录、切维度、重生时服务端向客户端发 `BalanceSyncPayload`

账户操作：

- `depositAllCurrency(Player)`：扫描整个玩家 inventory container，把所有实体撒币折算成小撒币单位存入账户，然后移除实体货币
- `withdraw(Player, amount)`：账户余额足够时扣余额，并按巨、大、中、小顺序发放实体货币；背包放不下就掉落

### 背包界面入口

`SabiClient` 监听 `InventoryScreen` 的渲染和鼠标点击。

账户框位置：

- `accountX = screen.getLeftPos() + 8`
- `accountY = screen.getTopPos() - 24`
- 尺寸：`60 x 18`

这个框只在按 E 打开的玩家背包界面中显示，避免常规 HUD 没有鼠标时无法点击的问题。点击后打开 `SabiAccountScreen`。

账户界面提供：

- 当前余额
- 提现金额输入框，过滤 `[0-9]{1,18}`
- 提现按钮
- 全部存入按钮

## 撒币机

相关文件：

- `SabiPawnMachineBlock.java`
- `SabiPawnMachineStorage.java`
- `SabiPawnMachineMenu.java`
- `client/SabiPawnMachineScreen.java`
- `SabiPawnMachineConfig.java`
- `SabiNetwork.java`
- `src/main/resources/data/sabi/sabi_machine/items.json`

注册 ID：

- 方块：`sabi:sabi_machine`
- 物品：`sabi:sabi_machine`
- Menu type：`sabi:sabi_machine`

方块属性：

- 硬度：`50.0F`
- 爆炸抗性：`1200.0F`
- 需要正确工具掉落
- 音效：石头
- 挖掘标签：`mineable/pickaxe` 和 `needs_diamond_tool`

合成配方：

```text
O E O
E C E
O X O
```

- `O`：黑曜石
- `E`：绿宝石块
- `C`：高级红石核心
- `X`：末影箱

右键撒币机时，服务端打开 `SabiPawnMachineMenu`，随后通过 `PawnMachinePayload` 把当前可典当物品列表、库存数量、典当价、赎回价同步给客户端。

### 高级红石核心

ID：`sabi:advanced_redstone_core`

配方：

```text
R Q R
C E C
R N R
```

- `R`：红石块
- `Q`：石英
- `C`：红石比较器
- `E`：回响碎片
- `N`：下界合金锭

它是撒币机的核心合成材料。

## 撒币机库存模型

`SabiPawnMachineStorage` 是全服务器共享库存，继承 `SavedData`，通过 `server.getDataStorage()` 保存。任意一台撒币机、任意玩家看到的都是同一份库存。

内部使用：

```java
Map<Item, Deque<ItemStack>> storedItemsByItem
```

也就是每一种 `Item` 对应一个栈结构。

典当：

1. 玩家把物品放入撒币机 UI 的典当槽
2. 点击确认
3. 服务端检查物品是否在配置允许列表中
4. `SabiPawnMachineStorage.get(server).store(pawned)`
5. 把 `pawn_price * count` 存入玩家个人撒币账户，并同步余额
6. 刷新所有当前打开撒币机 UI 的玩家

`store(ItemStack stack)` 的行为：

- 空 stack 直接忽略
- 以 `stack.getItem()` 找到对应 deque
- 只尝试和 deque 栈顶合并
- 合并条件：`ItemStack.isSameItemSameComponents(top, stack)`
- 如果能合并，`top.grow(stack.getCount())`
- 如果不能合并，`push(stack.copy())`

重要细节：

- 栈顶合并后 `ItemStack#getCount()` 可以超过该物品正常堆叠上限
- 这样可以压缩普通同类物品，同时保留耐久、附魔、组件、NBT 等差异
- 不会扫描 deque 内部做全局合并，避免重排真实 ItemStack 历史

赎回：

1. 客户端发送 `PawnMachineRedeemPayload(pos, itemId, amount)`
2. 服务端确认玩家距离撒币机不超过 8 格左右，实际判断是 `distanceToSqr <= 64`
3. 检查物品仍在配置允许列表
4. 检查 `amount <= new ItemStack(item).getMaxStackSize()`
5. 检查全局库存足够
6. 检查玩家个人撒币账户余额足够
7. 扣除账户余额
8. 从对应 deque 栈顶弹出 ItemStack，并发到玩家背包；背包满则掉落
9. 刷新所有当前打开撒币机 UI 的玩家

赎回扣玩家个人撒币账户余额，不消耗实体小撒币。

购买：

- 如果某物品当前全局库存为 `0`，详情页第二行从赎回切换为购买
- 购买价是 `pawn_price * 4 * amount`
- 购买扣玩家个人撒币账户余额，不消耗实体小撒币
- 服务端处理 `PawnMachineBuyPayload` 时会重新确认该物品库存仍为 `0`，防止客户端 UI 过期时误走购买路径
- 购买成功后凭空生成对应 ItemStack 并发给玩家；背包满则掉落

打破撒币机：

- 不会掉出全局库存，因为库存不再属于某一台机器

持久化：

- 全局库存保存为 SavedData：`sabi:sabi_machine_storage`
- 保存字段：`stored_items`
- 每个子项通过 `ItemStack.CODEC` 保存
- 因为新版 `ItemStack.CODEC` 的 count 上限是 `99`，保存时会把内部大栈拆成不超过 `99` 的小栈；读取时再按 `store(stack)` 规则压缩

## 撒币机 UI

客户端类：`client/SabiPawnMachineScreen.java`

界面尺寸：

- `IMAGE_WIDTH = 244`
- `IMAGE_HEIGHT = 238`

页面模式：

- `GRID`：主列表页
- `DETAIL`：选中物品后的交易页
- `PAWN_CONFIRM`：主页面快速典当确认页

主列表页：

- 搜索框：`leftPos + 30, topPos + 28, 118 x 20`
- 搜索图标：代码绘制的像素图标
- 快速典当槽：menu slot 0，坐标 `154, 28`
- 快速典当按钮：`leftPos + 176, topPos + 28`
- 物品表格：9 列 x 4 行，每格 `18 x 18`
- 网格位置：`gridX = leftPos + (IMAGE_WIDTH - GRID_WIDTH) / 2`，`gridY = topPos + 54`
- 支持鼠标滚轮滚动

列表排序：

1. 撒币机中已有数量降序
2. 配置文件原始顺序升序

过滤：

- 搜索中文显示名
- 搜索完整物品 ID

单元格显示：

- 有库存：浅灰背景，右下角显示数量，超过 99 显示 99
- 无库存：深色背景，不显示数量
- hover 和 selected 有不同描边

输入处理：

- 覆写 `keyPressed`
- 如果搜索框或数量框聚焦，按 E 不关闭界面
- 如果编辑框未聚焦，按 E 按原版逻辑关闭

详情页：

- 展示物品图标、名称、全局库存数量、典当收益、赎回花费
- 典当行有一个典当槽，玩家必须放入当前选中的同一种物品
- 赎回行有数量输入框
- 典当确认或赎回确认后直接完成并关闭界面
- 返回按钮回到主列表页

快速典当确认页：

- 主列表页可直接把任意允许物品放入快速典当槽
- 点击“典当”进入确认页
- 显示将存入账户的小撒币数
- 确认后执行典当并回主列表
- 取消回主列表

## 撒币机价格配置

白名单与显示顺序配置文件：

```text
src/main/resources/data/sabi/sabi_machine/items.json
```

这是 datapack/resource manager 读取的 JSON，资源 ID 是：

```text
sabi:sabi_machine/items.json
```

当前它仍然是撒币机的完整白名单和 UI 原始顺序来源，不包含价格字段。实际交易价格只来自基础价/派生价文件；白名单中无法求出价格的物品不会进入撒币机可典当列表。

基础价配置文件：

```text
src/main/resources/data/sabi/sabi_machine/base_prices.json
```

资源 ID：

```text
sabi:sabi_machine/base_prices.json
```

派生价公式配置文件：

```text
src/main/resources/data/sabi/sabi_machine/derived_prices.json
```

资源 ID：

```text
sabi:sabi_machine/derived_prices.json
```

辅助生成器：

```text
tools/generate_sabi_price_rules.py
```

当前 `base_prices.json` / `derived_prices.json` 是由生成器从 `items.json` 和本地 vanilla / mod recipe 数据生成的。生成器同时写出：

```text
src/main/resources/data/sabi/sabi_machine/price_rules_report.md
```

用于记录统计、强制基础价规则、未能安全求解而保留为基础价的物品，以及被跳过的 recipe type。

`items.json` 当前格式：

```json
{
  "comment": "...",
  "groups": [
    {
      "id": "wooden_planks",
      "items": [
        "minecraft:oak_planks",
        "minecraft:spruce_planks"
      ]
    }
  ]
}
```

语义：

- `items.json` 的 `groups` 顺序就是 UI 的原始顺序
- `items.json` 仍决定哪些物品允许被撒币机典当
- `base_prices.json` 里的 `groups` 是需要人工定价的基础物品；修改价格时优先改这里；当前共有 `183` 个基础价 group
- `base_prices.json` 的 `symbols` 是虚拟基础价，目前有 `sabi:generic_dye`
- `derived_prices.json` 里的 `groups` 是可计算物品；每个 group 写 `items` 和一个或多个 `recipes`
- 白名单中的物品如果无法从 `base_prices.json` 或 `derived_prices.json` 求出价格，会被跳过
- 派生价求值时，如果一个物品有多条可解公式，取当前基础价下最便宜的一条
- 公式项支持 `item` 单项和 `any_of` 候选项；`any_of` 会取候选项中的最便宜价格
- 工作台、切石机不额外收费
- 烧炼、烟熏、营火、疾速熔炉配方额外加入 `0.125 * minecraft:coal`
- `fortune_iii_loot` 公式表示按时运 III 挖掘的期望掉落物计价；只给没有普通合成公式、且方块本身主要意义就是掉落小物的白名单方块生成，例如矿石、蘑菇方块和紫水晶簇
- 染色类派生公式不使用具体染料价格，而使用 `sabi:generic_dye`
- 具体染料本身按原版合成或烧炼配方推导，例如绿色染料会包含仙人掌和 `0.125 * minecraft:coal`
- 手写等价公式用于表达非普通合成配方但价格应等量代换的情况，例如水桶/牛奶桶等于桶、泥巴等于泥土、雕刻南瓜等于南瓜、成书等于书与笔、鱼桶等于桶加对应鱼、喷溅/滞留药水包含药水、材料和本步酿造摊销的烈焰粉
- 磨损铁砧按完整铁砧折价：开裂铁砧为 `2/3 * anvil`，损坏铁砧为 `1/3 * anvil`
- 铜质氧化态物品如果未氧化本体可计算，则按未氧化本体等价计算；当前覆盖铜块、避雷针、铜门、铜活板门和铜箱子
- 煤、木炭、岩浆桶、烈焰棒等燃料本体保留为基础价，不按燃烧时间互相等量代换
- 钻石、绿宝石、青金石、石英、红石等矿物掉落物保留为基础价；矿石方块反过来由它们的时运 III 期望掉落计算
- 公式计算结果向下取整
- `redeem_price` 不在配置文件里
- 赎回价由代码计算：`ceil(pawn_price * 1.2)`，实现是 `(pawn * 6 + 4) / 5`
- `pawn_price < 0` 会被夹到 0

分组规则：

- 只有材质本质相同、形态也相同，只是颜色、纹饰、木种、氧化/涂蜡状态等不同，才应共用一个 group
- 木板、木台阶、木楼梯是不同形态，应分不同 group
- 竹马赛克、竹马赛克台阶、竹马赛克楼梯已分别并入木板、木台阶、木楼梯 group
- 音乐唱片、陶片、盔甲纹饰模板等当前被合并定价，这是用户明确接受的
- 基础价 group 也会按人工价格口径合并同类自然物或同状态变体，例如基础石材、圆石等价物、草状植物、小花、蘑菇、杜鹃花丛、紫水晶芽、苔藓块、灵魂沙/灵魂土、下界绯红/诡异两两对应物等

当前物品集合口径：

- 目标是 Java 版生存模式可获得物品
- 已对照中文 Minecraft Wiki “教程:获取任意物品”的不可获得清单审计
- 可疑的沙子、可疑的沙砾保留，因为 wiki 标注为“仅基岩版”不可得，当前项目是 Java 版
- 撒币货币不允许被撒币机典当，因此不在配置中
- 当前统计：`798 groups / 1363 items`
- 当前价格规则统计：`183` 个基础价 group，`401` 个基础价物品，`962` 个派生价物品，`1422` 条派生配方项，其中 `21` 条是时运 III 掉落公式

配置加载：

- `SabiPawnMachineConfig.load(MinecraftServer)` 每次从 server resource manager 读取
- `items.json` 读取失败返回空配置，不会崩溃
- `base_prices.json` 或 `derived_prices.json` 读取失败时会忽略对应新价格文件；无法求出的物品会被跳过
- 重复 item ID 只接受第一次出现
- ID 解析失败、物品不存在、`new ItemStack(item).isEmpty()` 的项会跳过

## 网络协议

所有 payload 在 `SabiNetwork.registerPayloads` 注册，版本号 `"1"`。

服务端到客户端：

- `BalanceSyncPayload`
  - ID：`sabi:balance_sync`
  - 内容：`long balance`
  - 用于同步账户余额到 `SabiClientState`

- `PawnMachinePayload`
  - ID：`sabi:sabi_machine`
  - 内容：`BlockPos pos` + `List<PawnMachineItemEntry>`
  - 用于刷新撒币机 UI 列表

客户端到服务端：

- `AccountActionPayload`
  - ID：`sabi:account_action`
  - 内容：`AccountAction action` + `long amount`
  - `DEPOSIT_ALL` 或 `WITHDRAW`

- `PawnMachineSelectPayload`
  - ID：`sabi:sabi_machine_select`
  - 内容：`BlockPos pos` + `Identifier itemId`
  - 选中详情页物品时同步给 server menu

- `PawnMachineRedeemPayload`
  - ID：`sabi:sabi_machine_redeem`
  - 内容：`BlockPos pos` + `Identifier itemId` + `int amount`
  - 执行赎回

PawnMachine item entry：

```java
record PawnMachineItemEntry(
    Identifier itemId,
    int storedCount,
    int pawnPrice,
    int redeemPrice
)
```

## 资源结构

主要资源：

- `assets/sabi/lang/en_us.json`
- `assets/sabi/lang/zh_cn.json`
- `assets/sabi/models/item/*.json`
- `assets/sabi/textures/item/*.png`
- `assets/sabi/models/block/sabi_machine.json`
- `assets/sabi/textures/block/sabi_machine_*.png`
- `data/sabi/recipe/*.json`
- `data/sabi/advancement/recipes/**/*.json`
- `data/sabi/loot_table/blocks/sabi_machine.json`
- `data/minecraft/tags/block/mineable/pickaxe.json`
- `data/minecraft/tags/block/needs_diamond_tool.json`

注意：当前 `README.md`、`docs/items.md` 旧内容以及部分中文资源曾出现过编码乱码。后续编辑中文文件时必须使用 UTF-8。

## 价格编辑脚本

脚本：

```text
tools/edit_sabi_prices.py
```

用途：

- 交互式逐 group 设置 `pawn_price`
- 显示 group 中物品的中文名称
- 显示示意贴图
- 保存时重写 `base_prices.json`
- 自动展示按代码规则计算出的赎回价

启动：

```powershell
python tools\edit_sabi_prices.py
```

资源解析策略：

- 优先读本 mod 的 `src/main/resources/assets`
- 再读 NeoForm 解包出的 vanilla assets
- 再尝试从 PCL 或 `%APPDATA%\.minecraft` 的 asset index 查找官方资源

当前已知问题：

- 脚本 UI 的部分中文文案存在乱码，需要后续修复
- 根目录启动器已按用户要求移除，不需要再加 `.bat` 启动器

## 已知设计约束与坑点

- 撒币机赎回和购买都使用账户余额
- 撒币不可被撒币机典当
- 撒币机库存是服务器全局库存，不属于某一台撒币机
- 典当任意物品时，实际进入全局库存的是完整 ItemStack，不是只记录数量
- 同一种物品按 `Item` 分桶，不按组件分桶；组件差异保存在 deque 里的不同 ItemStack 中
- UI 列表上的“库存数量”是同一 `Item` 的总数，不区分组件
- 赎回从栈顶取，因此如果同一种物品有多个不同组件栈，玩家不能在 UI 中指定取哪一个
- `ItemStack#grow` 允许内部保存超过正常堆叠上限的 count，但发给玩家或写入 SavedData 时必须拆回合法大小
- 配置文件是完整白名单，不是注册表减黑名单
- 如果 `items.json` 解析失败，撒币机可典当列表会变空
- 中文 Minecraft Wiki 页面曾用于审计 Java 版不可获得物品；页面最后编辑时间在当时为 2026-05-24
- NeoForge / Minecraft API 名称可能随 26.x 更新改变，升级时应优先重新跑 build，再审计 `Identifier`、`ValueInput/ValueOutput`、`GuiGraphicsExtractor` 等 API

## 建议给后续 Agent 的工作流

1. 修改代码前先跑 `git status --short`，确认是否有用户未提交改动。
2. 改可典当物品和 UI 原始顺序时优先改 `src/main/resources/data/sabi/sabi_machine/items.json`。
3. 改基础价格时优先改 `base_prices.json`，可以用 `tools/edit_sabi_prices.py` 辅助编辑；改等量代换规则后运行 `tools/generate_sabi_price_rules.py` 重新生成。
4. 改撒币机行为时同时检查：
   - `SabiPawnMachineMenu`
   - `SabiPawnMachineStorage`
   - `SabiNetwork`
   - `SabiPawnMachineScreen`
5. 改账户行为时同时检查：
   - `SabiAccount`
   - `SabiClient`
   - `SabiAccountScreen`
   - `SabiNetwork`
6. 修改完成后运行：

```powershell
.\gradlew.bat build --no-daemon
```

7. 如果只改 Python 脚本，至少运行：

```powershell
python -m py_compile tools\edit_sabi_prices.py
```

8. 构建成功后 jar 会自动复制到 `pcl_mods_dir`。
