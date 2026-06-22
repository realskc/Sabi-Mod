# 技术设计文档索引

`design/` 只记录技术细节、设计原因、关键文件和维护注意事项。

## 基础

- `build-and-workflow.md`：构建、验证命令、后续 Agent 工作流。
- `registration-and-resources.md`：顶层注册和资源结构。
- `item.md`：新增物品、方块、精确数值、配方和内容口径。
- `known-constraints.md`：当前约束、坑点和升级注意事项。

## 系统

- `currency.md`：实体撒币、兑换配方和为什么需要合成事件补扣。
- `account.md`：玩家账户、附件、同步和账户界面。
- `sabi-machine-behavior.md`：撒币机交易行为、潜影盒规则和交互入口。
- `sabi-machine-pending-input.md`：典当槽防丢、玩家附件兜底和归还规则。
- `sabi-machine-storage.md`：全局库存、SavedData、真实 ItemStack 保存。
- `sabi-machine-ui.md`：撒币机客户端 UI 页面结构和交互。
- `sabi-machine-pricing.md`：白名单、基础价、派生价和分组口径。
- `network.md`：自定义 payload 和同步方向。
- `price-tools.md`：价格生成器和交互式编辑脚本。

## 维护原则

- 顶层 docs 面向功能说明；不要把实现细节写回顶层。
- 单篇设计文档只讲一个系统或问题。
- 如果某篇文档开始膨胀，继续拆出更小文档。
