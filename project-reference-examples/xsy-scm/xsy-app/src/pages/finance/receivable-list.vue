<template>
  <view class="container">
    <mescroll-body @init="mescrollInit" :down="{ auto: false }" @down="onDown" @up="onUp">
      <mescroll-empty v-if="dataList.length === 0"></mescroll-empty>
      <view class="card" v-for="item in dataList" :key="item.receivableId">
        <view class="card-header">
          <text class="no">{{ item.receivableNo }}</text>
          <text class="status" :class="statusClass(item.status)">
            {{ $smartEnumPlugin.getDescByValue('RECEIVABLE_STATUS_ENUM', item.status) }}
          </text>
        </view>
        <view class="card-body">
          <view class="row"><text class="label">客户ID：</text><text>{{ item.customerId }}</text></view>
          <view class="row">
            <text class="label">结算方式：</text>
            <text>{{ $smartEnumPlugin.getDescByValue('SETTLE_TYPE_ENUM', item.settleType) }}</text>
          </view>
          <view class="row"><text class="label">应收金额：</text><text class="price">¥{{ item.amount }}</text></view>
          <view class="row"><text class="label">已收金额：</text><text>¥{{ item.receivedAmount }}</text></view>
          <view class="row"><text class="label">待收余额：</text><text class="price">¥{{ item.balanceAmount }}</text></view>
          <view class="row"><text class="label">到期日：</text><text>{{ item.dueTime }}</text></view>
        </view>
        <view class="card-footer">
          <button v-if="item.status === receivableStatusEnum.PENDING.value || item.status === receivableStatusEnum.PARTIAL.value"
                  class="btn confirm" size="mini" @click.stop="register(item)">登记收款</button>
        </view>
      </view>
    </mescroll-body>
  </view>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { onPageScroll, onReachBottom } from '@dcloudio/uni-app';
  import useMescroll from '@/uni_modules/uni-mescroll/hooks/useMescroll';
  import { smartSentry } from '@/lib/smart-sentry';
  import { SmartLoading, SmartToast } from '@/lib/smart-support';
  import { financeApi } from '@/api/business/finance/finance-api';
  import financeConst from '@/constants/business/finance/finance-const';
  import orderConst from '@/constants/business/order/order-const';

  const receivableStatusEnum = financeConst.RECEIVABLE_STATUS_ENUM;

  const queryForm = reactive({ receivableNo: null, customerId: null, status: null, pageNum: 1, pageSize: 10, searchCount: true });
  const dataList = ref([]);

  function buildParam(pageNum) {
    return Object.assign({}, queryForm, { pageNum });
  }

  async function query(mescroll, isDown, param) {
    try {
      let res = await financeApi.queryReceivable(param);
      if (isDown) {
        dataList.value = res.data.list;
      } else {
        dataList.value = dataList.value.concat(res.data.list);
      }
      mescroll.endSuccess(res.data.list.length, res.data.pages > res.data.pageNum);
    } catch (e) {
      smartSentry.captureError(e);
      mescroll.endErr();
    }
  }

  const { mescrollInit, getMescroll } = useMescroll(onPageScroll, onReachBottom);

  function onDown(mescroll) {
    query(mescroll, true, buildParam(1));
  }
  function onUp(mescroll) {
    query(mescroll, false, buildParam(mescroll.num));
  }

  function statusClass(s) {
    if (s === receivableStatusEnum.SETTLED.value) {
      return 's-done';
    }
    if (s === receivableStatusEnum.PARTIAL.value) {
      return 's-partial';
    }
    return 's-wait';
  }

  function register(item) {
    uni.navigateTo({
      url: `/pages/finance/payment-form?receivableId=${item.receivableId}&customerId=${item.customerId}&balance=${item.balanceAmount}`,
    });
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
      margin-bottom: 20rpx;
      .no {
        font-size: 32rpx;
        font-weight: bold;
        color: #000;
      }
      .status {
        font-size: 26rpx;
        padding: 2rpx 14rpx;
        border-radius: 8rpx;
      }
      .s-wait { color: #fa8c16; background: #fff7e6; }
      .s-partial { color: #1a9aff; background: #e6f7ff; }
      .s-done { color: #52c41a; background: #f6ffed; }
    }
    .card-body {
      .row {
        font-size: 28rpx;
        color: #555;
        line-height: 48rpx;
        display: flex;
        .label { color: #999; width: 150rpx; }
      }
      .price { color: #f5222d; }
    }
    .card-footer {
      margin-top: 16rpx;
      display: flex;
      justify-content: flex-end;
      .btn.confirm {
        background: #1a9aff;
        color: #fff;
      }
    }
  }

  page {
    background-color: #f5f5f5;
  }
</style>
