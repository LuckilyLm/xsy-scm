/**
 * §10.4 / §10.5 商品配置三张表（分类 / 计量单位 / 商品标签）的列与操作列契约单测。
 *
 * 这三张表藏在 `product/components/` 下，§8 / §31.1 的「操作列居中」批量改动
 * （commit ffa95370）只扫了页面级列表，漏掉了组件里的表，所以它们的操作列一直是
 * `align: 'right'` —— 视觉上与全站相反，且靠肉眼验收不容易发现（组件不在主路由上）。
 *
 * 本文件钉住：
 * 1. **操作列居中**（§31.1 / §8）。
 * 2. **分类表按 §10.4 展示父分类、不展示内部编码**：§10.4 的保留清单是
 *    分类名称 / 层级 / 父分类 / 状态 / 排序 / 操作，没有分类编码；
 *    父分类从 `categoryPath` 推导（一级分类为 —）。
 * 3. **数值走全局 `.scm-quantity`**，不再保留局部 `.price` 样式。
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

const BASE = '../src/views/business/scm/product/components/';
const category = code(`${BASE}category-tree-table.vue`);
const uom = code(`${BASE}product-uom-table.vue`);
const tag = code(`${BASE}product-tag-table.vue`);

test('§31.1 三张配置表的操作列统一居中', () => {
    for (const [name, source] of [['分类表', category], ['计量单位表', uom], ['标签表', tag]]) {
        assert.match(source, /dataIndex: 'action'[\s\S]{0,60}?align: 'center'/, `${name} 的操作列未居中`);
        assert.doesNotMatch(source, /dataIndex: 'action'[\s\S]{0,60}?align: 'right'/, `${name} 的操作列仍是右对齐`);
    }
});

test('§10.4 分类表展示父分类、不展示分类编码', () => {
    assert.match(category, /title: '分类名称'/);
    assert.match(category, /title: '层级'/);
    assert.match(category, /title: '父分类'/);
    assert.match(category, /title: '状态'/);
    assert.match(category, /title: '排序'/);
    // §10.4 的保留清单没有分类编码；它属于内部维护字段
    assert.ok(!/title: '分类编码'/.test(category), '分类表仍有独立的分类编码列');
    // 父分类由 categoryPath 去掉最后一段推导
    assert.match(category, /record\.categoryPath/);
    assert.match(category, /slice\(0, -1\)/);
    // 一级分类没有父级，空值统一 —
    assert.match(category, /return segments\.length > 1 \? segments\.slice\(0, -1\)\.join\(' \/ '\) : '—'/);
});

test('§10.5 单位表与标签表保留字典编码、不展示时间列', () => {
    // §10.5 明确单位编码可保留（维护型字典）
    assert.match(uom, /title: '单位编码'/);
    assert.match(uom, /title: '单位名称'/);
    assert.match(uom, /title: '状态'/);
    assert.match(tag, /title: '标签编码'/);
    assert.match(tag, /title: '标签名称'/);
    // 隐藏创建 / 更新时间
    for (const [name, source] of [['单位表', uom], ['标签表', tag], ['分类表', category]]) {
        assert.ok(!/title: '创建时间'/.test(source), `${name} 仍展示创建时间`);
        assert.ok(!/title: '更新时间'/.test(source), `${name} 仍展示更新时间`);
    }
});

test('§10.5 数值统一走全局 .scm-quantity，不留局部样式', () => {
    for (const [name, source] of [['单位表', uom], ['标签表', tag]]) {
        assert.match(source, /class="scm-quantity"/, `${name} 未使用 .scm-quantity`);
        assert.ok(!/class="price"/.test(source), `${name} 仍用局部 .price 类`);
        assert.ok(!/\.price\s*\{/.test(source), `${name} 仍定义局部 .price 样式`);
    }
});
