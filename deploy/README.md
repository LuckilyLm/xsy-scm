# 部署与升级

更新：2026-11-05，Codex。命令在仓库根目录执行；开发启动见 [根 README](../README.md)。

## Docker Compose


Docker Compose 一次启动 Vue3/Nginx、Java 21 后端、PostgreSQL 18、Redis、MinIO，并运行桶策略初始化。该协调用于受限测试与试用，不构成生产对象存储基线。

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
- 其余必填项、盘点时保存的数据签名密钥和邮件占位配置见 [.env.example](../.env.example) 与 下文配置表。

| 服务 | 默认宿主机地址 | `.env` 配置 |
| --- | --- | --- |
| 后台入口 | `0.0.0.0:7515` | `WEB_BIND_ADDRESS` / `WEB_PORT` |
| 后端 | `127.0.0.1:1024` | `SERVER_BIND_ADDRESS` / `SERVER_PORT` |
| PostgreSQL | `127.0.0.1:15432` | `POSTGRES_BIND_ADDRESS` / `POSTGRES_PORT` |
| Redis | `127.0.0.1:6379` | `REDIS_BIND_ADDRESS` / `REDIS_PORT` |
| MinIO API | `127.0.0.1:9000` | `MINIO_BIND_ADDRESS` / `MINIO_PORT` |
| MinIO Console | `127.0.0.1:9001` | `MINIO_CONSOLE_BIND_ADDRESS` / `MINIO_CONSOLE_PORT` |

浏览器访问 `http://服务器IP:7515`（端口随 `WEB_PORT` 修改）。前端 API 固定走同源 `/api`，由 Nginx 转发到 `backend:1024`。上传入口限制 25 MB，后端单文件限制 20 MB。数据库、Redis、MinIO API 和 Console 默认仅绑定本机，Console 可通过 SSH 隧道访问。

数据库名与schema统一为`xsy`。首次空库由Flyway创建schema，顺序执行`db/migration/modules`中24份模块SQL和后续增量。已有数据按下节导入新库，保持baseline-on-migrate关闭，不自动接管旧历史。

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM xsy.flyway_schema_history ORDER BY installed_rank;"'
docker compose logs -f backend
docker compose logs -f web
```

查询迁移历史时连接的数据库必须与`XSY_DB_URL`一致；如果数据库容器的`POSTGRES_DB`只是初始管理库，替换上例`-d`参数为实际业务库名，不能从管理库缺表推断应用没有迁移。

初始管理员由system模块默认数据写入（账号`admin`；初始口令来源可从Git中的原V3种子说明追溯）。首次登录后立即修改管理员密码及系统配置中的万能密码。

### 模块初始化与数据导入

正式初始化按24个模块组织，后续差异追加增量。当前初始化链用于新的`xsy`库；旧库的版本记录不能直接复制进来，也不能通过修改旧历史的checksum接管。新库投入使用后，已应用SQL保持字节稳定。模块清单见`tools/install_baseline_manifest.json`。

| 数据库状态 | 路线 |
| --- | --- |
| 新的空库 | Flyway执行模块初始化与增量 |
| 需要保留原资料 | 备份原库 → 新建`xsy` → 初始化 → 导入表数据与序列 → 核对后切换 |
| 已投入使用的`xsy`库 | 正常校验与追加增量，不重复初始化或替换历史 |
| 结构或数据校验不一致 | 保留原库和备份，修正差异后再切换 |

发布前的静态预检：

```bash
python tools/migration_checksum_guard.py check
python tools/build_install_baseline.py check
```

导入应在维护窗口执行：

1. 停止后端和其他业务写入，用`pg_dump --format=custom`完整备份原库，并确认备份可读取。备份保存在项目目录之外。
2. 创建`xsy`数据库，按正式初始化链建表；导入前应已执行V1001，使用当前后端业务校验对应的结构。
3. 清空新库业务种子，按列复制原业务表，映射源schema到`xsy`。保留主键、口令、角色授权、金额、数量和序列值；不导入`flyway_schema_history`及其备份表，不对业务文本作全局替换。
4. 逐表比较行数与完整行摘要，逐项比较序列位置；补齐当前系统任务类名及权限目录。V1003按现有采购新增权补提交权，不覆盖原授权，编号由数据库分配；落后序列只前移。
5. 完成Flyway校验，将`POSTGRES_DB`与`XSY_DB_URL`指向新库，启动系统，核对账号登录、菜单和主要业务资料。
6. 新库验证通过、备份可恢复后，再删除原库。新库有误时停止切换，不删除原库或覆盖备份。

当前库的结构和历史可只读核对，`SCM_MIGRATION_DSN`应指向`xsy`，口令通过`PGPASSWORD`或pgpass提供：

```bash
python -m pip install -r tools/requirements-baseline.txt
python tools/cutover_schema_baseline.py
```

结构核对包含列、类型、默认值、identity、约束、索引、函数、触发器及序列定义；业务数据和序列当前位置还须按导入前后的独立记录核对。未知、失败、缺失或checksum不匹配的版本均拒绝通过，不关闭校验或使用repair掩盖差异。

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
| `SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET` | `scm.inventory.stocktake.snapshot.secret` | 盘点表导入凭证的 HMAC-SHA256 签名密钥（`/scm/inventory/stocktake/import/template` 签发的盘点时保存的数据） | 应用启动期直接失败：`StocktakeSnapshotSigner` 在 `pre` / `prod` profile 下拒绝空值，也拒绝沿用代码仓库内公开可知的开发默认值 |

取值要求：高熵随机串（建议不少于 32 字节随机值），`pre` 与 `prod` 使用不同值，各环境独立保管。
轮换该值只会让**尚未提交**的资料校验凭证失效（凭证有效期以分钟计），不影响已入库的盘点单。

之所以按环境拒绝而不是靠注释提醒：静默退回公开密钥会让「保存的数据与当前账面一致」这一校验变成可伪造的断言，
盘点差异写库前的最后一道防线随之失效。

### 开发 / 测试

`dev` 与 `test` 配置显式写入仓库内的开发默认密钥，便于本地与自动化测试直接跑；
该默认值出现在 `pre` / `prod` 时按上表启动失败，不会降级运行。
