# 实体撒币与兑换

相关文件：

- `CurrencyDenomination.java`
- `CurrencyItem.java`
- `SabiCurrencyExchange.java`
- `CurrencyStackUpgradeRecipe.java`
- `src/main/resources/data/sabi/recipe/*.json`

## 面额

具体物品 ID、显示名、账户价值和兑换比例见 `item.md`。

设计规则：

- 实体货币只负责展示、交易和掉落。
- 大额长期余额放在个人账户里。
- 撒币本身不可被撒币机典当。

## 兑换实现

降级兑换是普通无序配方。具体兑换比例见 `item.md`。

升级兑换使用自定义 recipe serializer：

- `sabi:small_sabi_to_medium_sabi`
- `sabi:medium_sabi_to_big_sabi`
- `sabi:big_sabi_to_giant_sabi`

对应 JSON 只有 type：

```json
{ "type": "sabi:small_sabi_to_medium_sabi" }
```

## 设计原因

原版合成事件默认只消耗 1 个输入物品。

因此升级合成需要 `SabiCurrencyExchange` 监听 `PlayerEvent.ItemCraftedEvent`，在升级合成发生后额外 shrink `63` 个低级撒币。

后续如果换更复杂的合成逻辑，需要重新审计这段补扣逻辑。
