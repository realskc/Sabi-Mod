# 撒币机全局库存

相关文件：

- `SabiPawnMachineStorage.java`
- `SabiNetwork.java`

## 数据模型

`SabiPawnMachineStorage` 是全服务器共享库存，继承 `SavedData`。

保存位置由服务器 `DataStorage` 管理：

```java
server.getDataStorage()
```

内部结构：

```java
Map<Item, Deque<ItemStack>> storedItemsByItem
```

每一种 `Item` 对应一个栈结构。

## 存入规则

`store(ItemStack stack)`：

- 空 stack 忽略。
- 以 `stack.getItem()` 找到 deque。
- 只尝试和 deque 栈顶合并。
- 合并条件：`ItemStack.isSameItemSameComponents(top, stack)`。
- 如果不能合并，则 push 一个 `stack.copy()`。

设计原因：

- UI 当前按 `Item` 展示总库存，不按组件细分。
- 真实 `ItemStack` 仍被保留，避免丢失耐久、附魔、组件、NBT。
- 只合并栈顶，避免重排历史 ItemStack。

## 取出规则

`take(Item item, int amount)`：

- 库存不足时返回空列表。
- 从对应 deque 栈顶开始取。
- 取出的 stack 发给玩家，背包满则掉落。

限制：

- 玩家不能指定同一种物品下的具体组件栈。
- 如果同一种物品有多个不同组件栈，会按栈顶顺序取出。

## 持久化

SavedData ID：

```text
sabi:sabi_machine_storage
```

保存字段：

```text
stored_items
```

每个子项通过 `ItemStack.CODEC` 保存。

新版 `ItemStack.CODEC` 的 count 上限是 `99`。因此保存时会把内部大栈拆成不超过 `99` 的小栈，读取时再按 `store(stack)` 规则压缩。

