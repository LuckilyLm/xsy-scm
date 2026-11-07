import assert from 'node:assert/strict';
import { readFileSync, existsSync, statSync } from 'node:fs';
import test from 'node:test';
import { withMedia, TEST_MEDIA_ROOT } from './mall-media-loader.mjs';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const { categoryIcon } = await import(
  `data:text/javascript;base64,${Buffer.from(withMedia(read('../src/utils/category-icon.js'))).toString('base64')}`
);

test('real categories select supplied PNGs without changing taxonomy or re-colouring images', () => {
  for (const [name, file] of [
    ['生鲜蔬菜', 'vegetables'],
    ['生鲜水果', 'fruit'],
    ['肉禽蛋品', 'meat-eggs'],
    ['禽蛋类', 'poultry'],
    ['蛋类', 'eggs'],
    ['根茎类', 'roots'],
    ['青菜类', 'leafy'],
    ['猪肉', 'pork'],
    ['水产', 'fish-shrimp'],
    ['活鲜', 'fish-shrimp'],
    ['鱼虾', 'fish-shrimp'],
    ['鱼类', 'fish-shrimp'],
    ['冰鲜', 'fish-shrimp'],
    ['国产水果', 'fruit'],
    ['进口水果', 'fruit'],
    ['时令水果', 'fruit'],
    ['基础调味', 'seasoning'],
    ['复合调味', 'seasoning'],
  ]) {
    assert.equal(categoryIcon(name), `${TEST_MEDIA_ROOT}/mall-visual/category/${file}.png`);
  }
  assert.equal(categoryIcon('尚无专属素材', '生鲜蔬菜'), categoryIcon('生鲜蔬菜'));
  assert.equal(categoryIcon('未知分类'), '');
  assert.equal(categoryIcon('全部'), '');
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
