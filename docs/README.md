# Sabi Mod 文档索引

给后续 Agent 的阅读顺序：

1. `technical-design.md`：项目架构、核心系统、网络协议、撒币机实现、配置规则、构建方式和已知坑点。
2. `items.md`：当前新增物品、方块、配方和内容层规则。

如果要改撒币机可典当物品或价格，优先阅读：

- `technical-design.md` 的“撒币机价格配置”
- `src/main/resources/data/sabi/sabi_machine/items.json`
- `tools/edit_sabi_prices.py`

如果要改 UI 或交易逻辑，优先阅读：

- `technical-design.md` 的“撒币机”“撒币机 UI”“网络协议”
- `src/main/java/top/sabi/SabiPawnMachineMenu.java`
- `src/main/java/top/sabi/SabiPawnMachineBlockEntity.java`
- `src/main/java/top/sabi/SabiNetwork.java`
- `src/main/java/top/sabi/client/SabiPawnMachineScreen.java`
