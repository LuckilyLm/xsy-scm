package com.xsy.scm.mall.home.domain.vo;

import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import java.util.List;
import lombok.Data;

@Data
public class MallHomeBlockVO {
    private String key;
    private String type;
    private Integer schemaVersion;
    private String title;
    private String subtitle;
    private String text;
    private String imageUrl;
    private String linkType;
    private String linkTarget;
    private List<MallProductVO> products;
}
