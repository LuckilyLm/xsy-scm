<script setup lang="ts">
import {computed, ref} from 'vue';
import {api, query, errorText, unknownResult} from '../api';
import {session} from '../store';
import type {Scope} from '../types';
const props = defineProps<{scope: Scope | null}>();
const emit = defineEmits<{saved: []}>();
type Request = {
  operationId: string;
  eventKey: string;
  lotId: string;
  packageType: string;
};
const visible = ref(false),
  busy = ref(false),
  error = ref(''),
  eventKey = ref(''),
  lotId = ref(''),
  packageType = ref('CRATE');
const lots = ref<{value: string; label: string}[]>([]);
const pending = ref<Request | null>(null);
const key = computed(() => `xsy-trace-label:${session.value}:${props.scope?.taskItemId}:${eventKey.value}`);
let generation = 0;
async function open(event: string) {
  if (!props.scope) return;
  const current = ++generation;
  visible.value = true;
  eventKey.value = event;
  error.value = '';
  busy.value = true;
  lots.value = [];
  lotId.value = '';
  try {
    pending.value = JSON.parse(localStorage.getItem(key.value) || 'null');
    const rows = await api<
      Array<{
        lotId: string | number;
        lotNo: string;
        balance: string;
        unit: string;
      }>
    >('/trace-lots' + query(props.scope), session.value);
    if (current !== generation) return;
    lots.value = rows.map((row) => ({
      value: String(row.lotId),
      label: `${row.lotNo}（余量 ${row.balance}${row.unit}）`,
    }));
    lotId.value = pending.value?.lotId ?? lots.value[0]?.value ?? '';
    packageType.value = pending.value?.packageType ?? 'CRATE';
  } catch (cause) {
    if (current === generation) error.value = errorText(cause);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function print() {
  if (!props.scope || busy.value || !lotId.value) return;
  const scope = {...props.scope},
    storageKey = key.value;
  busy.value = true;
  error.value = '';
  try {
    pending.value ??= {
      operationId: crypto.randomUUID(),
      eventKey: eventKey.value,
      lotId: lotId.value,
      packageType: packageType.value,
    };
    localStorage.setItem(storageKey, JSON.stringify(pending.value));
    await api('/trace-labels' + query(scope), session.value, pending.value);
    localStorage.removeItem(storageKey);
    pending.value = null;
    visible.value = false;
    emit('saved');
  } catch (cause) {
    error.value = errorText(cause);
    if (!unknownResult(cause)) {
      localStorage.removeItem(storageKey);
      pending.value = null;
    }
  } finally {
    busy.value = false;
  }
}
function close() {
  if (!busy.value) {
    ++generation;
    visible.value = false;
  }
}
defineExpose({open});
</script>
<template>
  <a-modal
    :open="visible"
    title="签发包装码并打印"
    ok-text="确认批次并打印"
    :confirm-loading="busy"
    :ok-button-props="{disabled: !lotId || busy}"
    :closable="!busy"
    :mask-closable="false"
    @ok="print"
    @cancel="close"
  >
    <a-alert
      type="info"
      show-icon
      message="请先核对实物批次。包装码由平台签发，商品完成后才绑定客户；标签不代表已经出库。请在完成商品前签发各筐标签。"
    />
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-form layout="vertical">
      <a-form-item label="来源批次" required><a-select v-model:value="lotId" :options="lots" :disabled="busy || !!pending" /></a-form-item>
      <a-form-item label="包装类型" required
        ><a-select
          v-model:value="packageType"
          :disabled="busy || !!pending"
          :options="[
            {value: 'CRATE', label: '筐'},
            {value: 'BOX', label: '箱'},
            {value: 'BAG', label: '袋'},
          ]"
      /></a-form-item>
    </a-form>
    <p v-if="pending">上次结果尚未确认，再次提交会核对原发码和打印请求，不增加称重记录。</p>
  </a-modal>
</template>
