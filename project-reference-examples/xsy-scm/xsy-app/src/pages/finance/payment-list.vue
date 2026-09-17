<template>
  <view class="container">
    <mescroll-body @init="mescrollInit" :down="{ auto: false }" @down="onDown" @up="onUp">
      <mescroll-empty v-if="dataList.length === 0"></mescroll-empty>
      <view class="card" v-for="item in dataList" :key="item.paymentId">
        <view class="card-header">
          <text class="no">{{ item.paymentNo }}</text>
          <text class="status" :class="statusClass(item.status)">
            {{ $smartEnumPlugin.getDescByValue('PAYMENT_STATUS_ENUM', item.status) }}
          </text>
        </view>
        <view class="card-body">
          <view class="row"><text class="label">客户ID：</text><text>{{ item.customerId }}</text></view>
          <view class="row">
            <text class="label">收款渠道：</text>
            <text>{{ $smartEnumPlugin.getDescByValue('PAY_CHANNEL_ENUM', item.payChannel) }}</text>
          </view>
          <view class="row"><text class="label">收款金额：</text><text class="price">¥{{ item.amount }}</text></view>
          <view class="row"><text class="label">收款时间：</text><text>{{ item.payTime }}</text></view>
          <view class="row"><text class="label">备注：</text><text>{{ item.remark }}</text></view>
        </view>
        <view class="card-footer">
          <button v-if="item.status === paymentStatusEnum.PENDING.value"
                  class="btn confirm" size="mini" @click.stop="confirmPayment(item)">确认核销</button>
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

  const paymentStatusEnum = financeConst.PAYMENT_STATUS_ENUM;

  const queryForm = reactive({ paymentNo: null, customerId: null, status: null, pageNum: 1, pageSize: 10, searchCount: true });
  const dataList = ref([]);

  function buildParam(pageNum) {
    return Object.assign({}, queryForm, { pageNum });
  }

  async function query(mescroll, isDown, param) {
    try {
      let res = await financeApi.queryPayment(param);
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
    if (s === paymentStatusEnum.CONFIRMED.value) {
      return 's-done';
    }
    if (s === paymentStatusEnum.REJECTED.value) {
      return 's-invalid';
    }
    return 's-wait';
  }

  function confirmPayment(item) {
    uni.showModal({
      title: '提示',
      content: `确定要确认收款单【${item.paymentNo}】吗？确认后将核销对应应收。`,
      success: async (res) => {
        if (res.confirm) {
          try {
            SmartLoading.show();
            await financeApi.confirmPayment(item.paymentId);
            SmartToast.success('确认成功，已核销应收');
            query(getMescroll(), true, buildParam(1));
          } catch (e) {
            smartSentry.captureError(e);
          } finally {
            SmartLoading.hide();
          }
        }
      },
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
      .s-done { color: #52c41a; background: #f6ffed; }
      .s-invalid { color: #bfbfbf; background: #f5f5f5; }
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
