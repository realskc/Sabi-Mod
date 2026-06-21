# 物品、方块与具体内容数据

本文档保存具体内容数据：物品 ID、显示名、配方、兑换比例、硬度等。玩家可见功能说明见 `docs/features.md`。

## 实体撒币

所有实体撒币最大堆叠数都是 `64`。

相邻面额汇率是 `64`。

| 物品 ID | 中文名 | 英文名 | 账户价值 |
| --- | --- | --- | ---: |
| `sabi:small_sabi` | 小撒币 | Xiao Sabi | 1 |
| `sabi:medium_sabi` | 中撒币 | Zhong Sabi | 64 |
| `sabi:big_sabi` | 大撒币 | Da Sabi | 4096 |
| `sabi:giant_sabi` | 巨撒币 | Ju Sabi | 262144 |

兑换比例：

- 64 小撒币 -> 1 中撒币
- 1 中撒币 -> 64 小撒币
- 64 中撒币 -> 1 大撒币
- 1 大撒币 -> 64 中撒币
- 64 大撒币 -> 1 巨撒币
- 1 巨撒币 -> 64 大撒币

贴图口径：

- 小撒币：绿色宝石风格，带黄色标记
- 中撒币：蓝色宝石风格，带黄色标记
- 大撒币：红色宝石风格，带黄色标记
- 巨撒币：紫金宝石风格，带黄色标记

## 高级红石核心

- 物品 ID：`sabi:advanced_redstone_core`
- 中文名：高级红石核心
- 英文名：Advanced Redstone Core
- 创造模式位置：材料标签
- 用途：撒币机核心合成材料

合成配方：

```text
R Q R
C E C
R N R
```

材料：

- `R`：红石块
- `Q`：石英
- `C`：红石比较器
- `E`：回响碎片
- `N`：下界合金锭

贴图口径：

- 红石核心外框
- 中央有回响碎片和合金质感核心

## 撒币机

- 方块 ID：`sabi:sabi_machine`
- 物品 ID：`sabi:sabi_machine`
- 中文名：撒币机
- 英文名：Sabi Machine
- 创造模式位置：材料标签
- 硬度：`50.0`
- 爆炸抗性：`1200.0`
- 挖掘要求：钻石镐级别工具

合成配方：

```text
O E O
E C E
O X O
```

材料：

- `O`：黑曜石
- `E`：绿宝石块
- `C`：高级红石核心
- `X`：末影箱

贴图口径：

- 黑曜石机器外壳
- 绿宝石正面面板
- 红石指示灯
- 顶部核心槽

## 撒币机物品集合

白名单与显示顺序配置文件：

```text
src/main/resources/data/sabi/sabi_machine/items.json
```

基础价配置文件：

```text
src/main/resources/data/sabi/sabi_machine/base_prices.json
```

派生价配置文件：

```text
src/main/resources/data/sabi/sabi_machine/derived_prices.json
```

当前物品集合口径：

- 目标是 Java 版生存模式可获得物品。
- 已对照中文 Minecraft Wiki 的不可获得物品清单审计。
- 可疑的沙子和可疑的沙砾在 Java 版口径下保留。
- 撒币货币被明确排除。

当前统计：

- `798` 个 group
- `1363` 个 item
- `183` 个基础价 group
- `401` 个基础价 item
- `962` 个派生价 item
- `1422` 条派生公式项
- `21` 条时运 III 掉落期望公式

