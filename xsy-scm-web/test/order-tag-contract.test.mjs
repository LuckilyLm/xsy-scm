import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
const sql = read('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V145__scm_order_tags.sql');
const mapper = read('../../xsy-scm-server/sa-admin/src/main/resources/mapper/scm/order/SalesOrderMapper.xml');
const service = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/order/service/OrderTagService.java');
const readService = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/order/service/SalesOrderQueryService.java');
const api = read('../src/api/business/scm/order-tag-api.ts');
const list = read('../src/views/business/scm/order/order-list.vue');
const modal = read('../src/views/business/scm/order/components/order-tag-modal.vue');

test('标签独立存储并按订单与标签对唯一约束，不能改变订单交易事实', () => {
  assert.match(sql, /CREATE TABLE xsy_order_tag\b/);
  assert.match(sql, /CREATE TABLE xsy_order_tag_link\b/);
  assert.match(sql, /PRIMARY KEY \(order_id, tag_id\)/);
  assert.doesNotMatch(sql, /ALTER TABLE xsy_sales_order|FOREIGN KEY/i);
  assert.match(service, /orderDao\.lock\(id\)/);
  assert.match(service, /scope\.allows\(order\.getSellerId\(\)\)/);
  assert.match(service, /@Transactional[\s\S]*?void apply\(/);
});

test('订单列表的原负责人范围先于标签过滤；非草稿可打标，但不可批量删除或核价', () => {
  assert.match(mapper, /scope\.all == false/);
  assert.match(mapper, /AND EXISTS \(SELECT 1 FROM xsy_order_tag_link/);
  assert.match(readService, /orderTagDao\.tagsByOrders\(/);
  assert.match(list, /queryForm\.tagId/);
  assert.match(list, /record\.tags/);
  assert.doesNotMatch(list, /getCheckboxProps/);
  assert.match(list, /selectedAllDraft/);
  assert.match(list, /!selectedAllDraft/);
  assert.match(list, /key: 'tag', label: '订单标签'/);
});

test('批量打标最多20单；只有订单更新与查询权限才可操作', () => {
  assert.match(api, /'\/scm\/order\/tags\/apply'/);
  assert.match(list, /hasPermission\('scm:order:query'\)/);
  assert.match(list, /hasPermission\('scm:order:update'\)/);
  assert.match(list, /selected\.length > 20/);
  assert.match(modal, /orderTagApi\.apply\(/);
  assert.match(service, /form\.getOrderIds\(\)\.stream\(\)\.distinct\(\)\.sorted\(\)/);
});
