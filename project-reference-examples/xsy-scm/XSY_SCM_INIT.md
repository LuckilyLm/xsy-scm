# XSY-SCM 初始化记录

## 1. 环境准备

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| JDK 17 | 新增安装 | `Microsoft.OpenJDK.17` -> `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`（原机器只有 JDK 1.8） |
| Maven 3.9.9 | 新增安装 | `%LOCALAPPDATA%\Programs\apache-maven-3.9.9`（原机器无 Maven） |
| Node | 已有 | v24.14.0 / npm 11.9.0 |
| MySQL | 已有 | 8.4.8 本地服务，root 空密码 |
| Redis | 新增 | Docker 容器 `xsy-redis`（redis:7-alpine，6379） |
| Git | 已有 | 2.53.0 |

> 说明：Maven 与 JDK 目录已加入用户 PATH；首次用 `setx` 追加 PATH 时被 Windows 截断到 1024 字符，已重建用户 PATH（保留原条目中系统 PATH 未覆盖的部分 + Maven），详见「遗留事项」。

## 2. 基线获取

- 来源：`https://gitee.com/lab1024/smart-admin.git`（远程重命名为 `upstream`）
- 版本：**v3.30.0**（Tag，commit `6867198b`，2026-03-01）
- 分支：
  - `baseline`：官方基线
  - `develop`：二开主分支（当前分支）
  - Tag：`baseline-smartadmin-v3.30.0`

## 3. 目录整理

采用「单仓库 + 三目录 + 一个业务库」形态：

```
xsy-scm/
├── xsy-scm-server/   # 后端（xsy-scm-base + xsy-scm-server）
├── xsy-scm-web/           # PC 管理后台（Vue3 + TS）
├── xsy-app/                            # 移动端（UniApp Vue3）
├── 数据库SQL脚本/                         # 官方 SQL（基线来源，保持原样）
├── docs/ deploy/                         # 项目文档与部署
└── AGENTS.md / README.md                 # 项目说明与 AI 协作约定
```

已移除（可从 `git checkout v3.30.0 -- <目录>` 恢复）：

- `smart-admin-api-java8-springboot2/`（规程禁止使用 Java8 版本）
- `smart-admin-web-javascript/`（规程要求使用 TypeScript 版本）

## 4. 数据库

- 数据库：`supply_chain`（utf8mb4 / utf8mb4_unicode_ci），共 44 张表
- 脚本来源：官方 `数据库SQL脚本/mysql/smart_admin_v3.sql`（v3.30.0 全量，已含 v3.30.0 增量内容，无需再执行 sql-update-log）
- 落地脚本：`deploy/sql/xsy_scm_v3.30.0_supply_chain.sql`
- **官方脚本缺陷修复**：`t_dict_data` 建表语句中 `data_style` 列后缺少逗号，导致 MySQL 8 报 1064 语法错误；已最小化补逗号（仅此一处）
- 账号：官方默认账号 `admin`，官方 Demo 标注密码为 `123456`，但基线库中的 argon2 哈希与 `123456` 不匹配（后端规则为 argon2(`密码_UID大写_UID小写`)），已将 admin 密码重置为 `123456`

## 5. 配置调整（仅本地 dev 环境）

`xsy-scm-server/xsy-scm-base/src/main/resources/dev/sa-base.yaml`

- datasource.url 数据库名 `smart_admin_v3` -> `supply_chain`
- datasource.password `SmartAdmin666` -> 空（本机 root 无密码）
- file.storage.local.upload-path `/home/smart_admin_v3/upload/` -> `C:/Users/chenk/xsy-scm/upload/`

`xsy-scm-server/xsy-scm-server/src/main/resources/dev/application.yaml`

- 新增 `localPath: C:/Users/chenk/xsy-scm`（日志与本地目录落到 Windows 可用路径）

前端 `.env.development` 中 `VITE_APP_API_URL` 与后端端口一致（127.0.0.1:1024），未作修改。

## 6. 源码修复

`xsy-scm-web/src/views/support/code-generator/components/preview/code-generator-preview-modal.vue`

- `import hljs from 'highlight'` -> `'highlight.js'`
- 原因：`highlight` 包未安装且与同文件其它 `highlight.js/...` 引用不一致，导致 vite dev 启动时依赖解析失败

## 7. 启动方式

```bash
# 后端（JDK17）
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
cd xsy-scm-server
mvn -P dev clean compile
java -jar xsy-scm-server/target/xsy-scm-server-dev-3.0.0.jar     # 端口 1024

# PC 管理后台（端口 8081）
cd xsy-scm-web && npm install && npm run dev

# xsy-app H5（端口 5173）
cd xsy-app && npm install && npm run dev:h5
```

## 8. 验证结果

| 验证项 | 结果 |
| --- | --- |
| 后端 `mvn -P dev clean compile` | BUILD SUCCESS |
| 后端 `mvn -P dev package` | BUILD SUCCESS，产物 `xsy-scm-server-dev-3.0.0.jar` |
| 后端启动 | 成功，监听 1024，SmartJob 正常执行 |
| 验证码接口 `/login/getCaptcha` | ok=true |
| 登录 `/login`（admin/123456，SM4 加密，loginDevice=1） | ok=true，返回 token |
| 菜单 `/menu/query` | ok=true，136 条 |
| 菜单树 `/menu/tree` | ok=true，7 个一级菜单 |
| 权限 URL `/menu/auth/url` | ok=true，196 条 |
| 员工 `/employee/queryAll` | ok=true，11 条 |
| PC 端 `npm install` | 成功 |
| PC 端 `npm run dev` | 启动成功 http://localhost:8081 |
| xsy-app `npm install` | 成功 |
| xsy-app `npm run dev:h5` | 启动成功 http://localhost:5173/index.html |
| PC 端 `npm run build:prod` | 成功（59.43s，仅 chunk 体积告警，产物 `xsy-scm-web/dist`） |

## 9. 遗留事项

1. 用户 PATH 曾被 `setx` 截断，已重建；如发现个别命令找不到，请检查用户 PATH
2. Redis 为 Docker 容器 `xsy-redis`，未配置持久化与密码，仅本地开发使用
3. 未配置公司远程 origin（无仓库地址），仅保留官方 `upstream`
4. 业务模块尚未开始，待需求确认后按 `docs/` 流程推进
