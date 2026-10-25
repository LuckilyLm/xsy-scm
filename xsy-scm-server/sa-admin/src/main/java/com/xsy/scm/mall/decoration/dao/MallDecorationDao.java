package com.xsy.scm.mall.decoration.dao;

import com.xsy.scm.mall.decoration.domain.vo.MallPageVO;
import com.xsy.scm.mall.decoration.domain.vo.MallStoreVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallDecorationDao {
    int ensureStore();

    MallPageVO page(@Param("pageCode") String pageCode);

    MallPageVO lockPage(@Param("pageCode") String pageCode);

    List<MallPageVO> pages();

    int savePage(@Param("pageCode") String pageCode, @Param("version") Integer version, @Param("title") String title,
        @Param("content") String content, @Param("operator") String operator);

    int publishPage(@Param("pageCode") String pageCode, @Param("version") Integer version,
        @Param("operator") String operator);

    MallStoreVO store();

    MallStoreVO lockStore();

    int saveStore(@Param("version") Integer version, @Param("storeName") String storeName,
        @Param("storeLink") String storeLink, @Param("operator") String operator);
}
