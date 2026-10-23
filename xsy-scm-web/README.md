# 鲜蔬园智链管理后台（xsy-scm-web）

SCM 管理后台前端，基于 Vue 3、TypeScript、Ant Design Vue、Vite 和 Pinia，并在 SmartAdmin 基础上改造。

## 环境与启动

要求 Node.js 20+（`package.json` 声明 `>=18`）。首次安装依赖后启动开发服务：

```bash
cd xsy-scm-web
npm ci
npm run dev
```

开发服务默认端口为 `18081`，可用 `VITE_DEV_PORT` 覆盖。接口地址由 `VITE_APP_API_URL` 配置，开发环境默认直连 `http://127.0.0.1:18080`，不经过 Vite 代理。

## 目录

- `src/api`：接口定义与通用分页、响应模型
- `src/views`：页面，SCM 业务页面在 `views/business/scm`
- `src/components`：共享组件
- `src/theme`：SCM 主题、表格与抽屉等公共样式
- `test`：基于源码文本的契约单测（`node:test`）
- `e2e`：按业务模块合并的 Playwright 浏览器用例；`e2e-chains` 为跨模块黄金链

## 格式与质量

- 格式统一由 Prettier 维护：`npm run format` 写入，`npm run format:check` 只检查。配置见 `.prettierrc.cjs`，替换配置前先确认 `format:check` 全量通过。
- 提交前运行 `npm run lint`、`npm run typecheck`、`npm run typecheck:e2e`、`npm test`。`npm test` 使用 Node 内置测试运行器，不需要启动浏览器。
- `test/` 下的契约用例通过正则读取源码，允许空白与换行随格式化变化；断言针对业务结构，不要用精确空白锁死实现。

## 构建与 E2E

- 构建：`npm run build`（生产模式输出到 `dist-verify`），环境相关配置见 `.env.*`。
- 浏览器用例：先启动后台、API 和数据库，再运行 `npx playwright test`；黄金链使用 `npx playwright test -c playwright.chains.config.ts`。用例默认访问 `http://127.0.0.1:18081`，临时账号与夹具由 `tools/e2e_fixtures.py` 提供。

## 相关文档

- 仓库根 [README.md](../README.md)：项目组成、环境要求与整体验证入口
- [AGENTS.md](../AGENTS.md)：工程约束
- [CONTRIBUTING.md](../CONTRIBUTING.md)：评审与格式约定
- [docs/status.md](../docs/status.md)：当前实现与验证证据
