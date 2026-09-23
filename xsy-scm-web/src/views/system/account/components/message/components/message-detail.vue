<template>
  <a-drawer v-model:open="showFlag" :width="800" title="消息内容" placement="right" :destroyOnClose="true">
    <a-descriptions bordered :column="2" size="small">
      <a-descriptions-item :labelStyle="{ width: '80px' }" :span="1" label="类型"
      >{{ $smartEnumPlugin.getDescByValue('MESSAGE_TYPE_ENUM', messageDetail.messageType) }}
      </a-descriptions-item>
      <a-descriptions-item :labelStyle="{ width: '120px' }" :span="1" label="发送时间">{{
          messageDetail.createTime
        }}
      </a-descriptions-item>
      <a-descriptions-item :labelStyle="{ width: '80px' }" :span="2" label="标题">{{
          messageDetail.title
        }}
      </a-descriptions-item>
      <a-descriptions-item :labelStyle="{ width: '80px' }" :span="2" label="内容">
        <pre>{{ messageDetail.content }}</pre>
      </a-descriptions-item>
    </a-descriptions>
    <template v-if="businessLink">
      <a-divider style="margin: 16px 0"/>
      <a-button type="primary" size="small" @click="goBusiness">{{ businessLink.label }}</a-button>
      <a-typography-text type="secondary" style="margin-left: 12px">
        单据内容仍由业务接口按权限返回；若已失去该单据的查看权限，页面会提示无权限。
      </a-typography-text>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {messageApi} from '/@/api/support/message-api';
import {useUserStore} from '/@/store/modules/system/user';
import {messageBusinessLink} from '/@/lib/message-business-link';

const emit = defineEmits(['refresh']);

const messageDetail = reactive({
  messageType: '',
  title: '',
  content: '',
  createTime: '',
  dataId: '',
});

const showFlag = ref(false);
const router = useRouter();
const businessLink = computed(() => messageBusinessLink(messageDetail.messageType, messageDetail.dataId));

function goBusiness() {
  const link = businessLink.value;
  if (!link) {
    return;
  }
  showFlag.value = false;
  void router.push({path: link.path, query: link.query});
}

function show(data) {
  Object.assign(messageDetail, data);
  showFlag.value = true;
  read(data);
}

async function read(message) {
  if (!message.readFlag) {
    await messageApi.updateReadFlag(message.messageId);
    await useUserStore().queryUnreadMessageCount();
    emit('refresh');
  }
}

defineExpose({show});
</script>
