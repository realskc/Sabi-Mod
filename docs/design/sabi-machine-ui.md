# 撒币机 UI

相关文件：

- `client/SabiPawnMachineScreen.java`
- `SabiPawnMachineMenu.java`

## 尺寸

- `IMAGE_WIDTH = 244`
- `IMAGE_HEIGHT = 238`

## 页面模式

- `GRID`：主列表页。
- `DETAIL`：选中物品后的交易页。
- `PAWN_CONFIRM`：主页面快速典当确认页。
- `EMPTY_SHULKER_CONFIRM`：主页面空潜影盒典当确认页。

## 主列表页

关键控件：

- 搜索框：`leftPos + 30, topPos + 28, 118 x 20`
- 快速典当槽：menu slot 0，坐标 `154, 28`
- 快速典当按钮：`leftPos + 176, topPos + 28`
- 物品表格：9 列 x 4 行，每格 `18 x 18`
- 网格位置：`gridX = leftPos + (IMAGE_WIDTH - GRID_WIDTH) / 2`，`gridY = topPos + 54`

行为：

- 支持鼠标滚轮滚动。
- 支持搜索中文显示名和完整物品 ID。
- `Shift+Click` 玩家背包物品时，只会在快速典当槽为空时放入该槽。

排序：

1. 全局库存数量降序。
2. 配置文件原始顺序升序。

## 详情页

展示内容：

- 物品图标
- 名称
- 全局库存数量
- 典当收益
- 赎回花费或购买花费
- 账户余额或花费/余额

详情页典当槽要求放入当前选中的同一种物品。

## 输入处理

屏幕覆写 `keyPressed`。

- 如果搜索框或数量框聚焦，按 E 不关闭界面。
- 如果编辑框未聚焦，按 E 按原版逻辑关闭。

