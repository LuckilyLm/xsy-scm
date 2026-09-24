<template>
  <a-drawer v-model:open="visible" title="配送线路详情" width="min(1500px, 96vw)" :destroy-on-close="true">
    <a-alert v-if="error" :message="error" type="error" show-icon
    >
      <template #action>
        <a-button @click="reload">刷新线路</a-button>
      </template>
    </a-alert
    >
    <a-spin :spinning="loading">
      <template v-if="detail">
        <div class="route-heading">
          <div>
            <h2>{{ detail.route.routeName }}</h2>
            <span>{{ detail.route.routeNo }} · {{ detail.route.deliveryDate }}</span>
          </div>
          <a-space wrap>
            <a-tag :color="routeStatuses[detail.route.status].color">{{
                routeStatuses[detail.route.status].label
              }}
            </a-tag>
            <a-button
                v-if="detail.route.status === 'DRAFT'"
                v-privilege="'scm:delivery:route:update'"
                :disabled="busy"
                @click="formDrawer?.open(detail.route)"
            >编辑信息
            </a-button
            >
            <a-button v-if="detail.route.status === 'DRAFT'" type="primary" v-privilege="'scm:delivery:route:plan'"
                      :disabled="busy" @click="plan"
            >确认规划
            </a-button
            >
            <a-button
                v-if="['PLANNED', 'DISPATCHED', 'COMPLETED'].includes(detail.route.status)"
                v-privilege="'scm:delivery:route:print'"
                @click="printer?.open(detail.route.id)"
            >打印发货单
            </a-button
            >
            <a-button
                v-if="['DRAFT', 'PLANNED'].includes(detail.route.status)"
                danger
                v-privilege="'scm:delivery:route:cancel'"
                :disabled="busy"
                @click="openReason('cancel')"
            >取消线路
            </a-button
            >
            <a-button v-privilege="'support:operateLog:query'" :disabled="routeId == null" @click="openOperateLog">操作日志</a-button>
          </a-space>
        </div>
        <div class="route-summary">
          <span
          >停靠点 <strong>{{ detail.route.stopCount }}</strong></span
          ><span
        >订单 <strong>{{ detail.route.orderCount }}</strong></span
        ><span v-if="canViewAmount">订单金额 <strong>{{ money(detail.route.totalAmount) }}</strong></span
        ><span
        >停靠点定位 <strong>{{ detail.route.locatedCount }} / {{ detail.route.stopCount }}</strong></span
        >
        </div>
        <a-tabs v-model:active-key="tab">
          <a-tab-pane key="base" tab="基础信息">
            <a-descriptions bordered :column="2" size="small">
              <a-descriptions-item label="仓库">{{ detail.route.warehouseNameSnapshot }}</a-descriptions-item>
              <a-descriptions-item label="仓库定位">{{
                  isLocated(startPoint) ? `已定位 · ${detail.route.startGeomCrs}` : '未定位'
                }}
              </a-descriptions-item>
              <a-descriptions-item label="仓库地址" :span="2">{{
                  detail.route.warehouseAddressSnapshot || '—'
                }}
              </a-descriptions-item>
              <a-descriptions-item label="司机"
              >{{ detail.route.driverNameSnapshot || '未分配' }} {{ detail.route.driverPhoneSnapshot }}
              </a-descriptions-item
              >
              <a-descriptions-item label="车辆">{{ detail.route.vehicleNoSnapshot || '未分配' }}</a-descriptions-item>
              <a-descriptions-item label="计划发车">{{
                  datetime(detail.route.plannedDepartureTime)
                }}
              </a-descriptions-item>
              <a-descriptions-item label="备注">{{ detail.route.remark || '—' }}</a-descriptions-item>
              <a-descriptions-item v-if="detail.route.cancelReason" label="取消原因" :span="2">
                {{ detail.route.cancelReason }}
              </a-descriptions-item>
            </a-descriptions>
          </a-tab-pane>
          <a-tab-pane key="orders" tab="线路订单">
            <div class="smart-table-btn-block">
              <a-button
                  v-if="detail.route.status === 'DRAFT'"
                  type="primary"
                  v-privilege="'scm:delivery:route:update'"
                  :disabled="busy"
                  @click="candidates?.open(detail.route)"
              >加入订单
              </a-button
              >
            </div>
            <a-table
                id="scm-delivery-route-orders"
                size="small"
                :columns="orderColumns"
                :data-source="detail.orders"
                row-key="id"
                :pagination="false"
                :scroll="{ x: 1100 }"
                bordered
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'stop'"
                >{{ stopOf(record.stopId)?.stopSeq }} · {{ stopOf(record.stopId)?.customerNameSnapshot }}
                </template
                >
                <template v-else-if="column.dataIndex === 'address'">{{
                    stopOf(record.stopId)?.addressSnapshot
                  }}
                </template>
                <template v-else-if="column.dataIndex === 'orderAmountSnapshot'">{{
                    money(record.orderAmountSnapshot)
                  }}
                </template>
                <template v-else-if="column.dataIndex === 'expectDeliveryTimeSnapshot'">
                  {{ datetime(record.expectDeliveryTimeSnapshot) }}
                </template>
                <template v-else-if="column.dataIndex === 'location'"
                >
                  <a-tag :color="isLocated(stopOf(record.stopId) ?? {}) ? 'green' : 'default'">{{
                      isLocated(stopOf(record.stopId) ?? {}) ? '已定位' : '未定位'
                    }}
                  </a-tag>
                </template
                >
                <template v-else-if="column.dataIndex === 'action'"
                >
                  <a-button
                      v-if="detail.route.status === 'DRAFT'"
                      type="link"
                      danger
                      v-privilege="'scm:delivery:route:update'"
                      :disabled="busy"
                      @click="openReason('remove', record.orderId)"
                  >移除
                  </a-button
                  >
                </template
                >
              </template>
            </a-table>
          </a-tab-pane>
          <a-tab-pane key="map" tab="停靠点 / 路线地图">
            <a-alert message="计划线路，仅表示配送停靠顺序，不代表实时车辆轨迹或导航路径。" type="info" show-icon/>
            <a-alert
                v-if="!allLocated"
                :message="`尚有 ${detail.route.stopCount - detail.route.locatedCount} 个停靠点未定位${
                !isLocated(startPoint) ? '，仓库起点也未定位' : ''
              }。补齐后才能确认规划。`"
                type="warning"
                show-icon
            />
            <div class="route-map-layout">
              <div class="route-stops">
                <div class="warehouse-stop">
                  <strong>起点 · {{ detail.route.warehouseNameSnapshot }}</strong>
                  <p>{{ detail.route.warehouseAddressSnapshot || '未填写地址' }}</p>
                </div>
                <p v-if="canEdit">拖动停靠点排序，或使用上移 / 下移。顺序调整后自动保存。</p>
                <a-empty v-if="!detail.stops.length" description="还没有停靠点，请先在线路订单中加入订单"/>
                <ol>
                  <li
                      v-for="(stop, index) in detail.stops"
                      :key="stop.id"
                      :draggable="canEdit && !busy"
                      @dragstart="dragFrom = index"
                      @dragend="dragFrom = undefined"
                      @dragover.prevent
                      @drop.prevent="drop(index)"
                  >
                    <div class="stop-heading">
                      <strong>{{ stop.stopSeq }}. {{ stop.customerNameSnapshot }}</strong
                      >
                      <a-tag :color="isLocated(stop) ? 'green' : 'default'">{{
                          isLocated(stop) ? '已定位' : '未定位'
                        }}
                      </a-tag>
                    </div>
                    <p>{{ stop.addressSnapshot }}</p>
                    <p>{{ stop.receiverNameSnapshot || '—' }} · {{ stop.receiverPhoneSnapshot || '—' }}</p>
                    <p>
                      {{ stop.orderCount }} 张订单<span v-if="canViewAmount"> · {{ money(stop.totalAmount) }}</span><span
                        v-if="stop.geomCrs"> · {{ stop.geomCrs }}</span>
                    </p>
                    <p v-if="stop.plannedArrivalTime">计划到达：{{ datetime(stop.plannedArrivalTime) }}</p>
                    <a-space v-if="canEdit"
                    >
                      <a-button
                          size="small"
                          :disabled="index === 0 || busy"
                          :aria-label="`上移${stop.customerNameSnapshot}`"
                          @click="move(index, index - 1)"
                      >上移
                      </a-button
                      >
                      <a-button
                          size="small"
                          :disabled="index === detail.stops.length - 1 || busy"
                          :aria-label="`下移${stop.customerNameSnapshot}`"
                          @click="move(index, index + 1)"
                      >下移
                      </a-button
                      >
                      <a-button size="small" :disabled="busy" @click="editStop(stop)">定位 / 备注</a-button>
                    </a-space
                    >
                  </li>
                </ol>
              </div>
              <ScmMap v-if="tab === 'map'" :points="mapPoints" route/>
            </div>
          </a-tab-pane>
          <a-tab-pane key="print" tab="配送打印">
            <div class="smart-table-btn-block">
              <a-segmented
                  v-model:value="printMode"
                  :options="[
                    { label: '按订单', value: 'orders' },
                    { label: '按客户', value: 'customers' },
                  ]"
              />
              <a-select
                  v-if="printMode === 'customers'"
                  v-model:value="customerStatusFilter"
                  style="width: 150px"
                  :options="[
                    { label: '全部客户', value: 'ALL' },
                    { label: '未打印客户', value: 'UNPRINTED' },
                    { label: '部分打印客户', value: 'PARTIAL' },
                    { label: '已打印客户', value: 'PRINTED' },
                  ]"
              />
              <a-select
                  v-if="printMode === 'customers'"
                  v-model:value="customerFilter"
                  style="width: 160px"
                  :options="[
                    { label: '全部订单', value: 'ALL' },
                    { label: '仅未打印订单', value: 'UNPRINTED' },
                    { label: '仅已打印订单', value: 'PRINTED' },
                  ]"
              />
              <a-button
                  v-if="canPrint"
                  type="primary"
                  v-privilege="'scm:delivery:route:print'"
                  :disabled="busy || !canRecordPrint"
                  @click="recordPrint"
              >生成打印 · 登记 {{ printTargetCount }}
              </a-button>
            </div>
            <a-alert
                message="「生成打印」仅登记本次已生成打印预览并累加计次，不代表发货确认，也不扣减库存。客户与订单的打印状态在生成时由服务端按当前有效订单重新判定，列表状态仅供预览。"
                type="info"
                show-icon
            />
            <a-table
                v-if="printMode === 'orders'"
                size="small"
                :columns="orderViewColumns"
                :data-source="ordersView"
                row-key="orderId"
                :loading="printLoading"
                :pagination="false"
                :scroll="{ x: 1080 }"
                :row-selection="orderRowSelection"
                bordered
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'orderAmount'">{{ money(record.orderAmount) }}</template>
                <template v-else-if="column.dataIndex === 'printStatus'">
                  <a-tag :color="printStatuses[record.printStatus as PrintStatus].color">{{
                      printStatuses[record.printStatus as PrintStatus].label
                    }}
                  </a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'lastPrintedAt'">
                  {{ record.lastPrintedAt ? datetime(record.lastPrintedAt) : '—' }}
                </template>
              </template>
            </a-table>
            <a-table
                v-else
                size="small"
                :columns="customerViewColumns"
                :data-source="customersShown"
                row-key="customerId"
                :loading="printLoading"
                :pagination="false"
                :scroll="{ x: 760 }"
                :row-selection="customerRowSelection"
                bordered
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'totalAmount'">{{ money(record.totalAmount) }}</template>
                <template v-else-if="column.dataIndex === 'printStatus'">
                  <a-tag :color="printStatuses[record.printStatus as PrintStatus].color">{{
                      printStatuses[record.printStatus as PrintStatus].label
                    }}
                  </a-tag>
                </template>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>
      </template>
      <a-empty v-else-if="!loading" description="线路尚未加载"/>
    </a-spin>
  </a-drawer>
  <RouteFormDrawer ref="formDrawer" @saved="changed"/>
  <CandidateOrderModal ref="candidates" @saved="changed"/>
  <RoutePrint ref="printer"/>
  <a-modal
      v-model:open="reasonVisible"
      :title="reasonAction === 'cancel' ? '取消线路' : '移除订单'"
      :confirm-loading="busy"
      ok-type="danger"
      @ok="submitReason"
  >
    <p>{{
        reasonAction === 'cancel' ? '取消后，该线路的订单将重新进入待配送订单池。此操作不可撤销。' : '移除后，该订单可重新分配到其他线路。'
      }}</p>
    <a-form layout="vertical"
    >
      <a-form-item label="原因" required>
        <a-textarea v-model:value="reason" :maxlength="500" :rows="3"/>
      </a-form-item
      >
    </a-form>
    <a-alert v-if="reasonError" type="error" :message="reasonError" show-icon/>
  </a-modal>
  <a-modal v-model:open="stopVisible" title="停靠点定位与备注" :width="640" :confirm-loading="busy" @ok="saveStop">
    <template v-if="stopForm"
    ><p>{{ stopForm.customerNameSnapshot }} · {{ stopForm.addressSnapshot }}</p>
      <a-form layout="vertical">
        <a-form-item label="准确位置"
        >
          <ScmMapPicker :value="stopForm" :address="stopForm.addressSnapshot" @change="Object.assign(stopForm!, $event)"
          />
        </a-form-item>
        <a-form-item label="计划到达">
          <a-date-picker v-model:value="arrival" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"/>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="stopForm.remark" :maxlength="500"/>
        </a-form-item>
      </a-form
      >
      <a-alert v-if="stopError" type="error" :message="stopError" show-icon
      />
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useRouter} from 'vue-router';
import dayjs from 'dayjs';
import {Modal, message, type TableColumnsType} from 'ant-design-vue';
import {useUserStore} from '/@/store/modules/system/user';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import ScmMap from '/@/components/business/scm/map/scm-map.vue';
import ScmMapPicker from '/@/components/business/scm/map/scm-map-picker.vue';
import {isLocated, locationError, type MapPoint} from '/@/components/business/scm/map/types';
import RouteFormDrawer from './components/route-form-drawer.vue';
import CandidateOrderModal from './components/candidate-order-modal.vue';
import RoutePrint from './route-print.vue';
import {datetime} from '../common/scm-display';
import {money} from './delivery-display';
import {useDeliveryPermission} from './use-delivery-permission';
import {
  deliveryError,
  printStatuses,
  routeStatuses,
  type DeliveryStop,
  type Id,
  type PrintStatus,
  type RouteCustomerView,
  type RouteDetail,
  type RouteOrderView,
} from './delivery-types';

const emit = defineEmits<{ changed: [] }>();
const visible = ref(false),
    loading = ref(false),
    busy = ref(false),
    error = ref(''),
    tab = ref('base');
const routeId = ref<Id>(),
    detail = ref<RouteDetail>();
const formDrawer = ref<InstanceType<typeof RouteFormDrawer>>(),
    candidates = ref<InstanceType<typeof CandidateOrderModal>>(),
    printer = ref<InstanceType<typeof RoutePrint>>();
const user = useUserStore();
const {canViewAmount} = useDeliveryPermission();
const router = useRouter();
const canEdit = computed(
    () =>
        detail.value?.route.status === 'DRAFT' &&
        (user.administratorFlag || user.getPointList?.some((point: {
          webPerms: string
        }) => point.webPerms === 'scm:delivery:route:update'))
);
const startPoint = computed<MapPoint>(() => ({
  longitude: detail.value?.route.startLongitude,
  latitude: detail.value?.route.startLatitude,
  geomCrs: detail.value?.route.startGeomCrs,
  label: `0 起点 · ${detail.value?.route.warehouseNameSnapshot ?? ''}`,
  description: detail.value?.route.warehouseAddressSnapshot ?? '',
}));
const allLocated = computed(() => isLocated(startPoint.value) && detail.value?.route.locatedCount === detail.value?.route.stopCount);
const mapPoints = computed<MapPoint[]>(() => [
  startPoint.value,
  ...(detail.value?.stops.map((s) => ({
    ...s,
    label: `${s.stopSeq} ${s.customerNameSnapshot}`,
    description: `${s.addressSnapshot}\n${s.receiverPhoneSnapshot ?? ''} · ${s.orderCount} 张订单${
        canViewAmount.value ? ` · ${money(s.totalAmount)}` : ''}`,
  })) ?? []),
]);
const orderColumns = computed<TableColumnsType>(() => [
  {title: '订单号', dataIndex: 'orderNoSnapshot', width: 170},
  {title: '停靠点 / 客户', dataIndex: 'stop', width: 200},
  {title: '配送地址', dataIndex: 'address', width: 240},
  {title: '期望配送', dataIndex: 'expectDeliveryTimeSnapshot', width: 180},
  ...(canViewAmount.value
      ? [{title: '订单金额', dataIndex: 'orderAmountSnapshot', align: 'right' as const, width: 120}]
      : []),
  {title: '定位', dataIndex: 'location', width: 90},
  {title: '操作', dataIndex: 'action', align: 'right' as const, width: 80},
]);

const printMode = ref<'orders' | 'customers'>('orders');
const customerFilter = ref<'ALL' | 'PRINTED' | 'UNPRINTED'>('ALL');
const customerStatusFilter = ref<'ALL' | 'PRINTED' | 'UNPRINTED' | 'PARTIAL'>('ALL');
const ordersView = ref<RouteOrderView[]>([]);
const customersView = ref<RouteCustomerView[]>([]);
const orderSelection = ref<Id[]>([]);
const customerSelection = ref<Id[]>([]);
const printLoading = ref(false);
const printLoaded = ref(false);

const canPrint = computed(() =>
    ['PLANNED', 'DISPATCHED', 'COMPLETED'].includes(detail.value?.route.status ?? '')
);
/**
 * 展示用的客户范围筛选：打印状态的事实在服务端（生成时按当前有效订单重算），
 * 这里只决定列表可见行与未勾选时的提交范围。
 */
const customersShown = computed(() =>
    customersView.value.filter(
        (item) => customerStatusFilter.value === 'ALL' || item.printStatus === customerStatusFilter.value
    )
);
const canRecordPrint = computed(() =>
    printMode.value === 'orders'
        ? orderSelection.value.length > 0
        : // 勾选行即候选范围；未勾选时必须给出收窄的状态范围，
          // 否则一次请求会无选择地重打整条线路。
          customerSelection.value.length > 0 ||
          (customerStatusFilter.value !== 'ALL' && customersShown.value.length > 0)
);
const printTargetCount = computed(() =>
    printMode.value === 'orders'
        ? orderSelection.value.length
        : customerSelection.value.length || customersShown.value.length
);
const orderRowSelection = computed(() => ({
  selectedRowKeys: orderSelection.value,
  onChange: (keys: (string | number)[]) => (orderSelection.value = keys),
}));
const customerRowSelection = computed(() => ({
  selectedRowKeys: customerSelection.value,
  onChange: (keys: (string | number)[]) => (customerSelection.value = keys),
}));
const orderViewColumns = computed<TableColumnsType>(() => [
  {title: '订单号', dataIndex: 'orderNo', width: 170},
  {title: '客户', dataIndex: 'customerName', width: 160},
  {title: '停靠序', dataIndex: 'stopSeq', width: 80, align: 'right' as const},
  {title: '商品行', dataIndex: 'itemCount', width: 80, align: 'right' as const},
  ...(canViewAmount.value ? [{title: '订单金额', dataIndex: 'orderAmount', width: 120, align: 'right' as const}] : []),
  {title: '打印次数', dataIndex: 'printCount', width: 90, align: 'right' as const},
  {title: '打印状态', dataIndex: 'printStatus', width: 100, align: 'center' as const},
  {title: '最近打印', dataIndex: 'lastPrintedAt', width: 170},
]);
const customerViewColumns = computed<TableColumnsType>(() => [
  {title: '客户', dataIndex: 'customerName', width: 200},
  {title: '订单数', dataIndex: 'orderCount', width: 90, align: 'right' as const},
  {title: '商品行', dataIndex: 'itemCount', width: 90, align: 'right' as const},
  ...(canViewAmount.value ? [{title: '金额', dataIndex: 'totalAmount', width: 130, align: 'right' as const}] : []),
  {title: '已打印订单', dataIndex: 'printedOrderCount', width: 110, align: 'right' as const},
  {title: '打印状态', dataIndex: 'printStatus', width: 110, align: 'center' as const},
]);

async function loadPrint() {
  if (routeId.value == null) return;
  printLoading.value = true;
  try {
    const [o, c] = await Promise.all([deliveryApi.ordersView(routeId.value), deliveryApi.customersView(routeId.value)]);
    ordersView.value = o.data;
    customersView.value = c.data;
    printLoaded.value = true;
  } catch (e) {
    error.value = deliveryError(e);
  } finally {
    printLoading.value = false;
  }
}

watch(tab, (value) => {
  if (value === 'print' && !printLoaded.value) loadPrint();
});

async function recordPrint() {
  const route = detail.value?.route;
  if (!route || !canRecordPrint.value) return;
  busy.value = true;
  error.value = '';
  try {
    if (printMode.value === 'orders') {
      await deliveryApi.printOrders(route.id, route.version, orderSelection.value);
    } else {
      // 未勾选行即「本状态范围内整条线路」；勾选时名单只作候选范围，状态由服务端复核。
      await deliveryApi.printCustomers(
          route.id,
          route.version,
          customerSelection.value.length ? customerSelection.value : undefined,
          customerStatusFilter.value,
          customerFilter.value
      );
    }
    message.success('已登记打印');
    orderSelection.value = [];
    customerSelection.value = [];
    await reload();
  } catch (e) {
    error.value = deliveryError(e);
  } finally {
    busy.value = false;
  }
}

let generation = 0;

async function reload() {
  if (routeId.value == null) return;
  const current = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await deliveryApi.detail(routeId.value);
    if (current === generation) {
      detail.value = result.data;
      // 线路结构（ACTIVE 订单集合）变化后，已加载的打印视图与旧选中项即失效，一并刷新。
      if (printLoaded.value) {
        orderSelection.value = [];
        customerSelection.value = [];
        await loadPrint();
      }
    }
  } catch (e) {
    if (current === generation) error.value = deliveryError(e);
  } finally {
    if (current === generation) loading.value = false;
  }
}

function open(id: Id, initialTab = 'base') {
  routeId.value = id;
  detail.value = undefined;
  printLoaded.value = false;
  ordersView.value = [];
  customersView.value = [];
  orderSelection.value = [];
  customerSelection.value = [];
  printMode.value = 'orders';
  customerStatusFilter.value = 'ALL';
  customerFilter.value = 'ALL';
  visible.value = true;
  tab.value = initialTab;
  reload();
}

async function changed() {
  await reload();
  emit('changed');
}

function stopOf(id: Id) {
  return detail.value?.stops.find((s) => String(s.id) === String(id));
}

function plan() {
  Modal.confirm({
    title: '确认线路规划？',
    content: '确认后订单、停靠点与顺序将锁定。此操作不扣减库存。',
    onOk: async () => {
      if (!detail.value) return;
      busy.value = true;
      try {
        await deliveryApi.plan(detail.value.route.id, detail.value.route.version);
        message.success('线路已规划');
        await changed();
      } catch (e) {
        error.value = deliveryError(e);
        throw e;
      } finally {
        busy.value = false;
      }
    },
  });
}

const dragFrom = ref<number>();

function drop(index: number) {
  if (dragFrom.value != null) move(dragFrom.value, index);
  dragFrom.value = undefined;
}

async function move(from: number, to: number) {
  if (!detail.value || busy.value || !canEdit.value || from === to) return;
  const ids = detail.value.stops.map((s) => s.id);
  const [id] = ids.splice(from, 1);
  ids.splice(to, 0, id);
  busy.value = true;
  try {
    await deliveryApi.reorder(detail.value.route.id, detail.value.route.version, ids);
    await changed();
  } catch (e) {
    error.value = deliveryError(e);
  } finally {
    busy.value = false;
  }
}

const reasonVisible = ref(false),
    reasonAction = ref<'cancel' | 'remove'>('cancel'),
    reason = ref(''),
    reasonError = ref(''),
    removeId = ref<Id>();

function openReason(action: 'cancel' | 'remove', id?: Id) {
  reasonAction.value = action;
  removeId.value = id;
  reason.value = '';
  reasonError.value = '';
  reasonVisible.value = true;
}

async function submitReason() {
  if (!reason.value.trim()) {
    reasonError.value = '请填写原因';
    return;
  }
  if (!detail.value) return;
  busy.value = true;
  reasonError.value = '';
  try {
    const route = detail.value.route;
    if (reasonAction.value === 'cancel') await deliveryApi.cancel(route.id, route.version, reason.value);
    else await deliveryApi.removeOrder(route.id, removeId.value!, route.version, reason.value);
    reasonVisible.value = false;
    await changed();
  } catch (e) {
    reasonError.value = deliveryError(e);
  } finally {
    busy.value = false;
  }
}

const stopVisible = ref(false),
    stopForm = ref<DeliveryStop>(),
    stopError = ref(''),
    arrival = ref<string>();

function editStop(stop: DeliveryStop) {
  stopForm.value = {...stop};
  arrival.value = stop.plannedArrivalTime ? dayjs(stop.plannedArrivalTime).format('YYYY-MM-DDTHH:mm:ssZ') : undefined;
  stopError.value = '';
  stopVisible.value = true;
}

async function saveStop() {
  if (!stopForm.value || !detail.value) return;
  stopError.value = locationError(stopForm.value) ?? '';
  if (stopError.value) return;
  busy.value = true;
  try {
    await deliveryApi.locate(detail.value.route.id, stopForm.value.id, {
      longitude: stopForm.value.longitude ?? null,
      latitude: stopForm.value.latitude ?? null,
      geomCrs: stopForm.value.geomCrs ?? null,
      version: detail.value.route.version,
      plannedArrivalTime: arrival.value || null,
      remark: stopForm.value.remark,
    });
    stopVisible.value = false;
    await changed();
  } catch (e) {
    stopError.value = deliveryError(e);
  } finally {
    busy.value = false;
  }
}

// 携带业务上下文跳到通用操作日志页，按线路 id 精确筛选。
function openOperateLog() {
  if (routeId.value == null) return;
  void router.push({
    path: '/support/operate-log/operate-log-list',
    query: {businessType: 'DELIVERY_ROUTE', businessId: String(routeId.value)},
  });
}

defineExpose({open});
</script>
<style scoped>
.route-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.route-heading h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.route-summary {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  padding: 12px 0;
  border-block: 1px solid var(--ant-color-border, #e5e6eb);
  font-variant-numeric: tabular-nums;
}

.route-summary strong {
  margin-left: 8px;
}

.route-map-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.route-stops {
  max-height: 620px;
  overflow: auto;
  padding-right: 8px;
}

.route-stops ol {
  list-style: none;
  padding: 0;
  margin: 0;
}

.route-stops li,
.warehouse-stop {
  padding: 16px 0;
  border-bottom: 1px solid var(--ant-color-border, #e5e6eb);
}

.route-stops li[draggable='true'] {
  cursor: grab;
}

.route-stops p {
  margin: 8px 0;
  overflow-wrap: anywhere;
}

.stop-heading {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

@media (max-width: 900px) {
  .route-map-layout {
    grid-template-columns: 1fr;
  }

  .route-stops {
    max-height: none;
  }
}
</style>
