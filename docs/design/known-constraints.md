# 已知约束与坑点

## 撒币机

- 撒币机赎回和购买都使用账户余额。
- 撒币不可被撒币机典当。
- 撒币机库存是服务器全局库存，不属于某一台撒币机。
- 打破撒币机不会掉出全局库存。
- 典当任意物品时，实际进入全局库存的是完整 `ItemStack`。
- 同一种物品按 `Item` 分桶，不按组件分桶。
- 组件差异保存在 deque 里的不同 `ItemStack` 中。
- UI 列表上的“库存数量”是同一 `Item` 的总数，不区分组件。
- 赎回从栈顶取，玩家不能指定同一种物品下的具体组件栈。

## 数据与序列化

- `ItemStack#grow` 允许内部保存超过正常堆叠上限的 count。
- 发给玩家或写入 SavedData 时必须拆回合法大小。
- 新版 `ItemStack.CODEC` 的 count 上限是 `99`。
- 典当槽有玩家附件兜底，但服务器崩溃前仍依赖玩家数据是否已经落盘；代码已在槽变化后主动触发保存来缩短窗口。

## 配置

- `items.json` 是完整白名单。
- 如果 `items.json` 解析失败，撒币机可典当列表会变空。
- `base_prices.json` 或 `derived_prices.json` 读取失败时会忽略对应文件。
- 白名单中无法求出价格的物品会被跳过。
- 重复 item ID 只接受第一次出现。
- ID 解析失败、物品不存在、`new ItemStack(item).isEmpty()` 的项会跳过。

## 版本升级

NeoForge / Minecraft API 名称可能随 26.x 更新改变。

升级时优先重新跑 build，再审计：

- `Identifier`
- `ValueInput`
- `ValueOutput`
- `GuiGraphicsExtractor`
- Data Components
- 自定义 payload 注册 API

