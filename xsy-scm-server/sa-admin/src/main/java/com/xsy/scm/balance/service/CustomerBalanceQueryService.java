package com.xsy.scm.balance.service;

import com.xsy.scm.balance.dao.CustomerBalanceAccountDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceAccountEntity;
import com.xsy.scm.balance.domain.form.BalanceMovementQueryForm;
import com.xsy.scm.balance.domain.form.BalanceQueryForm;
import com.xsy.scm.balance.domain.vo.BalanceMovementVO;
import com.xsy.scm.balance.domain.vo.CustomerBalanceVO;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 余额只读查询（ADM-12 3-12a）。
 *
 * <p>
 * <b>数据范围 fail-closed</b>：范围为空时列表直接返回空页（SQL 里是 {@code AND FALSE}），
 * 概览直接拒绝。不做「无范围即全量」的宽松处理 —— 那是 fail-open，而余额是客户的钱。
 *
 * <p>
 * 范围判定按**结算主体的业务员**，与收款/应收列表同一套规则：钱包属于结算主体，
 * 因此「能不能看这家客户的钱包」取决于「能不能看这家客户」。
 */
@Service
@RequiredArgsConstructor
public class CustomerBalanceQueryService {

    private final CustomerBalanceAccountDao customerBalanceAccountDao;

    private final CustomerBalanceMovementDao customerBalanceMovementDao;

    private final CustomerBalanceService customerBalanceService;

    private final CustomerService customerService;

    private final ScmDataScopeService dataScopeService;

    /**
     * 余额概览。
     *
     * <p>
     * 入参只有 {@code customerId}：结算主体由服务端解析，**不接受客户端直接指定钱包账户 id**。
     * 否则客户端可以拿任意 accountId 去看别人的钱包。
     */
    @Transactional(readOnly = true)
    public CustomerBalanceVO balanceView(BalanceQueryForm form) {
        CustomerEntity customer = customerService.require(form.getCustomerId());
        CustomerEntity settlement = customerBalanceService.settlementCustomerOf(form.getCustomerId());
        requireVisible(settlement);

        CustomerBalanceVO vo = new CustomerBalanceVO();
        vo.setCustomerId(customer.getId());
        vo.setCustomerName(customer.getName());
        vo.setSettlementCustomerId(settlement.getId());
        vo.setSettlementCustomerName(settlement.getName());

        CustomerBalanceAccountEntity account = customerBalanceAccountDao
                .selectBySettlementCustomerId(settlement.getId());
        // 没有账户就是 0 元：「还没有钱包」与「钱包是空的」在金额上等价，不必为此落一行
        vo.setAccountId(account == null ? null : account.getId());
        vo.setAvailableBalance(account == null ? BigDecimal.ZERO
                : customerBalanceService.balanceOfSettlement(settlement.getId()));
        return vo;
    }

    /**
     * 流水分页。范围收窄在 SQL 里做（见 Mapper 的说明），因此翻页不会漏掉越权行。
     */
    @Transactional(readOnly = true)
    public PageResult<BalanceMovementVO> movementPage(BalanceMovementQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        Page<?> page = new Page<>(form.getPageNum(), form.getPageSize());
        return SmartPageUtil.convert2PageResult(page,
                customerBalanceMovementDao.queryPage(page, form, scope));
    }

    /** 越权与「客户不存在」共用同一个拒绝（对外 30005）：能分辨两者就等于把主键探测变成可用信号。 */
    private void requireVisible(CustomerEntity settlement) {
        if (!dataScopeService.resolve().getCustomerSellerScope().allows(settlement.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }
}
