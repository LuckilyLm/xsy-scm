# 鲜蔬园智慧供应链管理平台

鲜蔬园智链（`xsy-scm`）V2：以 SmartAdmin v3.31 为系统底座、按业务域组织的供应链管理平台，覆盖客户与商品、销售订单、采购收货、库存、分拣、配送、财务和报表，并提供配套的微信小程序与 Windows 秤端工作台。

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

小程序已覆盖手机号密码登录、首次改密、商品目录、购物车、收货地址、结算下单、订单中心、履约售后、钱包与账期；短信登录、微信登录、语音下单、相册上传、订阅消息和微信支付仍待对应后端或平台能力。接口契约与联调说明见 [小程序 README](xsy-scm-miniprogram/README.md)。

### Windows 秤端

秤端的构建、隔离演练和现场模式见 [秤端 README](xsy-scale-agent/README.md)。平台设备接口为 `/scm/device/scale/v1`；真实电子秤、打印机和现场标定须单独验收。

## 验证

日常验证统一从仓库根目录运行：

```powershell
./tools/verify.ps1          # Windows
./tools/verify.sh           # Linux / macOS
python tools/verify.py all  # 直接调用，可按 quality/backend/frontend/miniprogram/e2e 分范围
```

入口说明与各范围职责见 [tools/README.md](tools/README.md)。验证结果保存在 `.runtime/verify/<timestamp>/summary.json` 与 `.runtime/playwright-result.json`；退出码 0 为通过，1 为失败，2 为未完成。

各模块也可单独执行：

```powershell
# 后端（需要可用的测试 PostgreSQL 与对应配置）
cd xsy-scm-server
mvn.cmd verify

# 管理后台
cd xsy-scm-web
npm run lint
npm run typecheck
npm run typecheck:e2e
npm test
npm run build
```

浏览器验证须先启动 PostgreSQL、Redis、后端与前端；`tools/verify.*` 只执行门禁，不管理服务生命周期。仓库未配置 GitHub Actions CI，需要验证时使用上述本地入口，并在交付记录中写明实际执行范围与结果；未执行或因环境跳过的检查不能报告为通过。

## Docker 部署

Docker Compose 一次启动 Vue3/Nginx、Java 21 后端、PostgreSQL 18、Redis、MinIO，并运行桶策略初始化。该编排用于受限测试与试用，不构成生产对象存储基线。

```bash
git clone https://github.com/LuckilyLm/xsy-scm.git
cd xsy-scm
cp .env.example .env
# 按 .env 注释替换全部 CHANGE_ME 和镜像占位值
docker compose config
docker compose build
docker compose up -d
docker compose ps -a
```

- `.env` 已忽略；`docker compose config` 会展开密码，只在本机检查，不要分享输出。Windows 可用 `Copy-Item .env.example .env`。
- `MINIO_IMAGE` 与 `MINIO_MC_IMAGE` 必须替换为经过安全审查的固定镜像，示例占位值不能直接拉取。
- `MINIO_PUBLIC_ENDPOINT` 必须是浏览器和 backend 容器都能访问的地址（如 `https://files.example.com`），不带末尾斜线；不能用 `minio:9000`、`localhost` 或 `127.0.0.1`。S3 Client 与 Presigner 共用该地址。
- MinIO API 默认只绑定 `127.0.0.1`；直连时通过 `MINIO_BIND_ADDRESS` 指定内网网卡并用网络 ACL 限制来源，生产入口优先使用 HTTPS 反向代理或托管对象存储，不要默认绑定 `0.0.0.0`。
- 其余必填项、盘点快照签名密钥和邮件占位配置见 [.env.example](.env.example) 与 [deploy/README.md](deploy/README.md)。

| 服务 | 默认宿主机地址 | `.env` 配置 |
| --- | --- | --- |
| 后台入口 | `0.0.0.0:7515` | `WEB_BIND_ADDRESS` / `WEB_PORT` |
| 后端 | `127.0.0.1:1024` | `SERVER_BIND_ADDRESS` / `SERVER_PORT` |
| PostgreSQL | `127.0.0.1:15432` | `POSTGRES_BIND_ADDRESS` / `POSTGRES_PORT` |
| Redis | `127.0.0.1:6379` | `REDIS_BIND_ADDRESS` / `REDIS_PORT` |
| MinIO API | `127.0.0.1:9000` | `MINIO_BIND_ADDRESS` / `MINIO_PORT` |
| MinIO Console | `127.0.0.1:9001` | `MINIO_CONSOLE_BIND_ADDRESS` / `MINIO_CONSOLE_PORT` |

浏览器访问 `http://服务器IP:7515`（端口随 `WEB_PORT` 修改）。前端 API 固定走同源 `/api`，由 Nginx 转发到 `backend:1024`。上传入口限制 25 MB，后端单文件限制 20 MB。数据库、Redis、MinIO API 和 Console 默认仅绑定本机，Console 可通过 SSH 隧道访问。

首次空库由 Flyway 自动创建 `xsy_v2`，并按顺序应用 `xsy-scm-server/sa-admin/src/main/resources/db/migration` 中的全部迁移；不导入手工业务建表 SQL、不自动 baseline、不修改旧 migration。

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM xsy_v2.flyway_schema_history ORDER BY installed_rank;"'
docker compose logs -f backend
docker compose logs -f web
```

初始管理员来自 V3 种子数据（账号 `admin`，初始口令见该迁移的种子注释）。首次登录后立即修改管理员密码及系统配置中的万能密码；F0-DEBT-01 的业务附件权限限制仍然适用。

升级前备份 PostgreSQL 与 MinIO 数据，再执行：

```bash
git pull --ff-only
docker compose build
docker compose up -d
docker compose ps -a
```

PostgreSQL、Redis AOF、MinIO 和后端日志使用 named volume。普通 `docker compose down` 保留数据，**升级禁止使用 `docker compose down -v`**；保持同一 Compose project name，已有卷的数据库密码不会因修改 `.env` 自动重置。

部署后检查登录、各业务页面和文件访问：`public/*` 图片原始 URL 应为 200，私有原始 URL 应为 403，系统生成的签名 URL 应可访问且域名、端口与 `MINIO_PUBLIC_ENDPOINT` 一致。商品图片当前使用 `folder=1`（`private/common/`），新上传商品图仍走签名 URL；本次部署配置不会把商品上传改为公开目录。

## 文档入口

- [项目文档入口](docs/README.md)：状态、决策、需求与文档维护规则
- [项目状态](docs/status.md)：代码现状、验证证据、风险与待完成工作
- [有效决策](docs/decisions.md)：业务、架构与权限不变量
- [下一阶段开发计划](docs/next-development-plan.md)：MVP 主链验收与基础缺口
- [需求基线](docs/requirements/产品功能需求基线.md) 与 [负责人确认口径](docs/requirements/2026-09-09-负责人确认口径.md)
- [业务身份与追溯架构](docs/architecture/business-identity-and-traceability.md)
- [地图部署说明](docs/map-deployment.md)
- [工具入口](tools/README.md)

## 协作约定

- 协作与 Git 约束见 [AGENTS.md](AGENTS.md)，代码评审与界面规范见 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 保持用户开始时的分支；未经明确要求不切分支、不推送、不发布。
- 提交只暂存本任务修改的路径，不使用 `git add -A`。
- 已应用的 Flyway migration 不得修改；新增数据库变更使用新版本 migration。
- 当前状态只写 `docs/status.md`，当前规则只写 `docs/decisions.md`；完成计划和一次性审计从工作区删除，由 Git 历史追溯。

## 许可证

本项目以 [MIT License](LICENSE) 发布。SmartAdmin 等第三方组件的来源与许可说明见 [NOTICE.md](NOTICE.md)。
