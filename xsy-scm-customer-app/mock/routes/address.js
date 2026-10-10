/*
 * Mock 契约层 · 收货地址
 *
 * 与后端 /scm/mall/addresses/** 同构：归属客户、一客一默认地址、
 * 首个地址自动成为默认、删除默认后自动提升最早的一条。
 * 仅用于开发环境（VITE_APP_USE_MOCK=true）。
 */
import { currentCustomer, fail, ok } from '../helpers';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';

/** 内存地址簿：customerKey -> Array<address> */
const BOOKS = new Map();
let seq = 0;

function customerKey(session) {
  return String(session.customerId || session.loginPhone || 'anon');
}

function bookOf(session) {
  const key = customerKey(session);
  if (!BOOKS.has(key)) {
    BOOKS.set(key, []);
  }
  return BOOKS.get(key);
}

function sorted(session) {
  return [...bookOf(session)].sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || a.addressId - b.addressId);
}

function guard(session) {
  return session ? null : fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
}

function applyPayload(address, data) {
  address.receiverName = String(data?.receiverName || '').trim();
  address.receiverPhone = String(data?.receiverPhone || '').trim();
  address.provinceName = data?.provinceName || null;
  address.cityName = data?.cityName || null;
  address.districtName = data?.districtName || null;
  address.detailAddress = String(data?.detailAddress || '').trim();
}

function invalid(address) {
  return !address.receiverName || !address.receiverPhone || !address.detailAddress;
}

function clearDefault(book) {
  book.forEach((item) => {
    item.isDefault = false;
  });
}

export const addressRoutes = [
  {
    method: 'GET',
    path: '/scm/mall/addresses',
    handler: async ({ session }) => guard(session) || ok(sorted(session)),
  },
  {
    method: 'GET',
    path: '/scm/mall/addresses/:id',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const hit = bookOf(session).find((item) => item.addressId === Number(params.id));
      return hit ? ok(hit) : fail(40484, '收货地址不存在');
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/addresses',
    handler: async ({ data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const book = bookOf(session);
      const address = { addressId: (seq += 1), isDefault: false };
      applyPayload(address, data);
      if (invalid(address)) return fail(400, '请填写收货人、联系电话与详细地址');
      if (!book.length || data?.isDefault === true) {
        clearDefault(book);
        address.isDefault = true;
      }
      book.push(address);
      return ok(sorted(session));
    },
  },
  {
    method: 'PUT',
    path: '/scm/mall/addresses/:id',
    handler: async ({ params, data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const book = bookOf(session);
      const address = book.find((item) => item.addressId === Number(params.id));
      if (!address) return fail(40484, '收货地址不存在');
      applyPayload(address, data);
      if (invalid(address)) return fail(400, '请填写收货人、联系电话与详细地址');
      if (data?.isDefault === true) {
        clearDefault(book);
        address.isDefault = true;
      }
      return ok(sorted(session));
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/addresses/:id/default',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const book = bookOf(session);
      const address = book.find((item) => item.addressId === Number(params.id));
      if (!address) return fail(40484, '收货地址不存在');
      clearDefault(book);
      address.isDefault = true;
      return ok(sorted(session));
    },
  },
  {
    method: 'DELETE',
    path: '/scm/mall/addresses/:id',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const book = bookOf(session);
      const index = book.findIndex((item) => item.addressId === Number(params.id));
      if (index < 0) return fail(40484, '收货地址不存在');
      const [removed] = book.splice(index, 1);
      if (removed.isDefault && book.length) {
        book[0].isDefault = true;
      }
      return ok(sorted(session));
    },
  },
];
