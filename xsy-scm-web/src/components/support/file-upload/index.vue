<!--
  * 文件上传
  *
  *
-->
<template>
  <div class="clearfix">
    <a-upload
      :type="dragger ? 'drag' : 'select'"
      :open-file-dialog-on-click="!dragger || (!uploading && fileList.length < maxUploadSize)"
      :multiple="props.multiple"
      :accept="props.accept"
      :before-upload="beforeUpload"
      :customRequest="customRequest"
      :file-list="fileList"
      :headers="{Authorization: 'Bearer ' + useUserStore().getToken}"
      :list-type="listType"
      @change="handleChange"
      @preview="handlePreview"
      @remove="handleRemove"
    >
      <div v-if="dragger" class="file-drop">
        <p class="file-drop__icon"><InboxOutlined /></p>
        <p class="file-drop__title" aria-live="polite">{{ dropTitle }}</p>
        <p class="file-drop__hint">最多 {{ maxUploadSize }} 个文件，单个小于 {{ maxSize }} MiB，选中文件后立即上传</p>
      </div>
      <div v-else-if="fileList.length < props.maxUploadSize">
        <template v-if="listType === 'picture-card'">
          <PlusOutlined />
          <div class="ant-upload-text">
            {{ buttonText }}
          </div>
        </template>
        <template v-if="listType === 'text'">
          <a-button>
            <upload-outlined />
            {{ buttonText }}
          </a-button>
        </template>
      </div>
    </a-upload>
    <a-modal :footer="null" :open="previewVisible" @cancel="handleCancel">
      <img :src="previewUrl" alt="example" style="width: 100%" />
    </a-modal>
  </div>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {Modal, theme} from 'ant-design-vue';
import {fileApi} from '/@/api/support/file-api';
import {useUserStore} from '/@/store/modules/system/user';
import {XsyLoading} from '/@/components/framework/xsy-loading';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import {xsySentry} from '/@/lib/xsy-sentry';

const props = defineProps({
  value: String,
  dragger: Boolean,
  buttonText: {
    type: String,
    default: '点击上传附件',
  },
  showUploadBtn: {
    type: Boolean,
    default: true,
  },
  defaultFileList: {
    type: Array,
    default: () => [],
  },
  multiple: {
    type: Boolean,
    default: false,
  },
  // 最多上传文件数量
  maxUploadSize: {
    type: Number,
    default: 10,
  },
  maxSize: {
    type: Number,
    default: 10,
  },
  // 上传的文件类型
  accept: {
    type: String,
    default: '',
  },
  // 文件上传类型
  folder: {
    type: Number,
    default: FILE_FOLDER_TYPE_ENUM.COMMON.value,
  },
  // 上传列表的内建样式，支持三种基本样式 text, picture 和 picture-card
  listType: {
    type: String,
    default: 'picture-card',
  },
});

const {token} = theme.useToken();

// 图片类型的后缀名
const imgFileType = ['jpg', 'jpeg', 'png', 'gif'];

// 重新修改图片展示字段
const files = computed(() => {
  let res = [];
  if (props.defaultFileList && props.defaultFileList.length > 0) {
    props.defaultFileList.forEach((element) => {
      element.url = element.fileUrl;
      element.name = element.fileName;
      res.push(element);
    });
    return res;
  }
  return res;
});
// -------------------- 逻辑 --------------------

const previewVisible = ref(false);
const fileList = ref([]);
const previewUrl = ref('');
const pendingUploads = ref(0);
const uploading = computed(() => pendingUploads.value > 0);
const dropTitle = computed(() => {
  if (uploading.value) return '正在上传文件';
  if (fileList.value.length >= props.maxUploadSize) return '已达到文件数量上限';
  return '点击或拖拽文件到此处';
});

watch(
  files,
  (value) => {
    fileList.value = value;
  },
  {
    immediate: true,
  }
);

const emit = defineEmits(['update:value', 'change']);
const customRequest = async (options) => {
  pendingUploads.value++;
  if (!props.dragger && pendingUploads.value === 1) XsyLoading.show();
  try {
    console.log(options);
    const formData = new FormData();
    formData.append('file', options.file);
    let res = await fileApi.uploadFile(formData, props.folder);
    let file = res.data;
    file.url = file.fileUrl;
    file.name = file.fileName;
    fileList.value.push(file);
    emit('change', fileList.value);
  } catch (e) {
    xsySentry.captureError(e);
  } finally {
    pendingUploads.value--;
    if (!props.dragger && !uploading.value) XsyLoading.hide();
  }
};

function handleChange(info) {
  let fileStatus = info.file.status;
  let file = info.file;
  if (fileStatus === 'removed') {
    let index = fileList.value.findIndex((e) => e.fileId === file.fileId);
    if (index !== -1) {
      fileList.value.splice(index, 1);
      emit('change', fileList.value);
    }
  }
}

function handleRemove(file) {
  console.log(fileList.value);
}

function beforeUpload(file, files) {
  if (props.dragger && uploading.value) return false;
  if (fileList.value.length + pendingUploads.value + files.length > props.maxUploadSize) {
    showErrorMsgOnce(`最多支持上传 ${props.maxUploadSize} 个文件哦！`);
    return false;
  }

  if (props.accept) {
    const suffixIndex = file.name.lastIndexOf('.');
    const fileSuffix = file.name.substring(suffixIndex <= -1 ? 0 : suffixIndex);
    if (props.accept.indexOf(fileSuffix) === -1) {
      showErrorMsgOnce(`只支持上传 ${props.accept.replaceAll(',', ' ')} 格式的文件`);
      return false;
    }
  }

  const isLimitSize = file.size / 1024 / 1024 < props.maxSize;
  if (!isLimitSize) {
    showErrorMsgOnce(`单个文件大小必须小于 ${props.maxSize} MiB`);
  }
  return isLimitSize;
}

const showErrorModalFlag = ref(true);
const showErrorMsgOnce = (content) => {
  if (showErrorModalFlag.value) {
    Modal.error({
      title: '提示',
      content: content,
      okType: 'danger',
      centered: true,
      onOk() {
        showErrorModalFlag.value = true;
      },
    });
    showErrorModalFlag.value = false;
  }
};

function handleCancel() {
  previewVisible.value = false;
}

const handlePreview = async (file) => {
  if (imgFileType.some((e) => e === file.fileType)) {
    previewUrl.value = file.url || file.preview;
    previewVisible.value = true;
  } else {
    fileApi.downLoadFile(file.fileKey);
  }
};

// ------------------------ 清空 上传 ------------------------
function clear() {
  fileList.value = [];
}

defineExpose({
  clear,
  uploading,
});
</script>
<style lang="less" scoped>
:deep(.ant-upload-picture-card-wrapper) {
  display: flex;
}

.file-drop {
  padding: 0 12px;
}

:deep(.ant-upload-btn:focus-visible) .file-drop {
  outline: 2px solid v-bind('token.colorPrimary');
  outline-offset: 4px;
}

.file-drop__icon {
  margin: 0 0 4px;
  color: v-bind('token.colorTextQuaternary');
  font-size: 24px;
}

.file-drop__title {
  margin: 0 0 2px;
  font-size: v-bind('token.fontSizeLG + "px"');
}

.file-drop__hint {
  margin: 0;
  color: v-bind('token.colorTextDescription');
}
</style>
