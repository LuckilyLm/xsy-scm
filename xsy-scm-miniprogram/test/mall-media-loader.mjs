import { readFileSync } from 'node:fs';

export const TEST_MEDIA_ROOT = 'https://assets.example.com/public/image/mall-ui/e62b6c6cabbb8a49';

/** 将真实媒体地址生成器注入独立模块测试，不依赖Node无法读取的Vite环境对象。 */
export function withMedia(source, root = TEST_MEDIA_ROOT) {
  const helper = readFileSync(new URL('../src/utils/mall-media.js', import.meta.url), 'utf8')
    .replace('import.meta.env.VITE_APP_MEDIA_URL', JSON.stringify(root))
    .replace('export function mallMedia', 'function mallMedia');
  return helper + '\n' + source.replace(/^import \{ mallMedia \} from ['"]@\/utils\/mall-media['"];\r?\n/m, '');
}
