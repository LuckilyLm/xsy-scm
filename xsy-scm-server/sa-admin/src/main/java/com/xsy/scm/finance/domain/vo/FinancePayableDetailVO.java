package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/** AP header, source lines, linked RED facts, write-offs and audit history. */
@Data
public class FinancePayableDetailVO {

    private FinancePayableVO payable;

    private List<FinancePayableItemVO> items;

    private List<FinancePayableVO> redEntries;

    private FinancePayableVO originalPayable;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
