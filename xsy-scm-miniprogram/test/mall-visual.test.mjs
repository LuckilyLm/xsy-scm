import assert from 'node:assert/strict';
import { readFileSync, existsSync, statSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const { categoryIcon } = await import(`data:text/javascript;base64,${Buffer.from(read('../src/utils/category-icon.js')).toString('base64')}`);

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
    ['水产', 'seafood'],
    ['活鲜', 'seafood'],
    ['鱼虾', 'fish-shrimp'],
  ]) {
    assert.equal(categoryIcon(name), `/static/mall-visual/category/${file}.png`);
  }
  assert.equal(categoryIcon('尚无专属素材', '生鲜蔬菜'), categoryIcon('生鲜蔬菜'));
  assert.equal(categoryIcon('未知分类'), '');
  assert.equal(categoryIcon('全部'), '');
});

test('every supplied local illustration exists and its recorded total fits a small media budget', () => {
  const manifest = JSON.parse(read('../src/static/mall-visual/sources.json'));
  assert.equal(manifest.length, 40);
  let total = 0;
  for (const item of manifest) {
    const path = new URL(`../src/static/mall-visual/${item.file}`, import.meta.url);
    assert.ok(existsSync(path), item.file);
    assert.match(item.sourceSha256, /^[a-f0-9]{64}$/);
    assert.match(item.file, /\.png$/);
    assert.equal(statSync(path).size, item.size);
    total += item.size;
  }
  assert.ok(total < 300 * 1024, `local illustrations occupy ${total} bytes`);
});
