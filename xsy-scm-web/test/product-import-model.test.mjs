import { test } from 'node:test';
import assert from 'node:assert/strict';
import { groupErrorsByRow, hasBlockingErrors, matchFilesBySpuCode } from '../src/views/business/scm/product/product-import-model.ts';

const error = (rowNumber, column, code, spuCode = null, message = code) => ({ rowNumber, spuCode, column, code, message });

test('errors regroup by Excel row, ascending, preserving per-row field order', () => {
  const groups = groupErrorsByRow([
    error(5, '市场价', 'DECIMAL_INVALID', 'SPU5'),
    error(3, '商品名称', 'REQUIRED', 'SPU3'),
    error(5, 'SKU编码', 'REQUIRED', 'SPU5'),
  ]);
  assert.deepEqual(groups.map(g => g.rowNumber), [3, 5]);
  assert.equal(groups[1].spuCode, 'SPU5');
  assert.deepEqual(groups[1].cells.map(c => c.column), ['市场价', 'SKU编码']);
});

test('file-level errors with row 0 stay their own group and never vanish', () => {
  const groups = groupErrorsByRow([error(0, '文件', 'SHEET_COUNT'), error(0, '文件', 'ROW_LIMIT')]);
  assert.equal(groups.length, 1);
  assert.equal(groups[0].cells.length, 2);
});

test('any backend error means the whole import was rejected, not a partial success', () => {
  assert.equal(hasBlockingErrors({ totalErrors: 0 }), false);
  assert.equal(hasBlockingErrors({ totalErrors: 1 }), true);
});

test('filename matches SPU code case-insensitively and ignores the extension', () => {
  const products = [{ spuId: 1, spuCode: 'SPU0001' }, { spuId: 2, spuCode: 'SPU0002' }];
  const [hit] = matchFilesBySpuCode([{ fileName: 'spu0001.png', fileKey: 'public/image/a.png' }], products);
  assert.equal(hit.status, 'matched');
  assert.equal(hit.candidates[0].spuCode, 'SPU0001');
});

test('unmatched and ambiguous files are surfaced, never silently dropped', () => {
  const products = [{ spuId: 1, spuCode: 'A' }, { spuId: 2, spuCode: 'A' }, { spuId: 3, spuCode: 'B' }];
  const results = matchFilesBySpuCode([
    { fileName: 'A.jpg', fileKey: 'k1' },
    { fileName: 'B.jpg', fileKey: 'k2' },
    { fileName: 'C.jpg', fileKey: 'k3' },
    { fileName: '.png', fileKey: 'k4' },
  ], products);
  assert.deepEqual(results.map(r => r.status), ['ambiguous', 'matched', 'unmatched', 'unmatched']);
  assert.equal(results[0].candidates.length, 2);
});
