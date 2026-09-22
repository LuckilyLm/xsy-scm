<template>
  <view class="order">
    <xsy-nav-bar title="订单">
      <template #bottom>
        <!-- 客户可理解状态，非内部 SCM 状态（规划 §18） -->
        <scroll-view class="order__tabs" scroll-x :scroll-into-view="`tab-${activeTab}`">
          <view class="order__tabs-inner">
            <view
              v-for="tab in ORDER_TABS"
              :id="`tab-${tab.key}`"
              :key="tab.key"
              class="order__tab"
              :class="{ 'order__tab--active': tab.key === activeTab }"
              @click="activeTab = tab.key"
            >
              <text class="order__tab-text">{{ tab.text }}</text>
              <view v-if="tab.key === activeTab" class="order__tab-bar" />
            </view>
          </view>
        </scroll-view>
      </template>
    </xsy-nav-bar>

    <view class="order__list">
      <PagePlaceholder
        mark="订单"
        title="暂无订单"
        desc="订单列表、订单详情、配送详情、实重明细与再来一单将在接入 order-api 后实现。"
        plan-ref="规划 §18"
      />
    </view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import XsyNavBar from '@/components/common/xsy-nav-bar.vue';
  import PagePlaceholder from '@/components/common/page-placeholder.vue';

  /**
   * 展示状态来自规划 §7 页面树；其到内部状态机的映射见 §18，
   * 例如「待备货」对应 PURCHASE / WAIT_SORTING / SORTING。
   * 具体映射以服务端状态机最终实现为准，客户端不自行推断。
   */
  const ORDER_TABS = [
    { key: 'ALL', text: '全部' },
    { key: 'PENDING', text: '待确认' },
    { key: 'PREPARING', text: '待备货' },
    { key: 'READY_TO_DELIVER', text: '待配送' },
    { key: 'DELIVERING', text: '配送中' },
    { key: 'COMPLETED', text: '已完成' },
    { key: 'CANCELLED', text: '已取消' },
    { key: 'AFTER_SALE', text: '售后中' },
  ];

  const activeTab = ref('ALL');
</script>

<style lang="scss" scoped>
  .order {
    min-height: 100vh;
    background-color: $color-bg-page;

    &__tabs {
      white-space: nowrap;
      @include hairline-bottom;
    }

    &__tabs-inner {
      display: inline-flex;
      align-items: center;
      padding: 0 $space-2;
    }

    &__tab {
      position: relative;
      padding: $space-3 $space-3 $space-2;
      flex-shrink: 0;
    }

    &__tab-text {
      font-size: $font-size-base;
      color: $color-text-secondary;
    }

    &__tab--active &__tab-text {
      color: $color-primary;
      font-weight: $font-weight-medium;
    }

    &__tab-bar {
      position: absolute;
      left: 50%;
      bottom: 0;
      transform: translateX(-50%);
      width: 40rpx;
      height: 6rpx;
      border-radius: $radius-pill;
      background-color: $color-primary;
    }

    &__list {
      padding-top: $space-4;
    }
  }
</style>
