# 本地开发 / 集成测试 PostgreSQL（Docker Desktop）

xsy-scm V2 的本地数据库**统一用 Docker Desktop 的官方 postgres 镜像**提供。
本目录是它的声明式定义与操作手册（形态对齐 [`../minio/`](../minio/)）。

## 1. 为什么不用本机原生 PostgreSQL

| # | 原因 | 事实 |
| --- | --- | --- |
| 1 | 启停需要管理员权限 | 原生实例注册为 Windows 服务 `postgresql-x64-18`，`net start` 返回「系统错误 5 / 拒绝访问」，非提权会话无法启动 |
| 2 | 每次启动都要等 crash recovery | 原生实例上一次是异常中断，启动时对全库做 fsync 同步（实测分钟级），期间端口不监听 |
| 3 | 环境不可整体重建 | 数据目录 `D:\PostgreSQL\data` 是系统级资产；容器实例可用 `down -v` 完全重置 |
| 4 | 版本无法固定 | 依赖宿主安装版本；容器按镜像 tag 固定（当前 `postgres:18-alpine`） |

> 结论：**原生 PostgreSQL 服务不再用于本项目**。如需彻底避免端口争用，可将服务启动类型保持「手动 / 停止」。

## 2. 与 sa-base 配置的对应关系

`sa-base/src/main/resources/{test,dev}/sa-base.yaml` 的默认值与容器参数**逐字一致**，
因此本地启动与集成测试**不需要设置任何 `XSY_V2_*` 环境变量**：

```text
host:port   127.0.0.1:15432          ← POSTGRES_PORT
database    xsy_scm                  ← POSTGRES_DB
schema      xsy_v2                   ← Flyway `create-schemas: true` 自动创建
user        xsy_scm_app              ← POSTGRES_USER
password    （空）                    ← POSTGRES_HOST_AUTH_METHOD=trust
```

### 连接凭据

| 项 | 值 |
| --- | --- |
| 主机 | `127.0.0.1` |
| 端口 | `15432` |
| 数据库 | `xsy_scm` |
| schema | `xsy_v2` |
| 账号 | `xsy_scm_app` |
| 密码 | **空**（trust 认证，任意密码均可连接） |
| JDBC URL | `jdbc:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2` |
| 覆盖用环境变量 | `XSY_V2_DB_URL` / `XSY_V2_DB_USERNAME` / `XSY_V2_DB_PASSWORD` |

> 同一份凭据同步记录在仓库根目录的 `local-environment-info.txt`。

## 3. 启动 / 停止

```bash
cd deploy/postgres

# 启动（首次会自动初始化数据库集群）
docker compose up -d

# 查看状态（healthy 之后才可连接）
docker compose ps

# 停止（保留数据）
docker compose down

# 彻底重置（删数据卷，回到空库；下次启动 Flyway 会从 V1 全量重建）
docker compose down -v
```

启动前**必须**确认 15432 没有被占用（原生服务或旧容器）：

```bash
(echo > /dev/tcp/127.0.0.1/15432) 2>/dev/null && echo "15432 OPEN" || echo "15432 CLOSED"
```

## 4. 连接与常用命令

```bash
# 交互式 psql（容器内客户端，不依赖宿主是否装 psql）
docker exec -it xsy-v2-postgres psql -U xsy_scm_app -d xsy_scm

# 一次性查询
docker exec xsy-v2-postgres psql -U xsy_scm_app -d xsy_scm -c "SELECT version();"

# 看 Flyway 已应用的迁移
docker exec xsy-v2-postgres psql -U xsy_scm_app -d xsy_scm \
  -c "SELECT version, description, success FROM xsy_v2.flyway_schema_history ORDER BY installed_rank;"
```

## 5. 认证方式说明（trust）

容器以 `POSTGRES_HOST_AUTH_METHOD=trust` 初始化，配合 sa-base 默认的**空密码**，
目的是保持零配置可用。该取舍的前提是：

- 端口**只绑回环地址**（`127.0.0.1:15432`），不对局域网暴露；
- 仅用于本地开发与集成测试，**不得**用于任何共享/生产环境。

若需要口令认证：在 `.env` 中设置 `POSTGRES_PASSWORD`，并显式设置
`POSTGRES_HOST_AUTH_METHOD=scram-sha-256`，同步设置 `XSY_V2_DB_PASSWORD`。
仅删除认证方式变量会重新使用 compose 的 `trust` 默认值，不能开启口令认证。
这些初始化变量仅对空数据卷生效；已有数据卷须先备份，再由维护者调整角色密码与
`pg_hba.conf`。仅在确认数据可丢弃时才重建开发卷，不能把删除数据卷当作常规切换步骤。

## 6. 镜像与数据目录的两个坑

1. **PostgreSQL 18+ 必须挂载 `/var/lib/postgresql`**（不是 `/var/lib/postgresql/data`）。
   18 官方镜像改用 `pg_ctlcluster` 兼容的 major-version 子目录布局，挂错路径会在启动时报
   `there appears to be PostgreSQL data in ... (unused mount/volume)` 并**反复重启**。
   见 [docker-library/postgres#1259](https://github.com/docker-library/postgres/pull/1259)。
2. **`down -v` 才会删数据**。普通 `down` 只删容器，具名卷 `postgres-data` 保留。

## 7. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-09-18 | 新建。W6-1（库存域）开工前把本地库从原生 PostgreSQL 18.3 迁移到 `postgres:18-alpine` 容器；实测版本 PostgreSQL 18.6，`Asia/Shanghai`，回环端口 15432。 |
