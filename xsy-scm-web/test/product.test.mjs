/**
 * 商品模块契约测试。
 *
 * 由 4 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {emptyProduct, emptySku, removeSku, productFormOf, validateProduct} from '../src/views/business/scm/product/product-form-model.ts';
import {groupErrorsByRow, hasBlockingErrors, matchFilesBySpuCode} from '../src/views/business/scm/product/product-import-model.ts';

// ---------------------------------------------------------------- 来源：image-center-pick-columns-contract.test.mjs
{
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
    assert.match(block, /dataIndex:\s*'spuCode'/, '选择区缺少独立的 spuCode 列，编码又会被并回名称格里');
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
}

// ---------------------------------------------------------------- 来源：product-config-tables-contract.test.mjs
{
  /**
   * 商品配置三张表（分类 / 计量单位 / 商品标签）的列与操作列契约测试。
   *
   * 这三张表藏在 `product/components/` 下，「操作列居中」的批量改动
   * （commit ffa95370）只扫了页面级列表，漏掉了组件里的表，所以它们的操作列一直是
   * `align: 'right'` —— 视觉上与全站相反，且靠肉眼验收不容易发现（组件不在主路由上）。
   *
   * 本文件钉住：
   * 1. 操作列居中。
   * 2. 分类表展示父分类、不展示内部编码：保留清单是
   *    分类名称 / 层级 / 父分类 / 状态 / 排序 / 操作，没有分类编码；
   *    父分类从 `categoryPath` 推导（一级分类为 —）。
   * 3. 数值走全局 `.scm-quantity`，不再保留局部 `.price` 样式。
   */

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

  test('三张配置表的操作列统一居中', () => {
    for (const [name, source] of [
      ['分类表', category],
      ['计量单位表', uom],
      ['标签表', tag],
    ]) {
      assert.match(source, /dataIndex: 'action'[\s\S]{0,60}?align: 'center'/, `${name} 的操作列未居中`);
      assert.doesNotMatch(source, /dataIndex: 'action'[\s\S]{0,60}?align: 'right'/, `${name} 的操作列仍是右对齐`);
    }
  });

  test('分类表展示父分类、不展示分类编码', () => {
    assert.match(category, /title: '分类名称'/);
    assert.match(category, /title: '层级'/);
    assert.match(category, /title: '父分类'/);
    assert.match(category, /title: '状态'/);
    assert.match(category, /title: '排序'/);
    // 保留清单没有分类编码；它属于内部维护字段
    assert.ok(!/title: '分类编码'/.test(category), '分类表仍有独立的分类编码列');
    // 父分类由 categoryPath 去掉最后一段推导
    assert.match(category, /record\.categoryPath/);
    assert.match(category, /slice\(0, -1\)/);
    // 一级分类没有父级，空值统一 —
    assert.match(category, /return segments\.length > 1 \? segments\.slice\(0, -1\)\.join\(' \/ '\) : '—'/);
  });

  test('单位表与标签表保留字典编码、不展示时间列', () => {
    // 单位编码可保留（维护型字典）
    assert.match(uom, /title: '单位编码'/);
    assert.match(uom, /title: '单位名称'/);
    assert.match(uom, /title: '状态'/);
    assert.match(tag, /title: '标签编码'/);
    assert.match(tag, /title: '标签名称'/);
    // 隐藏创建 / 更新时间
    for (const [name, source] of [
      ['单位表', uom],
      ['标签表', tag],
      ['分类表', category],
    ]) {
      assert.ok(!/title: '创建时间'/.test(source), `${name} 仍展示创建时间`);
      assert.ok(!/title: '更新时间'/.test(source), `${name} 仍展示更新时间`);
    }
  });

  test('数值统一走全局 .scm-quantity，不留局部样式', () => {
    for (const [name, source] of [
      ['单位表', uom],
      ['标签表', tag],
    ]) {
      assert.match(source, /class="scm-quantity"/, `${name} 未使用 .scm-quantity`);
      assert.ok(!/class="price"/.test(source), `${name} 仍用局部 .price 类`);
      assert.ok(!/\.price\s*\{/.test(source), `${name} 仍定义局部 .price 样式`);
    }
  });
}

// ---------------------------------------------------------------- 来源：product-form-model.test.mjs
{
  test('new product starts with one default SKU and zero price stays explicit', () => {
    const form = emptyProduct();
    assert.equal(form.skuList.length, 1);
    assert.equal(form.skuList[0].defaultFlag, true);
    assert.equal(form.skuList[0].marketPrice, '0.0000');
  });
  test('PCO-1 master fields start at the backend defaults', () => {
    const form = emptyProduct();
    // tagIds 与 taxExempt 是新增接口的必填项，缺省值必须能直接提交。
    assert.deepEqual(form.tagIds, []);
    assert.equal(form.taxExempt, false);
    assert.equal(form.masterStatus, 'ENABLED');
    assert.equal(form.shelfLifeDays, null);
    assert.equal(form.lossRate, null);
    assert.equal(form.taxRate, null);
  });
  test('detail VO maps to the edit form: tags become tagIds and summary fields stay out', () => {
    const detail = {
      ...emptyProduct(),
      spuId: 11,
      version: 3,
      masterStatus: 'DISABLED',
      skuList: [emptySku(0, true)],
      tags: [{tagId: 5, tagCode: 'T', name: '有机', status: 'DISABLED'}],
      skuCount: 2,
      categoryPath: '生鲜 / 蔬菜',
      minMarketPrice: '1.0000',
    };
    const form = productFormOf(detail);
    // 编辑接口按 tagIds 整批替换：漏映射等于把这个商品的标签清空。
    assert.deepEqual(form.tagIds, [5]);
    assert.equal(form.masterStatus, 'DISABLED');
    assert.equal(form.version, 3);
    assert.equal(form.skuCount, undefined);
    assert.equal(form.categoryPath, undefined);
    assert.equal(form.tags, undefined);
    // SKU 深拷贝：弹窗里改数量不能写回列表页已缓存的行对象。
    assert.deepEqual(form.skuList, detail.skuList);
    assert.notEqual(form.skuList[0], detail.skuList[0]);
  });
  test('archived product cannot stay on shelf in the form model either', () => {
    const form = emptyProduct();
    Object.assign(form.skuList[0], {skuCode: 'A', specName: '大', saleUnit: 'kg'});
    form.masterStatus = 'ARCHIVED';
    assert.match(validateProduct(form), /归档/);
    form.status = 'OFF_SHELF';
    assert.equal(validateProduct(form), undefined);
  });
  test('last SKU cannot be removed; removing default selects first survivor and keeps identities', () => {
    assert.throws(() => removeSku(emptyProduct().skuList, 0));
    const rows = [
      {...emptySku(0, true), skuId: 7},
      {...emptySku(1), skuId: 8},
      {...emptySku(2), skuId: 9},
    ];
    const saved = removeSku(rows, 0);
    assert.deepEqual(
      saved.map((s) => [s.skuId, s.defaultFlag]),
      [
        [8, true],
        [9, false],
      ]
    );
  });
  test('validates four decimal prices and normalized duplicate specifications', () => {
    const form = emptyProduct();
    Object.assign(form.skuList[0], {skuCode: 'A', specName: '大', saleUnit: 'kg', specValues: {Size: ' Large '}});
    assert.equal(validateProduct(form), undefined);
    form.skuList[0].marketPrice = '1.23456';
    assert.match(validateProduct(form), /两位/);
    form.skuList[0].marketPrice = '0.0000';
    form.skuList.push({...form.skuList[0], skuCode: 'B', defaultFlag: false, specValues: {size: 'large'}});
    assert.match(validateProduct(form), /规格组合重复/);
  });
}

// ---------------------------------------------------------------- 来源：product-import-model.test.mjs
{
  const error = (rowNumber, column, code, spuCode = null, message = code) => ({rowNumber, spuCode, column, code, message});

  test('errors regroup by Excel row, ascending, preserving per-row field order', () => {
    const groups = groupErrorsByRow([
      error(5, '市场价', 'DECIMAL_INVALID', 'SPU5'),
      error(3, '商品名称', 'REQUIRED', 'SPU3'),
      error(5, 'SKU编码', 'REQUIRED', 'SPU5'),
    ]);
    assert.deepEqual(
      groups.map((g) => g.rowNumber),
      [3, 5]
    );
    assert.equal(groups[1].spuCode, 'SPU5');
    assert.deepEqual(
      groups[1].cells.map((c) => c.column),
      ['市场价', 'SKU编码']
    );
  });

  test('file-level errors with row 0 stay their own group and never vanish', () => {
    const groups = groupErrorsByRow([error(0, '文件', 'SHEET_COUNT'), error(0, '文件', 'ROW_LIMIT')]);
    assert.equal(groups.length, 1);
    assert.equal(groups[0].cells.length, 2);
  });

  test('any backend error means the whole import was rejected, not a partial success', () => {
    assert.equal(hasBlockingErrors({totalErrors: 0}), false);
    assert.equal(hasBlockingErrors({totalErrors: 1}), true);
  });

  test('filename matches SPU code case-insensitively and ignores the extension', () => {
    const products = [
      {spuId: 1, spuCode: 'SPU0001'},
      {spuId: 2, spuCode: 'SPU0002'},
    ];
    const [hit] = matchFilesBySpuCode([{fileName: 'spu0001.png', fileKey: 'public/image/a.png'}], products);
    assert.equal(hit.status, 'matched');
    assert.equal(hit.candidates[0].spuCode, 'SPU0001');
  });

  test('unmatched and ambiguous files are surfaced, never silently dropped', () => {
    const products = [
      {spuId: 1, spuCode: 'A'},
      {spuId: 2, spuCode: 'A'},
      {spuId: 3, spuCode: 'B'},
    ];
    const results = matchFilesBySpuCode(
      [
        {fileName: 'A.jpg', fileKey: 'k1'},
        {fileName: 'B.jpg', fileKey: 'k2'},
        {fileName: 'C.jpg', fileKey: 'k3'},
        {fileName: '.png', fileKey: 'k4'},
      ],
      products
    );
    assert.deepEqual(
      results.map((r) => r.status),
      ['ambiguous', 'matched', 'unmatched', 'unmatched']
    );
    assert.equal(results[0].candidates.length, 2);
  });

  test('批量导入按编码向后端反查目标，不再只匹配当前页商品', () => {
    const source = readFileSync(new URL('../src/views/business/scm/product/image-center.vue', import.meta.url), 'utf8');
    assert.match(source, /productImageApi\.matchTargets\(/, '批量导入未请求后端匹配接口');
    assert.match(source, /matchCache/, '批量导入没有编码缓存');
    assert.match(source, /matchFilesBySpuCode\(stagedFiles\.value, \[\.\.\.matchCache\.values\(\)\]\.flat\(\)\)/, '匹配目标未来自编码缓存');
    assert.doesNotMatch(source, /products\.value\.map\(\(row\) => \(\{spuId: row\.spuId, spuCode: row\.spuCode\}\)\)/, '匹配目标仍取当前页商品');
  });
}
