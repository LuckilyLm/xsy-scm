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
 * 余额查询按结算主体和数据权限过滤，不向无权账户返回资金信息。
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
     * 余额概览。结算主体由服务端解析，不接受客户端指定余额账户 id，否则拿任意 id 就能看别人的余额。
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
        // 没有账户就是 0 元：没开户和余额为空在金额上一样，不必为此建一行
        vo.setAccountId(account == null ? null : account.getId());
        vo.setAvailableBalance(
            account == null ? BigDecimal.ZERO : customerBalanceService.balanceOfSettlement(settlement.getId()));
        return vo;
    }

    /**
     * 流水分页。范围在 SQL 里收窄（见 Mapper），翻页不会漏掉越权行。
     */
    @Transactional(readOnly = true)
    public PageResult<BalanceMovementVO> movementPage(BalanceMovementQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        Page<?> page = new Page<>(form.getPageNum(), form.getPageSize());
        return SmartPageUtil.convert2PageResult(page, customerBalanceMovementDao.queryPage(page, form, scope));
    }

    /**
     * 越权和「客户不存在」返回同样的拒绝（30005）：能区分两者就等于能探测主键。
     */
    private void requireVisible(CustomerEntity settlement) {
        if (!dataScopeService.resolve().getCustomerSellerScope().allows(settlement.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }
}
