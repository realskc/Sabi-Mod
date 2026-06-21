# 价格工具

相关文件：

- `tools/generate_sabi_price_rules.py`
- `tools/edit_sabi_prices.py`
- `src/main/java/top/sabi/tools/SabiPriceResolverCli.java`
- `src/main/resources/data/sabi/sabi_machine/price_rules_report.md`

## 生成器

`tools/generate_sabi_price_rules.py` 从 `items.json` 和本地 recipe 数据生成：

- `base_prices.json`
- `derived_prices.json`
- `price_rules_report.md`

报告用于记录：

- 统计信息
- 强制基础价规则
- 未能安全求解而保留为基础价的物品
- 被跳过的 recipe type

## 交互式编辑器

启动：

```powershell
python tools\edit_sabi_prices.py
```

用途：

- 逐 group 设置 `pawn_price`
- 显示 group 中物品的中文名称
- 显示示意贴图
- 保存时重写 `base_prices.json`
- 自动展示按代码规则计算出的赎回价

## 资源解析策略

编辑器找贴图时按顺序尝试：

1. 本 mod 的 `src/main/resources/assets`
2. NeoForm 解包出的 vanilla assets
3. PCL 或 `%APPDATA%\.minecraft` 的 asset index

## 已知问题

- 脚本 UI 的部分中文文案存在乱码，需要后续修复。
- 根目录启动器已按用户要求移除，不需要再加 `.bat` 启动器。

