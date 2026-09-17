<template>
  <view class="container">
    <mescroll-body @init="mescrollInit" :down="{ auto: false }" @down="onDown" @up="onUp">
      <mescroll-empty v-if="dataList.length === 0"></mescroll-empty>
      <view class="card" v-for="(item, index) in dataList" :key="item.receiveId">
        <view class="card-header">
          <text class="no">{{ item.receiveNo }}</text>
          <text class="status" :class="statusClass(item.status)">{{ $smartEnumPlugin.getDescByValue('RECEIVE_STATUS_ENUM', item.status) }}</text>
        </view>
        <view class="card-body">
          <view class="row"><text class="label">采购单：</text><text>{{ item.purchaseId }}</text></view>
          <view class="row"><text class="label">明细ID：</text><text>{{ item.itemId }}</text></view>
          <view class="row"><text class="label">数量：</text><text>{{ item.receiveQuantity }}</text></view>
          <view class="row"><text class="label">重量：</text><text>{{ item.receiveWeight }}</text></view>
          <view class="row">
            <text class="label">标记：</text>
            <text :class="flagClass(item.receiveFlag)">{{ $smartEnumPlugin.getDescByValue('RECEIVE_FLAG_ENUM', item.receiveFlag) }}</text>
          </view>
          <view class="row"><text class="label">收货时间：</text><text>{{ item.receiveTime }}</text></view>
        </view>
        <view class="card-footer" v-if="item.status === 1">
          <button class="btn confirm" size="mini" @click="confirmInbound(item)">入库确认</button>
        </view>
      </view>
    </mescroll-body>

    <view class="fab" @click="goAdd">
      <uni-icons type="plus" size="24" color="#fff"></uni-icons>
    </view>
  </view>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { onPageScroll, onReachBottom } from '@dcloudio/uni-app';
  import useMescroll from '@/uni_modules/uni-mescroll/hooks/useMescroll';
  import { smartSentry } from '@/lib/smart-sentry';
  import { SmartLoading, SmartToast } from '@/lib/smart-support';
  import { purchaseReceiveApi } from '@/api/business/purchase/purchase-receive-api';

  const defaultForm = {
    purchaseId: null,
    itemId: null,
    status: null,
    pageNum: 1,
    pageSize: 10,
    searchCount: true,
  };
  const queryForm = reactive({ ...defaultForm });
  const dataList = ref([]);

  function buildParam(pageNum) {
    queryForm.pageNum = pageNum;
    return Object.assign({}, queryForm, { pageNum });
  }

  async function query(mescroll, isDown, param) {
    try {
      let res = await purchaseReceiveApi.query(param);
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
    return s === 1 ? 's-received' : s === 2 ? 's-stocked' : 's-invalid';
  }
  function flagClass(f) {
    return f === 3 ? 'f-over' : f === 2 ? 'f-under' : 'f-normal';
  }

  function confirmInbound(item) {
    uni.showModal({
      title: '提示',
      content: `确定将收货单【${item.receiveNo}】入库确认吗？`,
      success: async (res) => {
        if (res.confirm) {
          try {
            SmartLoading.show();
            await purchaseReceiveApi.confirmInbound(item.receiveId);
            SmartToast.success('入库确认成功');
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

  function goAdd() {
    uni.navigateTo({ url: '/pages/purchase/receive-form' });
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
      .s-received { color: #fa8c16; background: #fff7e6; }
      .s-stocked { color: #52c41a; background: #f6ffed; }
      .s-invalid { color: #bfbfbf; background: #f5f5f5; }
    }
    .card-body {
      .row {
        font-size: 28rpx;
        color: #555;
        line-height: 48rpx;
        display: flex;
        .label { color: #999; width: 140rpx; }
      }
      .f-normal { color: #52c41a; }
      .f-under { color: #fa8c16; }
      .f-over { color: #f5222d; }
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

  .fab {
    position: fixed;
    right: 40rpx;
    bottom: 60rpx;
    width: 96rpx;
    height: 96rpx;
    border-radius: 50%;
    background: #1a9aff;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4rpx 12rpx rgba(26, 154, 255, 0.4);
  }

  page {
    background-color: #f5f5f5;
  }
</style>
