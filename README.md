# 鲜蔬源智慧供应链管理平台

鲜蔬源智链（`xsy-scm`）V2，以 SmartAdmin v3.31 为系统底座，供应链业务采用模块化单体架构。

## 当前范围

截至 2026-09-17，W0 基线、W1 商品、W2 客户与供应商、W3 定价、W4 销售订单、W5 采购均已完成实施与对应阶段验收；W5.5 原生功能同步与 F0 对象存储激活也已完成。W5 包含采购需求、采购单、多次实重收货和最小仓库主数据，**不包含库存实现**。W6 库存与小程序尚未启动，后续范围须另行确认。

阶段证据与业务设计见 [文档总览](docs/00-文档总览与索引.md)。历史阶段结论及限制以各验收报告原文为准，不代表本次重新运行了验收。

## 工程目录

| 目录 | 用途 |
| --- | --- |
| `xsy-scm-server/` | 正式后端：Java 21、Spring Boot、MyBatis-Plus、Sa-Token、Redis、PostgreSQL、Flyway |
| `xsy-scm-web/` | 正式后台：Vue3、TypeScript、Ant Design Vue、Vite、Pinia |
| `docs/` | 有效业务规则、架构设计与阶段验收 |
| `tools/` | 本地工具，已排除 Git 跟踪（克隆后不会自动获得） |
| `deploy/minio/` | F0 本地对象存储与集成验证环境 |
| `xsy-scm-miniapp/` | 冻结的 Taro + React legacy 小程序，后续目标为 uni-app + Vue3 |
| `project-reference-examples/` | 纳入 Git 的只读上游参考；含凭据的环境配置仍忽略 |

系统登录、权限、菜单、日志、字典、文件和后台 Layout 使用 SmartAdmin 原生实现。规则见 [AGENTS.md](AGENTS.md) 与 [SmartAdmin 底座规则](SMARTADMIN_REFERENCE_RULES.md)。

## 本地开发

需要 Java 21、Maven、Node.js、PostgreSQL 与 Redis。后端是 `sa-base`、`sa-admin` 两模块 Maven 工程；环境配置见 `sa-admin/src/main/resources/dev/application.yaml` 及对应 profile 资源。数据库连接、Redis 和其他密钥使用本地配置或环境变量，不提交真实凭据。

```powershell
cd xsy-scm-server
mvn.cmd -pl sa-admin -am compile
```

运行后端时先安装依赖模块，再单独启动应用模块：

```powershell
mvn.cmd install -DskipTests
mvn.cmd -pl sa-admin spring-boot:run
```

dev profile 后端默认端口为 `18080`，可通过 `XSY_V2_SERVER_PORT` 覆盖。数据库使用独立 schema `xsy_v2`；Flyway 是唯一 schema 演进机制，已应用 migration 不得回改。

前端：

```powershell
cd xsy-scm-web
npm ci
npm run dev
```

Vite 默认端口为 `18081`，可通过 `VITE_DEV_PORT` 覆盖。API 地址由 `VITE_APP_API_URL` 配置；开发环境默认连接 `http://127.0.0.1:18080`，不是 Vite 代理。

## 验证

后端从 `xsy-scm-server/` 执行 `mvn.cmd verify`；受影响的数据库集成测试需要可用的测试 PostgreSQL 和对应配置。

前端从 `xsy-scm-web/` 执行：

```powershell
npm run lint
npm run typecheck
npm test
npm run build
```

浏览器验证须先启动后端和所需服务，并使用当前工程中的 Playwright 配置与用例。没有执行的检查不能报告为通过。
