<template>
  <view class="container">
    <view class="smart-form">
      <uni-forms ref="formRef" :label-width="120" :modelValue="form" label-position="left" :rules="rules">
        <view class="smart-form-group">
          <view class="smart-form-group-title"> 收货信息 </view>
          <view class="smart-form-group-content">
            <uni-forms-item class="smart-form-item" label="采购单ID" name="purchaseId" required>
              <uni-easyinput trim="all" v-model="form.purchaseId" placeholder="请输入采购单ID" type="number" />
            </uni-forms-item>
            <uni-forms-item class="smart-form-item" label="明细ID" name="itemId" required>
              <uni-easyinput trim="all" v-model="form.itemId" placeholder="请输入明细ID" type="number" />
            </uni-forms-item>
            <uni-forms-item class="smart-form-item" label="收货数量" name="receiveQuantity" required>
              <uni-easyinput trim="all" v-model="form.receiveQuantity" placeholder="请输入收货数量" type="number" />
            </uni-forms-item>
            <uni-forms-item class="smart-form-item" label="收货重量" name="receiveWeight">
              <uni-easyinput trim="all" v-model="form.receiveWeight" placeholder="请输入收货重量(kg)" type="number" />
            </uni-forms-item>
            <uni-forms-item class="smart-form-item" label="单价" name="unitPrice">
              <uni-easyinput trim="all" v-model="form.unitPrice" placeholder="请输入单价" type="number" />
            </uni-forms-item>
            <uni-forms-item class="smart-form-item" label="收货人ID" name="receiveBy">
              <uni-easyinput trim="all" v-model="form.receiveBy" placeholder="请输入收货人ID" type="number" />
            </uni-forms-item>
            <uni-forms-item class="smart-form-item" label="直接入库" name="directStock">
              <switch :checked="form.directStock" @change="onDirectStockChange" />
            </uni-forms-item>
          </view>
        </view>
      </uni-forms>

      <view class="smart-form-submit smart-margin-top20 bottom-button">
        <button class="smart-form-submit-btn smart-margin-right20" type="default" @click="cancel">取消</button>
        <button class="smart-form-submit-btn" type="primary" @click="ok">保存</button>
      </view>
    </view>
  </view>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { purchaseReceiveApi } from '@/api/business/purchase/purchase-receive-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { SmartLoading, SmartToast } from '@/lib/smart-support';

  const defaultForm = {
    purchaseId: undefined,
    itemId: undefined,
    receiveQuantity: undefined,
    receiveWeight: undefined,
    unitPrice: undefined,
    receiveBy: undefined,
    directStock: false,
  };
  let form = reactive({ ...defaultForm });

  const rules = {
    purchaseId: { rules: [{ required: true, errorMessage: '请输入采购单ID' }] },
    itemId: { rules: [{ required: true, errorMessage: '请输入明细ID' }] },
    receiveQuantity: { rules: [{ required: true, errorMessage: '请输入收货数量' }] },
  };

  function onDirectStockChange(e) {
    form.directStock = e.detail.value;
  }

  const formRef = ref();

  function cancel() {
    uni.navigateBack();
  }

  function ok() {
    formRef.value
      .validate()
      .then(async () => {
        SmartLoading.show();
        try {
          await purchaseReceiveApi.add(form);
          SmartToast.success('添加成功');
          uni.navigateBack();
        } catch (e) {
          smartSentry.captureError(e);
        } finally {
          SmartLoading.hide();
        }
      })
      .catch(() => {
        SmartToast.toast('参数验证错误，请仔细填写表单数据!');
      });
  }
</script>

<style lang="scss" scoped>
  .bottom-button {
    position: fixed;
    bottom: 0;
  }
</style>
