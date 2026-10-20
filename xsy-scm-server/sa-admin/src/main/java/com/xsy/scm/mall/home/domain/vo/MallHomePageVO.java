package com.xsy.scm.mall.home.domain.vo;

import java.util.List;

public record MallHomePageVO(Long releaseId, String themeCode, List<MallHomeBlockVO> blocks) {
}
