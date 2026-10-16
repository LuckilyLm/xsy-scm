package com.xsy.scm.mall.order.dao;

import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallWarehouseDao {
    List<MallWarehouseOptionVO> byDistrict(@Param("districtCode") Integer districtCode);
}
