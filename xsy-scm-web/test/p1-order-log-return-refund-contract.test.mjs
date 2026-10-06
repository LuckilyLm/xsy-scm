/**
 * 订单日志、退货、退款的前端契约测试。
 *
 * 这三个页面此前只做过「操作列居中」（commit ffa95370 是 8 文件 16 行的 align 改动），
 * 列精简与动作分组都没有做。本文件钉住两件容易回退的事：
 *
 * 1. <b>越界的操作列宽度</b>：普通操作列 120～160px，
 *    而这三个页面此前一律是 240px。
 * 2. <b>危险动作不能与普通动作同排常驻</b>：驳回 / 取消必须进「更多」，
 *    否则 240px 的操作列里最显眼的永远是那排红色文字。
 *
 * 日志页的时间是时间字段的例外，单独断言，防止「统一隐藏时间」被误用到审计页。
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

const BASE = '../src/views/business/scm/order/';
const orderLog = code(`${BASE}order-log-list.vue`);
const orderReturn = code(`${BASE}order-return-list.vue`);
const orderRefund = code(`${BASE}order-refund-list.vue`);

/** 取出「操作」列声明里的 width；取不到就断言失败，不返回默认值。 */
function actionWidth(source, name) {
  const matched = /dataIndex: 'action'[\s\S]{0,120}?width: (\d+)/.exec(source);
  assert.ok(matched, `${name}：找不到操作列的 width`);
  return Number(matched[1]);
}

test('订单日志：操作列收到 120px 以内，时间必须保留', () => {
  const width = actionWidth(orderLog, '订单日志');
  assert.ok(width <= 120, `订单日志操作列 ${width}px 超出 120px`);
  // 日志页是时间例外：隐藏时间会让「谁在什么时候改的」这条审计链断掉
  assert.match(orderLog, /title: '时间'/);
  assert.match(orderLog, /title: '操作人'/);
  assert.match(orderLog, /title: '原因'/);
});

test('退货：操作列收到 160px 以内，驳回与取消进「更多」', () => {
  const width = actionWidth(orderReturn, '退货');
  assert.ok(width <= 160, `退货操作列 ${width}px 超出 160px`);
  // 常驻的仍是详情与当前状态唯一的推进动作
  assert.match(orderReturn, /showDetail\(record\.returnId\)/);
  assert.match(orderReturn, /record\.status==='PENDING'/);
  assert.match(orderReturn, /record\.status==='APPROVED'/);
  // 审批 / 接收 / 拒绝 / 取消 不能全部常驻，低频与危险动作按状态进「更多」
  assert.match(orderReturn, /ScmActionMore/);
  assert.match(orderReturn, /hasPermission\('scm:order:return:reject'\)/);
  assert.match(orderReturn, /hasPermission\('scm:order:return:cancel'\)/);
  // 原因可能很长，必须 ellipsis 而不是把行撑成两行
  assert.match(orderReturn, /title: '退货原因'[\s\S]{0,80}?ellipsis: true/);
  assert.match(orderReturn, /title: '退货单号'/);
  assert.match(orderReturn, /title: '批准金额'/);
});

test('退款：操作列收到 160px 以内，外部凭证不常驻长文本', () => {
  const width = actionWidth(orderRefund, '退款');
  assert.ok(width <= 160, `退款操作列 ${width}px 超出 160px`);
  assert.match(orderRefund, /title: '退款单号'/);
  assert.match(orderRefund, /title: '已返还钱包'/);
  // 外部凭证是渠道返回的长字符串，要求 ellipsis + Tooltip
  assert.match(orderRefund, /title: '外部凭证'[\s\S]{0,120}?ellipsis: true/);
});

test('三个订单页面的操作列全部居中', () => {
  for (const [name, source] of [['订单日志', orderLog], ['退货', orderReturn], ['退款', orderRefund]]) {
    // 列声明有单行与多行两种写法，不能假设 align 紧跟在 dataIndex 后面
    assert.match(source, /dataIndex: 'action'[\s\S]{0,120}?align: 'center'/, `${name}的操作列未居中`);
  }
});
