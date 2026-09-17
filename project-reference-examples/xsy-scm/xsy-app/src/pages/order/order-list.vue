<template>
  <view class="container">
    <mescroll-body @init="mescrollInit" :down="{ auto: false }" @down="onDown" @up="onUp">
      <mescroll-empty v-if="dataList.length === 0"></mescroll-empty>
      <view class="card" v-for="item in dataList" :key="item.orderId" @click="goDetail(item)">
        <view class="card-header">
          <text class="no">{{ item.orderNo }}</text>
          <text class="status" :class="statusClass(item.status)">
            {{ $smartEnumPlugin.getDescByValue('ORDER_STATUS_ENUM', item.status) }}
          </text>
        </view>
        <view class="card-body">
          <view class="row"><text class="label">客户ID：</text><text>{{ item.customerId }}</text></view>
          <view class="row">
            <text class="label">结算方式：</text>
            <text>{{ $smartEnumPlugin.getDescByValue('SETTLE_TYPE_ENUM', item.settleType) }}</text>
          </view>
          <view class="row"><text class="label">应付金额：</text><text class="price">¥{{ item.payableAmount }}</text></view>
          <view class="row"><text class="label">实付金额：</text><text class="price">¥{{ item.actualAmount }}</text></view>
          <view class="row">
            <text class="label">支付状态：</text>
            <text>{{ $smartEnumPlugin.getDescByValue('PAY_STATUS_ENUM', item.payStatus) }}</text>
          </view>
          <view class="row"><text class="label">期望配送：</text><text>{{ item.expectDeliveryTime }}</text></view>
        </view>
        <view class="card-footer" v-if="canOperate(item.status)">
          <button v-if="canConfirm(item.status)" class="btn confirm" size="mini" @click.stop="confirmOrder(item)">确认订单</button>
          <!-- 仅「已确认」可发货，与后端 deliver 的状态校验保持一致 -->
          <button v-if="item.status === orderStatusEnum.CONFIRMED.value" class="btn confirm" size="mini" @click.stop="deliverOrder(item)">发货</button>
          <!-- 仅「配送中」可签收，与后端 sign 的状态校验保持一致 -->
          <button v-if="item.status === orderStatusEnum.DELIVERING.value" class="btn confirm" size="mini" @click.stop="signOrder(item)">签收</button>
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
  import { orderApi } from '@/api/business/order/order-api';
  import orderConst from '@/constants/business/order/order-const';

  const orderStatusEnum = orderConst.ORDER_STATUS_ENUM;

  const queryForm = reactive({ status: null, pageNum: 1, pageSize: 10, searchCount: true });
  const dataList = ref([]);

  function buildParam(pageNum) {
    return Object.assign({}, queryForm, { pageNum });
  }

  async function query(mescroll, isDown, param) {
    try {
      let res = await orderApi.query(param);
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
    if (s === orderStatusEnum.DELIVERING.value || s === orderStatusEnum.SORTING.value || s === orderStatusEnum.WAIT_SORTING.value) {
      return 's-delivering';
    }
    if (s === orderStatusEnum.SIGNED.value || s === orderStatusEnum.COMPLETED.value) {
      return 's-done';
    }
    if (s === orderStatusEnum.CANCELLED.value || s === orderStatusEnum.INVALID.value) {
      return 's-invalid';
    }
    return 's-wait';
  }

  // 仅草稿 / 待确认 可确认，与后端 confirm 的状态校验保持一致
  function canConfirm(s) {
    return s === orderStatusEnum.DRAFT.value || s === orderStatusEnum.WAIT_CONFIRM.value;
  }
  function canOperate(s) {
    return canConfirm(s) || s === orderStatusEnum.CONFIRMED.value || s === orderStatusEnum.DELIVERING.value;
  }

  function confirmOrder(item) {
    uni.showModal({
      title: '提示',
      content: `确定要确认订单【${item.orderNo}】吗？确认后方可发货。`,
      success: async (res) => {
        if (res.confirm) {
          try {
            SmartLoading.show();
            await orderApi.confirm(item.orderId);
            SmartToast.success('确认成功，订单已可发货');
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

  function signOrder(item) {
    uni.showModal({
      title: '提示',
      content: `确定要签收订单【${item.orderNo}】吗？签收后将生成应收。`,
      success: async (res) => {
        if (res.confirm) {
          try {
            SmartLoading.show();
            await orderApi.sign(item.orderId);
            SmartToast.success('签收成功，已生成应收');
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

  function goDetail(item) {
    uni.navigateTo({
      url: `/pages/order/order-detail?orderId=${item.orderId}&orderNo=${encodeURIComponent(item.orderNo)}&status=${item.status}`,
    });
  }

  function deliverOrder(item) {
    uni.showModal({
      title: '提示',
      content: `确定要发货订单【${item.orderNo}】吗？发货后将触发销售出库并扣减库存。`,
      success: async (res) => {
        if (res.confirm) {
          try {
            SmartLoading.show();
            await orderApi.deliver(item.orderId);
            SmartToast.success('发货成功，已触发销售出库');
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
      .s-delivering { color: #1a9aff; background: #e6f7ff; }
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
