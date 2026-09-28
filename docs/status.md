# 项目状态

最后更新：2026-09-28

## 当前完成

- SCM V2 正式底座、商品、客户、供应商、定价、订单、采购、库存、分拣、配送和 Finance R1 的 F1-3B 已完成，详情见 [有效决策](decisions.md) 与归档记录。
- Java 质量整改 Q0–Q4 实施与最终验收已完成；当前快照、baseline 收缩、测试结果和允许的云门控跳过见 [最终验收报告](quality/java-quality-final-verification-2026-09-28.md)。
- Q2 类型、权限、校验、命名、工具复用和格式债务均为 0；Q3 架构门禁与跨域 DAO 白名单通过。

## 当前进行

- 无 Java 质量整改待办。后端全量回归符合验收条件；5 个 `F0FileStorageCloudIT` 仅因云存储环境门控而跳过。

## 暂停或未开始

- Finance R1 F1-3C 至 F1-8 尚未开始。
- W6-2 小程序尚未开始，当前目录保持冻结。
- 地图 M2 的完整商用底图方案仍待路线选择；现有 M0/M1 与可配置接入不受影响。

## 风险与验证状态

- `python tools/verify.py backend` 汇总 1203 tests / 0 failures / 0 errors / 5 skipped；跳过项全部属于既有 `F0FileStorageCloudIT`。该入口把非零跳过标为 `INCOMPLETE`，具体用例与处理口径见最终验收报告。
- 质量入口 PASS；Spotless 全量检查覆盖 731 个 SCM 生产 Java 文件；质量工具单测 85/85；迁移校验 67 migrations，drift / missing / renamed / unbaked 全为 0。
- 归档进度中的测试结果仅描述当时的运行记录，不代表本次改动已验证。

## 下一步

- Finance R1 F1-3C 至 F1-8 仍暂停；只有用户明确开始下一阶段后才推进。
- 若地图 M2 或多仓默认选择进入实施，先更新对应 ADR 和活动计划。
