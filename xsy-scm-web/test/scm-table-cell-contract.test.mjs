/**
 * 表格与提示形态契约。
 *
 * 钉住 2026-10 用户验收后的三条长期规则（见 docs/architecture/scm-ui-guidelines.md §2、§7、§8.1）：
 * 1. 一格一个值：`.scm-cell-stack` 双行复合单元整体退役，值各自成列；
 * 2. 查询与操作失败走 toast，不保留常驻 error 横幅；
 * 3. 间距由主题统一提供，页面不各自补 margin。
 *
 * 这三条是「不得退回」方向的断言，不是「当前有多少处」的统计。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';

const SRC = fileURLToPath(new URL('../src/', import.meta.url));
const SCM_VIEW_DIR = join(SRC, 'views/business/scm');
const THEME = readFileSync(join(SRC, 'theme/scm/table.less'), 'utf8');
const SMART = readFileSync(join(SRC, 'theme/smart-admin.less'), 'utf8');

function collectVue(dir, out = []) {
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      collectVue(full, out);
    } else if (entry.name.endsWith('.vue')) {
      out.push(full);
    }
  }
  return out;
}

const SCM_VIEWS = collectVue(SCM_VIEW_DIR);

assert.ok(SCM_VIEWS.length > 100, `SCM 页面数量异常：${SCM_VIEWS.length}`);

const read = (file) => readFileSync(file, 'utf8');

test('SCM 页面不再使用双行复合单元 .scm-cell-stack', () => {
  for (const file of SCM_VIEWS) {
    assert.doesNotMatch(
        read(file),
        /scm-cell-stack/,
        `${file} 仍把两个值叠进同一个单元格，应拆成独立列`
    );
  }
});

test('主题层退役 .scm-cell-stack，同时保留单元格不换行的默认与退出方式', () => {
  assert.doesNotMatch(THEME, /\.scm-cell-stack/);
  assert.match(
      THEME,
      /\.ant-table-tbody > tr:not\(\.ant-table-expanded-row\) > td > \.ant-table-cell\s*\{[\s\S]{0,160}?white-space:\s*nowrap/,
      '主题缺少「单元格默认不换行」规则'
  );
  assert.match(THEME, /\.scm-cell-wrap/, '缺少长文本换行的退出方式');
  // 展开行与内嵌表格不能被裁掉
  assert.match(THEME, /:has\(\.scm-cell-wrap\)/, '换行退出方式没有回退单元格自身的裁剪');
  assert.match(THEME, /:has\([^)\n]*\.ant-table\)/, '内嵌表格未被排除在裁剪之外');
});

test('SCM 页面不保留常驻 error 横幅，错误状态统一走 useScmErrorToast', () => {
  for (const file of SCM_VIEWS) {
    const source = read(file);
    assert.doesNotMatch(
        source,
        /<a-alert[^>]*type="error"/,
        `${file} 仍用常驻 Alert 承载失败提示，应改为 toast`
    );
  }
});

test('查询表单换行后由主题提供行距，表格工具栏用 gap 提供按钮间距', () => {
  assert.match(
      SMART,
      /\.smart-query-form-row:not\(:first-child\)\s*\{[\s\S]{0,80}?margin-top:\s*16px/,
      '查询表单换行后的行距被改小，两行筛选条件会贴在一起'
  );
  assert.match(
      SMART,
      /\.smart-table-btn-block\s*\{[\s\S]{0,300}?gap:\s*12px/,
      '表格工具栏缺少 gap，相邻按钮会贴死'
  );
  assert.match(
      SMART,
      /\.smart-table-operate-block\s*\{[\s\S]{0,200}?gap:\s*12px/,
      '工具栏动作区缺少 gap'
  );
});

test('摘要卡与 KPI 网格按内容自适应，不写死列数', () => {
  const detail = readFileSync(join(SRC, 'theme/scm/detail.less'), 'utf8');
  assert.match(
      detail,
      /\.scm-summary\s*\{[\s\S]{0,200}?grid-template-columns:\s*repeat\(auto-fit/,
      '.scm-summary 写死列数，卡数不整除时会留下空白大卡'
  );
});

/**
 * scroll.x 不能小于列宽之和。
 *
 * 拆列最常见的副作用：加了列却忘了同步 scroll.x。antd 在 scroll.x 小于列宽之和时
 * 会按比例压缩各列，于是新列被挤回去换行 —— 正好是拆列想解决的问题。
 * 只在「单表 + 单个 columns 数组」的文件上判定，多表页面的归属关系无法可靠解析。
 */
test('单表页面的 scroll.x 不小于列宽之和', () => {
  for (const file of SCM_VIEWS) {
    const source = read(file);
    const scrolls = [...source.matchAll(/:scroll="\{\s*x:\s*(\d+)/g)].map((m) => Number(m[1]));
    if (scrolls.length !== 1) {
      continue;
    }
    const blocks = [...source.matchAll(/const\s+\w*[Cc]olumns\w*\s*=[^\n]*\[\([\s\S]*?\n\]\s*[;)]/g)];
    if (blocks.length !== 1) {
      continue;
    }
    const sum = [...blocks[0][0].matchAll(/width:\s*(\d+)/g)].reduce((a, m) => a + Number(m[1]), 0);
    if (sum === 0) {
      continue;
    }
    assert.ok(
        scrolls[0] >= sum,
        `${file} scroll.x=${scrolls[0]} 小于列宽之和 ${sum}，列会被压缩换行`
    );
  }
});
