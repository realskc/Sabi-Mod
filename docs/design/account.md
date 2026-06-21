# 个人撒币账户

相关文件：

- `SabiAccount.java`
- `SabiClientState.java`
- `SabiClient.java`
- `client/SabiAccountScreen.java`
- `SabiNetwork.java`

## 数据模型

账户是玩家附件，不是物品：

```java
AttachmentType.serializable(AccountData::new)
    .copyOnDeath()
    .sync(AccountData.STREAM_CODEC)
    .build()
```

账户数据：

- 字段：`long balance`
- 下限：0
- 加法：`saturatingAdd`
- 溢出时饱和到 `Long.MAX_VALUE`
- 死亡复制：开启 `copyOnDeath`

## 同步

服务端在以下时机同步余额：

- 登录
- 切维度
- 重生
- 账户交易后

同步 payload 是 `BalanceSyncPayload`。

客户端余额保存在 `SabiClientState`。

## 账户操作

- `depositAllCurrency(Player)`：扫描玩家背包，把所有实体撒币折算成小撒币单位存入账户。
- `withdraw(Player, amount)`：账户余额足够时扣余额，并按巨、大、中、小顺序发放实体货币。
- `spend(Player, amount)`：账户余额足够时扣款；`amount == 0` 允许成功。

## 背包界面入口

`SabiClient` 监听 `InventoryScreen` 的渲染和鼠标点击。

账户框位置：

- `accountX = screen.getLeftPos() + 8`
- `accountY = screen.getTopPos() - 24`
- 尺寸：`60 x 18`

设计原因：

- 只在按 E 打开的玩家背包界面显示，避免普通 HUD 没有鼠标时无法点击。
- 点击账户框打开 `SabiAccountScreen`。

