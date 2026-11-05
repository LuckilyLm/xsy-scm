package com.xsy.scm.mall.home.dao;

import com.xsy.scm.mall.home.domain.vo.MallHomePopupStorageVO;
import com.xsy.scm.mall.home.domain.vo.MallHomeReleaseVO;
import com.xsy.scm.mall.home.domain.vo.MallHomeStorageVO;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallHomeDao {
    /** 补齐单行草稿 / 指针记录；历史库被清理过也不会让页面 500。 */
    int ensureDraft();

    int ensurePointer();

    int ensurePopup();

    MallHomePopupStorageVO popup();

    MallHomePopupStorageVO lockPopup();

    int savePopup(@Param("version") Integer version, @Param("enabled") Boolean enabled,
        @Param("displayRule") String displayRule, @Param("startAt") OffsetDateTime startAt,
        @Param("endAt") OffsetDateTime endAt, @Param("content") String content, @Param("operator") String operator);

    MallHomeStorageVO draft();

    MallHomeStorageVO lockDraft();

    MallHomeStorageVO pointer();

    MallHomeStorageVO lockPointer();

    int saveDraft(@Param("version") Integer version, @Param("content") String content,
        @Param("themeCode") String themeCode, @Param("themeColor") String themeColor,
        @Param("operator") String operator);

    int activate(@Param("version") Integer version, @Param("releaseId") Long releaseId,
        @Param("operator") String operator);

    int insertRelease(MallHomeReleaseVO release);

    MallHomeReleaseVO release(@Param("id") Long id);

    List<MallHomeReleaseVO> history();
}
