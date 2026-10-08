<!--
  * 消息通知
  *
-->

<template>
  <div>
    <a-popover v-model:open="show" trigger="click" placement="bottomLeft" :getPopupContainer="getPopupContainer">
      <a-button type="text" aria-label="查看未读消息" @click="showMessage" style="padding: 4px 5px">
        <a-badge :count="unreadMessageCount">
          <div style="width: 26px; height: 26px">
            <BellOutlined :style="{ fontSize: '16px' }"/>
          </div>
        </a-badge>
      </a-button>

      <template #content>
        <a-spin :spinning="loading">
          <a-tabs class="dropdown-tabs" centered :tabBarStyle="{ textAlign: 'center' }"
                  style="width: 300px; z-index: 1030">
            <a-tab-pane key="message">
              <template #tab>
                未读消息
                <a-badge :count="unreadMessageCount" :show-zero="false" :offset="[-5, -15]"/>
              </template>
              <a-list class="tab-pane" size="small">
                <a-list-item v-for="item in messageList" :key="item.messageId">
                  <a-list-item-meta>
                    <template #title>
                      <div class="title">
                        <a-badge status="error"/>
                        <a @click="showMessageDetail(item)">{{ item.title }}</a>
                      </div>
                    </template>
                    <template #description>
                      <span> {{ timeago(item.createTime) }}</span>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
                <a-list-item v-if="unreadMessageCount > 3">
                  <a-button type="link" @click="gotoMessage" style="margin: 0 auto"> 查看更多</a-button>
                </a-list-item>
              </a-list>
            </a-tab-pane>
          </a-tabs>
        </a-spin>
      </template>
    </a-popover>
    <MessageDetailModal ref="messageDetailModalRef" @refresh="queryMessage"/>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {BellOutlined} from '@ant-design/icons-vue';
import {useUserStore} from '/@/store/modules/system/user';
import {smartSentry} from '/@/lib/smart-sentry';
import {messageApi} from '/@/api/support/message-api';
import dayjs from 'dayjs';
import {theme} from 'ant-design-vue';
import {useRouter} from 'vue-router';
import MessageDetailModal from './header-message-detail-modal.vue';

const {useToken} = theme;
const {token} = useToken();

function getPopupContainer() {
  return document.body;
}

const loading = ref(false);
const show = ref(false);

// 点击按钮打开消息气泡卡片的同时刷新消息
function showMessage() {
  show.value = true;
  queryMessage();
}

function closeMessage() {
  show.value = false;
}

// ------------------------- 查询消息  -------------------------

// 未读消息
const unreadMessageCount = computed(() => {
  return useUserStore().unreadMessageCount;
});

// 消息列表
const messageList = ref([]);

// 查询我的未读消息
async function queryMessage() {
  try {
    loading.value = true;
    let responseModel = await messageApi.queryMessage({
      pageNum: 1,
      pageSize: 3,
      readFlag: false,
    });
    messageList.value = responseModel.data.list;
    // 若中途有新消息了 打开列表也能及时更新未读数量
    useUserStore().queryUnreadMessageCount();
  } catch (e) {
    smartSentry.captureError(e);
  } finally {
    loading.value = false;
  }
}

const messageDetailModalRef = ref();

function showMessageDetail(data) {
  messageDetailModalRef.value.show(data);
  closeMessage();
}

const router = useRouter();

function gotoMessage() {
  show.value = false;
  router.push({path: '/account', query: {menuId: 'message'}});
}

// ------------------------- 时间计算  -------------------------
function timeago(dateStr) {
  let dateTimeStamp = dayjs(dateStr).toDate().getTime();
  let result = '';
  let minute = 1000 * 60; //把分，时，天，周，半个月，一个月用毫秒表示
  let hour = minute * 60;
  let day = hour * 24;
  let week = day * 7;
  let month = day * 30;
  let now = new Date().getTime(); //获取当前时间毫秒
  let diffValue = now - dateTimeStamp; //时间差

  if (diffValue < 0) {
    return '刚刚';
  }
  let minC = diffValue / minute; //计算时间差的分，时，天，周，月
  let hourC = diffValue / hour;
  let dayC = diffValue / day;
  let weekC = diffValue / week;
  let monthC = diffValue / month;
  if (monthC >= 1 && monthC <= 3) {
    result = ' ' + parseInt(monthC) + '月前';
  } else if (weekC >= 1 && weekC <= 3) {
    result = ' ' + parseInt(weekC) + '周前';
  } else if (dayC >= 1 && dayC <= 6) {
    result = ' ' + parseInt(dayC) + '天前';
  } else if (hourC >= 1 && hourC <= 23) {
    result = ' ' + parseInt(hourC) + '小时前';
  } else if (minC >= 1 && minC <= 59) {
    result = ' ' + parseInt(minC) + '分钟前';
  } else if (diffValue >= 0 && diffValue <= minute) {
    result = '刚刚';
  } else {
    let datetime = new Date();
    datetime.setTime(dateTimeStamp);
    let year = datetime.getFullYear();
    let month = datetime.getMonth() + 1 < 10 ? '0' + (datetime.getMonth() + 1) : datetime.getMonth() + 1;
    let date = datetime.getDate() < 10 ? '0' + datetime.getDate() : datetime.getDate();
    result = year + '-' + month + '-' + date;
  }
  return result;
}
</script>

<style lang="less" scoped>
@smart-page-tag-operate-width: 40px;
@color-primary: v-bind('token.colorPrimary');

.message-icon-div {
  cursor: pointer;
  height: 32px;
  width: 42px;
  padding-left: 10px;
}

.message-icon-div:hover {
  background: @hover-bg-color !important;
}

.header-notice {
  display: inline-block;
  transition: all 0.3s;

  span {
    vertical-align: initial;
  }

  .notice-badge {
    color: inherit;
  }
}

.title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.dropdown-tabs {
  border-radius: 4px;
}

.tab-pane {
  height: auto;
}
</style>
