<template>
  <component
    :is="icons[name] ?? AppstoreOutlined"
    class="scm-icon"
    :style="{fontSize: `${size}px`}"
    :role="title ? 'img' : undefined"
    :aria-label="title || undefined"
    :aria-hidden="title ? undefined : true"
  />
</template>

<script setup lang="ts">
import {
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CarOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  InboxOutlined,
  LineChartOutlined,
  PieChartOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  TagsOutlined,
  TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue';
import type {Component} from 'vue';

withDefaults(defineProps<{
  name: string;
  size?: number;
  title?: string;
}>(), {size: 24, title: ''});

const icons: Record<string, Component> = {
  'kpi-sales-amount': DollarOutlined,
  'kpi-order-count': ShoppingCartOutlined,
  'kpi-purchase-amount': ShoppingOutlined,
  'kpi-receipt-count': InboxOutlined,
  'kpi-inventory-warning': WarningOutlined,
  'section-business-trend': LineChartOutlined,
  'section-business-todo': FileDoneOutlined,
  'section-customer-ranking': TeamOutlined,
  'section-product-ranking': TagsOutlined,
  'section-inventory-health': PieChartOutlined,
  'section-notice': BellOutlined,
  'section-changelog': ClockCircleOutlined,
  'todo-inventory-error': WarningOutlined,
  'todo-pending-receipt': InboxOutlined,
  'todo-pending-approval': AuditOutlined,
  'todo-draft-delivery': CarOutlined,
};
</script>

<style scoped lang="less">
.scm-icon {
  display: inline-flex;
  flex-shrink: 0;
  line-height: 1;
  vertical-align: -0.125em;
}
</style>
