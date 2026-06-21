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

购买还会检查当前全局库存为 0。

