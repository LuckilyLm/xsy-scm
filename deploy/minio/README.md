# F0 MinIO 本地开发 / 集成环境

这是 F0 的 S3-compatible 验证目标，不代表生产存储选型。复用现有 `dev` profile，
不新增 Maven/resources profile。Local 开发默认仍为 `local`。

Canonical server 从 [官方固定源码 tag](https://github.com/minio/minio/tree/RELEASE.2025-10-15T17-29-55Z)
构建为 `xsy-scm/minio:RELEASE.2025-10-15T17-29-55Z`。
构建遵循该 tag 的 [Go 版本](https://github.com/minio/minio/blob/RELEASE.2025-10-15T17-29-55Z/go.mod)
及 [链接参数生成器](https://github.com/minio/minio/blob/RELEASE.2025-10-15T17-29-55Z/buildscripts/gen-ldflags.go)。
mc 固定 `quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z`。不使用 latest、第三方 MinIO 镜像或旧 server release。
（2026-09-17 验收修正：Docker Hub 的 `minio/mc`、`minio/minio` 仓库已移除并返回 HTTP 404，
改用 MinIO 官方 registry `quay.io`；固定 tag 与镜像摘要 `sha256:a7fe349e…f11727` 均未变化。）
镜像内保留 LICENSE 和 SOURCE_COMMIT，便于核对构建来源。

## 启动（仓库根目录 PowerShell）

```powershell
# 仅首次复制；不要覆盖已有 .env。
if (-not (Test-Path deploy/minio/.env)) { Copy-Item deploy/minio/.env.example deploy/minio/.env }
docker compose --env-file deploy/minio/.env -f deploy/minio/compose.yaml up -d --build
docker compose --env-file deploy/minio/.env -f deploy/minio/compose.yaml logs bootstrap
. ./deploy/minio/Import-FileStorageEnv.ps1
# 保留既有数据库/Redis 环境变量，在当前进程中构建/启动 dev 后端。
```

`.env.example` 的账号密码是公开的、仅限本地开发的样例。`.env` 必须保持 gitignored；
compose 使用必填插值，没有隐式生产凭据。pre/prod 不允许复用开发样例。
Compose 的 .env 只负责容器插值，不会自动传给宿主机 Java；须执行导入脚本。
不要把含凭据的 `docker compose config` 输出或 presigned URL 贴进报告。

首次构建需要 Docker Linux engine、官方镜像仓库、GitHub 和 Go modules 网络访问。
named volume 持久保存对象；`down` 保留数据，不使用 `down -v` 作为例行操作。
bootstrap 必须以退出码 0 完成才可开始集成验收。

## 访问与策略

- API 9000；Console 9001；默认仅绑定本机。
- `XSY_FILE_BUCKET=xsy-scm-dev`；一个环境一个 bucket，bootstrap 不创建 UAT/prod bucket。
- anonymous policy 只允许 `public/*` 的 GetObject，不允许列桶、写、删除及 `private/*` 读取。
- Notice/HelpDoc 是登录员工可读的私密资源；必须经应用门禁取签名 URL，桶端仍拒绝匿名原始 URL。
- 手机/其他电脑访问时，将绑定地址改为适当 LAN 地址，并同时设置可达的
  `XSY_FILE_ENDPOINT` 和 `XSY_FILE_PUBLIC_URL_PREFIX`（LAN IP、hostname 或 domain）。
  私有签名绑定 endpoint，不能只改 public prefix。
- `XSY_FILE_PATH_STYLE=true`，`XSY_FILE_SEND_OBJECT_ACL=false`，默认私有 URL 有效 600 秒。
  private TTL 1～5 秒不缓存，避免非正 Redis TTL 变成永久缓存。

## 外部验收入口

当前实现按用户要求未运行最终构建、MinIO 集成与回归；不是 PASS 证据。
当前阶段状态见 [项目进度](../../docs/progress.md)；对象存储配置以本目录和代码为准。
回到 local 时在启动后端的进程设置 `XSY_FILE_STORAGE_MODE=local` 并重启；
cloud 对象不会自动搬到本地，已有引用须先确认可用性。
