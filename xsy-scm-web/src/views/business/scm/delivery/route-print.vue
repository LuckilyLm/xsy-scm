<template>
  <a-modal v-model:open="visible" title="线路发货单预览" :width="1000" ok-text="打印"
           :ok-button-props="{ disabled: loading || !data }" @ok="print">
    <a-alert v-if="error" type="error" :message="error" show-icon/>
    <a-spin :spinning="loading">
      <div v-if="data" ref="paper" class="delivery-print">
        <h1>线路发货单</h1>
        <div class="print-meta">
          <p>线路：{{ data.detail.route.routeNo }} · {{ data.detail.route.routeName }}</p>
          <p>配送日期：{{ data.detail.route.deliveryDate }}　仓库：{{ data.detail.route.warehouseNameSnapshot }}</p>
          <p>
            司机：{{
              data.detail.route.driverNameSnapshot || '未分配'
            }}　电话：{{ data.detail.route.driverPhoneSnapshot || '—' }}　车辆：{{
              data.detail.route.vehicleNoSnapshot || '未分配'
            }}
          </p>
          <p>
            订单 {{ data.detail.route.orderCount }} 张 · 停靠点 {{ data.detail.route.stopCount }} 个<span
              v-if="canViewAmount"> · 订单金额 {{ money(data.detail.route.totalAmount) }}</span>
          </p>
          <p v-if="data.detail.route.remark">备注：{{ data.detail.route.remark }}</p>
        </div>
        <section v-for="stop in data.detail.stops" :key="stop.id">
          <h2>{{ stop.stopSeq }}. {{ stop.customerNameSnapshot }}</h2>
          <p>{{ stop.addressSnapshot }} · {{ stop.receiverNameSnapshot || '—' }} · {{
              stop.receiverPhoneSnapshot || '—'
            }}</p>
          <template v-for="order in ordersOf(stop.id)" :key="order.orderId">
            <h3>
              {{ order.orderNoSnapshot }}　期望配送：{{ datetime(order.expectDeliveryTimeSnapshot) }}<span
                v-if="canViewAmount">　订单金额：{{ money(order.orderAmountSnapshot) }}</span>
            </h3>
            <table>
              <thead>
              <tr>
                <th>商品 / 规格</th>
                <th>单位</th>
                <th>订购数量</th>
                <th>实重 / 实际量</th>
                <th v-if="canViewAmount">订单金额</th>
                <th v-if="canViewAmount">结算金额</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="item in itemsOf(order.orderId)" :key="item.id">
                <td>{{ item.productNameSnapshot }} {{ item.specNameSnapshot }}</td>
                <td>{{ item.saleUnitSnapshot }}</td>
                <td class="numeric">{{ item.orderedQuantity }}</td>
                <td class="numeric">{{ item.actualQuantity ?? '—' }}</td>
                <td v-if="canViewAmount" class="numeric">{{ money(item.orderedLineAmount) }}</td>
                <td v-if="canViewAmount" class="numeric">{{ money(item.settlementLineAmount) }}</td>
              </tr>
              </tbody>
            </table>
          </template>
          <p v-if="stop.remark">停靠备注：{{ stop.remark }}</p>
        </section>
        <p class="print-note">本单为配送计划；实重未填写显示“—”。打印不代表发货确认，不扣减库存。</p>
      </div>
    </a-spin>
  </a-modal>
</template>
<script setup lang="ts">
import {nextTick, ref} from 'vue';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import {deliveryError, type Id, type RoutePrint} from './delivery-types';
import {money} from './delivery-display';
import {useDeliveryPermission} from './use-delivery-permission';
import {datetime} from '../common/scm-display';

const {canViewAmount} = useDeliveryPermission();

const visible = ref(false),
    loading = ref(false),
    error = ref(''),
    data = ref<RoutePrint>();
const paper = ref<HTMLElement>();

function ordersOf(stopId: Id) {
  return data.value?.detail.orders.filter((o) => String(o.stopId) === String(stopId)) ?? [];
}

function itemsOf(orderId: Id) {
  return data.value?.items.filter((i) => String(i.orderId) === String(orderId)) ?? [];
}

async function open(id: Id) {
  visible.value = true;
  loading.value = true;
  error.value = '';
  data.value = undefined;
  try {
    data.value = (await deliveryApi.print(id)).data;
  } catch (e) {
    error.value = deliveryError(e);
  } finally {
    loading.value = false;
  }
}

async function print() {
  await nextTick();
  if (!paper.value) return;
  // Clone rendered text nodes; no business content is interpolated into HTML strings.
  const frame = document.createElement('iframe');
  frame.setAttribute('title', '线路发货单打印');
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0';
  document.body.appendChild(frame);
  const doc = frame.contentDocument;
  const win = frame.contentWindow;
  if (!doc || !win) {
    frame.remove();
    error.value = '无法打开打印窗口';
    return;
  }
  doc.title = '线路发货单';
  const style = doc.createElement('style');
  style.textContent =
      '@page{size:A4;margin:12mm}body{font:12px sans-serif;color:#000}h1{font-size:22px}h2{font-size:16px;margin-top:22px;break-after:avoid}h3{font-size:12px;break-after:avoid}p{line-height:1.6}table{width:100%;border-collapse:collapse}th,td{border:1px solid #777;padding:6px;text-align:left}thead{display:table-header-group}tr{break-inside:avoid}.numeric{text-align:right;font-variant-numeric:tabular-nums}.print-note{margin-top:20px}';
  doc.head.appendChild(style);
  doc.body.appendChild(paper.value.cloneNode(true));
  await doc.fonts.ready;
  win.addEventListener('afterprint', () => frame.remove(), {once: true});
  win.focus();
  win.print();
  window.setTimeout(() => frame.remove(), 60000);
}

defineExpose({open});
</script>
<style scoped>
.delivery-print {
  background: #fff;
  color: #1f2329;
  padding: 24px;
}

.delivery-print h1 {
  font-size: 24px;
}

.delivery-print h2 {
  font-size: 18px;
  margin-top: 24px;
}

.delivery-print h3 {
  font-size: 14px;
}

.delivery-print table {
  width: 100%;
  border-collapse: collapse;
}

.delivery-print th,
.delivery-print td {
  border: 1px solid #e5e6eb;
  padding: 8px;
}

.numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.print-note {
  margin-top: 24px;
}
</style>
