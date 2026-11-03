# 鲜蔬源智慧供应链管理平台

鲜蔬源智链（`xsy-scm`）V2：以 SmartAdmin v3.31 为系统底座、按业务域组织的供应链管理平台，覆盖客户与商品、销售订单、采购收货、库存、分拣、配送、财务和报表，并提供配套的微信小程序与 Windows 秤端工作台。

当前交付状态、验证证据和未完成工作以 [项目状态](docs/status.md) 为准；业务边界以 [项目决策](docs/decisions.md) 和 [需求基线](docs/requirements/产品功能需求基线.md) 为准。本文只说明工程组成、启动方式和常用入口。

## 项目组成

| 模块 | 技术栈 | 说明 |
| --- | --- | --- |
| [`xsy-scm-server/`](xsy-scm-server/) | Java 21、Spring Boot、MyBatis-Plus、Sa-Token、Redis、PostgreSQL、Flyway | 正式后端，`sa-base` + `sa-admin` 两模块 Maven 工程 |
| [`xsy-scm-web/`](xsy-scm-web/) | Vue 3、TypeScript、Ant Design Vue、Vite、Pinia、Playwright | 管理后台与浏览器 E2E |
| [`xsy-scm-miniprogram/`](xsy-scm-miniprogram/README.md) | uni-app、Vue 3、Vite、Pinia | 面向客户的正式微信小程序 |
| [`xsy-scale-agent/`](xsy-scale-agent/README.md) | .NET 10、SQLite、Vue 3 | 独立 Windows 秤端服务与触屏工作台；正式业务事实仍由服务端维护 |
| [`tools/`](tools/README.md) | Python、PowerShell | 质量门禁、迁移校验、E2E 夹具和统一验证入口 |
| [`deploy/`](deploy/README.md) | Docker Compose、MinIO | 对象存储初始化、数据库容器与部署期必填配置说明 |
| [`docs/`](docs/README.md) | Markdown | 状态、决策、需求、架构与操作说明 |

系统登录、认证、用户、角色、权限、菜单、日志、字典、文件和后台 Layout 使用 SmartAdmin 原生实现；SCM 只实现供应链业务，不复制通用能力。

## 目录结构

```text
xsy-scm/
├── xsy-scm-server/        # 后端（sa-base、sa-admin、Flyway migrations）
├── xsy-scm-web/           # 管理后台（src、e2e、e2e-chains）
├── xsy-scm-miniprogram/   # 微信小程序
├── xsy-scale-agent/       # Windows 秤端服务与触屏工作台
├── tools/                 # 验证与质量工具
├── deploy/                # 部署配置与对象存储引导脚本
├── docs/                  # 当前状态、决策、需求与说明
├── docker-compose.yml     # 第一版部署编排
└── CONTRIBUTING.md        # 代码评审与界面约定
```

## 环境要求

- Java 21、Maven 3.9+
- Node.js 20+、npm
- PostgreSQL（仓库验证环境为 18）与 Redis
- 运行秤端或 Docker 部署时另需 .NET 10 SDK 或 Docker Engine / Docker Desktop（Linux 容器）与 Compose v2

## 本地开发

后端环境配置见 `xsy-scm-server/sa-admin/src/main/resources/dev/application.yaml` 及对应 profile 资源。数据库连接、Redis 和其他密钥使用本地配置或环境变量，不提交真实凭据。

### 后端

```powershell
cd xsy-scm-server
mvn.cmd -pl sa-admin -am compile

# 首次运行或依赖变更后
mvn.cmd install -DskipTests
mvn.cmd -pl sa-admin spring-boot:run
```

Linux / macOS 将 `mvn.cmd` 替换为 `mvn`。dev profile 默认端口为 `18080`，可通过 `XSY_V2_SERVER_PORT` 覆盖。数据库使用独立 schema `xsy_v2`；Flyway 是唯一的 schema 演进机制，已应用的 migration 不得回改。

### 管理后台

```powershell
cd xsy-scm-web
npm ci
npm run dev
```

Vite 默认端口为 `18081`，可通过 `VITE_DEV_PORT` 覆盖。API 地址由 `VITE_APP_API_URL` 配置；开发环境默认连接 `http://127.0.0.1:18080`，不经过 Vite 代理。

### 微信小程序

```powershell
cd xsy-scm-miniprogram
npm ci
npm run dev:mp-weixin:api
```

小程序提供手机号密码登录、首次改密、目录、购物车、地址、下单、订单及履约售后。当前仅下单，不选择支付方式、不扣余额，钱包与账单入口隐藏，不接微信支付。真实微信端验收与未交付能力见 [小程序 README](xsy-scm-miniprogram/README.md) 和 [项目状态](docs/status.md)。

### Windows 秤端

秤端的构建、隔离演练和现场模式见 [秤端 README](xsy-scale-agent/README.md)。平台设备接口为 `/scm/device/scale/v1`；真实电子秤、打印机和现场标定须单独验收。


## 部署与验证

- Docker Compose 编排、端口、MinIO、密钥与升级步骤统一见 [部署说明](deploy/README.md) 和 [.env.example](.env.example)。升级保留数据卷，不使用 `docker compose down -v`。
- 默认不执行测试、构建、迁移演练或服务启动；用户明确授权后使用 [tools/README.md](tools/README.md) 中的本地验证入口。未执行的检查不得报告为通过。
- 文件上传当前采用公开目录，商品图只绑定 `public/image/`；存量私有文件继续按原授权读取。具体规则见 [有效决策](docs/decisions.md)。

## 文档与协作

| 内容 | 入口 |
| --- | --- |
| 文档导航与维护规则 | [docs/README.md](docs/README.md) |
| 当前能力、待办、验证边界 | [docs/status.md](docs/status.md) |
| 业务与架构规则 | [docs/decisions.md](docs/decisions.md) |
| 工程协作、评审、后台设计 | [AGENTS.md](AGENTS.md)、[CONTRIBUTING.md](CONTRIBUTING.md)、[DESIGN.md](DESIGN.md) |
| 产品范围与用户 | [PRODUCT.md](PRODUCT.md)、[需求基线](docs/requirements/产品功能需求基线.md) |

保持开始时的分支；未经明确要求不推送或发布。只暂存明确授权的路径，不使用 `git add -A`。已应用的 Flyway migration 不得修改，新增变更追加版本。历史过程从 Git 追溯。

## 许可证

本项目以 [MIT License](LICENSE) 发布，第三方来源与许可见 [NOTICE.md](NOTICE.md)。
