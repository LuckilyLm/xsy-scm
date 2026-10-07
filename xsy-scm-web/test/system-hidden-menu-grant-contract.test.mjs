/*
 * 角色权限树的级联契约：勾父菜单必须把隐藏子菜单一并授出。
 *
 * 隐藏的详情页路由（商品详情 / 客户详情 / 供应商详情 / 线路详情）在侧栏里没有入口，
 * 完全靠「勾了父菜单就一并授出」这一条链路。后端菜单树不过滤 visible_flag（否则隐藏菜单
 * 根本不进树），前端勾选递归进 children（否则勾了父菜单也授不出详情）。
 * 两段任一被改掉，新建角色就会出现「能看列表、点详情空白」，而迁移当时的角色仍然正常 ——
 * 这种只在新建角色上暴露的回归最难查，所以在这里钉住。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const MENU = new URL('../src/views/system/role/components/role-tree/role-tree-menu.vue', import.meta.url);
const POINT = new URL('../src/views/system/role/components/role-tree/role-tree-point.vue', import.meta.url);
const STORE = new URL('../src/store/modules/system/role.ts', import.meta.url);

const code = (url) => readFileSync(url, 'utf8');

test('勾选菜单时递归勾上全部子级，含隐藏的详情页菜单', () => {
  const menu = code(MENU);
  assert.match(menu, /roleStore\.addCheckedDataAndChildren\(module\)/);
  assert.match(menu, /roleStore\.deleteCheckedDataAndChildren\(module\)/);

  const store = code(STORE);
  // 递归必须真的进 children：只勾本级会让隐藏详情路由授不出去。
  assert.match(
    store,
    /addCheckedDataAndChildren\(data\) \{[\s\S]*?data\.children\.forEach\(\(item\) => \{\s*this\.addCheckedDataAndChildren\(item\);/,
    'addCheckedDataAndChildren 必须递归进 children',
  );
  assert.match(
    store,
    /deleteCheckedDataAndChildren\(data\) \{[\s\S]*?data\.children\.forEach\(\(item\) => \{\s*this\.deleteCheckedDataAndChildren\(item\);/,
    '取消勾选同样要递归，否则会留下授不出去的孤儿菜单',
  );
});

test('菜单节点把全部子级铺成可勾选项，隐藏菜单才看得见、点得到', () => {
  const menu = code(MENU);
  // 带功能点子级的菜单走 RoleTreePoint，而它把 module.children 整体铺开（不过滤类型）。
  assert.match(menu, /<RoleTreePoint :tree="module\.children" @selectCheckbox="selectCheckbox"\/>/);

  const point = code(POINT);
  assert.match(point, /<template v-for="module in props\.tree" :key="module\.menuId">/);
  assert.match(point, /<a-checkbox @change="emits\('selectCheckbox', module\)" :value="module\.menuId">/);
});
