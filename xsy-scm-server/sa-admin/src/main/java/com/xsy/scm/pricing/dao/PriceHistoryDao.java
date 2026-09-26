package com.xsy.scm.pricing.dao;

import org.apache.ibatis.annotations.*;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.pricing.domain.form.PriceHistoryQueryForm;
import com.xsy.scm.pricing.domain.vo.PriceHistoryVO;

@Mapper
public interface PriceHistoryDao {
    List<PriceHistoryVO> query(Page<?> page, @Param("query") PriceHistoryQueryForm query);
}
