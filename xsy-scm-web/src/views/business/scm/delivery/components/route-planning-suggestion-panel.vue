<template>
  <p class="scm-note scm-note--block">根据当前停靠点提供配送顺序建议。建议不会自动修改线路，需要人工确认应用。</p>
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
      <a-descriptions-item label="算路来源" :span="2">
        {{ proposal.providerCode }} v{{ proposal.providerVersion }}
        <a-tag v-if="proposal.estimated" color="orange" class="ml">估算，非真实路网</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="生成时间">{{ datetime(proposal.createdAt) }}</a-descriptions-item>
      <a-descriptions-item label="排序规则" :span="3">
        {{ planRuleLabels[proposal.ruleCode] || proposal.ruleCode }}
      </a-descriptions-item>
    </a-descriptions>

    <a-table
        size="small"
        :data-source="proposal.legs ?? []"
        :columns="planColumns"
        row-key="stopId"
        bordered
        :pagination="false"
        :scroll="{ x: 900 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'legDistance' || column.dataIndex === 'cumulativeDistance'">
          <span class="scm-quantity">{{ record[column.dataIndex] }}</span>
        </template>
      </template>
    </a-table>

    <a-space class="plan-actions">
      <a-button
          type="primary"
          v-privilege="DELIVERY_PERM.PLAN_APPLY"
          :loading="busy"
          :disabled="proposal.status !== 'PROPOSED' || !canEdit || loading || busy"
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
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" :disabled="busy || loading" @click="emit('select', record)">查看</a-button>
        </template>
      </template>
    </a-table>
  </template>
</template>

<script setup lang="ts">
import {type TableColumnsType} from 'ant-design-vue';
import {DELIVERY_PERM} from '../use-delivery-permission';
import {datetime} from '../../common/scm-display';
import {planProposalStatuses, planRuleLabels, type DeliveryPlanProposal} from '../delivery-types';

defineProps<{
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

const planColumns: TableColumnsType = [
  {title: '顺序', dataIndex: 'seq', align: 'right', width: 70},
  {title: '停靠点 / 客户', dataIndex: 'customerNameSnapshot', width: 200},
  {title: '配送地址', dataIndex: 'addressSnapshot', width: 280},
  {title: '本段距离（米）', dataIndex: 'legDistance', align: 'right', width: 130},
  {title: '累计距离（米）', dataIndex: 'cumulativeDistance', align: 'right', width: 130},
];

const planHistoryColumns: TableColumnsType = [
  {title: '生成时间', dataIndex: 'createdAt', width: 180},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center'},
  {title: '停靠点数', dataIndex: 'stopCount', align: 'right', width: 100},
  {title: '总距离（米）', dataIndex: 'totalDistance', align: 'right', width: 130},
  {title: '算路来源', dataIndex: 'providerCode', width: 220},
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
</style>
