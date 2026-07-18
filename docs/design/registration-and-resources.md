# 顶层注册与资源结构

## 核心入口

核心入口是：

```text
src/main/java/top/sabi/Sabi.java
```

注册内容：

- 使用原版 `Registry.register` 注册方块、物品和菜单类型
- 使用 `ExtendedMenuType` 传输撒币机方块坐标
- 玩家账户附件：`SabiAccount.register()`
- 撒币机挂起典当槽附件：`SabiPawnMachinePendingInput.register()`
- 网络 payload：`SabiNetwork.registerServer()`

## 创造模式物品栏

以下内容加入 `CreativeModeTabs.INGREDIENTS`：

- 所有撒币
- 高级红石核心
- 撒币机

## 主要资源

语言：

- `assets/sabi/lang/en_us.json`
- `assets/sabi/lang/zh_cn.json`

物品和方块模型：

- `assets/sabi/models/item/*.json`
- `assets/sabi/models/block/sabi_machine.json`
- `assets/sabi/blockstates/sabi_machine.json`

贴图：

- `assets/sabi/textures/item/*.png`
- `assets/sabi/textures/block/sabi_machine_*.png`

数据包资源：

- `data/sabi/recipe/*.json`
- `data/sabi/advancement/recipes/**/*.json`
- `data/sabi/loot_table/blocks/sabi_machine.json`
- `data/minecraft/tags/block/mineable/pickaxe.json`
- `data/minecraft/tags/block/needs_diamond_tool.json`

注意：中文文件必须使用 UTF-8。
