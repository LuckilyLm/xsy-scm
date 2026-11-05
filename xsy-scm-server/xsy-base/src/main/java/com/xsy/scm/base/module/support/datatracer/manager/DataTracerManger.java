package com.xsy.scm.base.module.support.datatracer.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xsy.scm.base.module.support.datatracer.dao.DataTracerDao;
import com.xsy.scm.base.module.support.datatracer.domain.entity.DataTracerEntity;
import org.springframework.stereotype.Service;

/**
 * manager层
 *
 */
@Service
public class DataTracerManger extends ServiceImpl<DataTracerDao, DataTracerEntity> {
}
