package com.xsy.scm.product.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.product.domain.form.ProductSkuOptionQueryForm;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;

@Mapper
public interface ProductSkuOptionDao {
    List<ProductSkuOptionVO> options(@Param("query") ProductSkuOptionQueryForm query,
            @Param("limitPlusOne") int limitPlusOne);

    List<ProductSkuOptionVO> selectByIds(@Param("ids") List<Long> ids);

    List<ProductSkuOptionVO> selectByCodes(@Param("codes") List<String> codes);
}
