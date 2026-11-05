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

首次空库由Flyway自动创建`xsy_v2`，顺序执行`db/migration/modules`中24份模块SQL（V1000.01～V1000.24），直接创建截至原V244的最终结构与系统默认数据。已有旧V历史的库必须先按下节核对并切换基线，再启动新版本；保持baseline-on-migrate关闭，不自动接管非空库。

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM xsy_v2.flyway_schema_history ORDER BY installed_rank;"'
docker compose logs -f backend
docker compose logs -f web
```

查询迁移历史时连接的数据库必须与`XSY_V2_DB_URL`一致；如果数据库容器的`POSTGRES_DB`只是初始管理库，替换上例`-d`参数为实际业务库名，不能从管理库缺表推断应用没有迁移。

初始管理员由system模块默认数据写入（账号`admin`；初始口令来源可从Git中的原V3种子说明追溯）。首次登录后立即修改管理员密码及系统配置中的万能密码。

### 模块初始化与旧库基线切换

正式资源只保留24份模块SQL，一模块一文件；每份直接定义当前表、字段、约束、索引、函数、触发器和默认数据，没有历次增删字段、重命名及回填。历史迁移SQL已退出正式资源；已提交原文由Git追溯，未发布改动直接并入所属模块。运行仅扫描modules子目录，打包排除旧平铺SQL与历史目录，防止增量构建残留旧资源进入制品。模块清单与完整性校验位于`tools/install_baseline_manifest.json`；旧历史仅保留文件名/checksum元数据，不再打包旧SQL。

| 数据库状态 | 路线 |
| --- | --- |
| 空库 | Flyway顺序执行24份V1000模块SQL |
| 原V243或V244完整历史 | 只读核对历史与最终结构 → 备份、停止业务写入 → 显式切换 → 启动新版本 |
| 已完成模块初始化或本工具切换 | 继续正常Flyway校验和后续增量，不重复初始化 |
| 更早、分叉、失败或没有历史的非空库 | 拒绝自动接管，先按对应旧版本完成升级或单独核对 |

发布前的静态预检：

```bash
python tools/migration_checksum_guard.py check
python tools/build_install_baseline.py check
```

旧库切换工具默认只读，连接串通过`SCM_MIGRATION_DSN`提供（libpq连接格式，例如`host=数据库地址 port=5432 dbname=业务库名 user=维护账号`；口令使用`PGPASSWORD`或pgpass）。该连接应与应用`XSY_V2_DB_URL`指向同一个业务库。工具需有查询目录、锁表、修改历史表及指定内置模板的权限。

```bash
python -m pip install -r tools/requirements-baseline.txt
python tools/cutover_schema_baseline.py
```

检查通过后进入维护窗口：停止旧backend与其他业务写入，完成目标数据库备份，然后显式切换。下例的`业务库名`必须替换为实际库名，备份文件须属于本次目标库。

```bash
docker compose stop backend
pg_dump --dbname="$SCM_MIGRATION_DSN" --format=custom --file=backup-before-schema-cutover.dump
python tools/cutover_schema_baseline.py --apply --expected-database 业务库名 --backup backup-before-schema-cutover.dump
```

工具重新检查完整历史和最终结构对象签名，差异即拒绝；短暂锁表以防切换时并发写入或DDL。单事务内仅补齐指定内置送货模板的下单数量，将原历史表重命名为`flyway_schema_history_legacy`原样保留，并在新历史表登记`1000.24`切换基线。不清空业务表、不重置序列、不覆盖客户自建模板。失败自动回滚；再次运行已完成切换的库只核对，不重复登记。

结构核对包含当前列、类型、默认值、identity、约束、索引、函数、触发器、序列定义及说明，忽略业务行、序列当前位置、owner与授权。PostgreSQL可能将无长度varchar常量数组转text[]表示为整体转换或逐项转换，签名只归一这两种等价形式，保留具体值、顺序与类型长度。自建表/索引或实际结构不同会阻止切换，不能通过关闭校验、改checksum或repair绕过。工具不替代备份恢复；完成切换后回滚旧镜像仍需恢复对应备份与原迁移历史。

切换完成后再构建并启动新版本。后续上线结构差异追加V1001及更高版本；已经使用的模块初始化SQL不能修改。再次整理基线需另行明确版本边界与旧库切换，不能靠修改CREATE TABLE自动升级现有表。

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
