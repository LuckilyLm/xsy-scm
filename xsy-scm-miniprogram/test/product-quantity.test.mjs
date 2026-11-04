import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/pages-sub/product/detail.vue', import.meta.url), 'utf8');

function quantityState({quantity = 1, minimum = 1, step = 1, available = 0, sellable = true} = {}) {
  const state = vm.createContext({
    quantity: {value: quantity},
    minQty: {value: minimum},
    stepQty: {value: step},
    maxQty: {value: available},
    orderable: {value: sellable},
    computed: (read) => ({
      get value() {
        return read();
      },
    }),
  });
  // 执行页面真实步进逻辑，避免测试另写一份数量算法。
  const declarations = ['scaled', 'unscaled', 'canDecrease', 'canIncrease'].map((name) => source.match(new RegExp(`const ${name} = [^\\n]+;`))[0]);
  const methods = ['increase', 'decrease'].map((name) => source.match(new RegExp(`function ${name}\\(\\) \\{[\\s\\S]*?\\n\\}`))[0]);
  vm.runInContext([...declarations, ...methods, 'globalThis.add = increase; globalThis.subtract = decrease;'].join('\n'), state);
  return state;
}

test('sellable preorder quantity can increase with zero stock and above available stock', () => {
  for (const available of [0, 1]) {
    const state = quantityState({available});
    state.add();
    assert.equal(state.quantity.value, 2);
    state.add();
    assert.equal(state.quantity.value, 3);
  }
});

test('quantity controls retain minimum and decimal step and stop for unsellable products', () => {
  const state = quantityState({quantity: 0.3, minimum: 0.3, step: 0.1});
  state.subtract();
  assert.equal(state.quantity.value, 0.3);
  state.add();
  assert.equal(state.quantity.value, 0.4);
  state.subtract();
  assert.equal(state.quantity.value, 0.3);
  const blocked = quantityState({quantity: 2, sellable: false});
  blocked.add();
  blocked.subtract();
  assert.equal(blocked.quantity.value, 2);
});
