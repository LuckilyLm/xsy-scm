const MOVEMENT_TYPES = {
  RECHARGE: '余额充值',
  CONSUME: '订单支付',
  REFUND: '退款退回',
  CORRECTION: '余额调整',
};

const RECEIVABLE_STATES = {
  OPEN: '未结清',
  PARTIAL: '部分结清',
  SETTLED: '已结清',
};

export const MOVEMENT_FILTERS = [
  { label: '全部', type: '' },
  { label: '充值', type: 'RECHARGE' },
  { label: '消费', type: 'CONSUME' },
  { label: '退款', type: 'REFUND' },
  { label: '调整', type: 'CORRECTION' },
];

export const RECEIVABLE_FILTERS = [
  { label: '全部', state: '' },
  { label: '未结清', state: 'OPEN' },
  { label: '部分结清', state: 'PARTIAL' },
  { label: '已结清', state: 'SETTLED' },
];

export function movementTypeText(type) {
  return MOVEMENT_TYPES[type] || '余额变动';
}

export function receivableStateText(state) {
  return RECEIVABLE_STATES[state] || '账单处理中';
}
