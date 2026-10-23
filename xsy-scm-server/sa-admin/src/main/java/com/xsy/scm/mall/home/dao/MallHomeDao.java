package com.xsy.scm.mall.home.dao;

import com.xsy.scm.mall.home.domain.vo.MallHomeReleaseVO;
import com.xsy.scm.mall.home.domain.vo.MallHomeStorageVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallHomeDao {
    MallHomeStorageVO draft();

    MallHomeStorageVO lockDraft();

    MallHomeStorageVO pointer();

    MallHomeStorageVO lockPointer();

    int saveDraft(@Param("version") Integer version, @Param("content") String content,
        @Param("themeCode") String themeCode, @Param("operator") String operator);

    int activate(@Param("version") Integer version, @Param("releaseId") Long releaseId,
        @Param("operator") String operator);

    int insertRelease(MallHomeReleaseVO release);

    MallHomeReleaseVO release(@Param("id") Long id);

    List<MallHomeReleaseVO> history();
}
