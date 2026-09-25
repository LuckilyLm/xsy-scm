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
                v-if="detail.route.status === 'PLANNED'"
                type="primary"
                v-privilege="DELIVERY_PERM.ROUTE_DISPATCH"
                :disabled="busy"
                @click="dispatch"
            >发车
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
                v-if="detail.route.status === 'DISPATCHED'"
                v-privilege="DELIVERY_PERM.ROUTE_COMPLETE"
                :disabled="busy"
                @click="complete"
            >完成线路
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
              <a-descriptions-item v-if="detail.route.dispatchedAt" label="发车">
                {{ datetime(detail.route.dispatchedAt) }} · {{ detail.route.dispatchedBy || '—' }}
              </a-descriptions-item>
              <a-descriptions-item v-if="detail.route.completedAt" label="完成">
                {{ datetime(detail.route.completedAt) }} · {{ detail.route.completedBy || '—' }}
              </a-descriptions-item>
              <!-- 出库单是发车在库存域留下的事实，配送侧只读编号：数量与金额口径归库存页，这里不复制一份。 -->
              <a-descriptions-item v-if="showOutbound" label="出库单">
                <a-button v-if="detail.route.outboundNo" type="link" size="small" @click="goOutbound(detail.route.outboundNo)">
                  {{ detail.route.outboundNo }}
                </a-button>
                <span v-else>—（整条线路实发为 0，未生成出库单）</span>
              </a-descriptions-item>
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
          <a-tab-pane key="fulfillment" tab="履约">
            <a-alert
                message="发车后订单进入在途，客户到手才登记签收；「异常签收」含拒收，但货已真实出库，因此不冲减库存——冲销必须走后续退货流程新增反向事实。完成线路要求全部在途订单都已登记结果。"
                type="info"
                show-icon
            />
            <a-table
                id="scm-delivery-route-fulfillment"
                size="small"
                :columns="fulfillmentColumns"
                :data-source="activeOrders"
                row-key="id"
                :loading="loading"
                :pagination="false"
                :locale="{emptyText: fulfillmentEmptyText}"
                :scroll="{x: 1160}"
                bordered
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'customer'">
                  {{ stopOf(record.stopId)?.customerNameSnapshot || '—' }}
                </template>
                <template v-else-if="column.dataIndex === 'fulfillmentStatus'">
                  <a-tag :color="fulfillmentStatuses[record.fulfillmentStatus as FulfillmentStatus].color">{{
                      fulfillmentStatuses[record.fulfillmentStatus as FulfillmentStatus].label
                    }}
                  </a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'signedAt'">
                  {{ datetime(record.signedAt) }}
                </template>
                <template v-else-if="column.dataIndex === 'signedBy'">
                  {{ record.signedBy || '—' }}
                </template>
                <template v-else-if="column.dataIndex === 'signReason'">
                  {{ record.signReason || '—' }}
                </template>
                <template v-else-if="column.dataIndex === 'action'">
                  <a-space :size="0">
                    <a-button
                        v-if="signable(record)"
                        type="link"
                        v-privilege="DELIVERY_PERM.ORDER_SIGN"
                        :disabled="busy"
                        @click="openSign(record, 'SIGNED')"
                    >签收
                    </a-button>
                    <a-button
                        v-if="signable(record)"
                        type="link"
                        danger
                        v-privilege="DELIVERY_PERM.ORDER_SIGN"
                        :disabled="busy"
                        @click="openSign(record, 'EXCEPTION')"
                    >异常签收
                    </a-button>
                    <span v-else>{{ signHintOf(record) }}</span>
                  </a-space>
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
  <a-modal
      v-model:open="signVisible"
      :title="signResults[signForm.result].label"
      ok-text="确认登记"
      :confirm-loading="busy"
      @ok="submitSign"
  >
    <template v-if="signTarget">
      <p>{{ signTarget.orderNoSnapshot }} · {{ signTarget.customerName }}</p>
      <a-alert
          v-if="signForm.result === 'EXCEPTION'"
          message="异常签收（含拒收）只登记到货事实：库存已按实发出库，此处不冲减，也不改动订单结算量。"
          type="warning"
          show-icon
      />
      <a-form layout="vertical">
        <a-form-item label="原因" :required="signForm.result === 'EXCEPTION'">
          <a-textarea
              v-model:value="signForm.reason"
              :maxlength="500"
              :rows="3"
              :placeholder="signForm.result === 'EXCEPTION' ? '拒收 / 破损 / 缺货争议等，必填' : '可留一句备注，如客户不在由邻居代收'"
          />
        </a-form-item>
      </a-form>
      <a-alert v-if="signError" type="error" :message="signError" show-icon/>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useRouter} from 'vue-router';
import dayjs from 'dayjs';
import {Modal, message, type TableColumnsType} from 'ant-design-vue';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import ScmMap from '/@/components/business/scm/map/scm-map.vue';
import ScmMapPicker from '/@/components/business/scm/map/scm-map-picker.vue';
import {isLocated, locationError, type MapPoint} from '/@/components/business/scm/map/types';
import RouteFormDrawer from './components/route-form-drawer.vue';
import CandidateOrderModal from './components/candidate-order-modal.vue';
import RoutePrint from './route-print.vue';
import {datetime} from '../common/scm-display';
import {money} from './delivery-display';
import {DELIVERY_PERM, useDeliveryPermission} from './use-delivery-permission';
import {
  deliveryError,
  fulfillmentStatuses,
  printStatuses,
  routeStatuses,
  SIGNABLE_FULFILLMENT,
  signResults,
  type DeliveryStop,
  type FulfillmentStatus,
  type Id,
  type PrintStatus,
  type RouteCustomerView,
  type RouteDetail,
  type RouteOrder,
  type RouteOrderView,
  type SignResult,
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
const {canViewAmount, canSign, hasPerm} = useDeliveryPermission();
const router = useRouter();
// 编辑权 = 草稿态 ∧ route:update；权限判定与 v-privilege 共用 hasPerm 一份口径
// （超管在其内部放行），不在这里再抄一次 administratorFlag。
const canEdit = computed(() => detail.value?.route.status === 'DRAFT' && hasPerm(DELIVERY_PERM.ROUTE_UPDATE));
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

/** 出库单只在发车之后存在；DRAFT / PLANNED 显示它只会让人以为漏了什么没填。 */
const showOutbound = computed(() => ['DISPATCHED', 'COMPLETED'].includes(detail.value?.route.status ?? ''));

/**
 * 履约视图只看仍挂在线路上的订单：CANCELLED 线路的详情会连 RELEASED 行一起返回，
 * 那些行已退出履约流程，留着它们会让人对着一条已取消的订单去点签收。
 */
const activeOrders = computed<RouteOrder[]>(() =>
    (detail.value?.orders ?? []).filter((order) => order.assignmentStatus === 'ACTIVE')
);

const fulfillmentColumns = computed<TableColumnsType>(() => [
  {title: '订单号', dataIndex: 'orderNoSnapshot', width: 180},
  {title: '客户', dataIndex: 'customer', width: 200},
  {title: '履约状态', dataIndex: 'fulfillmentStatus', width: 110, align: 'center' as const},
  {title: '签收时间', dataIndex: 'signedAt', width: 170},
  {title: '签收人', dataIndex: 'signedBy', width: 140},
  {title: '原因', dataIndex: 'signReason', width: 240, ellipsis: true},
  // 无签收权时整列消失（指令只能删列里的节点，删不掉列头），留下一个全空的表头比没有更糟。
  ...(canSign.value
      ? [{title: '操作', dataIndex: 'action', width: 160, align: 'right' as const, fixed: 'right' as const}]
      : []),
]);

// 「没有行」有三种原因，文案要能区分：线路没订单 / 线路已取消，用户下一步要做的事完全不同。
const fulfillmentEmptyText = computed(() =>
  detail.value?.route.status === 'CANCELLED'
    ? '线路已取消，不再有履约动作'
    : '线路还没有订单，请先在「线路订单」页加入订单'
);

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
  // 换线路时签收弹窗一起关掉并清掉目标行：里面带的是上一条线路的**行版本与订单号**，
  // 留着会把签收登记到另一条线路的订单上。
  signVisible.value = false;
  signTarget.value = undefined;
  signError.value = '';
  visible.value = true;
  tab.value = initialTab;
  reload();
}

async function changed() {
  await reload();
  emit('changed');
}

/**
 * 发车：PLANNED → DISPATCHED，服务端在同一事务内按分拣实发量生成一张出库单并扣库存。
 * 之后不能回退（要修正只能走退货新增反向事实），因此必须二次确认。
 */
function dispatch() {
  const route = detail.value?.route;
  if (!route) return;
  Modal.confirm({
    title: '确认发车？',
    content: '发车将按分拣实发量生成出库单并扣减库存，线路上全部活动订单进入在途。此操作不可撤销。',
    okText: '确认发车',
    onOk: async () => {
      busy.value = true;
      error.value = '';
      try {
        const result = await deliveryApi.dispatch(route.id, route.version);
        // 整条线路实发为 0 时不存在出库单，那是合法成功：文案必须说清「为什么没有单号」，
        // 否则用户会把空号当成发车失败再点一次。
        message.success(
            result.data.outboundNo
                ? `已发车：${result.data.orderCount} 张订单进入在途，出库单 ${result.data.outboundNo}`
                : '已发车：本线路实发为 0，未生成出库单'
        );
        await changed();
      } catch (e) {
        error.value = deliveryError(e);
      } finally {
        busy.value = false;
      }
    },
  });
}

/**
 * 完成线路：DISPATCHED → COMPLETED。未全部签收时由服务端 41117 拒绝 ——
 * 刻意不在前端按当前行数预先禁用按钮：详情可能是别人签收前的旧快照，
 * 按旧快照禁用会把「其实已经能完成」的线路锁死在本页。
 */
function complete() {
  const route = detail.value?.route;
  if (!route) return;
  Modal.confirm({
    title: '确认完成线路？',
    content: '完成后线路进入终态，不能再为订单登记签收，也不再产生任何库存影响。',
    okText: '确认完成',
    onOk: async () => {
      busy.value = true;
      error.value = '';
      try {
        await deliveryApi.complete(route.id, route.version);
        message.success('线路已完成');
        await changed();
      } catch (e) {
        error.value = deliveryError(e);
      } finally {
        busy.value = false;
      }
    },
  });
}

function goOutbound(outboundNo: string) {
  // 出库单归库存域：这里只带走编号一个筛选条件，跳过去看 SALES_OUT 事实，
  // 不在配送页复制数量或金额口径。
  void router.push({path: '/inventory/inventory-outbound-list', query: {outboundNo}});
}

const signVisible = ref(false),
    signError = ref(''),
    signForm = ref<{ result: SignResult; reason: string }>({result: 'SIGNED', reason: ''}),
    signTarget = ref<RouteOrder & { customerName: string }>();

function openSign(record: RouteOrder, result: SignResult) {
  signTarget.value = {...record, customerName: stopOf(record.stopId)?.customerNameSnapshot ?? ''};
  signForm.value = {result, reason: ''};
  signError.value = '';
  signVisible.value = true;
}

async function submitSign() {
  const target = signTarget.value;
  const route = detail.value?.route;
  if (!target || !route) return;
  const reason = signForm.value.reason.trim();
  // 异常签收无原因在前端就拦住：后端 41118 是同一口径，但要等一次往返才看得见，
  // 而弹窗若已被关掉，这句话只能重录一遍。
  if (signForm.value.result === 'EXCEPTION' && !reason) {
    signError.value = '异常签收必须填写原因';
    return;
  }
  busy.value = true;
  signError.value = '';
  try {
    // version 用的是这一行打开弹窗时读到的**行版本**：同一线路上不同订单要能并发签收，
    // 传线路版本等于用线路版本覆盖别人对这一行的签收。
    await deliveryApi.sign(route.id, target.orderId, {
      version: target.version,
      result: signForm.value.result,
      reason: reason || undefined,
    });
    message.success(signForm.value.result === 'EXCEPTION' ? '已登记异常签收' : '已签收');
    signVisible.value = false;
    await changed();
  } catch (e) {
    // 版本冲突写进弹窗而不是全局横幅：用户大概率还想补那句原因。
    signError.value = deliveryError(e);
  } finally {
    busy.value = false;
  }
}

/**
 * 可签收 = 线路在途 ∧ 该行在途未签，两者缺一服务端都会拒。
 * 权限由 v-privilege 摘节点，这里只判状态，避免留下「能点但必然失败」的入口。
 */
function signable(record: RouteOrder): boolean {
  return detail.value?.route.status === 'DISPATCHED' && SIGNABLE_FULFILLMENT.includes(record.fulfillmentStatus);
}

/** 不可签收的行要说清原因：终态已登记、未发车、线路已取消是三件不同的事。 */
function signHintOf(record: RouteOrder): string {
  if (record.fulfillmentStatus === 'SIGNED' || record.fulfillmentStatus === 'EXCEPTION') {
    return '已登记';
  }
  const status = detail.value?.route.status;
  if (status === 'COMPLETED') {
    return '线路已完成';
  }
  if (status === 'CANCELLED') {
    return '线路已取消';
  }
  return '发车后可签收';
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
