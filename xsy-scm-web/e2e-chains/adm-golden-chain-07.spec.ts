/*
 * §15 E2E-07 黄金业务链（清单原文：在线充值 → Receipt → 钱包 CREDIT → 余额支付 → 无 Receipt → BALANCE 核销）
 *
 * 这条链守的是「钱的两种身份不能混」：
 *   充值是**真收钱** —— 必须留下一张财务 Receipt，并把钱包记一笔 CREDIT；
 *   余额支付是**花已经收过的钱** —— 绝不能再开一张 Receipt（否则收入被重复计一次），
 *   它只能以 BALANCE 身份去核销应收。
 * 只验钱包不验 Receipt 数量，就测不出「重复计收」这个最危险的不变量破坏。
 *
 * 关键契约：本链显式使用 `DELAYED` 模拟场景，把渠道确认与创建意图分成两步。
 * `/scm/balance/recharge/create` 只建立待支付意图；验签成功的渠道成功回调在同一事务中
 * 派生 Finance Receipt 与钱包 CREDIT。缺省 `SUCCESS` 场景会在创建时同步走完这条成功链，
 * 不能用来验证「渠道确认前不得到账」。
 * 回调报文用 `X-Scm-Mock-Signature` 携带 `HmacSHA256(rawBody, secret)` 的小写十六进制，
 * secret 取 `scm.payment.mock.secret`（dev 默认 `scm-local-mock-secret`）；
 * 签名不匹配时业务侧不会抛错，而是如实落一条 REJECTED 事件 —— 所以这里必须自己算签名，
 * 不能指望"随便发一发就算渠道回调"。
 */
import {expect, test} from '../e2e/scm-test-base';
import {
    call,
    createSignedOrder,
    financeDateRange,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
    type SignedOrderFacts,
} from '../e2e/scm-finance-fixtures';
import {createHmac, randomUUID} from 'node:crypto';

test.describe.configure({mode: 'serial'});

const RECHARGE = '100.0000';
const MOCK_SECRET = 'scm-local-mock-secret';

let harness: FinanceHarness;
let facts: SignedOrderFacts;
let receiptsBefore = 0;
let receiptsAtPayment = 0;
let rechargeIntent: Row;

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (harness) await harness.close();
});

const receiptRows = async () => {
    const range = financeDateRange();
    const list = await call<Row>(harness.finance, 'post', '/scm/finance/receipt/query',
        {pageNum: 1, pageSize: 100, customerId: Number(facts.customerId),
         startDate: range.startDate, endDate: range.endDate});
    return (list.list ?? []) as Row[];
};

const writeOffRows = async () => {
    const range = financeDateRange();
    const rows = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query',
        {pageNum: 1, pageSize: 100, targetId: Number(facts.receivable.receivableId),
         startDate: range.startDate, endDate: range.endDate});
    return (rows.list ?? []) as Row[];
};

const wallet = async () => {
    const rows = await call<Row>(harness.admin, 'post', '/scm/balance/query',
        {pageNum: 1, pageSize: 20, customerId: Number(facts.customerId)});
    const list = (rows.list ?? []) as Row[];
    return list.length ? Number(list[0].availableBalance ?? 0) : 0;
};

/** 用与报文**逐字节相同**的字符串去算签名，否则一定验签失败。 */
const deliverMockCallback = async (payload: Record<string, unknown>) => {
    const rawBody = JSON.stringify(payload);
    const signature = createHmac('sha256', MOCK_SECRET).update(rawBody, 'utf8').digest('hex');
    const response = await harness.admin.post('/scm/payment/callback/mock', {
        data: rawBody,
        headers: {'Content-Type': 'application/json', 'X-Scm-Mock-Signature': signature,
                  'X-Scm-Mock-Event-Id': String(payload.eventId),
                  'Idempotency-Key': randomUUID()},
    });
    const body = await response.json();
    expect(body.code, `回调投递失败：${body.code} ${body.msg}`).toBe(0);
    return body.data as Row;
};

test('1 订单 → 签收 → NORMAL 应收（余额支付要核销的对象）', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN07', [
        {quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'},
    ]);
    expect(Number(facts.receivable.amount) > 0).toBe(true);
    receiptsBefore = (await receiptRows()).length;
    expect(await wallet(), '新客户的钱包期初余额应为 0').toBe(0);
});

test('2 延迟充值只建待支付意图，渠道确认前没有 Receipt 或钱包 CREDIT', async () => {
    rechargeIntent = await call<Row>(harness.admin, 'post', '/scm/balance/recharge/create',
        {customerId: Number(facts.customerId), amount: RECHARGE, provider: 'MOCK', mockScenario: 'DELAYED',
         remark: `${harness.runTag} GOLDEN07 充值`});
    expect(String(rechargeIntent.status)).toBe('PENDING');
    expect(await wallet(), '还没收到渠道确认就入账 = 凭空生钱').toBe(0);
    expect((await receiptRows()).length, '渠道成功前不能登记真实收款').toBe(receiptsBefore);
});

test('3 合法成功回调同时形成 Receipt 与钱包 CREDIT，重复回调不重复入账', async () => {
    const intent = await call<Row>(harness.admin, 'get', `/scm/payment/intent/detail/${rechargeIntent.id}`);
    const transaction = (intent.transactions as Row[])[0];
    expect(transaction?.providerTransactionNo, '充值意图没有可供回调匹配的渠道交易号').toBeTruthy();
    const eventId = `${harness.runTag}-GOLDEN07-SUCCESS`;
    const payload = {
        eventId,
        eventType: 'PAYMENT_SUCCEEDED',
        providerTransactionNo: transaction.providerTransactionNo,
        amount: RECHARGE,
    };
    const applied = await deliverMockCallback(payload);
    expect(String(applied.processStatus)).toBe('APPLIED');
    expect(Number(applied.transactionId)).toBe(Number(transaction.id));

    const receipts = await receiptRows();
    expect(receipts.length, '渠道成功必须登记一张真实收款').toBe(receiptsBefore + 1);
    const rechargeReceipt = receipts.find((row) => String(row.method) === 'ONLINE_PAYMENT') as Row;
    expect(rechargeReceipt !== undefined, '充值没有形成 ONLINE_PAYMENT 收款').toBe(true);
    expect(Number(rechargeReceipt.amount)).toBe(Number(RECHARGE));
    expect(Boolean(rechargeReceipt.walletFunding), '充值收款必须标记为钱包权益来源').toBe(true);
    expect(await wallet(), '渠道成功后钱包没有增加权益').toBe(Number(RECHARGE));

    await deliverMockCallback(payload);
    expect((await receiptRows()).length, '重复回调产生了第二张收款').toBe(receiptsBefore + 1);
    expect(await wallet(), '重复回调产生了第二笔钱包 CREDIT').toBe(Number(RECHARGE));
});

test('4 余额支付只消费钱包，不产生第二张 Receipt', async () => {
    receiptsAtPayment = (await receiptRows()).length;
    const paid = String(facts.receivable.amount);
    const intent = await call<Row>(harness.admin, 'post', '/scm/payment/intent/create', {
        customerId: Number(facts.customerId),
        sourceType: 'SALES_ORDER',
        sourceId: Number(facts.orderId),
        amount: paid,
        method: 'BALANCE',
        provider: 'INTERNAL_BALANCE',
        remark: `${harness.runTag} GOLDEN07 余额支付`,
    });
    expect(String(intent.status)).toBe('SUCCEEDED');
    expect((await receiptRows()).length, '余额支付把已收的钱重复登记为收款').toBe(receiptsAtPayment);
    expect(await wallet(), '余额支付没有扣减钱包').toBeCloseTo(Number(RECHARGE) - Number(paid), 4);
});

test('5 余额消费以 BALANCE_MOVEMENT 自动核销订单应收', async () => {
    const rows = await writeOffRows();
    const balanceWriteOff = rows.find((row) => String(row.sourceType) === 'BALANCE_MOVEMENT') as Row;
    expect(balanceWriteOff !== undefined, '余额支付没有产生 BALANCE_MOVEMENT 核销').toBe(true);
    expect(Number(balanceWriteOff.amount)).toBe(Number(facts.receivable.amount));
    expect(String(balanceWriteOff.entryType)).toBe('NORMAL');

    const detail = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${facts.receivable.receivableId}`);
    const receivable = (detail.receivable ?? detail) as Row;
    expect(Number(receivable.writtenOffAmount)).toBe(Number(facts.receivable.amount));
    expect(Number(receivable.openAmount)).toBe(0);
});
