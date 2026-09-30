package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/** 一次 M:N 核销命令生成的核销事实行。 */
@Data
public class FinanceWriteOffAddResultVO {

    private List<FinanceWriteOffVO> items;
}
