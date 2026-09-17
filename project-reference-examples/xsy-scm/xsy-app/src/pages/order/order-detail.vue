<template>
  <view class="container">
    <view class="card">
      <view class="card-header">
        <text class="no">{{ orderNo }}</text>
        <text class="status">{{ $smartEnumPlugin.getDescByValue('ORDER_STATUS_ENUM', status) }}</text>
      </view>
    </view>

    <view class="section-title">订单明细（价格快照）</view>
    <view class="card" v-for="item in itemList" :key="item.itemId">
      <view class="row"><text class="label">商品ID：</text><text>{{ item.productId }}</text></view>
      <view class="row"><text class="label">下单数量：</text><text>{{ item.quantity }}</text></view>
      <view class="row"><text class="label">成交价快照：</text><text class="price">¥{{ item.snapshotPrice }}</text></view>
      <view class="row">
        <text class="label">价格类型：</text>
        <text>{{ $smartEnumPlugin.getDescByValue('PRICE_TYPE_ENUM', item.priceType) }}</text>
      </view>
      <view class="row"><text class="label">实际重量：</text><text>{{ item.actualWeight }}</text></view>
      <view class="row"><text class="label">明细金额：</text><text class="price">¥{{ item.itemAmount }}</text></view>
    </view>
    <view class="empty" v-if="itemList.length === 0">暂无订单明细</view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import { smartSentry } from '@/lib/smart-sentry';
  import { orderItemApi } from '@/api/business/order/order-item-api';

  const orderId = ref(null);
  const orderNo = ref('');
  const status = ref(null);
  const itemList = ref([]);

  onLoad((options) => {
    orderId.value = options.orderId;
    orderNo.value = options.orderNo ? decodeURIComponent(options.orderNo) : '';
    status.value = options.status ? Number(options.status) : null;
    queryItems();
  });

  // 明细成交价使用下单时锁定的快照价（B），不随商品实时价变动
  async function queryItems() {
    try {
      let res = await orderItemApi.query({ orderId: orderId.value, pageNum: 1, pageSize: 50, searchCount: true });
      itemList.value = res.data.list;
    } catch (e) {
      smartSentry.captureError(e);
    }
  }
</script>

<style lang="scss" scoped>
  .card {
    width: 700rpx;
    background: #ffffff;
    border-radius: 12rpx;
    box-shadow: 0px 3px 4px 0px rgba(24, 144, 255, 0.06);
    margin: 24rpx auto 0;
    box-sizing: border-box;
    padding: 30rpx;
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      .no {
        font-size: 32rpx;
        font-weight: bold;
        color: #000;
      }
      .status {
        font-size: 26rpx;
        padding: 2rpx 14rpx;
        border-radius: 8rpx;
        color: #1a9aff;
        background: #e6f7ff;
      }
    }
    .row {
      font-size: 28rpx;
      color: #555;
      line-height: 48rpx;
      display: flex;
      .label { color: #999; width: 180rpx; }
    }
    .price { color: #f5222d; }
  }

  .section-title {
    width: 700rpx;
    margin: 30rpx auto 0;
    font-size: 28rpx;
    color: #999;
  }

  .empty {
    width: 700rpx;
    margin: 24rpx auto 0;
    text-align: center;
    font-size: 28rpx;
    color: #bfbfbf;
  }

  page {
    background-color: #f5f5f5;
  }
</style>
