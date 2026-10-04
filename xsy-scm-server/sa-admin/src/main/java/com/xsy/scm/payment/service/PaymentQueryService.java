package com.xsy.scm.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentSourceTypeEnum;
import com.xsy.scm.payment.dao.PaymentCallbackEventDao;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentReconciliationDao;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.form.PaymentCallbackQueryForm;
import com.xsy.scm.payment.domain.form.PaymentIntentQueryForm;
import com.xsy.scm.payment.domain.form.PaymentReconciliationQueryForm;
import com.xsy.scm.payment.domain.form.PaymentRefundQueryForm;
import com.xsy.scm.payment.domain.form.PaymentTransactionQueryForm;
import com.xsy.scm.payment.domain.vo.PaymentCallbackEventVO;
import com.xsy.scm.payment.domain.vo.PaymentIntentVO;
import com.xsy.scm.payment.domain.vo.PaymentReconciliationVO;
import com.xsy.scm.payment.domain.vo.PaymentRefundVO;
import com.xsy.scm.payment.domain.vo.PaymentTransactionVO;
import com.xsy.scm.payment.support.PaymentVoAssembler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付域的**只读**查询。
 *
 * <p>
 * 集中一处而不是散在各个 Controller：查询不改状态，没有事务与编排可言， 分成四个 Service 只会让「哪个接口查什么」更难找。
 *
 * <p>
 * 排序一律在 Mapper 里写死（{@code created_at DESC, id DESC}），**不接受客户端排序字段**： 把客户端字符串拼进 ORDER BY 是注入面，而且会让分页结果不稳定。
 */
@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentIntentDao paymentIntentDao;
    private final ScmDataScopeService dataScopeService;
    private final PaymentSourceDao paymentSourceDao;

    private final PaymentTransactionDao paymentTransactionDao;

    private final PaymentRefundDao paymentRefundDao;

    private final PaymentCallbackEventDao paymentCallbackEventDao;

    private final PaymentReconciliationDao paymentReconciliationDao;

    private final PaymentReconciliationService paymentReconciliationService;

    @Transactional(readOnly = true)
    public PageResult<PaymentIntentVO> intentPage(PaymentIntentQueryForm form) {
        Page<?> page = new Page<>(form.getPageNum(), form.getPageSize());
        var rows = paymentIntentDao.queryPage(page, form, dataScopeService.resolve()).stream()
                .map(PaymentVoAssembler::toIntent).toList();
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    /**
     * 支付意图详情：连同它下面的交易流水一起返回。
     *
     * <p>
     * 交易是「这个意图发起过几次、渠道怎么回的」的完整答案，让前端再发一次请求去取 只会让详情页在慢网络下出现「意图已加载、交易还没到」的半截状态。
     */
    @Transactional(readOnly = true)
    public PaymentIntentVO intentDetail(Long id) {
        var intent = paymentIntentDao.selectById(id);
        if (intent == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_INTENT_NOT_FOUND);
        }
        var scope = dataScopeService.resolve();
        if (!paymentSourceDao.customerVisible(intent.getCustomerId(), scope.getCustomerSellerScope())) {
            throw new ScmDataScopeException();
        }
        if (ScmPaymentSourceTypeEnum.SALES_ORDER.name().equals(intent.getSourceType())) {
            var order = paymentSourceDao.selectOrder(intent.getSourceId());
            if (order == null || !scope.getOrderSellerScope().allows(order.sellerId())) {
                throw new ScmDataScopeException();
            }
        }
        PaymentIntentVO vo = PaymentVoAssembler.toIntent(intent);
        List<PaymentTransactionVO> transactions = paymentTransactionDao
                .queryPage(new Page<>(1, 200), transactionQueryOf(id), dataScopeService.resolve()).stream()
                .map(PaymentVoAssembler::toTransaction).toList();
        vo.setTransactions(transactions);
        return vo;
    }

    @Transactional(readOnly = true)
    public PageResult<PaymentTransactionVO> transactionPage(PaymentTransactionQueryForm form) {
        Page<?> page = new Page<>(form.getPageNum(), form.getPageSize());
        var rows = paymentTransactionDao.queryPage(page, form, dataScopeService.resolve()).stream()
                .map(PaymentVoAssembler::toTransaction).toList();
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public PageResult<PaymentRefundVO> refundPage(PaymentRefundQueryForm form) {
        Page<?> page = new Page<>(form.getPageNum(), form.getPageSize());
        return SmartPageUtil.convert2PageResult(page,
                paymentRefundDao.queryPage(page, form).stream().map(PaymentVoAssembler::toRefund).toList());
    }

    /**
     * 回调记录。{@code processStatus = REJECTED} 的记录**必须能查**： 它回答「有没有人伪造过回调」，这正是把未验签事件也落库的理由。
     */
    @Transactional(readOnly = true)
    public PageResult<PaymentCallbackEventVO> callbackPage(PaymentCallbackQueryForm form) {
        Page<?> page = new Page<>(form.getPageNum(), form.getPageSize());
        return SmartPageUtil.convert2PageResult(page, paymentCallbackEventDao.queryPage(page, form).stream()
                .map(PaymentVoAssembler::toCallbackEvent).toList());
    }

    @Transactional(readOnly = true)
    public PageResult<PaymentReconciliationVO> reconciliationPage(PaymentReconciliationQueryForm form) {
        Page<?> page = new Page<>(form.getPageNum(), form.getPageSize());
        return SmartPageUtil.convert2PageResult(page, paymentReconciliationDao.queryPage(page, form).stream()
                .map(PaymentVoAssembler::toReconciliation).toList());
    }

    /** 对账详情：连同逐条差异一起返回（平账批次 items 为空）。 */
    @Transactional(readOnly = true)
    public PaymentReconciliationVO reconciliationDetail(Long id) {
        var row = paymentReconciliationDao.selectById(id);
        if (row == null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_RECONCILIATION_NOT_FOUND);
        }
        PaymentReconciliationVO vo = PaymentVoAssembler.toReconciliation(row);
        vo.setItems(
                paymentReconciliationService.items(id).stream().map(PaymentVoAssembler::toReconciliationItem).toList());
        return vo;
    }

    private static PaymentTransactionQueryForm transactionQueryOf(Long intentId) {
        PaymentTransactionQueryForm query = new PaymentTransactionQueryForm();
        query.setIntentId(intentId);
        return query;
    }
}
