/**
 * 打印中心（模板配置 + 打印记录）的前端契约测试。
 *
 * 只钉「违背之后页面照样能跑、但事实已经错了」的那一类：
 *
 * 1. 打印记录是审计凭据：必须能说清当初用的是哪一版模板，模板编码 / 版本 / 操作人
 *    各自成列，一格一个值。
 * 2. 打印时间与操作人同样各自成列，不叠进同一格。
 * 3. 模板配置页的编码有维护价值，与普通主数据不同。
 * 4. 操作列统一居中固定，不再右对齐。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
}

const templateList = code('../src/views/business/scm/print/print-template-list.vue');
const recordList = code('../src/views/business/scm/print/print-record-list.vue');

test('打印记录把模板编码、版本与打印人各自拆成独立列', () => {
  // 一格一值：编码 / 版本 / 操作人各自成列，且审计要能在列表上直接核出来
  assert.match(recordList, /title: '模板编码', dataIndex: 'templateCode'/);
  assert.match(recordList, /title: '模板版本', dataIndex: 'templateVersion'/);
  assert.match(recordList, /title: '打印人', dataIndex: 'printedBy'/);
  assert.match(recordList, /title: '模板名称', dataIndex: 'templateName'/);
  assert.doesNotMatch(recordList, /scm-cell-stack/, '打印记录仍把两个值叠进一个单元格');
  // 时间字段例外：打印时间属于历史记录页的核心信息，不能隐藏
  assert.match(recordList, /record\.printedAt/);
});

test('打印模板配置页保留模板编码（与普通主数据的口径不同）', () => {
  assert.match(templateList, /title: '模板编码', dataIndex: 'templateCode'/);
  assert.match(templateList, /title: '模板名称', dataIndex: 'templateName'/);
  assert.match(templateList, /title: '更新时间'/);
  // 明细列数是模板内部实现细节，不属于配置页要回答的问题
  assert.ok(!templateList.includes(`title: '明细列数'`), '模板页仍有明细列数列');
  // 两个开关走语义档位
  assert.match(templateList, /ScmStatusTag/);
  assert.match(templateList, /tone="processing" label="默认"/);
});

test('两个页面的操作列统一居中固定，金额 / 状态不再各写色名', () => {
  for (const [name, source] of [['模板页', templateList], ['记录页', recordList]]) {
    assert.match(source, /dataIndex: 'action', align: 'center', fixed: 'right'/, `${name} 的操作列未居中`);
    assert.doesNotMatch(source, /<a-tag :color=/, `${name} 仍有裸 a-tag 色名`);
  }
});

test('打印模板页的操作列收到 160px 以内，低频动作进「更多」', () => {
  // 「设为默认」与「删除」不再常驻；只有「编辑」留在行内
  const width = /dataIndex: 'action', align: 'center', fixed: 'right', width: (\d+)/.exec(templateList);
  assert.ok(width, '未取到模板页操作列宽度');
  assert.ok(Number(width[1]) <= 160, `模板页操作列 ${width[1]}px 超出 160px`);
  assert.match(templateList, /ScmActionMore/);
  // v-privilege 对菜单项不生效，权限必须在 rowActions 里显式裁剪
  assert.match(templateList, /hasPermission\('scm:print:template:update'\)/);
  assert.match(templateList, /hasPermission\('scm:print:template:delete'\)/);
  assert.match(templateList, /onRowAction[\s\S]{0,300}setDefault\(record\)/);
  assert.match(templateList, /onRowAction[\s\S]{0,300}remove\(record\)/);
  // 默认模板不提供「设为默认 / 删除」
  assert.match(templateList, /hidden: isDefault/);
});
