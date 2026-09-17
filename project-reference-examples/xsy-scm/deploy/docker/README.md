# XSY-SCM 本地 Docker Desktop 部署（前端 + 后端）

用容器跑完整链路：**MySQL(本机) + Redis(Docker) + Spring Boot 后端 + Nginx 前端(管理后台 + H5)**。
Maven 编译和 Node 构建都在容器内完成，本机不需要装 JDK / Maven / Node。

## 一、目录结构

```
deploy/
├─ docker/
│  ├─ docker-compose.yml       主编排（backend + web）
│  ├─ docker-compose.db.yml    可选：容器版 MySQL / Redis
│  ├─ .env                     端口、数据库、Redis、镜像 tag 等参数
│  ├─ backend/Dockerfile       maven:3.9.9-temurin-17 构建 → temurin:17-jre 运行
│  ├─ web/Dockerfile           node:20 构建 admin + h5 → nginx:1.27 托管
│  ├─ scripts/deploy.sh        Linux / Git Bash 一键脚本
│  ├─ scripts/deploy.bat       Windows 双击脚本
│  └─ README.md
├─ nginx/nginx.conf            /admin、/h5 静态资源 + /api 反代（compose 挂载生效）
└─ sql/                        使用容器版 MySQL 时自动导入
```

## 二、快速开始

```bash
# Git Bash
cd deploy/docker/scripts
./deploy.sh up          # 构建 + 启动 + 等健康检查
```

Windows 资源管理器双击 `deploy/scripts/deploy.bat`，或命令行：

```bat
deploy\docker\scripts\deploy.bat up
```

访问地址：

| 服务 | 地址 |
| --- | --- |
| PC 管理后台 | http://localhost:8080/admin/ |
| 移动端 H5 | http://localhost:8080/h5/ |
| 后端接口 | http://localhost:1024 |

## 三、常用命令

| 命令 | 说明 |
| --- | --- |
| `deploy.sh build` | 只构建镜像（后端 `mvn -P dev package`、前端 `vite build` + `uni build`） |
| `deploy.sh up` | 构建并后台启动，等待健康检查 |
| `deploy.sh down` | 停止并移除容器（数据卷保留） |
| `deploy.sh restart` | 重启 |
| `deploy.sh logs` | 实时日志（`logs backend` 只看后端） |
| `deploy.sh status` | 容器状态与健康检查 |
| `deploy.sh clean` | 停止并删除本项目镜像 |
| `deploy.sh db` | 叠加启动容器版 MySQL / Redis |

> 脚本会自动兼容 `docker compose`（插件）与 `docker-compose`（独立二进制）。

## 四、配置说明（deploy/docker/.env）

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `TAG` | dev | 镜像标签 `xsy-scm-server:dev` / `xsy-scm-web:dev` |
| `MAVEN_PROFILE` | dev | 后端 Maven profile：dev / test / pre / prod |
| `SERVER_PORT` | 1024 | 后端宿主机映射端口 |
| `WEB_PORT` | 8088 | 前端 Nginx 宿主机映射端口（本机 8080 已被占用） |
| `MYSQL_PORT/DB/USER/PASSWORD` | 3306 / supply_chain / root / 空 | 宿主机 MySQL |
| `REDIS_PORT/DB/PASSWORD` | 6379 / 1 / 空 | 宿主机 6379（xsy-redis 容器） |
| `VITE_APP_API_URL` | /api | 前端接口前缀，Nginx 反代剥离后转发到后端 |
| `JAVA_OPTS` | -Xms512m -Xmx1024m | JVM 参数 |

容器内通过 `host.docker.internal` 访问宿主机端口（compose 已配置 `host-gateway`），
所以**复用本机 MySQL、已有 Redis 容器**是默认形态，现有数据不会丢。

> 前端镜像用 `--mode docker` 构建，读取 `xsy-scm-web/.env.docker` 与 `xsy-app/.env.docker`
> （仅用于定义 `VITE_APP_API_URL`，不影响现有 dev/prod 构建）。

## 五、切换环境

1. 后端：改 `.env` 的 `MAVEN_PROFILE=prod`，重新 `deploy.sh build backend && deploy.sh up`。
2. 前端：`VITE_APP_API_URL` 改成直连地址（如 `http://localhost:1024`）后重建前端镜像。
3. 若要用容器内的数据库：`deploy.sh db`，并把 `.env` 里 `MYSQL_HOST/REDIS_HOST` 指向容器名
   （需要同步调整 `docker-compose.yml` 中的 `host.docker.internal` 为 `mysql` / `redis`）。

## 六、常见问题

- **后端起不来**：`deploy.sh logs backend`，多半是 MySQL 账号密码或 `supply_chain` 库不存在，
  或宿主机防火墙拦截；确认 `host.docker.internal` 在容器内可解析。
- **页面 502**：后端未就绪，Nginx 已就绪；等后端 `healthy` 后刷新。
- **改了 nginx 配置**：`docker restart xsy-scm-web` 即可（配置是挂载的，无需重建镜像）。
- **改了前端代码**：`deploy.sh build web && deploy.sh up`。
- **构建慢**：第一次会拉基础镜像 + 下载 Maven/npm 依赖（约 8-10 分钟）。
  Maven 依赖单独成层，pom 不变时直接命中 Docker 层缓存，二次构建通常 1-2 分钟。
