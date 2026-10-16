package com.xsy.scm.mall.order.dao;

import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallWarehouseDao {
    List<MallWarehouseOptionVO> byDistrict(@Param("districtCode") Integer districtCode);

    int insertDistrictMapping(@Param("districtCode") Integer districtCode, @Param("warehouseId") Long warehouseId,
            @Param("priority") Integer priority, @Param("operator") String operator);

    int deleteDistrictMapping(@Param("districtCode") Integer districtCode, @Param("warehouseId") Long warehouseId,
            @Param("operator") String operator);
}
