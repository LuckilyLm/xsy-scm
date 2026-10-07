<template>
  <div class="scm-detail-grid">
    <div class="scm-detail-card">
      <h3 class="scm-detail-card__title">基本信息</h3>
      <dl class="scm-field-list scm-field-list--2">
        <div class="scm-field scm-field--wide">
          <dt class="scm-field__label">仓库</dt>
          <dd class="scm-field__value">
            {{ route.warehouseNameSnapshot }}
            <span class="field-note">{{ route.warehouseAddressSnapshot || '暂无详细地址' }}</span>
          </dd>
        </div>
        <div class="scm-field">
          <dt class="scm-field__label">仓库定位</dt>
          <dd class="scm-field__value">
            <!-- 坐标系属于定位细节：平时不占版面，悬停可查 -->
            <a-tooltip :title="isLocated(startPoint) ? `坐标系 ${route.startGeomCrs}` : undefined">
              <span>{{ isLocated(startPoint) ? '已定位' : '未定位' }}</span>
            </a-tooltip>
          </dd>
        </div>
        <div class="scm-field">
          <dt class="scm-field__label">司机</dt>
          <dd class="scm-field__value">{{ route.driverNameSnapshot || '未分配' }} {{ route.driverPhoneSnapshot }}</dd>
        </div>
        <div class="scm-field">
          <dt class="scm-field__label">车辆</dt>
          <dd class="scm-field__value">{{ route.vehicleNoSnapshot || '未分配' }}</dd>
        </div>
        <div class="scm-field">
          <dt class="scm-field__label">计划发车</dt>
          <dd class="scm-field__value">{{ datetime(route.plannedDepartureTime) }}</dd>
        </div>
        <div class="scm-field">
          <dt class="scm-field__label">实际发车</dt>
          <dd class="scm-field__value">{{ dispatchText }}</dd>
        </div>
        <div v-if="route.completedAt" class="scm-field">
          <dt class="scm-field__label">完成</dt>
          <dd class="scm-field__value">{{ datetime(route.completedAt) }} · {{ route.completedBy || '—' }}</dd>
        </div>
        <div class="scm-field scm-field--wide">
          <dt class="scm-field__label">备注</dt>
          <dd class="scm-field__value">{{ route.remark || '—' }}</dd>
        </div>
        <div v-if="route.cancelReason" class="scm-field scm-field--wide">
          <dt class="scm-field__label">取消原因</dt>
          <dd class="scm-field__value">{{ route.cancelReason }}</dd>
        </div>
      </dl>
    </div>
    <!-- 发车在库存域生成出库事实；配送侧只读单号，不重复展示数量或金额。 -->
    <div v-if="showOutbound" class="scm-detail-card">
      <h3 class="scm-detail-card__title">关联出库单</h3>
      <p class="scm-field__value">
        <a-button v-if="route.outboundNo" type="link" size="small" class="outbound-link"
                  @click="emit('outbound', route.outboundNo)">
          {{ route.outboundNo }} →
        </a-button>
        <span v-else>—（整条线路实发为 0，未生成出库单）</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {isLocated, type MapPoint} from '/@/components/business/scm/map/types';
import {datetime} from '../../common/scm-display';
import type {DeliveryRoute} from '../delivery-types';

const props = defineProps<{
  route: DeliveryRoute;
  startPoint: MapPoint;
  showOutbound: boolean;
}>();

const emit = defineEmits<{
  outbound: [outboundNo: string];
}>();

const dispatchText = computed(() =>
  props.route.dispatchedAt ? `${datetime(props.route.dispatchedAt)} · ${props.route.dispatchedBy || '—'}` : '—'
);
</script>

<style scoped>
.field-note {
  display: block;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

.outbound-link {
  height: auto;
  padding: 0;
}
</style>
