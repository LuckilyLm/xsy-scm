package com.xsy.scm.mall.funds.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.manager.CustomerValidator;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.funds.dao.MallFundsSourceDao;
import com.xsy.scm.mall.funds.domain.form.MallReceivableQueryForm;
import com.xsy.scm.mall.funds.domain.form.MallWalletMovementQueryForm;
import com.xsy.scm.mall.funds.domain.vo.MallCreditSummaryVO;
import com.xsy.scm.mall.funds.domain.vo.MallReceivableVO;
import com.xsy.scm.mall.funds.domain.vo.MallWalletMovementVO;
import com.xsy.scm.mall.funds.domain.vo.MallWalletSummaryVO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallFundsQueryService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CustomerService customerService;
    private final CustomerTypeService customerTypeService;
    private final CustomerBalanceService balanceService;
    private final MallFundsSourceDao sourceDao;

    @Transactional(readOnly = true)
    public MallWalletSummaryVO walletSummary(RequestMallCustomer identity) {
        CustomerEntity customer = customer(identity);
        CustomerEntity settlement = customerService.requireSettlementAccount(customer);
        boolean shared = sharedSettlement(customer, settlement);
        MallWalletSummaryVO result = new MallWalletSummaryVO();
        result.setBalanceVisible(!shared);
        if (shared) {
            return result;
        }
        result.setHasWallet(sourceDao.walletAccountExists(settlement.getId()));
        result.setAvailableBalance(balanceService.balanceOfSettlement(settlement.getId()));
        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<MallWalletMovementVO> walletMovements(RequestMallCustomer identity,
        MallWalletMovementQueryForm form) {
        CustomerEntity customer = customer(identity);
        CustomerEntity settlement = customerService.requireSettlementAccount(customer);
        Page<MallWalletMovementVO> page = new Page<>(form.getPageNum(), form.getPageSize(), true);
        page.setOptimizeCountSql(false);
        List<MallWalletMovementVO> rows = sourceDao.walletMovements(page, customer.getId(), settlement.getId(),
            form.getType(), form.getDirection());
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public MallCreditSummaryVO creditSummary(RequestMallCustomer identity) {
        CustomerEntity customer = customer(identity);
        CustomerEntity settlement = customerService.requireSettlementAccount(customer);
        boolean shared = sharedSettlement(customer, settlement);
        MallFundsSourceDao.CreditFacts facts = sourceDao.creditFacts(customer.getId());
        MallCreditSummaryVO result = new MallCreditSummaryVO();
        result.setSharedSettlement(shared);
        result.setCreditLimitVisible(
            !shared && settlement.getCreditLimit() != null && settlement.getCreditLimit().signum() > 0);
        if (!shared) {
            result.setCreditLimit(
                Boolean.TRUE.equals(result.getCreditLimitVisible()) ? settlement.getCreditLimit() : null);
            result.setCreditPeriodType(settlement.getCreditPeriodType());
            result.setCreditPeriodValue(settlement.getCreditPeriodValue());
            result.setCreditPeriodUnit(settlement.getCreditPeriodUnit());
            result.setCreditAmountThreshold(settlement.getCreditAmountThreshold());
            result.setSettleDay(settlement.getSettleDay());
        }
        result.setOpenReceivableAmount(facts.getOpenReceivableAmount());
        result.setConfirmedOrderAmount(facts.getConfirmedOrderAmount());
        result.setEarliestOverdueDate(facts.getEarliestOverdueDate());
        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<MallReceivableVO> receivables(RequestMallCustomer identity, MallReceivableQueryForm form) {
        Long customerId = customer(identity).getId();
        Page<FinanceReceivableVO> page = new Page<>(form.getPageNum(), form.getPageSize(), true);
        page.setOptimizeCountSql(false);
        List<MallReceivableVO> rows = sourceDao.receivables(page, customerId, form.getSettleState()).stream()
            .map(MallFundsQueryService::receivableView).toList();
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public MallReceivableVO receivable(RequestMallCustomer identity, Long receivableId) {
        FinanceReceivableVO row = sourceDao.receivable(customer(identity).getId(), receivableId);
        if (row == null) {
            throw new ScmBusinessException(MallErrorCode.FUNDS_RECEIVABLE_NOT_FOUND);
        }
        return receivableView(row);
    }

    private CustomerEntity customer(RequestMallCustomer identity) {
        return customerService.requireTradable(identity.getCustomerId());
    }

    private boolean sharedSettlement(CustomerEntity customer, CustomerEntity settlement) {
        // 唯一判定点见 CustomerValidator.sharedSettlement：商城结算方式选项必须与这里同口径。
        return CustomerValidator.sharedSettlement(customer.getId(), settlement.getId(),
            customerTypeService.require(settlement.getCustomerTypeId()).getTypeCode());
    }

    private static MallReceivableVO receivableView(FinanceReceivableVO row) {
        MallReceivableVO result = new MallReceivableVO();
        result.setReceivableId(row.getReceivableId());
        result.setReceivableNo(row.getReceivableNo());
        result.setOrderId(row.getOrderId());
        result.setOrderNo(row.getOrderNo());
        result.setSettleState(row.getSettleState());
        result.setDueDate(row.getDueDate());
        result.setOverdue(row.getDueDate() != null && row.getOpenAmount().signum() > 0
            && row.getDueDate().isBefore(LocalDate.now(BUSINESS_ZONE)));
        result.setEventAt(row.getEventAt());
        result.setAmount(row.getAmount());
        result.setRedAmount(row.getAmount().subtract(row.getNetAmount()));
        result.setWrittenOffAmount(row.getWrittenOffAmount());
        result.setOpenAmount(row.getOpenAmount());
        return result;
    }
}
