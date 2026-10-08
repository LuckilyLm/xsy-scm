/**
 * 业务待办（首页只读聚合）的前端契约测试。
 *
 * 钉死这一波破坏后不报错的前端边界（权限裁剪与领域计数由 Java 测试覆盖）：
 * 1. 待办是只读 Pull：走 getRequest，绝不 POST、绝不复用带幂等的写命令；
 * 2. 前端不重复判断权限、不缓存计数、不写业务表 —— 卡片清单完全来自后端；
 * 3. 卡片入口用 scm:todo:query 门禁（无权整卡隐藏），不新建第二套消息中心；
 * 4. 点击是带条件跳转：直接使用后端给的 route（已含查询串），不在前端拼状态。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const api = code('../src/api/business/scm/dashboard-api.ts');
const card = code('../src/views/system/home/components/business-todo-card/home-business-todo.vue');
const home = code('../src/views/system/home/index.vue');

test('待办接口是只读 GET，不引入写命令或幂等封装', () => {
  assert.match(api, /todo:[\s\S]*?getRequest\('\/scm\/dashboard\/todo',\s*\{\},\s*REGION_REQUEST_OPTIONS\)/);
  assert.match(api, /const REGION_REQUEST_OPTIONS = \{suppressGlobalErrorMessage: true\}/);
  assert.doesNotMatch(api, /postRequest|putRequest|deleteRequest|Idempotency|sendMessage/);
});

test('卡片挂载即拉取，数字与清单完全来自后端，不做前端权限判断或本地缓存', () => {
  assert.match(card, /onMounted\(load\)/);
  assert.match(card, /useRegionData<ScmTodo\[\]>\(scmDashboardApi\.todo,/);
  assert.match(card, /const todos = computed\(\(\) => data\.value \?\? \[\]\)/);
  assert.match(card, /<region-error\b[^>]*v-if="error"[^>]*@retry="load"/);
  // 前端不自行按权限过滤卡片，也不落 localStorage
  assert.doesNotMatch(card, /localSave|localStorage|hasPermission|usePermission|filter\(.*perm/);
});

test('卡片点击直接跳转后端给的条件路由，不在前端拼接查询状态', () => {
  assert.match(card, /<router-link\b[^>]*:to="todo\.route"/);
  assert.doesNotMatch(card, /query:\s*\{[\s\S]*status:/);
});

test('首页入口用 scm:todo:query 门禁，复用既有 home 卡片容器', () => {
  assert.match(home, /const canTodo = hasPermission\('scm:todo:query'\)/);
  assert.match(home, /<a-col\b[^>]*v-if="canTodo"[^>]*>\s*<HomeBusinessTodo\b/);
  // 允许带属性：首页的刷新按钮通过模板引用调用卡片的 load，所以会挂 ref
  assert.match(home, /<HomeBusinessTodo\b[^>]*\/>/);
  assert.match(card, /DefaultHomeCard|default-home-card/);
});
