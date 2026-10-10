import { resolve } from 'path';
import { defineConfig, loadEnv } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';

const pathResolve = (dir) => {
  return resolve(__dirname, '.', dir);
};

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const useMock = env.VITE_APP_USE_MOCK === 'true';

  return {
    plugins: [uni()],
    root: process.cwd(),
    resolve: {
      alias: [
        /*
         * mock 契约层刻意放在 src/ 之外，且用 `@mock` 作为引入标识：
         *
         * 1) 为什么在 src/ 之外：uni-app 会把 src/ 整棵树镜像进构建产物
         *    （未被引用的文件同样会被发射），假数据留在 src/ 里就会被打进包里。
         *    放在项目根 mock/ 才不会被镜像。
         * 2) 为什么不用 `@/mock`：uni-app 自带一条 `@` → `src/` 的别名且优先级更高，
         *    `@/mock` 会被它先命中并解析成 src/mock，导致解析失败。
         *    `@mock` 不匹配它的 `@` 规则，能稳定落到这里。
         *
         * 关闭时指向空实现，确保假数据与 mock 路由绝不进产物。
         */
        {
          find: '@mock',
          replacement: pathResolve(useMock ? 'mock/index.js' : 'mock/noop.js'),
        },
        // 绝对路径重命名：/@/xxxx => src/xxxx
        {
          find: /\/@\//,
          replacement: pathResolve('src') + '/',
        },
        {
          find: /^~/,
          replacement: '',
        },
      ],
    },
    // H5 本地开发代理：仅用于绕过浏览器 CORS。
    // 小程序端不走这里，微信开发者工具请直接把 VITE_APP_API_URL 配成完整后端地址。
    server: {
      port: 5173,
      proxy: {
        '/scm': {
          target: env.VITE_APP_PROXY_TARGET || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
      },
    },
    build: {
      minify: 'terser',
      terserOptions: {
        compress: {
          drop_console: true,
        },
      },
    },
  };
});
