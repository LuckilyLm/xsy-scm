/**
 * 图片中心左侧商品选择区的列契约测试。
 *
 * 规划要求左侧「商品名称 + 商品编码 + 主图状态」各自成列：名称是主信息，
 * 编码是可核对的技术标识，两者不叠进同一个单元格（一格一值）。
 *
 * 本文件钉住：
 * 1. 选择区有独立的 `spuCode` 列，名称列不再叠副行。
 * 2. 名称与编码都真实渲染在 DOM 中（E2E 按编码文本定位行）。
 * 3. 主图状态列保留。
 * 4. 不再有局部 .code 之类为编码列单独写的样式。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const file = new URL('../src/views/business/scm/product/image-center.vue', import.meta.url);
const source = readFileSync(file, 'utf8');

/**
 * 去注释后再断言结构，避免注释里的说明文字误命中。
 *
 * 陷阱：不能直接用 `/\/\*[\s\S]*?\*\//` 剥块注释 —— 本文件里有 `accept="image/*"`，
 * 其中的 `/*` 会被当成块注释开头，贪婪吞掉后面大段代码（含 pickColumns 定义）。
 * 因此块注释只按「行首」剥（真实块注释总在行首），行注释同理。
 */
const code = source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/^\s*\/\*[\s\S]*?\*\/\s*$/gm, '')
    .replace(/^\s*\/\/.*$/gm, '');

/** 取 pickColumns 的字面量块（多行、带尾逗号都要能取到）。 */
function pickColumnsBlock() {
    const match = code.match(/const\s+pickColumns\s*=\s*\[([\s\S]*?)\];/);
    if (!match) {
        throw new Error('未找到 pickColumns 定义');
    }
    return match[1];
}

test('左侧选择区把商品编码独立成列', () => {
    const block = pickColumnsBlock();
    assert.match(block, /dataIndex:\s*'spuCode'/,
        '选择区缺少独立的 spuCode 列，编码又会被并回名称格里');
    assert.match(block, /dataIndex:\s*'name'/, '选择区缺少名称列');
});

test('名称与编码各自成列，单元格不再叠行', () => {
    // 名称列与编码列各走自己的 bodyCell 分支，且都不使用双行复合单元
    assert.match(code, /column\.dataIndex === 'spuCode'/, '编码列未走自定义渲染');
    assert.match(code, /column\.dataIndex === 'name'/, '名称列未走自定义渲染');
    assert.ok(!/scm-cell-stack/.test(code), '名称格里仍在叠副行，应各自成列');
});

test('编码文本仍在 DOM 中可见（E2E 按编码定位行）', () => {
    // E2E 用 hasText 按 spuCode 找行，因此编码必须真实渲染，不能被删掉
    assert.match(code, /record\.spuCode/, '编码已从渲染中消失，会破坏按编码定位的 E2E');
});

test('主图状态列保留', () => {
    const block = pickColumnsBlock();
    assert.match(block, /dataIndex:\s*'primary'/, '选择区缺少主图状态列');
    assert.match(code, /primaryImageUrl[\s\S]{0,80}有主图/, '主图状态未按「有主图 / 无主图」渲染');
});

test('不再为编码列保留单独样式', () => {
    assert.ok(!/\.code\s*\{/.test(source), '仍残留为编码列单独写的 .code 样式');
});
