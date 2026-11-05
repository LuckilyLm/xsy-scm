package com.xsy.scm.base.module.support.serialnumber.service;

import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;

import java.util.List;

/**
 * 单据序列号
 *
 */
public interface SerialNumberService {

    /**
     * 生成
     *
     * @param serialNumberIdEnum
     * @return
     */
    String generate(SerialNumberIdEnum serialNumberIdEnum);

    /**
     * 生成n个
     *
     * @param serialNumberIdEnum
     * @param count
     * @return
     */
    List<String> generate(SerialNumberIdEnum serialNumberIdEnum, int count);

}
