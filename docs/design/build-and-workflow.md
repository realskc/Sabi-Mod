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

## 构建产物

Fabric jar 生成在 `build/libs/`。默认情况下构建任务不会写入启动器的 mods 目录；部署实例需要安装匹配版本的 Fabric Loader 与 Fabric API。

如果项目根目录存在被 Git 忽略的 `local.properties`，并配置了 `pcl_mods_dir`，完整 `build` 会在结束后自动把 jar 复制到该本地 PCL 实例的 mods 目录。机器相关的实际路径不要写入受版本控制的文件。

## 后续 Agent 工作流

1. 修改前运行 `git status --short`。
2. 不要回滚用户未提交改动。
3. 改可典当物品和 UI 原始顺序时优先改 `items.json`。
4. 改基础价格时优先改 `base_prices.json`。
5. 改撒币机行为时同时检查菜单、存储、网络和客户端界面。
6. 改账户行为时同时检查账户、客户端入口、账户界面和网络。
7. 完成后运行 `compileJava` 和完整 `build`。
