import { defineConfig } from '@playwright/test';
export default defineConfig({
  // 文件内用例共享模块级夹具，必须按声明顺序执行；workers: 1 只限并发，不改调度
  fullyParallel: false,
  testDir: './e2e', timeout: 120000, expect: { timeout: 10000 }, workers: 1,
  outputDir: '../.runtime/playwright-results', reporter: [['list'], ['json', { outputFile: '../.runtime/playwright-result.json' }]],
  use: { baseURL: 'http://127.0.0.1:18081', viewport: { width: 1440, height: 1000 }, actionTimeout: 15000, navigationTimeout: 30000, screenshot: 'only-on-failure', trace: 'off' },
});
