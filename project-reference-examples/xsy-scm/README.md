# 供应链管理系统（xsy-scm）

基于 **SmartAdmin v3.30.0（Java17 + Spring Boot 3）** 二次开发的供应链业务系统（本项目 XSY-SCM），包含 PC 管理后台（Vue3 + TypeScript）与移动端 xsy-app（UniApp Vue3）。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5.4、MyBatis-Plus 3.5.12、Sa-Token 1.44.0、Maven、MySQL 8、Redis |
| PC 管理后台 | Vue 3.4、TypeScript 5.6、Vite 5、Pinia、Ant Design Vue 4 |
| 移动端 | UniApp（Vue 3）、Uni UI，目标：H5 / 微信小程序 / Android / iOS |
| 数据库 | MySQL 8（本地库名 `supply_chain`） |

## 目录结构

```
xsy-scm/
├── xsy-scm-server/   # 后端（xsy-scm-base + xsy-scm-server）
├── xsy-scm-web/           # PC 管理后台（Vue3 + TS）
├── xsy-app/                            # 移动端（UniApp Vue3）
├── 数据库SQL脚本/                         # SmartAdmin 官方 SQL（基线来源）
├── docs/                                 # 需求 / 数据库 / API / 架构文档
├── deploy/                               # docker / nginx / sql
└── XSY_SCM_INIT.md                    # 初始化记录
```

## 本地环境要求

- JDK 17（本机：`C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`，环境变量 `JAVA17_HOME`）
- Maven 3.9+（本机：`%LOCALAPPDATA%\Programs\apache-maven-3.9.9`）
- Node >= 18（本机 v24）
- MySQL 8（本机 8.4.8，root 空密码）
- Redis（本机通过 Docker 容器 `xsy-redis` 提供，端口 6379；可选）

## 快速启动

### 1. 后端

```bash
# 必须使用 JDK17
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
cd xsy-scm-server
mvn -P dev clean compile                 # 编译
mvn -P dev -pl xsy-scm-server -am spring-boot:run   # 启动（端口 1024）
```

启动后检查：http://127.0.0.1:1024/doc.html （Knife4j 接口文档）

### 2. PC 管理后台

```bash
cd xsy-scm-web
npm install
npm run dev
```

### 3. xsy-app（H5）

```bash
cd xsy-app
npm install
npm run dev:h5
```

## 数据库

- 数据库：`supply_chain`（utf8mb4 / utf8mb4_unicode_ci）
- 初始化脚本：`deploy/sql/xsy_scm_v3.30.0_supply_chain.sql`
  （来源：官方 `数据库SQL脚本/mysql/smart_admin_v3.sql`，仅替换库名并修复 `t_dict_data.data_style` 缺逗号的语法问题）
- 业务表后续以独立业务表形式新增，**不修改 SmartAdmin 官方系统表结构承载业务数据**

## Git 分支约定

- `baseline`：SmartAdmin 官方基线（Tag `baseline-smartadmin-v3.30.0`）
- `develop`：二开主分支
- `upstream`：SmartAdmin 官方仓库（只读参照）
- 业务分支：`feature/product`、`feature/customer`、`feature/order` ...
