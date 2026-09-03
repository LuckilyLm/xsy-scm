# 鲜蔬源智慧供应链管理平台

当前分支实现 Sprint 1 商品档案纵向切片：PostgreSQL、Spring Boot REST API 与 React 管理页贯通，商品采用 SPU 聚合、SKU 交易单元模型。

## Sprint 1 范围

- 三级商品分类
- SPU 商品档案分页、筛选、上下架和软删除
- 一个 SPU 下多个 SKU，且必须有且仅有一个默认 SKU
- 编辑时保留已有 SKU 的数据库 ID，不删除重建保留规格
- PostgreSQL Flyway 迁移，无数据库外键
- MyBatis-Plus 内置 CRUD 与 XML 自定义 SQL
- React 双侧栏商品列表、SKU 展开明细和新增/编辑面板

本阶段不包含登录、RBAC、供应商/仓库页面、客户价格、订单、采购、库存、设备接入、Docker Compose 或 CI。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- PostgreSQL 18（其他受 Flyway 支持的 PostgreSQL 版本也可）

后端只从以下环境变量读取数据库连接信息：

```text
XSY_DB_URL
XSY_DB_USERNAME
XSY_DB_PASSWORD
```

测试数据库使用 `XSY_TEST_DB_URL`。请在本机或密钥管理系统中配置真实值，不要提交密码或 `.env` 文件。

## 启动后端

在 PowerShell 中选择 Java 21，并从 `xsy-scm-server` 启动：

```powershell
$env:JAVA_HOME='D:\Java\JDK21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn.cmd spring-boot:run
```

首次启动会由 Flyway 自动创建 Sprint 1 的六张表并写入演示商品。默认 API 地址为 `http://127.0.0.1:8080/api`。

## 启动前端

```powershell
cd xsy-scm-web
npm install
npm run dev
```

Vite 开发服务器将 `/api` 代理到本机 `8080` 端口。商品页路径为 `/products`。

## 验证

后端：

```powershell
cd xsy-scm-server
mvn.cmd clean verify
```

前端：

```powershell
cd xsy-scm-web
npm run lint
npm run typecheck
npm test
npm run build
```

首次运行浏览器验收前安装项目锁定版本的 Chromium：

```powershell
npx playwright install chromium
npm run e2e -- product-flow.spec.ts
```

浏览器验收会创建带唯一编码的双 SKU 商品，验证保留 SKU ID、增删规格、上下架和软删除，并在结束时清理验收数据。
