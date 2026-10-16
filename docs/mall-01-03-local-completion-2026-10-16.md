# XSY 商城 MALL-01～03 本地实现与交付（2026-10-16）

> 本文记录当前本地 `main` 工作区的完成范围与验证证据。数据库迁移只编写在仓库，**尚未在生产执行**；不可据此宣称已完成联调或已部署。

## 本次实现范围

### MALL-01：配送仓与可售库存

- 客户绑定配送仓为强约束：绑定存在时只能使用该仓；未绑定时依据收货地址区县映射和优先级列出可选仓；没有合法配送仓时不能下单。
- uni-app 结算页按收货地址加载配送仓、支持切换，切换时重算预览和请求幂等签名。商品可售量只读取指定仓的余额减正式预留，不跨仓混算。
- 结算预览/提交均由服务端重新校验仓库、地址、客户价、数量、库存与指纹，配送线路入单和发车双重核对仓库与订单指定仓一致。
- 管理后台“仓库列表 → 商城配送区县”可维护区县仓及优先级；客户表单可维护客户绑定仓。复用 V135 数据库迁移和现有数据范围/权限判定。

### MALL-02：起订规则、销售/库存单位与履约闭环

- 每 SKU 支持销售单位下的起订量和递增量；商品详情新增库存记账单位换算配置维护入口。
- 后端接口：
  - `GET /scm/product/sku/stock-conversions?skuId=...`
  - `POST /scm/product/sku/stock-conversions`
  - `POST /scm/product/sku/stock-conversions/delete`
- V136 配置表保存“销售单位 → 库存记账单位”关系（例如 1 箱 = 10 kg）。确认订单时冻结记账单位和换算比例，后续变更规则不会追溯历史订单。
- 正式库存预留按绑定配送仓及库存数量占用，分拣和订单金额仍按销售单位，发车 SALES_OUT 库存流水按记账数量，出库行额外保存 `stock_quantity`；库存换算禁止无声四舍五入。
- **退货修复：**原退货接收混用了销售单位与库存流水单位。新增 V137 给退货接收行保存 `stock_quantity`、`stock_unit_snapshot`；退货批准、接收、累计退货上限继续使用销售单位；源出库流水的已分配量以及返库事实统一使用库存单位；历史记录可回退读取原 `quantity`。
- 当前退货行 VO 同时返回销售数量、销售单位、库存数量、库存单位；单价成本依旧是库存记账单位成本。

### MALL-03：高德地图地址

- 复核了已有地图选点和 GCJ-02 坐标存储链路，保留原有配置与实现。本轮未新增地图 Key，也未声称已在线验证地图服务。

## 验证范围与结果

- 后端定向 JUnit：涵盖销售/库存数量换算、不同配送仓可售库存、绑定仓优先、商城结算和商品查询。实际执行结果以本地 Maven 日志为准。
- Maven `spotless:apply spotless:check`：已通过；不再绕过格式门禁。
- uni-app H5 构建：此前执行 `npm run build:h5` 成功，含历史 Dart Sass 弃用警告；商城页面相关 ESLint 检查通过。
- 管理后台触及页面的 ESLint 检查通过；全仓 `vue-tsc` 仍存在原有约 1775 条错误，触及文件在该次检查中无匹配错误，故不能宣称全仓类型检查通过。
- **未执行数据库联调、微信真机测试、物流发车/退货真实 Postgres 集成测试或生产部署。**这些属于上线前验收，不代表已通过。

## 上线前必验

1. 将目标数据库 Flyway 按顺序升级到至少 **V137**；校验 V135 配送仓字段/规则、V136 单位换算、V137 退货库存数量字段均成功迁移。
2. 在测试环境配 1 箱 = 10 kg、配送仓 A 现存 100 kg、B 没有库存；预览 3 箱只在 A 可售。切换地址和配送仓验证禁下单逻辑。
3. 下单并确认 3 箱，显式预留应增加 30 kg；分拣实发 2 箱，发车应释放整笔预留且库存扣 20 kg，出库明细 `quantity=2`、`stock_quantity=20`。
4. 对该 2 箱再分批退 1 箱、1 箱：销售累计退回 2 箱，库存流水分别返 10 kg，再超额退货应失败；检查坏件不返库、成本跟随原 SALES_OUT 库存单位均价。
5. 检查绑定仓隔离、无区县规则、换算配置缺失、库存计量单位错误、精度不可表达、普通历史订单与历史退货兼容路径。
6. 测试 GCJ-02 地址选点/逆地理编码、前后端地图 Key 环境设置，RBAC 及员工数据范围。

## 本地验证命令（PowerShell）

```powershell
cd 'D:\Browser Download\xsy-scm\xsy-scm-server'
mvn -B -pl sa-admin -am spotless:check
mvn -B -pl sa-admin -am '-Dtest=InventorySaleUnitConverterTest,MallStockAvailabilityServiceTest,MallWarehouseServiceTest,MallCheckoutServiceTest,MallCatalogServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

cd 'D:\Browser Download\xsy-scm\xsy-scm-customer-app'
npm run build:h5

cd 'D:\Browser Download\xsy-scm'
git diff --check
git status --short
```

## 提交与部署边界

本次功能应在完成本地编译和定向测试后提交为一个可审计的本地 Git commit。**提交不等于部署**；生产数据库和应用更新须走单独上线流程。
