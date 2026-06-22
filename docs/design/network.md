# 网络协议

相关文件：

- `SabiNetwork.java`
- `SabiClientState.java`
- `client/SabiClient.java`
- `client/SabiPawnMachineScreen.java`

所有 payload 在 `SabiNetwork.registerPayloads` 注册。

协议版本：

```text
1
```

## 服务端到客户端

`BalanceSyncPayload`

- ID：`sabi:balance_sync`
- 内容：`long balance`
- 用于同步账户余额到 `SabiClientState`

`PawnMachinePayload`

- ID：`sabi:sabi_machine`
- 内容：`BlockPos pos` + `List<PawnMachineItemEntry>`
- 用于刷新撒币机 UI 列表

`PawnMachineItemEntry`：

```java
record PawnMachineItemEntry(
    Identifier itemId,
    int storedCount,
    int pawnPrice,
    int redeemPrice
)
```

## 客户端到服务端

`AccountActionPayload`

- ID：`sabi:account_action`
- 内容：`AccountAction action` + `long amount`
- 操作：`DEPOSIT_ALL` 或 `WITHDRAW`

`PawnMachineSelectPayload`

- ID：`sabi:sabi_machine_select`
- 内容：`BlockPos pos` + `Identifier itemId`
- 用于详情页选中物品时同步 server menu

`PawnMachineInputModePayload`

- ID：`sabi:sabi_machine_input_mode`
- 内容：`BlockPos pos` + `boolean quickPawnInputActive` + `boolean detailPawnInputActive`
- 用于同步撒币机当前页面启用哪个典当槽
- 原因：`Shift+Click` 背包物品时，实际移动逻辑由服务端 menu 执行；如果只在客户端 screen 设置槽激活状态，服务端会拒绝移动并把客户端预测放入槽内的物品同步回背包。

`PawnMachineRedeemPayload`

- ID：`sabi:sabi_machine_redeem`
- 内容：`BlockPos pos` + `Identifier itemId` + `int amount`
- 执行赎回

`PawnMachineBuyPayload`

- ID：`sabi:sabi_machine_buy`
- 内容：`BlockPos pos` + `Identifier itemId` + `int amount`
- 执行购买

## 服务端校验

赎回和购买都会检查：

- 玩家仍在撒币机附近。
- 目标方块仍是撒币机。
- 物品仍在配置允许列表里。
- 数量不超过该物品最大堆叠数。
- 账户余额足够。

如果客户端发来的数量小于等于 0，服务端会按 1 处理。
这只用于兜住异常或过期客户端；正常 UI 会禁用空数量或 0 数量的交易按钮。

购买还会检查当前全局库存为 0。
