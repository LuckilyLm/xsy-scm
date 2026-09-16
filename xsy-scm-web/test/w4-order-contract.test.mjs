import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {newOrder,validateOrder,payload,amount,fixed} from '../src/views/business/scm/order/order-form-model.ts';
import {orderError} from '../src/views/business/scm/order/order-errors.ts';
function form(){const f=newOrder();f.customerId='1';f.address={receiverName:'客户',receiverPhone:'123',address:'地址'};f.items=[{skuId:'3',orderedQuantity:'1.0000',manualPriceOverride:false}];return f;}
test('null, zero and not-yet-settled amounts remain distinguishable',()=>{assert.equal(amount(null,true),'未定价');assert.equal(amount(null),'—');assert.equal(amount('0.0000',true),'¥ 0.0000');assert.equal(fixed('1.00005'),'1.0001');});
test('valid draft and supplement validation',()=>{const f=form();assert.equal(validateOrder(f),undefined);f.orderSource='SUPPLEMENT';assert.match(validateOrder(f),/补单原因/);f.supplementReason='补录';assert.equal(validateOrder(f),undefined);});
test('duplicate SKU and missing override reason rejected',()=>{const f=form();f.items.push({...f.items[0]});assert.match(validateOrder(f),/重复/);f.items.pop();f.items[0].manualPriceOverride=true;f.items[0].unitPrice='0.0000';assert.match(validateOrder(f),/原因/);f.items[0].overrideReason='免费';assert.equal(validateOrder(f),undefined);});
test('payload preserves item identity and removes inactive override',()=>{const f=form();Object.assign(f.items[0],{itemId:'9',version:3,unitPrice:'2.0000',overrideReason:'old'});assert.deepEqual(payload(f).items[0],{itemId:'9',version:3,skuId:'3',orderedQuantity:'1.0000',manualPriceOverride:false,unitPrice:null,overrideReason:null,sortOrder:0});});
test('stale data and availability errors are actionable',()=>{assert.match(orderError({code:40921}),/刷新/);assert.match(orderError({code:40949}),/未定价/);assert.equal(orderError({msg:'业务提示'}),'业务提示');assert.match(orderError({data:{code:40921}}),/刷新/);assert.equal(orderError({response:{data:{msg:'业务提示'}}}),'业务提示');});
test('frontend enum excludes fulfillment and unpriced source values',()=>{const source=readFileSync(new URL('../src/constants/business/scm/order-const.ts',import.meta.url),'utf8');assert.match(source,/DRAFT.*PENDING.*CONFIRMED.*CANCELLED/);assert.doesNotMatch(source,/UNPRICED|DELIVERING|PURCHASING|SIGNED/);});
