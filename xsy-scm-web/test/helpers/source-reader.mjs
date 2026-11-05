import {existsSync, readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const manifest = JSON.parse(readFileSync(new URL('../../../tools/legacy_migration_manifest.json', import.meta.url), 'utf8'));
let paths;
const history = new Map();

/** 当前源码直接读取；已退役的SQL只从已核对的Git版本读取，不复制回应用资源。 */
export function readSourceFileSync(source, options) {
  if (existsSync(source)) return readFileSync(source, options);
  const name = source instanceof URL ? fileURLToPath(source) : String(source);
  const match = name.replaceAll('\\', '/').match(/\/db\/migration\/(V(\d+)__[^/]+\.sql)$/);
  if (!match || manifest.migrations[match[2]]?.file !== match[1]) return readFileSync(source, options);
  if (!paths) {
    paths = execFileSync('git', ['ls-tree', '-r', '--name-only', manifest.git_revision], {cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024})
      .trim()
      .split('\n');
  }
  if (!history.has(match[1])) {
    const matches = paths.filter((path) => path.endsWith(`/src/main/resources/db/migration/${match[1]}`));
    if (matches.length !== 1) throw new Error(`历史SQL文件未唯一匹配：${match[1]}`);
    history.set(match[1], execFileSync('git', ['show', `${manifest.git_revision}:${matches[0]}`], {cwd: root, maxBuffer: 16 * 1024 * 1024}));
  }
  const content = history.get(match[1]);
  const encoding = typeof options === 'string' ? options : options?.encoding;
  return encoding ? content.toString(encoding) : Buffer.from(content);
}
