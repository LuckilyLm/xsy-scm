package com.xsy.scm.finance.dao;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.domain.entity.FinanceCustomerExpenseEntity;
import com.xsy.scm.finance.domain.vo.FinanceCustomerExpenseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FinanceCustomerExpenseDao {
    long nextNumber();
    int insert(FinanceCustomerExpenseEntity expense);
    FinanceCustomerExpenseVO find(@Param("id") Long id);
    FinanceCustomerExpenseVO lock(@Param("id") Long id);
    int insertVoid(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);
    List<FinanceCustomerExpenseVO> query(Page<?> page, @Param("query") FinanceCustomerExpenseForm.Query query,
        @Param("scope") ScmValueScope scope);
}
