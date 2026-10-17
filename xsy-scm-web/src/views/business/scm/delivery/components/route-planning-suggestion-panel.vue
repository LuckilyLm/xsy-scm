<template>
  <p class="scm-note scm-note--block">建议仅覆盖当前线路停靠点；应用前请核对。</p>
  <a-space class="plan-actions">
    <a-button
        type="primary"
        v-privilege="DELIVERY_PERM.PLAN_PROPOSE"
        :loading="busy"
        :disabled="!canEdit || loading || busy"
        @click="emit('propose')"
    >
      生成排线建议
    </a-button>
    <span class="plan-note" v-if="disabledReason">{{ disabledReason }}</span>
  </a-space>
  <a-alert v-if="error" type="error" :message="error" show-icon/>

  <template v-if="proposal">
    <a-descriptions bordered size="small" :column="3" class="plan-meta">
      <a-descriptions-item label="状态">
        <a-tag :color="planProposalStatuses[proposal.status]?.color || 'default'">
          {{ planProposalStatuses[proposal.status]?.label || proposal.status }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="总距离">{{ proposal.totalDistance }} 米</a-descriptions-item>
      <a-descriptions-item label="停靠点数">{{ proposal.stopCount }}</a-descriptions-item>
      <a-descriptions-item label="距离计算方式" :span="2">
        {{ distanceBasis(proposal) }}
      </a-descriptions-item>
      <a-descriptions-item label="生成时间">{{ datetime(proposal.createdAt) }}</a-descriptions-item>
      <a-descriptions-item label="排序规则" :span="3">
        {{ planRuleLabels[proposal.ruleCode] || '未知规则' }}
      </a-descriptions-item>
    </a-descriptions>

    <a-alert
        v-if="sourceVersionStale"
        type="warning" show-icon style="margin-bottom: 12px"
        message="此建议由历史线路版本生成，可能已失效。仅供比较；请刷新线路并重新生成建议后应用。"
    />
    <a-alert
        type="info" show-icon style="margin-bottom: 12px"
        :message="`建议未考虑：${ignoredFactorText}。请核对车辆和送达要求。`"
    />
    <a-space style="margin-bottom: 12px">
      <a-radio-group v-model:value="viewMode" button-style="solid">
        <a-radio-button value="list">顺序对比</a-radio-button>
        <a-radio-button value="map">地图对比</a-radio-button>
      </a-radio-group>
      <span class="plan-note">生成时线路版本：{{ proposal.sourceRouteVersion ?? '未知' }}，当前版本：{{ route.version }}</span>
    </a-space>
    <a-table
        v-if="viewMode === 'list'"
        size="small"
        :data-source="proposal.legs ?? []"
        :columns="planColumns"
        row-key="stopId"
        bordered
        :pagination="false"
        :scroll="{ x: scmColumnsWidth(planColumns) }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'change'">
          <a-tag v-if="record.originalSeq == null">历史建议无原顺序</a-tag>
          <a-tag v-else :color="record.originalSeq === record.seq ? 'default' : 'orange'">
            {{ record.originalSeq === record.seq ? '不变' : `${record.originalSeq} → ${record.seq}` }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'legDistance' || column.dataIndex === 'cumulativeDistance'">
          <span class="scm-quantity">{{ record[column.dataIndex] }}</span>
        </template>
      </template>
    </a-table>

    <div v-if="viewMode === 'map'" class="plan-map-pane">
      <a-radio-group v-model:value="mapVariant" button-style="solid" style="margin-bottom: 12px">
        <a-radio-button value="current">当前线路顺序</a-radio-button>
        <a-radio-button value="suggested">建议生成时顺序</a-radio-button>
      </a-radio-group>
      <p class="plan-note">地图显示当前道路，仅供位置参考；建议距离以生成时为准。</p>
      <ScmMap v-if="mapReady" :points="previewMapPoints" route />
      <a-alert v-else message="该视角缺少完整、同坐标系的起点或停靠点；历史顺序表仍可查看。" show-icon type="warning"/>
    </div>

    <a-space class="plan-actions">
      <a-button
          type="primary"
          v-privilege="DELIVERY_PERM.PLAN_APPLY"
          :loading="busy"
          :disabled="!canApplyProposal || loading || busy"
          @click="emit('apply')"
      >
        应用建议
      </a-button>
      <a-button
          v-privilege="DELIVERY_PERM.PLAN_APPLY"
          :loading="busy"
          :disabled="proposal.status !== 'PROPOSED' || !canEdit || loading || busy"
          @click="emit('discard')"
      >
        放弃建议
      </a-button>
    </a-space>
  </template>
  <a-spin v-else-if="loading"/>
  <a-empty
      v-else-if="!busy"
      description="暂无排线建议"
  >
    <template #description>
      <p>暂无排线建议。</p>
      <p v-if="disabledReason" class="plan-note">{{ disabledReason }}</p>
    </template>
  </a-empty>

  <template v-if="history.length > 1">
    <a-divider orientation="left">历史建议</a-divider>
    <a-table
        size="small"
        :data-source="history"
        :columns="planHistoryColumns"
        row-key="id"
        bordered
        :pagination="false"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="planProposalStatuses[record.status]?.color || 'default'">
            {{ planProposalStatuses[record.status]?.label || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'providerCode'">{{ distanceBasis(record) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" :disabled="busy || loading" @click="emit('select', record)">查看</a-button>
        </template>
      </template>
    </a-table>
  </template>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, ref} from 'vue';
import {type TableColumnsType} from 'ant-design-vue';
import ScmMap from '/@/components/business/scm/map/scm-map.vue';
import {isLocated, type MapPoint} from '/@/components/business/scm/map/types';
import {DELIVERY_PERM} from '../use-delivery-permission';
import {datetime} from '../../common/scm-display';
import {planProposalStatuses, planRuleLabels, type DeliveryPlanProposal, type DeliveryRoute, type DeliveryStop} from '../delivery-types';

const props = defineProps<{
  route: DeliveryRoute;
  stops: DeliveryStop[];
  canEdit: boolean;
  loading: boolean;
  busy: boolean;
  error: string;
  disabledReason?: string;
  proposal?: DeliveryPlanProposal;
  history: DeliveryPlanProposal[];
}>();

const emit = defineEmits<{
  propose: [];
  apply: [];
  discard: [];
  select: [proposal: DeliveryPlanProposal];
}>();

const viewMode = ref<'list' | 'map'>('list');
const mapVariant = ref<'current' | 'suggested'>('suggested');
const distanceBasis = (plan: DeliveryPlanProposal) => plan.estimated ? '直线距离估算' : '道路距离';

const sourceVersionStale = computed(() =>
    props.proposal?.sourceRouteVersion == null ||
    props.proposal.sourceRouteVersion !== props.route.version
);
const canApplyProposal = computed(() =>
    props.canEdit && props.route.status === 'DRAFT' &&
    props.proposal?.status === 'PROPOSED' && !sourceVersionStale.value
);
const factorLabels: Record<string, string> = {
    TIME_WINDOW: '客户时间窗',
    VEHICLE_CAPACITY: '车辆载重/容积',
    TRAFFIC: '实时路况',
};
const ignoredFactorText = computed(() =>
    (props.proposal?.ignoredFactors ?? []).length
        ? (props.proposal?.ignoredFactors ?? []).map((item) => factorLabels[item] ?? item).join('、')
        : '未提供限制说明（历史建议）'
);

const currentMapPoints = computed<MapPoint[]>(() => [
    {
        longitude: props.route.startLongitude,
        latitude: props.route.startLatitude,
        geomCrs: props.route.startGeomCrs,
        label: `起点 · ${props.route.warehouseNameSnapshot}`,
    },
    ...props.stops.map((stop, index) => ({
        longitude: stop.longitude,
        latitude: stop.latitude,
        geomCrs: stop.geomCrs,
        label: `${index + 1} · ${stop.customerNameSnapshot}`,
        description: stop.addressSnapshot,
    })),
]);
const suggestedMapPoints = computed<MapPoint[]>(() => {
    const plan = props.proposal;
    if (!plan) return [];
    return [
        {
            longitude: plan.sourceLongitude,
            latitude: plan.sourceLatitude,
            geomCrs: plan.sourceGeomCrs,
            label: '建议生成时起点',
        },
        ...(plan.legs ?? []).map((leg) => ({
            longitude: leg.longitude,
            latitude: leg.latitude,
            geomCrs: plan.sourceGeomCrs,
            label: `${leg.seq} · ${leg.customerNameSnapshot ?? '停靠点'}`,
            description: leg.addressSnapshot,
        })),
    ];
});
const previewMapPoints = computed<MapPoint[]>(() =>
    mapVariant.value === 'current' ? currentMapPoints.value : suggestedMapPoints.value
);
const mapReady = computed(() =>
    previewMapPoints.value.length >= 2 &&
    previewMapPoints.value.every(isLocated) &&
    previewMapPoints.value.every((point) => point.geomCrs === previewMapPoints.value[0].geomCrs)
);

const planColumns: TableColumnsType = [
  {title: '建议顺序', dataIndex: 'seq', width: 95},
  {title: '与生成时顺序对比', dataIndex: 'change', width: 165},
  {title: '停靠点 / 客户', dataIndex: 'customerNameSnapshot', width: 200},
  {title: '配送地址', dataIndex: 'addressSnapshot', width: 280},
  {title: '本段距离（米）', dataIndex: 'legDistance', width: 130},
  {title: '累计距离（米）', dataIndex: 'cumulativeDistance', width: 130},
];

const planHistoryColumns: TableColumnsType = [
  {title: '生成时间', dataIndex: 'createdAt', width: 180},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center'},
  {title: '停靠点数', dataIndex: 'stopCount', width: 92},
  {title: '总距离（米）', dataIndex: 'totalDistance', width: 130},
  {title: '距离计算方式', dataIndex: 'providerCode', width: 160},
  {title: '操作', dataIndex: 'action', align: 'center', width: 90},
];
</script>

<style scoped>
.plan-intro {
  margin: 0 0 12px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

.plan-actions {
  margin: 12px 0;
}

.plan-meta {
  margin-bottom: 12px;
}

.plan-note {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.ml {
  margin-left: 8px;
}

.plan-map-pane {
  height: 470px;
  margin-bottom: 20px;
}

.plan-map-pane .plan-note {
  margin-bottom: 12px;
}
</style>
