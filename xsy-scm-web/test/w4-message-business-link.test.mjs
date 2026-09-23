/**
 * Wave 4 §6.2「报损报溢驳回消息要能进入目标业务」前端契约单测。
 *
 * 业务类型判定必须走原生 `t_message.message_type` 的**数值**，不能解析中文标题；
 * 跳转目标必须与后端待办枚举指向同一个列表页（该页消费 `?id=` 打开详情，详情接口自带权限校验）。
 * 这里同时核对前端常量与后端枚举的数值镜像——两侧漂移会让入口静默消失。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  INVENTORY_LOSS_GAIN_LIST_PATH,
  MESSAGE_TYPE_INVENTORY_LOSS_GAIN,
  messageBusinessLink,
} from '../src/lib/message-business-link.ts';
import {MESSAGE_TYPE_ENUM} from '../src/constants/business/message/message-const.ts';

function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const ACCOUNT_DETAIL = '../src/views/system/account/components/message/components/message-detail.vue';
const HEADER_DETAIL = '../src/layout/components/header-user-space/header-message-detail-modal.vue';
const BACKEND_ENUM =
  '../../xsy-scm-server/sa-base/src/main/java/net/lab1024/sa/base/module/support/message/constant/MessageTypeEnum.java';
const LOSS_GAIN_SERVICE =
  '../../xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/inventory/service/InventoryLossGainService.java';

test('驳回消息解析为单据入口，主键原样进 query', () => {
  const link = messageBusinessLink(MESSAGE_TYPE_INVENTORY_LOSS_GAIN, '1234567890123456789');
  assert.ok(link);
  assert.equal(link.path, INVENTORY_LOSS_GAIN_LIST_PATH);
  // 主键保持字符串：转成 Number 会让 19 位 id 静默失真并跳到另一张单据
  assert.deepEqual(link.query, {id: '1234567890123456789'});
  assert.equal(link.label, '查看业务单据');
  // 原生 MessageService 对 null dataId 做过 String.valueOf，数字型 dataId 也要能识别
  assert.equal(messageBusinessLink(MESSAGE_TYPE_INVENTORY_LOSS_GAIN, 123).query.id, '123');
});

test('没有可用主键或非业务消息不给入口', () => {
  for (const dataId of [undefined, null, '', '   ', 'null', '../etc/passwd', '1; drop table', '12.5']) {
    assert.equal(messageBusinessLink(MESSAGE_TYPE_INVENTORY_LOSS_GAIN, dataId), undefined, `${dataId} 不可用`);
  }
  // 通用站内信 / 订单消息不是报损报溢单据
  assert.equal(messageBusinessLink(MESSAGE_TYPE_ENUM.MAIL.value, '123'), undefined);
  assert.equal(messageBusinessLink(MESSAGE_TYPE_ENUM.ORDER.value, '123'), undefined);
});

test('前后端业务类型数值镜像一致，且跳转路径与待办入口同一页面', () => {
  assert.equal(
    MESSAGE_TYPE_ENUM.INVENTORY_LOSS_GAIN.value,
    MESSAGE_TYPE_INVENTORY_LOSS_GAIN,
    '前端消息类型常量与跳转解析器必须同值'
  );
  const java = readFileSync(new URL(BACKEND_ENUM, import.meta.url), 'utf8');
  assert.match(
    java,
    new RegExp(`SCM_INVENTORY_LOSS_GAIN\\(${MESSAGE_TYPE_INVENTORY_LOSS_GAIN},`),
    '后端 MessageTypeEnum 的数值必须与前端镜像一致，否则入口静默失效'
  );
  const todo = readFileSync(
    new URL(
      '../../xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/dashboard/constant/ScmTodoCardEnum.java',
      import.meta.url
    ),
    'utf8'
  );
  assert.ok(todo.includes(`"${INVENTORY_LOSS_GAIN_LIST_PATH}`), '消息入口应跳到待办使用的同一个列表页');
  // 驳回通知改用业务类型，而不是留在通用站内信类型上
  assert.match(
    readFileSync(new URL(LOSS_GAIN_SERVICE, import.meta.url), 'utf8'),
    /setMessageType\(MessageTypeEnum\.SCM_INVENTORY_LOSS_GAIN\.getValue\(\)\)/
  );
});

test('两个消息详情视图共用解析入口，且绝不按中文标题判定业务', () => {
  for (const file of [ACCOUNT_DETAIL, HEADER_DETAIL]) {
    const vue = code(file);
    assert.match(vue, /import \{messageBusinessLink\} from '\/@\/lib\/message-business-link';/);
    // dataId 必须显式声明在 reactive 里，否则类型收窄拿不到主键
    assert.match(vue, /dataId: ''/);
    assert.match(vue, /messageBusinessLink\(messageDetail\.messageType, messageDetail\.dataId\)/);
    assert.match(vue, /router\.push\(\{path: link\.path, query: link\.query\}\)/);
    assert.doesNotMatch(vue, /messageDetail\.title\s*\.\s*(includes|indexOf|startsWith)/, '不得解析中文标题');
  }
  // 跳转只落在列表页 deep-link 上：列表/详情接口继续负责鉴权，失去权限时仍是 403
  const lossGain = code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue');
  assert.match(lossGain, /const id = deepLinkId\(route\.query\);/);
  assert.match(lossGain, /await openDetailById\(id\);/);
});
