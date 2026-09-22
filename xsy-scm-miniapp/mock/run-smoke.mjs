/*
 * Mock 契约层冒烟测试的运行器。
 *
 * 为什么要有个运行器：mock 源码用了 `@/` 别名与 `import.meta.env`，
 * Node 无法直接加载。这里用 esbuild（Vite 自带依赖，无需新增）先打包再导入。
 *
 * 产物写到系统临时目录，不污染仓库。
 *
 * 用法：npm run test:mock
 */
import { build } from 'esbuild';
import { mkdtempSync, rmSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { pathToFileURL } from 'url';

const outDir = mkdtempSync(join(tmpdir(), 'xsy-mock-smoke-'));
const outFile = join(outDir, 'smoke.mjs');

try {
  await build({
    entryPoints: ['mock/smoke.test.mjs'],
    bundle: true,
    format: 'esm',
    platform: 'node',
    // 与 vite.config.js 的 `@` → src 别名保持一致
    alias: { '@': './src' },
    // 测试里必须让 mock 处于"开启"状态
    define: { 'import.meta.env.VITE_APP_USE_MOCK': '"true"' },
    outfile: outFile,
    logLevel: 'warning',
  });

  await import(pathToFileURL(outFile).href);
} finally {
  rmSync(outDir, { recursive: true, force: true });
}
