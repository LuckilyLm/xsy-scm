// §15 黄金业务链专用配置：链用例会留下主数据与期次事实（仓库、应收、核销、红字、已冻结对账单），
// 与默认 e2e 套件共享库时会打红聚合/范围类断言（见 docs/quality/adm-acceptance-test-log-2026-10-04.md 的 D-37）。
// 因此默认 verify.py e2e 不跑它们；单独执行：
//   npx playwright test --config playwright.chains.config.ts
import { defineConfig } from '@playwright/test';
export default defineConfig({
  fullyParallel: false,
  testDir: './e2e-chains',
  timeout: 120000,
  expect: { timeout: 10000 },
  workers: 1,
  outputDir: '../.runtime/playwright-results-chains',
  reporter: [['list'], ['json', { outputFile: '../.runtime/playwright-chains-result.json' }]],
  use: { baseURL: 'http://127.0.0.1:18081', viewport: { width: 1440, height: 1000 }, actionTimeout: 15000, navigationTimeout: 30000, screenshot: 'only-on-failure', trace: 'off' },
});
