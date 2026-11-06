import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/pages-sub/product/detail.vue', import.meta.url), 'utf8');

test('preorder opens confirmation without adding, while stock and inquiry retain their actions', () => {
  const card = readFileSync(new URL('../src/components/business/product-card.vue', import.meta.url), 'utf8');
  const display = readFileSync(new URL('../src/utils/product-display.js', import.meta.url), 'utf8');
  const events = [];
  const product = { price: '10.0000', sellable: true, stockStatus: 'OUT_OF_STOCK' };
  const state = vm.createContext({
    props: { product },
    emit: (name, value) => events.push({ name, value }),
    computed: (read) => ({
      get value() {
        return read();
      },
    }),
  });
  vm.runInContext(display.replace(/^import .*;$/gm, '').replace(/export /g, ''), state);
  const start = card.indexOf('  const priced =');
  const end = card.indexOf('  /*', card.indexOf('  function onAction()'));
  vm.runInContext(card.slice(start, end) + '\nglobalThis.action = onAction;', state);
  state.action();
  assert.equal(events.at(-1).name, 'click');
  assert.equal(
    events.some((event) => event.name === 'add'),
    false
  );
  product.stockStatus = 'IN_STOCK';
  state.action();
  assert.equal(events.at(-1).name, 'add');
  product.sellable = false;
  const before = events.length;
  state.action();
  assert.equal(events.length, before);
  product.price = null;
  state.action();
  assert.equal(events.at(-1).name, 'inquiry');
  assert.ok(events.every((event) => event.value === product));
});

function quantityState({ quantity = 1, minimum = 1, step = 1, available = 0, sellable = true } = {}) {
  const state = vm.createContext({
    quantity: { value: quantity },
    minQty: { value: minimum },
    stepQty: { value: step },
    maxQty: { value: available },
    orderable: { value: sellable },
    computed: (read) => ({
      get value() {
        return read();
      },
    }),
  });
  // 执行页面真实步进逻辑，避免测试另写一份数量算法。
  const declarations = ['scaled', 'unscaled', 'canDecrease', 'canIncrease'].map((name) => source.match(new RegExp(`const ${name} = [^\\n]+;`))[0]);
  // 按花括号配对截取完整函数，不依赖缩进：格式变化不会再把函数截断到内层 if 的右括号。
  const functionSource = (name) => {
    const start = source.search(new RegExp(`function ${name}\\(\\) \\{`));
    let depth = 0;
    for (let i = source.indexOf('{', start); i < source.length; i += 1) {
      if (source[i] === '{') depth += 1;
      else if (source[i] === '}') {
        depth -= 1;
        if (depth === 0) return source.slice(start, i + 1);
      }
    }
    throw new Error(`function ${name} not found`);
  };
  const methods = ['increase', 'decrease'].map(functionSource);
  vm.runInContext([...declarations, ...methods, 'globalThis.add = increase; globalThis.subtract = decrease;'].join('\n'), state);
  return state;
}

test('sellable preorder quantity can increase with zero stock and above available stock', () => {
  for (const available of [0, 1]) {
    const state = quantityState({ available });
    state.add();
    assert.equal(state.quantity.value, 2);
    state.add();
    assert.equal(state.quantity.value, 3);
  }
});

test('quantity controls retain minimum and decimal step and stop for unsellable products', () => {
  const state = quantityState({ quantity: 0.3, minimum: 0.3, step: 0.1 });
  state.subtract();
  assert.equal(state.quantity.value, 0.3);
  state.add();
  assert.equal(state.quantity.value, 0.4);
  state.subtract();
  assert.equal(state.quantity.value, 0.3);
  const blocked = quantityState({ quantity: 2, sellable: false });
  blocked.add();
  blocked.subtract();
  assert.equal(blocked.quantity.value, 2);
});
