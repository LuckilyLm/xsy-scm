<template>
  <PrintDocumentModal :open="visible" document-type="DELIVERY_NOTE"
                      :business-ids="routeId === undefined ? [] : [routeId]" @close="visible = false"/>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import PrintDocumentModal from '../print/print-document-modal.vue';
import type {Id} from './delivery-types';

const visible = ref(false);
const routeId = ref<Id>();

function open(id: Id) {
  routeId.value = id;
  visible.value = true;
}

defineExpose({open});
</script>
