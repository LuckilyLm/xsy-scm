import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { computed, ref } from 'vue';

const read = (file) => readFileSync(new URL(file, import.meta.url), 'utf8');
const strip = (source) => source.replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');
const previewData = (amount = '95.0000', coupon = null) => ({
  estimatedAmount: '100.0000',
  priceFingerprint: 'same-price',
  warehouseId: 1,
  warehouses: [{ warehouseId: 1, warehouseName: '配送仓' }],
  warehouseRequired: false,
  items: [{ skuId: 1008, quantity: '1.0000', availability: 'AVAILABLE', lineAmount: '100.0000' }],
  promotion: { payableAmount: amount, couponInstanceId: coupon, couponName: coupon ? `优惠券${coupon}` : '' },
});

function engine(quote, list = async () => ({ pages: 0, list: [] })) {
  const context = vm.createContext({
    mallOrderApi: { preview: async (form) => ({ data: await quote(form) }) },
    mallPromotionApi: { myCoupons: async (query) => ({ data: await list(query) }) },
  });
  vm.runInContext(
    strip(read('../src/pages-sub/shared/utils/checkout-preview.js')).replace(
      'export async function previewCheckout',
      'async function previewCheckout'
    ) + '\nglobalThis.run = previewCheckout;',
    context
  );
  return context.run;
}

test('automatic coupons use final server payable, traverse every page and limit concurrent previews', async () => {
  const pages = [];
  let active = 0,
    peak = 0;
  const run = engine(
    async (form) => {
      if (!form.couponInstanceId) return previewData();
      peak = Math.max(peak, ++active);
      await Promise.resolve();
      active--;
      return previewData(form.couponInstanceId === 5 ? '60.0000' : '80.0000', form.couponInstanceId);
    },
    async (query) => {
      pages.push(query.pageNum);
      return { pages: 2, list: (query.pageNum === 1 ? [1, 2, 3, 4] : [5]).map((id) => ({ id, effectiveStatus: 'AVAILABLE' })) };
    }
  );
  const result = await run({ items: [{ skuId: 1, quantity: '1.0000' }] });
  assert.equal(result.couponId, 5);
  assert.equal(result.data.promotion.payableAmount, '60.0000');
  assert.deepEqual(pages, [1, 2]);
  assert.equal(peak, 3);
});

test('inapplicable coupons and equal payable never consume a coupon for no benefit', async () => {
  const run = engine(
    async (form) =>
      form.couponInstanceId === 1 ? { ...previewData(), promotionError: '未达到使用门槛' } : previewData('95.0000', form.couponInstanceId),
    async () => ({ pages: 1, list: [1, 2].map((id) => ({ id, effectiveStatus: 'AVAILABLE' })) })
  );
  assert.equal((await run({ items: [] })).couponId, null);
});

test('manual choice and explicit no-coupon never trigger automatic replacement', async () => {
  let listed = 0;
  const run = engine(
    async (form) => previewData('70.0000', form.couponInstanceId),
    async () => {
      listed++;
      return {};
    }
  );
  assert.equal((await run({ couponInstanceId: 3 }, 'MANUAL')).couponId, 3);
  assert.equal((await run({ couponInstanceId: 3 }, 'NONE')).couponId, null);
  assert.equal(listed, 0);
});

test('a manual coupon that becomes invalid is cleared with a fresh no-coupon quote and explanation', async () => {
  const run = engine(async (form) => (form.couponInstanceId ? { ...previewData(), promotionError: '优惠券已过期' } : previewData()));
  const result = await run({ couponInstanceId: 1 }, 'MANUAL');
  assert.equal(result.couponId, null);
  assert.equal(result.data.promotion.payableAmount, '95.0000');
  assert.match(result.couponNotice, /已过期/);
});

test('coupon amounts compare all four decimal digits without losing large-integer precision', async () => {
  const run = engine(
    async (form) => previewData(form.couponInstanceId ? '9999999999999.0001' : '9999999999999.0002', form.couponInstanceId),
    async () => ({ pages: 1, list: [{ id: 1, effectiveStatus: 'AVAILABLE' }] })
  );
  assert.equal((await run({})).couponId, 1);
});

test('coupon network failure or changing product prices cannot claim to have selected an optimal coupon', async () => {
  for (const changed of [false, true]) {
    const run = engine(
      async (form) => {
        if (!form.couponInstanceId) return previewData();
        if (!changed) throw new Error('网络中断');
        return { ...previewData('80.0000', 1), priceFingerprint: 'new-price' };
      },
      async () => ({ pages: 1, list: [{ id: 1, effectiveStatus: 'AVAILABLE' }] })
    );
    await assert.rejects(run({}), changed ? /价格已更新/ : /网络中断/);
  }
});

test('an obsolete automatic request stops querying later coupon pages', async () => {
  let current = true,
    pages = 0;
  const run = engine(
    async () => previewData(),
    async () => {
      pages++;
      current = false;
      return { pages: 3, list: [] };
    }
  );
  await run({}, 'AUTO', () => current);
  assert.equal(pages, 1);
});

function checkout(quote = async () => ({ data: previewData(), couponId: null, couponName: '', couponNotice: '' })) {
  const hooks = {},
    calls = { preview: [], warehouse: [], routes: [], submitted: [] };
  let addresses = [];
  let cartFailure = false;
  const keyboardHeight = ref(0);
  const context = vm.createContext({
    computed,
    ref,
    onLoad: (fn) => {
      hooks.load = fn;
    },
    onShow: (fn) => {
      hooks.show = fn;
    },
    onHide: (fn) => {
      hooks.hide = fn;
    },
    useMallTheme: () => ({}),
    useKeyboardHeight: () => ({ keyboardHeight }),
    shanghaiDate: () => '2026-11-06',
    displayMoney: String,
    CHECKOUT_ADDRESS: 'picked-address',
    mallCartApi: {
      getCart: async () => {
        if (cartFailure) throw new Error('购物车读取失败');
        return { data: { items: [{ skuId: 1008, quantity: '1.0000', availability: 'AVAILABLE' }] } };
      },
    },
    mallAddressApi: { getList: async () => ({ data: addresses }) },
    mallOrderApi: {
      getWarehouses: async (id) => {
        calls.warehouse.push(id);
        return { data: [{ warehouseId: 1 }] };
      },
      submit: async (form) => {
        calls.submitted.push(form);
        return { data: {} };
      },
    },
    previewCheckout: async (...args) => {
      calls.preview.push(args);
      return quote(...args);
    },
    xsySentry: { captureError() {} },
    uni: {
      getStorageSync: () => '',
      removeStorageSync() {},
      navigateTo: (value) => calls.routes.push(value),
      showToast() {},
      redirectTo() {},
      switchTab() {},
    },
  });
  const source = strip(
    read('../src/pages-sub/checkout/index.vue')
      .match(/<script setup>([\s\S]*?)<\/script>/)[1]
      .trimStart()
  );
  vm.runInContext(
    source +
      '\nglobalThis.page={load,refreshPreview,retryPreview,clearCoupon,chooseCoupon,onDateChange,submit,previewReady,estimatedAmount,promotionError,canSubmit,warehouseId,selectedCouponId,couponMode,loadError,remarkFocused,keyboardHeight};',
    context
  );
  hooks.load({ skus: '1008' });
  return {
    page: context.page,
    calls,
    hooks,
    setAddresses: (value) => {
      addresses = value;
    },
    setCartFailure: (value) => {
      cartFailure = value;
    },
  };
}

test('checkout without an address still fetches the real amount and keeps submission unavailable', async () => {
  const h = checkout();
  await h.page.load();
  assert.equal(h.calls.preview.length, 1);
  assert.equal(h.calls.warehouse[0], undefined);
  assert.equal(h.page.previewReady.value, true);
  assert.equal(h.page.promotionError.value, '');
  assert.equal(h.page.estimatedAmount.value, '100.0000');
  assert.equal(h.page.canSubmit.value, false);
  await h.page.submit();
  assert.equal(h.calls.submitted.length, 0);
});

test('load failures never leave old checkout data eligible for submission and retry recovers', async () => {
  const h = checkout();
  h.setAddresses([{ addressId: 1, isDefault: true }]);
  await h.page.load();
  assert.equal(h.page.canSubmit.value, true);
  h.setCartFailure(true);
  await h.page.load();
  assert.equal(h.page.canSubmit.value, false);
  assert.match(h.page.loadError.value, /购物车读取失败/);
  h.setCartFailure(false);
  await h.page.retryPreview();
  assert.equal(h.page.canSubmit.value, true);
});

test('explicit no-coupon intent survives reload and delivery-date changes', async () => {
  const h = checkout();
  await h.page.load();
  await h.page.clearCoupon();
  await h.page.load();
  await h.page.onDateChange({ detail: { value: '2026-11-07' } });
  assert.equal(h.page.couponMode.value, 'NONE');
  assert.equal(h.calls.preview.at(-1)[1], 'NONE');
});

test('a late old quote cannot overwrite newer totals or coupon selection', async () => {
  const pending = [];
  const h = checkout(() => new Promise((resolve) => pending.push(resolve)));
  const first = h.page.load();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(pending.length, 1);
  const second = h.page.refreshPreview();
  assert.equal(pending.length, 2);
  pending[1]({ data: previewData('70.0000', 2), couponId: 2, couponName: '新券', couponNotice: '' });
  await second;
  pending[0]({ data: previewData('80.0000', 1), couponId: 1, couponName: '旧券', couponNotice: '' });
  await first;
  assert.equal(h.page.selectedCouponId.value, 2);
  assert.equal(h.page.previewReady.value, true);
});

test('hiding checkout resets keyboard and focus state and invalidates pending quotes', async () => {
  const h = checkout();
  await h.page.load();
  for (let i = 0; i < 8; i++) {
    h.page.remarkFocused.value = true;
    h.page.keyboardHeight.value = 300;
    h.hooks.hide();
    assert.equal(h.page.remarkFocused.value, false);
    assert.equal(h.page.keyboardHeight.value, 0);
  }
});
