# 鲜蔬源智慧供应链管理平台

鲜蔬源智链（`xsy-scm`）V2，以 SmartAdmin v3.31 为系统底座，供应链业务采用模块化单体架构。

## 当前范围

截至 2026-09-18，W0–W5、W5.5 与 F0 已完成对应阶段交付。W6-1 已完成后端与浏览器验证，覆盖库存余额、不可变流水、`DIRECT` / `WAREHOUSE_CONFIRM` 双入库模式、仓库启停和历史收货回填。W6-2 小程序未启动，仍需裁决遗留项后再开工。详见 [项目进度](docs/progress.md)。

业务需求和旧系统语义以 [参考项目目录](project-reference-examples/xsy-scm/) 为主，当前边界见 [项目决策](docs/decisions.md)。

## 工程目录

| 目录 | 用途 |
| --- | --- |
| `xsy-scm-server/` | 正式后端：Java 21、Spring Boot、MyBatis-Plus、Sa-Token、Redis、PostgreSQL、Flyway |
| `xsy-scm-web/` | 正式后台：Vue3、TypeScript、Ant Design Vue、Vite、Pinia |
| `docs/` | 当前进度和少量项目决策 |
| `tools/` | 本地工具，已排除 Git 跟踪（克隆后不会自动获得） |
| `deploy/minio/` | F0 本地对象存储与集成验证环境 |
| `deploy/postgres/` | 本地 PostgreSQL 容器配置与操作说明 |
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

浏览器验证须先启动 PostgreSQL、Redis、后端 18080 与前端 18081；`verify.ps1` / `verify.sh` 只执行门禁，不管理服务生命周期。完整入口从仓库根目录运行 `./verify.ps1`，结果保存在 `.runtime/verify/<timestamp>/summary.json` 与 `.runtime/playwright-result.json`。没有执行或因环境跳过的检查不能报告为通过。

## Docker 第一版部署

需要 Docker Engine / Docker Desktop（Linux 容器）与 Docker Compose v2 或以上。首次构建需要访问 Maven、npm、Docker Hub 和 quay.io。根 Compose 一次启动 Vue3/Nginx、Java 21 后端、PostgreSQL 18、Redis、MinIO，并运行桶策略初始化。Linux 服务器直接拉取固定 MinIO 镜像，不需要本机已有镜像或 Go 编译工具链。

```bash
git clone https://github.com/LuckilyLm/xsy-scm.git
cd xsy-scm
cp .env.example .env
vim .env
docker compose config
docker compose build
docker compose up -d
docker compose ps -a
```

把 `.env` 中所有 `CHANGE_ME` 换成独立密码。`.env` 已忽略，`docker compose config` 会展开密码，只在本机检查，不要分享其输出。Windows 可用 `Copy-Item .env.example .env`。

`MINIO_PUBLIC_ENDPOINT` 必须改为**浏览器和 backend 容器都能访问**的服务器地址，例如 `http://192.168.1.100:9000`，不要带末尾斜线。不能使用 `minio:9000`、`localhost` 或 `127.0.0.1`。S3 Client 与 Presigner 使用同一个 endpoint；公开图片前缀自动生成 `{MINIO_PUBLIC_ENDPOINT}/{MINIO_BUCKET}/`，私有文件的签名也使用这个地址。若改了 `MINIO_PORT`，同时改 endpoint 中的端口；域名或 HTTPS 入口需自行保证解析、证书和容器回连可用。

| 服务 | 默认宿主机地址 | `.env` 配置 |
| --- | --- | --- |
| 后台入口 | `0.0.0.0:7515` | `WEB_BIND_ADDRESS` / `WEB_PORT` |
| 后端 | `127.0.0.1:1024` | `SERVER_BIND_ADDRESS` / `SERVER_PORT` |
| PostgreSQL | `127.0.0.1:15432` | `POSTGRES_BIND_ADDRESS` / `POSTGRES_PORT` |
| Redis | `127.0.0.1:6379` | `REDIS_BIND_ADDRESS` / `REDIS_PORT` |
| MinIO API / 文件 | `0.0.0.0:9000` | `MINIO_BIND_ADDRESS` / `MINIO_PORT` |
| MinIO Console | `127.0.0.1:9001` | `MINIO_CONSOLE_BIND_ADDRESS` / `MINIO_CONSOLE_PORT` |

浏览器访问 `http://服务器IP:7515`（端口随 `WEB_PORT` 修改）。前端 API 固定走同源 `/api`，由 Nginx 转发到 `backend:1024`。上传入口限制为 25 MB，后端单文件限制为 20 MB。数据库和 Redis 默认仅绑定本机；Console 可通过 SSH 隧道访问。

MinIO 使用可直接拉取的 `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`，可通过 `MINIO_IMAGE` 指定其他经验证的固定镜像。这个历史官方版本未包含 [CVE-2025-62506 修复](https://github.com/minio/minio/security/advisories/GHSA-jjjj-jwhf-8rgr)，仅用于受限测试环境；不作为生产安全基线。生产应换成包含补丁的可信镜像或托管 S3，不能将隐藏 Console 当作漏洞修复。

桶策略脚本 `deploy/minio/bootstrap.sh` 纳入仓库。bootstrap 每次运行覆盖匿名策略，只允许 `public/*` 的 `s3:GetObject`；不开放整个桶、列桶或 `private/*`。`minio-bootstrap` 正常状态为 `Exited (0)`，其余五个服务应为 `healthy`。后端健康检查复用匿名只读登录配置接口并检查业务成功码。

首次空库由 Flyway 自动创建 `xsy_v2` 并应用 V1–V34，不导入手工业务建表 SQL、不自动 baseline、不修改旧 migration。检查迁移与日志：

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM xsy_v2.flyway_schema_history ORDER BY installed_rank;"'
docker compose logs -f backend
docker compose logs -f web
```

初始管理员来自 V3 的种子数据（账号 `admin`，初始口令见该迁移的种子注释）。首次登录后修改管理员密码及系统配置中的万能密码；现有 F0-DEBT-01 的业务附件权限限制仍适用，不能据此开放非管理员业务角色。

升级前备份 PostgreSQL 与 MinIO 数据，再执行：

```bash
git pull --ff-only
docker compose build
docker compose up -d
docker compose ps -a
```

PostgreSQL、Redis AOF、MinIO 和后端日志使用 named volume。普通 `docker compose down` 保留数据，**普通升级禁止使用 `docker compose down -v`**。保持同一 Compose project name，避免误用另一组卷；已有卷的数据库账号密码不会因为修改 `.env` 自动重置。

部署后检查登录与各业务页面，以及文件访问：`public/*` 图片原始 URL 应为 200，私有原始 URL 应为 403，系统生成的签名 URL 应可访问且域名/端口与 `MINIO_PUBLIC_ENDPOINT` 一致。

现有商品图片组件使用 `folder=1`（`private/common/`），因此新上传商品图目前仍走签名 URL。部署配置只启用 `public/*` 策略，不会把商品上传改为公开目录；“商品图片上传即公开”尚需另行调整业务上传分类，本次不改该行为。
