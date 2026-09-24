package net.lab1024.sa.admin.module.business.oa.enterprise.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.admin.module.business.oa.enterprise.dao.EnterpriseDao;
import net.lab1024.sa.admin.module.business.oa.enterprise.dao.EnterpriseEmployeeDao;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.entity.EnterpriseEmployeeEntity;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.entity.EnterpriseEntity;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.form.*;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.vo.EnterpriseEmployeeVO;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.vo.EnterpriseExcelVO;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.vo.EnterpriseListVO;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.vo.EnterpriseVO;
import net.lab1024.sa.admin.module.business.oa.enterprise.manager.EnterpriseEmployeeManager;
import net.lab1024.sa.admin.module.system.department.service.DepartmentService;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.module.support.datatracer.constant.DataTracerTypeEnum;
import net.lab1024.sa.base.module.support.datatracer.domain.form.DataTracerForm;
import net.lab1024.sa.base.module.support.datatracer.service.DataTracerService;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileRelationService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 企业
 *
 */
@Service
@Slf4j
public class EnterpriseService {

    @Resource
    private EnterpriseDao enterpriseDao;

    @Resource
    private EnterpriseEmployeeDao enterpriseEmployeeDao;

    @Resource
    private EnterpriseEmployeeManager enterpriseEmployeeManager;

    @Resource
    private DataTracerService dataTracerService;

    @Resource
    private DepartmentService departmentService;

    @Resource
    private FileRelationService fileRelationService;

    /**
     * 分页查询企业模块
     *
     */
    public ResponseDTO<PageResult<EnterpriseVO>> queryByPage(EnterpriseQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<EnterpriseVO> enterpriseList = enterpriseDao.queryPage(page, queryForm);
        PageResult<EnterpriseVO> pageResult = SmartPageUtil.convert2PageResult(page, enterpriseList);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 获取导出数据
     */
    public List<EnterpriseExcelVO> getExcelExportData(EnterpriseQueryForm queryForm) {
        queryForm.setDeletedFlag(false);
        return enterpriseDao.selectExcelExportData(queryForm);
    }

    /**
     * 查询企业详情
     *
     */
    public EnterpriseVO getDetail(Long enterpriseId) {
        return enterpriseDao.getDetail(enterpriseId, Boolean.FALSE);
    }

    /**
     * 新建企业
     *
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> createEnterprise(EnterpriseCreateForm createVO) {
        // 验证企业名称是否重复
        EnterpriseEntity validateEnterprise = enterpriseDao.queryByEnterpriseName(createVO.getEnterpriseName(), null, Boolean.FALSE);
        if (Objects.nonNull(validateEnterprise)) {
            return ResponseDTO.userErrorParam("企业名称重复");
        }
        // 数据插入
        EnterpriseEntity insertEnterprise = SmartBeanUtil.copy(createVO, EnterpriseEntity.class);
        enterpriseDao.insert(insertEnterprise);
        rebindAttachments(insertEnterprise.getEnterpriseId(), insertEnterprise.getEnterpriseLogo(),
                insertEnterprise.getBusinessLicense());
        dataTracerService.insert(insertEnterprise.getEnterpriseId(), DataTracerTypeEnum.OA_ENTERPRISE);
        return ResponseDTO.ok();
    }

    /**
     * 编辑企业
     *
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> updateEnterprise(EnterpriseUpdateForm updateVO) {
        Long enterpriseId = updateVO.getEnterpriseId();
        // 校验企业是否存在
        EnterpriseEntity enterpriseDetail = enterpriseDao.selectById(enterpriseId);
        if (Objects.isNull(enterpriseDetail) || enterpriseDetail.getDeletedFlag()) {
            return ResponseDTO.userErrorParam("企业不存在");
        }
        // 验证企业名称是否重复
        EnterpriseEntity validateEnterprise = enterpriseDao.queryByEnterpriseName(updateVO.getEnterpriseName(), enterpriseId, Boolean.FALSE);
        if (Objects.nonNull(validateEnterprise)) {
            return ResponseDTO.userErrorParam("企业名称重复");
        }
        // 数据编辑
        EnterpriseEntity updateEntity = SmartBeanUtil.copy(enterpriseDetail, EnterpriseEntity.class);
        SmartBeanUtil.copyProperties(updateVO, updateEntity);
        enterpriseDao.updateById(updateEntity);
        // updateById 跳过 null 列，因此按实际落库值换绑：表单未提交该列时不能收回旧附件的读取权
        rebindAttachments(enterpriseId,
                updateEntity.getEnterpriseLogo() == null ? enterpriseDetail.getEnterpriseLogo() : updateEntity.getEnterpriseLogo(),
                updateEntity.getBusinessLicense() == null ? enterpriseDetail.getBusinessLicense() : updateEntity.getBusinessLicense());

        //变更记录
        DataTracerForm dataTracerForm = DataTracerForm.builder()
                .dataId(updateVO.getEnterpriseId())
                .type(DataTracerTypeEnum.OA_ENTERPRISE)
                .content("修改企业信息")
                .diffOld(dataTracerService.getChangeContent(enterpriseDetail))
                .diffNew(dataTracerService.getChangeContent(updateEntity))
                .build();

        dataTracerService.addTrace(dataTracerForm);
        return ResponseDTO.ok();
    }

    /**
     * 企业两处附件列共用一份授权集合：只按其中一处换绑会把另一处仍在引用的 key 判成「不再引用」。
     */
    private void rebindAttachments(Long enterpriseId, String enterpriseLogo, String businessLicense) {
        List<String> fileKeys = new ArrayList<>(FileRelationService.splitKeys(enterpriseLogo));
        fileKeys.addAll(FileRelationService.splitKeys(businessLicense));
        fileRelationService.rebind(FileRelationBizTypeEnum.ENTERPRISE, enterpriseId, fileKeys);
    }


    /**
     * 删除企业
     *
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> deleteEnterprise(Long enterpriseId) {
        // 校验企业是否存在
        EnterpriseEntity enterpriseDetail = enterpriseDao.selectById(enterpriseId);
        if (Objects.isNull(enterpriseDetail) || enterpriseDetail.getDeletedFlag()) {
            return ResponseDTO.userErrorParam("企业不存在");
        }
        enterpriseDao.deleteEnterprise(enterpriseId, Boolean.TRUE);
        dataTracerService.delete(enterpriseId, DataTracerTypeEnum.OA_ENTERPRISE);
        return ResponseDTO.ok();
    }

    /**
     * 企业列表查询
     */
    public ResponseDTO<List<EnterpriseListVO>> queryList(Integer type) {
        List<EnterpriseListVO> enterpriseList = enterpriseDao.queryList(type, Boolean.FALSE, Boolean.FALSE);
        return ResponseDTO.ok(enterpriseList);
    }

    //----------------------------------------- 以下为员工相关--------------------------------------------

    /**
     * 企业添加员工
     *
     */
    public synchronized ResponseDTO<String> addEmployee(EnterpriseEmployeeForm enterpriseEmployeeForm) {
        Long enterpriseId = enterpriseEmployeeForm.getEnterpriseId();
        EnterpriseEntity enterpriseEntity = enterpriseDao.selectById(enterpriseId);
        if (enterpriseEntity == null || enterpriseEntity.getDeletedFlag()) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        //过滤掉已存在的员工
        List<Long> waitAddEmployeeIdList = enterpriseEmployeeForm.getEmployeeIdList();
        List<EnterpriseEmployeeEntity> enterpriseEmployeeEntityList = enterpriseEmployeeDao.selectByEnterpriseAndEmployeeIdList(enterpriseId, waitAddEmployeeIdList);
        if (CollectionUtils.isNotEmpty(enterpriseEmployeeEntityList)) {
            List<Long> existEmployeeIdList = enterpriseEmployeeEntityList.stream().map(EnterpriseEmployeeEntity::getEmployeeId).collect(Collectors.toList());
            waitAddEmployeeIdList = waitAddEmployeeIdList.stream().filter(e -> !existEmployeeIdList.contains(e)).collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(waitAddEmployeeIdList)) {
            return ResponseDTO.ok();
        }
        List<EnterpriseEmployeeEntity> batchAddList = Lists.newArrayList();
        for (Long employeeId : waitAddEmployeeIdList) {
            EnterpriseEmployeeEntity enterpriseEmployeeEntity = new EnterpriseEmployeeEntity();
            enterpriseEmployeeEntity.setEnterpriseId(enterpriseId);
            enterpriseEmployeeEntity.setEmployeeId(employeeId);
            batchAddList.add(enterpriseEmployeeEntity);
        }
        enterpriseEmployeeManager.saveBatch(batchAddList);
        return ResponseDTO.ok();
    }

    /**
     * 企业删除员工
     *
     */
    public synchronized ResponseDTO<String> deleteEmployee(EnterpriseEmployeeForm enterpriseEmployeeForm) {
        Long enterpriseId = enterpriseEmployeeForm.getEnterpriseId();
        EnterpriseEntity enterpriseEntity = enterpriseDao.selectById(enterpriseId);
        if (enterpriseEntity == null || enterpriseEntity.getDeletedFlag()) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        List<Long> waitDeleteEmployeeIdList = enterpriseEmployeeForm.getEmployeeIdList();
        enterpriseEmployeeDao.deleteByEnterpriseAndEmployeeIdList(enterpriseId, waitDeleteEmployeeIdList);
        return ResponseDTO.ok();
    }

    /**
     * 企业下员工列表
     *
     */
    public List<EnterpriseEmployeeVO> employeeList(List<Long> enterpriseIdList) {
        if (CollectionUtils.isEmpty(enterpriseIdList)) {
            return Lists.newArrayList();
        }
        return enterpriseEmployeeDao.selectByEnterpriseIdList(enterpriseIdList);
    }

    /**
     * 分页查询企业员工
     *
     */
    public PageResult<EnterpriseEmployeeVO> queryPageEmployeeList(EnterpriseEmployeeQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<EnterpriseEmployeeVO> enterpriseEmployeeVOList = enterpriseEmployeeDao.queryPageEmployeeList(page, queryForm);
        for (EnterpriseEmployeeVO enterpriseEmployeeVO : enterpriseEmployeeVOList) {
            enterpriseEmployeeVO.setDepartmentName(departmentService.getDepartmentPath(enterpriseEmployeeVO.getDepartmentId()));
        }
        return SmartPageUtil.convert2PageResult(page, enterpriseEmployeeVOList);
    }
}
