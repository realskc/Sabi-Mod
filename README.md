# Sabi Mod

Sabi 是一个基于 Fabric 的 Minecraft Mod，主题是“撒币”经济系统和后续的德州扑克玩法。

## 主要内容

- 撒币货币系统：加入小撒币、中撒币、大撒币、巨撒币等实体货币。
- 个人撒币账户：玩家可以把实体撒币存入账户，也可以从账户中提现。
- 撒币机：右键打开交易界面，可典当配置允许的物品并用小撒币赎回。
- 德州扑克：计划加入，当前尚未实现。

## 版本

- Minecraft：`26.2`
- Fabric Loader：`0.18.4`
- Fabric API：`0.154.2+26.2`
- Mod ID：`sabi`
- 当前 Mod 版本：`1.2.0`

## 构建

需要安装 Java/JDK 25。构建命令：

```powershell
.\gradlew.bat build
```

构建完成后，mod 文件会生成在 `build/libs/` 目录中。

项目支持通过根目录的 `local.properties` 将构建产物自动复制到本机启动器实例。这个文件是可选的，并已被 Git 忽略；下载仓库后即使没有它，也可以正常执行构建。如果需要自动复制，可自行创建该文件：

```properties
pcl_mods_dir=D:/path/to/your/minecraft-instance/mods
```
