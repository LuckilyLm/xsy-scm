<template>
  <a-card :bordered="false" size="small">
    <div class="page-heading">
      <div>
        <h2>{{ heading }}</h2>
        <p class="scm-note">{{ description }}</p>
        <p class="page-versions">
          草稿版本 {{ page.version }}
          <template v-if="page.publishedVersion != null"> · 已发布版本 {{ page.publishedVersion }} {{ page.publishedAt || '' }}</template>
          <template v-else> · 尚未发布</template>
          <a-tag v-if="page.unpublishedChanges" color="orange" class="page-versions__tag">有未发布修改</a-tag>
        </p>
      </div>
      <a-space>
        <a-button v-privilege="'scm:mall:page:query'" :disabled="!page.publishedContent" @click="previewOpen = true">预览已发布内容</a-button>
        <a-button v-privilege="'scm:mall:page:edit'" :loading="saving" :disabled="!dirty" @click="save">保存草稿</a-button>
        <a-button v-privilege="'scm:mall:page:edit'" type="primary" :loading="publishing" :disabled="dirty || !saved" @click="publish">发布</a-button>
      </a-space>
    </div>

    <a-alert v-if="error" type="error" show-icon :message="error" class="page-alert">
      <template #action><a-button size="small" @click="load">重新加载</a-button></template>
    </a-alert>
    <p v-if="dirty" class="scm-note page-note">有未保存修改，发布前请先保存草稿。</p>

    <a-spin :spinning="loading">
      <a-form layout="vertical" class="page-form">
        <a-form-item label="页面标题" required>
          <a-input v-model:value="title" :maxlength="60" show-count placeholder="显示在商城页面顶部" @change="dirty = true" />
        </a-form-item>
        <a-form-item label="页面内容">
          <WangEditor
            :model-value="content"
            :height="420"
            :folder-type="FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value"
            @update:model-value="onContentUpdate"
          />
        </a-form-item>
      </a-form>
    </a-spin>

    <a-modal v-model:open="previewOpen" title="页面预览" :footer="null" :width="720">
      <div class="page-preview">
        <h3>{{ page.title }}</h3>
        <div v-if="page.publishedContent" class="page-preview__content" v-html="page.publishedContent"></div>
        <a-empty v-else description="这个页面还没有发布过" />
      </div>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import {mallDecorationApi, type MallPage, type MallPageCode} from '/@/api/business/scm/mall-decoration-api';
import WangEditor from '/@/components/framework/wangeditor/index.vue';

const props = defineProps<{pageCode: MallPageCode}>();
const PAGE_META: Record<MallPageCode, {heading: string; description: string}> = {
  ANNOUNCEMENT: {heading: '商城公告', description: '展示在商城公告入口，适合发布门店通知、配送调整等信息。'},
  TERMS: {heading: '服务条款', description: '顾客下单前可查看的服务条款；发布后顾客端立即可见。'},
  ABOUT: {heading: '关于我们', description: '介绍门店与品牌信息，展示在商城「我的」页面。'},
  AFTER_SALE: {heading: '售后规则', description: '说明退换货、质量问题处理等售后规则。'},
};
const heading = computed(() => PAGE_META[props.pageCode].heading);
const description = computed(() => PAGE_META[props.pageCode].description);

const page = ref<MallPage>({pageCode: props.pageCode, title: '', content: '', version: 0});
const title = ref('');
const content = ref('');
const loading = ref(false),
  saving = ref(false),
  publishing = ref(false),
  dirty = ref(false),
  error = ref(''),
  previewOpen = ref(false);
/** 空内容不允许发布，与服务端口径一致。 */
const saved = computed(() => hasContent(page.value.content));
/** 编辑器回填时也会触发 onChange，回填期间不标记为未保存。 */
const applying = ref(false);

function hasContent(html?: string | null): boolean {
  const value = html || '';
  return Boolean(value.replace(/<[^>]*>/g, '').trim() || /<img\b/i.test(value));
}

function onContentUpdate(value: string): void {
  content.value = value;
  if (!applying.value) dirty.value = true;
}

function apply(data: MallPage): void {
  applying.value = true;
  page.value = data;
  title.value = data.title;
  content.value = data.content || '';
  dirty.value = false;
  void nextTick(() => {
    applying.value = false;
  });
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    apply((await mallDecorationApi.page(props.pageCode)).data);
  } catch (cause) {
    error.value = String((cause as Error)?.message || '页面加载失败');
  } finally {
    loading.value = false;
  }
}

async function save(): Promise<void> {
  if (!title.value.trim()) {
    void message.warning('请填写页面标题');
    return;
  }
  if (!hasContent(content.value)) {
    void message.warning('页面内容不能为空');
    return;
  }
  saving.value = true;
  try {
    apply(
      (await mallDecorationApi.savePage({pageCode: props.pageCode, version: page.value.version, title: title.value.trim(), content: content.value}))
        .data
    );
    void message.success('草稿已保存');
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '草稿保存失败'));
  } finally {
    saving.value = false;
  }
}

function publish(): void {
  Modal.confirm({
    title: `发布「${heading.value}」？`,
    content: '发布后顾客端立即显示当前草稿内容。',
    onOk: async () => {
      publishing.value = true;
      try {
        apply((await mallDecorationApi.publishPage(props.pageCode, page.value.version)).data);
        void message.success('页面已发布');
      } catch (cause) {
        void message.error(String((cause as Error)?.message || '页面发布失败'));
        throw cause;
      } finally {
        publishing.value = false;
      }
    },
  });
}

onMounted(load);
</script>

<style scoped>
.page-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.page-heading h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.page-versions {
  margin: 0;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  line-height: 20px;
}

.page-versions__tag {
  margin-left: 8px;
}

.page-alert {
  margin-bottom: 12px;
}

.page-note {
  margin: 0 0 12px;
}

.page-form {
  max-width: 900px;
}

.page-preview h3 {
  margin: 0 0 12px;
  text-align: center;
}

.page-preview__content {
  max-height: 60vh;
  overflow: auto;
  padding: 4px 8px;
  line-height: 1.7;
}

.page-preview__content :deep(img) {
  max-width: 100%;
  height: auto;
}

.page-preview__content :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.page-preview__content :deep(td),
.page-preview__content :deep(th) {
  border: 1px solid var(--scm-line);
  padding: 6px 8px;
}
</style>
