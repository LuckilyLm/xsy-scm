import assert from 'node:assert/strict';
import { readFileSync, existsSync, statSync } from 'node:fs';
import test from 'node:test';
import { withMedia } from './mall-media-loader.mjs';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const { categoryIcon } = await import(
  `data:text/javascript;base64,${Buffer.from(withMedia(read('../src/utils/category-icon.js'))).toString('base64')}`
);

test('category icons use independent backend configuration without name or parent inference', () => {
  const domestic = { categoryName: '国产水果', iconUrl: 'https://images.example/domestic.png' };
  const imported = { categoryName: '进口水果', iconUrl: 'https://images.example/imported.png' };
  assert.equal(categoryIcon(domestic), domestic.iconUrl);
  assert.equal(categoryIcon(imported), imported.iconUrl);
  assert.notEqual(categoryIcon(domestic), categoryIcon(imported));
  assert.equal(categoryIcon({ categoryName: '未知空分类', iconUrl: domestic.iconUrl }), domestic.iconUrl);
  assert.equal(categoryIcon({ categoryName: '国产水果' }), '');
  assert.equal(categoryIcon(null), '');
});

test('every supplied online illustration retains its source and recorded original file size', () => {
  const manifest = JSON.parse(read('../media/mall-visual/sources.json'));
  assert.equal(manifest.length, 33);
  let total = 0;
  for (const item of manifest) {
    const path = new URL(`../media/mall-visual/${item.file}`, import.meta.url);
    assert.ok(existsSync(path), item.file);
    assert.match(item.sourceSha256, /^[a-f0-9]{64}$/);
    assert.match(item.file, /\.png$/);
    assert.equal(statSync(path).size, item.size);
    total += item.size;
  }
  assert.ok(total < 300 * 1024, `local illustrations occupy ${total} bytes`);
});
