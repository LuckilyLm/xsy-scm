package com.xsy.scm.mall.catalog.domain.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MallFrequentProductVO {
    private List<MallProductVO> list;
}
