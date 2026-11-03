# 部署与升级

更新：2026-11-03，Codex。命令在仓库根目录执行；开发启动见 [根 README](../README.md)。

## Docker Compose


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
- 本机试用若 MinIO 走 HTTP，`.env` 的 `MAVEN_PROFILE` 必须改为 `test`（或 `dev`）；`prod` profile 会强制对象存储的公开地址与签名地址都是 HTTPS，HTTP 端点会导致后端拒绝启动。
- MinIO API 默认只绑定 `127.0.0.1`；直连时通过 `MINIO_BIND_ADDRESS` 指定内网网卡并用网络 ACL 限制来源，生产入口优先使用 HTTPS 反向代理或托管对象存储，不要默认绑定 `0.0.0.0`。
- 其余必填项、盘点快照签名密钥和邮件占位配置见 [.env.example](../.env.example) 与 下文配置表。

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

初始管理员来自 V3 种子数据（账号 `admin`，初始口令见该迁移的种子注释）。首次登录后立即修改管理员密码及系统配置中的万能密码。

升级前备份 PostgreSQL 与 MinIO 数据，再执行：

```bash
git pull --ff-only
docker compose build
docker compose up -d
docker compose ps -a
```

PostgreSQL、Redis AOF、MinIO 和后端日志使用 named volume。普通 `docker compose down` 保留数据，**升级禁止使用 `docker compose down -v`**；保持同一 Compose project name，已有卷的数据库密码不会因修改 `.env` 自动重置。

部署后检查登录、业务页面与文件访问：新上传文件统一使用 `public/`，商品图片只绑定 `public/image/`；公开原始 URL 应可匿名读取。存量 `private/` 对象继续受授权与签名访问控制，不批量搬运。报告撤回或过期可阻止报告接口返回附件，但不能撤销已经公开的对象 URL。策略详见 [有效决策](../docs/decisions.md)。


## 环境变量与启动约束

### 生产 / 预发必填

| 环境变量 | 配置键 | 用途 | 缺失后果 |
| --- | --- | --- | --- |
| `SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET` | `scm.inventory.stocktake.snapshot.secret` | 盘点快照导入凭证的 HMAC-SHA256 签名密钥（`/scm/inventory/stocktake/import/template` 签发的实盘快照） | 应用启动期直接失败：`StocktakeSnapshotSigner` 在 `pre` / `prod` profile 下拒绝空值，也拒绝沿用代码仓库内公开可知的开发默认值 |

取值要求：高熵随机串（建议不少于 32 字节随机值），`pre` 与 `prod` 使用不同值，各环境独立保管。
轮换该值只会让**尚未提交**的快照凭证失效（凭证有效期以分钟计），不影响已入库的盘点单。

之所以按环境拒绝而不是靠注释提醒：静默退回公开密钥会让「快照与当前账面一致」这一校验变成可伪造的断言，
盘点差异写库前的最后一道防线随之失效。

### 开发 / 测试

`dev` 与 `test` 配置显式写入仓库内的开发默认密钥，便于本地与自动化测试直接跑；
该默认值出现在 `pre` / `prod` 时按上表启动失败，不会降级运行。
