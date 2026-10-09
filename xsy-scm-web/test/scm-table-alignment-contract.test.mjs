import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync, statSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import ts from 'typescript';

const tableStyle = readFileSync(new URL('../src/theme/scm/table.less', import.meta.url), 'utf8');
const supplierPage = readFileSync(new URL('../src/views/business/scm/supplier/supplier-list.vue', import.meta.url), 'utf8');
const supplierEditableTable = readFileSync(new URL('../src/views/business/scm/supplier/components/supplier-sku-editable-table.vue', import.meta.url), 'utf8');
const payablePage = readFileSync(new URL('../src/views/business/scm/finance/finance-payable-list.vue', import.meta.url), 'utf8');
const srcRoot = fileURLToPath(new URL('../src', import.meta.url));

function sourceFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory)) {
    const absolutePath = path.join(directory, entry);
    if (statSync(absolutePath).isDirectory()) files.push(...sourceFiles(absolutePath));
    else if (absolutePath.endsWith('.vue') || absolutePath.endsWith('.ts')) files.push(absolutePath);
  }
  return files;
}

function propertyName(node) {
  return node && (ts.isIdentifier(node) || ts.isStringLiteral(node) || ts.isNumericLiteral(node)) ? node.text : '';
}

function property(object, name) {
  return object.properties.find((item) => ts.isPropertyAssignment(item) && propertyName(item.name) === name);
}

function stringValue(node) {
  if (node && (ts.isAsExpression(node) || ts.isTypeAssertionExpression(node) || ts.isParenthesizedExpression(node))) {
    return stringValue(node.expression);
  }
  return node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) ? node.text : '';
}

function tokens(value) {
  return String(value)
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1 $2')
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(Boolean);
}

const numericTokens = new Set([
  'amount', 'price', 'quantity', 'qty', 'count', 'rate', 'ratio', 'percent', 'percentage',
  'balance', 'stock', 'volume', 'weight', 'distance', 'mileage', 'profit', 'discount',
  'creditlimit', 'lowerlimit', 'upperlimit', 'minimum', 'maximum',
]);
const numericTitle = /金额|总额|单价|售价|进价|市场价|数量|进度|(?:商品|规格|订单|关联|浏览|访问|登录失败|生成|行|单据|文档|来源订单|来源行|停靠点|异常|记录)数|库存|销量|折扣率|税率|费率|百分比|占比|比例|额度|余额|核销金额|未核销|已核销|超额核销|分配量|未分配|已分配|实收|上限|下限|载重|容积|距离|里程|耗时|时长|重量|体积|利润|成本|佣金|毛利|合计|入库量|出库量|交付量|登录失败次数|生成数量/;
const statusTitle = /状态|方向|策略|结清|启用|停用|是否|类型|级别|等级|默认来源|计算状态|履约状态|打印状态|发布状态|锁定状态|方式/;
const textTitle = /名称|编码|编号|单号|标识|联系人|电话|手机号|供应商|客户|商品|规格|仓库|地址|备注|描述|说明|摘要|时间|日期|时点|来源|内容|标题|设备|用户|部门|角色|菜单|批次|流水号|操作人|IP/;
const imageTitle = /主图|缩略图|头像|图片|图标|二维码/;
const shortSourceKeys = new Set(['sourcetype', 'ordersource', 'pricesource', 'lockedpricesource']);

function expectedAlignment(title, dataIndex) {
  const key = String(dataIndex || '');
  const lowerKey = key.toLowerCase();
  const keyTokens = tokens(key);
  if (/^(action|actions|operate)$/.test(lowerKey)) return 'center';
  if (shortSourceKeys.has(lowerKey)) return 'center';
  if (/单位$/.test(title)) return 'center';
  if (/(execute|response|description|reason|text)/i.test(key) &&
      !/(count|amount|quantity|qty|price|rate|ratio|percent)/i.test(key)) {
    return textTitle.test(title) ? 'left' : undefined;
  }
  if (statusTitle.test(title) && !/(名称|编码|编号|路径|内容|描述|备注)/.test(title) &&
      !/(execute|response|content|description)/i.test(key)) return 'center';
  const identifierTitle = /单号|编号|编码|手机号|联系电话|电话|日期|时间|时点|名称|地址|备注|描述|说明|路径|内容|摘要|流水号/;
  const quantifiedTitle = /数量|数|金额|量|次数|占比|比例|百分比|税率|费率|单价|售价|进价|余额|额度|上限|下限|载重|容积|里程|距离|耗时|时长|重量|体积|利润|成本|佣金|毛利|合计/;
  const hasNumberLabel = numericTitle.test(title);
  const numericByKey = keyTokens.some((part) => numericTokens.has(part));
  if ((hasNumberLabel || numericByKey) &&
      !(identifierTitle.test(title) && !quantifiedTitle.test(title)) &&
      !(keyTokens.includes('source') && !quantifiedTitle.test(title)) &&
      !(keyTokens.includes('text') || keyTokens.includes('reason'))) return 'right';
  if (imageTitle.test(title)) return 'center';
  if (textTitle.test(title)) return 'left';
  return undefined;
}

function alignmentViolations(file) {
  const source = readFileSync(file, 'utf8');
  const scriptBlocks = file.endsWith('.vue')
    ? [...source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map((match) => match[1])
    : [source];
  const violations = [];
  for (const block of scriptBlocks) {
    const sourceFile = ts.createSourceFile(file, block, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
    function visit(node) {
      if (ts.isObjectLiteralExpression(node)) {
        const titleProperty = property(node, 'title');
        const dataIndexProperty = property(node, 'dataIndex');
        const keyProperty = property(node, 'key');
        const hasTableColumnProperty = ['width', 'align', 'sorter', 'fixed', 'customRender']
          .some((name) => property(node, name));
        const dataProperty = dataIndexProperty || (hasTableColumnProperty && keyProperty);
        if (titleProperty && dataProperty) {
          const title = stringValue(titleProperty.initializer);
          const dataIndex = stringValue(dataProperty.initializer);
          if (dataIndex) {
            const expected = expectedAlignment(title, dataIndex);
            const alignProperty = property(node, 'align');
            const actual = alignProperty ? stringValue(alignProperty.initializer) : 'left';
            if (expected && actual !== expected) {
              const line = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1;
              violations.push(`${path.relative(srcRoot, file).replace(/\\/g, '/')}:${line} ${title}[${dataIndex}] expected ${expected}, got ${actual || 'dynamic'}`);
            }
          }
        }
      }
      ts.forEachChild(node, visit);
    }
    visit(sourceFile);
  }
  return violations;
}

function column(source, dataIndex) {
  const escapedIndex = dataIndex.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = new RegExp(`\\{[^{}]*dataIndex:\\s*'${escapedIndex}'[^{}]*\\}`).exec(source);
  assert.ok(match, `缺少 dataIndex=${dataIndex} 的表格列`);
  return match[0];
}

test('全局表格样式不覆盖列水平对齐，并让数据单元垂直居中', () => {
  assert.doesNotMatch(tableStyle, /\.ant-table-thead\s*>\s*tr\s*>\s*th\s*\{[^}]*text-align:\s*center\s*!important/s);
  assert.match(tableStyle, /\.ant-table-tbody\s*>\s*tr\s*>\s*td\s*\{[^}]*vertical-align:\s*middle/s);
  assert.match(supplierEditableTable, /th\s*,\s*td\s*\{[^}]*vertical-align:\s*middle/s);
});

test('供应商与应付样板按字段语义设置同轴对齐', () => {
  assert.match(column(supplierPage, 'name'), /align:\s*'left'/);
  assert.match(column(supplierPage, 'skuCount'), /align:\s*'right'/);
  assert.match(column(supplierPage, 'status'), /align:\s*'center'/);
  assert.match(column(supplierPage, 'action'), /align:\s*'center'/);

  assert.match(column(payablePage, 'payableNo'), /align:\s*'left'/);
  assert.match(column(payablePage, 'supplierName'), /align:\s*'left'/);
  assert.match(column(payablePage, 'amount'), /align:\s*'right'/);
  assert.match(column(payablePage, 'writtenOffAmount'), /align:\s*'right'/);
  assert.match(column(payablePage, 'settleState'), /align:\s*'center'/);
  assert.match(column(payablePage, 'action'), /align:\s*'center'/);
  assert.match(column(payablePage, 'redAmount'), /align:\s*'right'/);
});

test('可排序表头让标题与排序图标作为一组沿列对齐轴排列', () => {
  assert.match(tableStyle, /\.ant-table-column-sorters\s*\{\s*justify-content:\s*flex-start/s);
  assert.match(tableStyle, /\[style\*='text-align: right'\].*?\.ant-table-column-sorters\s*\{\s*justify-content:\s*flex-end/s);
  assert.match(tableStyle, /\[style\*='text-align: center'\].*?\.ant-table-column-sorters\s*\{\s*justify-content:\s*center/s);
  assert.match(tableStyle, /\.ant-table-column-title\s*\{\s*flex:\s*0 1 auto/s);
});

test('全站 Vue/TypeScript 表格列按字段语义对齐', () => {
  const violations = sourceFiles(srcRoot).flatMap(alignmentViolations);
  assert.deepEqual(violations, [], `表格列对齐不符合字段语义：\n${violations.join('\n')}`);
});
