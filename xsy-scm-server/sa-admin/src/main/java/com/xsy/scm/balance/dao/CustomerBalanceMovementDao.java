package com.xsy.scm.balance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.domain.form.BalanceMovementQueryForm;
import com.xsy.scm.balance.domain.vo.BalanceMovementVO;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerBalanceMovementDao extends BaseMapper<CustomerBalanceMovementEntity> {

    int insertOnConflictDoNothing(CustomerBalanceMovementEntity movement);

    /**
     * 账户余额（有符号合计）。
     *
     * <p>
     * 方向字面量由调用方以参数传入（{@code #{credit}} / {@code #{debit}}），不写死在 SQL 里： 方向的唯一来源是 Java 枚举，SQL 里再抄一份就会在加方向时静默算错。
     */
    BigDecimal sumSignedByAccount(@Param("accountId") Long accountId, @Param("credit") String creditDirection);

    /** 按来源取流水：重复驱动不重复入账的前置查询（最终仲裁是库上的来源唯一索引）。 */
    CustomerBalanceMovementEntity selectBySource(@Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId);

    /** 钱包流水按结算主体所属客户范围过滤，没有权限就返回空结果。查询同时返回结算主体和实际客户名称。 */
    List<BalanceMovementVO> queryPage(Page<?> page, @Param("query") BalanceMovementQueryForm query,
            @Param("scope") ScmDataScopeContext scope);

    /** 账户下的流水（升序，用于对账复盘）。 */
    List<CustomerBalanceMovementEntity> listByAccount(@Param("accountId") Long accountId);

    /** 流水号序列。必须在事务内调用（nextval 不回滚，跳号可接受）。 */
    @org.apache.ibatis.annotations.Select("SELECT nextval('customer_balance_movement_seq')")
    long nextMovementNo();
}
