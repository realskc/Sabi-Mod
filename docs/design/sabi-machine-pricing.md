# 撒币机价格与白名单

相关文件：

- `SabiPawnMachineConfig.java`
- `SabiPriceRules.java`
- `src/main/resources/data/sabi/sabi_machine/items.json`
- `src/main/resources/data/sabi/sabi_machine/base_prices.json`
- `src/main/resources/data/sabi/sabi_machine/derived_prices.json`

## 白名单

`items.json` 是完整白名单，不是注册表减黑名单。

资源 ID：

```text
sabi:sabi_machine/items.json
```

语义：

- 决定哪些物品允许被撒币机典当。
- 决定 UI 原始显示顺序。
- 不包含价格字段。

## 基础价

`base_prices.json` 放需要人工定价的基础物品。

当前共有 `183` 个基础价 group。

`symbols` 用于虚拟基础价，目前有：

```text
sabi:generic_dye
```

## 派生价

`derived_prices.json` 放可计算物品。

每个 group 写 `items` 和一个或多个 `recipes`。

求值规则：

- 如果一个物品有多条可解公式，取最便宜的一条。
- `any_of` 取候选项中的最便宜价格。
- 工作台、切石机不额外收费。
- 烧炼、烟熏、营火、疾速熔炉额外加入 `0.125 * minecraft:coal`。
- `fortune_iii_loot` 表示按时运 III 期望掉落物计价。
- 染色类派生公式使用 `sabi:generic_dye`。
- 公式计算结果向下取整。

## 赎回价

`redeem_price` 不写在配置里。

代码按以下规则计算：

```text
ceil(pawn_price * 1.2)
```

实现：

```text
(pawn * 6 + 4) / 5
```

`pawn_price < 0` 会被夹到 0。

## 分组口径

只有材质本质相同、形态也相同，只是颜色、纹饰、木种、氧化/涂蜡状态等不同，才应共用一个 group。

不要把不同形态放进同一 group。例如木板、木台阶、木楼梯应分开。

## 当前统计

- `798 groups / 1363 items`
- `183` 个基础价 group
- `401` 个基础价物品
- `962` 个派生价物品
- `1422` 条派生配方项
- `21` 条时运 III 掉落公式

