# 构建与工作流

## 构建命令

修改 Java、资源或文档中涉及行为说明后，至少运行：

```powershell
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat build --no-daemon
```

如果只改 Python 脚本，至少运行：

```powershell
python -m py_compile tools\edit_sabi_prices.py
```

## copyModToPcl

`build.gradle` 在 `build` 任务结束后自动执行 `copyModToPcl`。

复制目标来自 `gradle.properties`：

```properties
pcl_mods_dir=D:/Portable Softwares/PCL/.minecraft/versions/26.1.2-NeoForge_26.1.2.73/mods
```

如果其他机器没有这个目录，需要修改 `gradle.properties` 或临时调整该属性。

## 后续 Agent 工作流

1. 修改前运行 `git status --short`。
2. 不要回滚用户未提交改动。
3. 改可典当物品和 UI 原始顺序时优先改 `items.json`。
4. 改基础价格时优先改 `base_prices.json`。
5. 改撒币机行为时同时检查菜单、存储、网络和客户端界面。
6. 改账户行为时同时检查账户、客户端入口、账户界面和网络。
7. 完成后运行 `compileJava` 和完整 `build`。

