import {defineConfig} from '@playwright/test';
export default defineConfig({
  // 文件内用例共享模块级夹具，必须按声明顺序执行；workers: 1 只限并发，不改调度
  fullyParallel: false,
  testDir: './e2e',
  timeout: 120000,
  expect: {timeout: 10000},
  workers: 1,
  outputDir: '../.runtime/playwright-results',
  reporter: [['list'], ['json', {outputFile: '../.runtime/playwright-result.json'}]],
  use: {
    baseURL: 'http://127.0.0.1:18081',
    viewport: {width: 1440, height: 1000},
    actionTimeout: 15000,
    navigationTimeout: 30000,
    screenshot: 'only-on-failure',
    trace: 'off',
  },
  // 合同夹具页（e2e/fixtures/adm-ui.html）是 .ts 模块，必须由 Vite 编译并解析 /@/ 别名；
  // 以前这第二个 Vite（18083）靠人工启动，干净机器上 scm-platform 整组 ERR_CONNECTION_REFUSED。
  webServer: {
    command: 'node node_modules/vite/bin/vite.js --port 18083 --strictPort',
    url: 'http://127.0.0.1:18083/e2e/fixtures/adm-ui.html',
    reuseExistingServer: true,
    timeout: 120000,
  },
});
