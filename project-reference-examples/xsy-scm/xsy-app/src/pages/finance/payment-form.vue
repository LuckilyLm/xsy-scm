<template>
  <view class="page">
    <view class="block">
      <view class="title">登记收款</view>
      <view class="field">
        <text class="label">应收单ID</text>
        <text class="value">{{ form.receivableId }}</text>
      </view>
      <view class="field">
        <text class="label">客户ID</text>
        <text class="value">{{ form.customerId }}</text>
      </view>
      <view class="field">
        <text class="label">收款金额</text>
        <input class="input" v-model="form.amount" type="digit" placeholder="请输入收款金额" />
      </view>
      <view class="field column">
        <text class="label">收款渠道</text>
        <view class="channel">
          <view
            v-for="item in channelList"
            :key="item.value"
            class="channel-item"
            :class="{ active: form.payChannel === item.value }"
            @click="form.payChannel = item.value"
          >{{ item.desc }}</view>
        </view>
      </view>
      <view class="field column">
        <text class="label">备注</text>
        <textarea class="textarea" v-model="form.remark" placeholder="选填" />
      </view>
    </view>

    <view class="footer">
      <button class="btn cancel" @click="goBack">取消</button>
      <button class="btn submit" @click="submit">提交</button>
    </view>
  </view>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import { smartSentry } from '@/lib/smart-sentry';
  import { SmartLoading, SmartToast } from '@/lib/smart-support';
  import { financeApi } from '@/api/business/finance/finance-api';
  import financeConst from '@/constants/business/finance/finance-const';

  const channelList = [
    { value: 1, desc: '现金' },
    { value: 2, desc: '转账' },
    { value: 3, desc: '在线支付' },
    { value: 4, desc: '余额扣减' },
  ];

  const form = reactive({
    receivableId: undefined,
    customerId: undefined,
    amount: undefined,
    payChannel: undefined,
    remark: '',
  });

  onLoad((options) => {
    form.receivableId = options.receivableId ? Number(options.receivableId) : undefined;
    form.customerId = options.customerId ? Number(options.customerId) : undefined;
    form.amount = options.balance ? Number(options.balance) : undefined;
  });

  function goBack() {
    uni.navigateBack();
  }

  async function submit() {
    if (!form.amount || Number(form.amount) <= 0) {
      SmartToast.error('请输入正确的收款金额');
      return;
    }
    if (!form.payChannel) {
      SmartToast.error('请选择收款渠道');
      return;
    }
    try {
      SmartLoading.show();
      await financeApi.addPayment(form);
      SmartToast.success('登记成功，收款单待财务确认');
      setTimeout(() => {
        uni.navigateBack();
      }, 600);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>

<style lang="scss" scoped>
  .page {
    min-height: 100vh;
    background-color: #f5f5f5;
    padding: 24rpx;
    box-sizing: border-box;
  }
  .block {
    background: #fff;
    border-radius: 12rpx;
    padding: 30rpx;
    .title {
      font-size: 34rpx;
      font-weight: bold;
      margin-bottom: 20rpx;
    }
    .field {
      display: flex;
      align-items: center;
      padding: 24rpx 0;
      border-bottom: 1rpx solid #f0f0f0;
      .label {
        width: 160rpx;
        font-size: 28rpx;
        color: #999;
      }
      .value {
        font-size: 28rpx;
        color: #333;
      }
      .input {
        flex: 1;
        font-size: 28rpx;
        text-align: right;
      }
    }
    .field.column {
      flex-direction: column;
      align-items: flex-start;
      .label {
        margin-bottom: 16rpx;
      }
    }
    .channel {
      display: flex;
      flex-wrap: wrap;
      gap: 16rpx;
      .channel-item {
        padding: 12rpx 28rpx;
        border-radius: 8rpx;
        background: #f5f5f5;
        color: #666;
        font-size: 26rpx;
        border: 1rpx solid transparent;
      }
      .channel-item.active {
        background: #e6f7ff;
        color: #1a9aff;
        border-color: #1a9aff;
      }
    }
    .textarea {
      width: 100%;
      height: 140rpx;
      background: #f8f8f8;
      border-radius: 8rpx;
      padding: 16rpx;
      font-size: 28rpx;
      box-sizing: border-box;
    }
  }
  .footer {
    display: flex;
    gap: 24rpx;
    margin-top: 40rpx;
    .btn {
      flex: 1;
      border-radius: 44rpx;
      font-size: 30rpx;
    }
    .cancel {
      background: #fff;
      color: #666;
      border: 1rpx solid #ddd;
    }
    .submit {
      background: #1a9aff;
      color: #fff;
    }
  }
</style>
