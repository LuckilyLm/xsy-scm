package com.xsy.scm.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceCustomerExpenseDao;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.entity.FinanceCustomerExpenseEntity;
import com.xsy.scm.finance.domain.vo.FinanceCustomerExpenseVO;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinanceCustomerExpenseService {
    private final FinanceCustomerExpenseDao expenseDao;
    private final FinanceCounterpartySourceDao counterpartySourceDao;
    private final ScmDataScopeService dataScopeService;
    private final ScmIdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public PageResult<FinanceCustomerExpenseVO> query(FinanceCustomerExpenseForm.Query form) {
        requireMonth(form.getExpenseMonth());
        if (!dataScopeService.resolve().isCostVisible()) {
            throw new ScmDataScopeException();
        }
        var scope = dataScopeService.resolve().getCustomerSellerScope();
        if (scope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, expenseDao.query(page, form, scope));
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceCustomerExpenseVO add(FinanceCustomerExpenseForm.Add form, String key) {
        requireMonth(form.getExpenseMonth());
        var customer = counterpartySourceDao.selectCustomer(form.getCustomerId());
        if (customer == null) {
            throw new ScmBusinessException(FinanceErrorCode.EXPENSE_NOT_FOUND);
        }
        requireVisible(customer.getSellerId());
        List<BigDecimal> amounts = List.of(amount(form.getTaxAmount()), amount(form.getServiceAmount()),
            amount(form.getFreightAmount()), amount(form.getCommissionAmount()));
        BigDecimal goodsCost = form.getGoodsCostAmount() == null ? null : amount(form.getGoodsCostAmount());
        if (goodsCost == null && amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() <= 0) {
            throw new ScmBusinessException(FinanceErrorCode.EXPENSE_AMOUNT_INVALID);
        }
        var claim = idempotencyService.claim("FINANCE_CUSTOMER_EXPENSE_ADD", key, form);
        if (claim.replay()) {
            return expenseDao.find(idempotencyService.replay(claim, Long.class));
        }
        FinanceCustomerExpenseEntity expense = insert(customer, form.getExpenseMonth(), goodsCost, amounts,
            form.getReason());
        idempotencyService.complete(claim, "FINANCE_CUSTOMER_EXPENSE", expense.getId(), expense.getId());
        return expenseDao.find(expense.getId());
    }

    /**
     * 批量登记同一月份的多客户费用。整批一个幂等键：任一行失败整批回滚，重试同键不会重复写入。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<FinanceCustomerExpenseVO> addBatch(FinanceCustomerExpenseForm.BatchAdd form, String key) {
        requireMonth(form.getExpenseMonth());
        var claim = idempotencyService.claim("FINANCE_CUSTOMER_EXPENSE_BATCH_ADD", key, form);
        if (claim.replay()) {
            Long[] ids = idempotencyService.replay(claim, Long[].class);
            return Arrays.stream(ids).map(id -> {
                var expense = expenseDao.find(id);
                if (expense == null) {
                    throw new ScmBusinessException(FinanceErrorCode.EXPENSE_NOT_FOUND);
                }
                requireVisible(expense.getSellerId());
                return expense;
            }).toList();
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (FinanceCustomerExpenseForm.Row row : form.getRows()) {
            if (!seen.add(row.getCustomerId())) {
                throw new ScmBusinessException(FinanceErrorCode.EXPENSE_BATCH_DUPLICATE);
            }
            var customer = counterpartySourceDao.selectCustomer(row.getCustomerId());
            if (customer == null) {
                throw new ScmBusinessException(FinanceErrorCode.EXPENSE_NOT_FOUND);
            }
            requireVisible(customer.getSellerId());
            List<BigDecimal> amounts = List.of(amount(row.getTaxAmount()), amount(row.getServiceAmount()),
                amount(row.getFreightAmount()), amount(row.getCommissionAmount()));
            BigDecimal goodsCost = row.getGoodsCostAmount() == null ? null : amount(row.getGoodsCostAmount());
            if (goodsCost == null && amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() <= 0) {
                throw new ScmBusinessException(FinanceErrorCode.EXPENSE_AMOUNT_INVALID);
            }
            ids.add(insert(customer, form.getExpenseMonth(), goodsCost, amounts, form.getReason()).getId());
        }
        idempotencyService.complete(claim, "FINANCE_CUSTOMER_EXPENSE_BATCH", ids.getFirst(), ids.toArray(Long[]::new));
        return ids.stream().map(expenseDao::find).toList();
    }

    private FinanceCustomerExpenseEntity insert(FinanceCustomerFactDto customer, LocalDate expenseMonth,
        BigDecimal goodsCost, List<BigDecimal> amounts, String reason) {
        FinanceCustomerExpenseEntity expense = new FinanceCustomerExpenseEntity();
        expense.setExpenseNo(ScmDocumentNumbers.next());
        expense.setCustomerId(customer.getCustomerId());
        expense.setCustomerNameSnapshot(customer.getCustomerName());
        expense.setExpenseMonth(expenseMonth);
        expense.setGoodsCostAmount(goodsCost);
        expense.setTaxAmount(amounts.get(0));
        expense.setServiceAmount(amounts.get(1));
        expense.setFreightAmount(amounts.get(2));
        expense.setCommissionAmount(amounts.get(3));
        expense.setReason(reason.trim());
        expense.setCreatedAt(OffsetDateTime.now());
        expense.setCreatedBy(ScmOperator.current());
        expenseDao.insert(expense);
        return expense;
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceCustomerExpenseVO voidExpense(FinanceCustomerExpenseForm.Void form, String key) {
        FinanceCustomerExpenseVO expense = expenseDao.lock(form.getExpenseId());
        if (expense == null) {
            throw new ScmBusinessException(FinanceErrorCode.EXPENSE_NOT_FOUND);
        }
        requireVisible(expense.getSellerId());
        var claim = idempotencyService.claim("FINANCE_CUSTOMER_EXPENSE_VOID:" + form.getExpenseId(), key, form);
        if (claim.replay()) {
            return expenseDao.find(form.getExpenseId());
        }
        if (Boolean.TRUE.equals(expense.getVoided())
            || expenseDao.insertVoid(expense.getExpenseId(), form.getReason().trim(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(FinanceErrorCode.EXPENSE_ALREADY_VOIDED);
        }
        idempotencyService.complete(claim, "FINANCE_CUSTOMER_EXPENSE", expense.getExpenseId(), expense.getExpenseId());
        return expenseDao.find(expense.getExpenseId());
    }

    private void requireVisible(Long sellerId) {
        if (!dataScopeService.resolve().isCostVisible()
            || !dataScopeService.resolve().getCustomerSellerScope().allows(sellerId)) {
            throw new ScmDataScopeException();
        }
    }

    private static BigDecimal amount(String value) {
        BigDecimal amount = ScmDecimalStrings.parseScale4Required(value);
        if (amount.signum() < 0) {
            throw new ScmBusinessException(FinanceErrorCode.EXPENSE_AMOUNT_INVALID);
        }
        return amount;
    }

    private static void requireMonth(LocalDate month) {
        if (month == null || month.getDayOfMonth() != 1) {
            throw new ScmBusinessException(FinanceErrorCode.EXPENSE_MONTH_INVALID);
        }
    }
}
