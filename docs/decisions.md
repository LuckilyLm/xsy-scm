# 当前决策索引

本文件只列出当前仍有效的决策和未决事项。正式约束以 ADR 正文为准；旧决策全文见
[2026-09-28 决策归档](archive/decisions/decisions-2026-09-28.md)。

## 工程边界

- SmartAdmin 系统底座边界见 [SmartAdmin 底座规则](architecture/smartadmin-foundation.md)。
- Java 质量、包结构和文档治理见 [整改基线](quality/java-code-quality-remediation-plan.md)。
- 已应用的数据库迁移不可修改；新变更只追加新版本。

## P0 基线收口裁决：角色模型、SCM 数据范围与 F0 附件收口

现行规则见 [ADR-001：数据范围与附件授权](adr/001-data-scope-and-file-access.md)。

## P1 分拣管理裁决

现行规则见 [ADR-002：分拣事实边界](adr/002-sorting-fact-boundary.md)。

## P2 物流配送 L3 裁决

现行规则见 [ADR-003：配送出库与签收](adr/003-delivery-outbound-and-signoff.md)。

## P3 Finance R1 裁决

现行规则见 [ADR-004：Finance R1 财务事实](adr/004-finance-r1-facts.md)；设计细节见
[Finance R1 设计](plan/active/finance-r1-design.md)。

## 其他仍有效的历史决策

采购商品每日清单的可配置调度、提交日期口径、快照与权限规则见
[ADR-005：采购商品每日清单](adr/005-purchase-daily-report.md)。

移动加权成本、调拨成本平移、订单来源、商品主档、大屏和地图 M0/M1 的决策仍可从
[完整归档](archive/decisions/decisions-2026-09-27.md)查阅。与当前 ADR 不一致的旧口径已被后续决策取代。

## 未决事项

- 在途调拨量是否需要独立的报表视图；现状是通过调拨单查看，不计入任何仓库余额。
- 订单和线路没有默认仓推断；创建或发车时由操作者显式选择仓库。
- 地图 M2 的完整底图、供应商点位和商用部署路线。
- 期初 `avg_cost` 的业务口径；当前采用最近一次采购入库单价，无历史时为 0。已应用迁移不得为此回改。
- W6-2 小程序的启动条件和迁移方案。
