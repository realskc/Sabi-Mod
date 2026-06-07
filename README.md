# Sabi Mod

Sabi 是一个基于 NeoForge 的 Minecraft Mod，主题是“撒币”经济系统和后续的德州扑克玩法。

## 主要内容

- 撒币货币系统：加入小撒币、中撒币、大撒币、巨撒币等实体货币。
- 个人撒币账户：玩家可以把实体撒币存入账户，也可以从账户中提现。
- 典当机：目前已加入方块外观和合成配方，典当与赎回逻辑仍在开发中。
- 德州扑克：计划加入，当前尚未实现。

## 版本

- Minecraft：`26.1.2`
- NeoForge：`26.1.2.41-beta`
- Mod ID：`sabi`
- 当前 Mod 版本：`1.0.0`

## 构建

需要安装 Java/JDK 25。构建命令：

```powershell
.\gradlew.bat build
```

构建完成后，mod 文件会生成在 `build/libs/` 目录中。
