package com.xsy.scm.mall.address.service;

import com.xsy.scm.common.constant.ScmGeoCoordinateSystemEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.address.dao.MallCustomerAddressDao;
import com.xsy.scm.mall.address.domain.entity.MallCustomerAddressEntity;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.address.domain.vo.MallMapLocationVO;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户收货地址：归属客户，同一客户的多个采购账号共享。 客户 ID 一律取自登录身份，请求体不承载归属；一个客户至多一个默认地址。
 */
@Service
@RequiredArgsConstructor
public class MallAddressService {
    private final MallCustomerAddressDao addressDao;
    private final MallMapService mapService;

    @Transactional(readOnly = true)
    public List<MallAddressVO> list(RequestMallCustomer customer) {
        return addressDao.listByCustomer(customer.getCustomerId());
    }

    @Transactional(readOnly = true)
    public MallAddressVO detail(RequestMallCustomer customer, Long id) {
        MallAddressVO row = addressDao.detailById(customer.getCustomerId(), id);
        if (row == null) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_NOT_FOUND);
        }
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<MallAddressVO> create(RequestMallCustomer customer, MallAddressSaveForm form) {
        MallMapLocationVO location = mapService.reverse(form.getLongitude(), form.getLatitude());
        String operator = ScmOperator.current();
        boolean first = addressDao.countByCustomer(customer.getCustomerId()) == 0;
        boolean asDefault = first || Boolean.TRUE.equals(form.getIsDefault());
        if (asDefault) {
            addressDao.clearDefault(customer.getCustomerId(), operator);
        }
        MallCustomerAddressEntity row = new MallCustomerAddressEntity();
        apply(row, customer, form, location);
        row.setIsDefault(asDefault);
        OffsetDateTime now = OffsetDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(operator);
        row.setUpdatedBy(operator);
        addressDao.insert(row);
        return addressDao.listByCustomer(customer.getCustomerId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<MallAddressVO> update(RequestMallCustomer customer, Long id, MallAddressSaveForm form) {
        MallMapLocationVO location = mapService.reverse(form.getLongitude(), form.getLatitude());
        String operator = ScmOperator.current();
        MallCustomerAddressEntity existing = lockOwned(customer, id);
        boolean asDefault = Boolean.TRUE.equals(form.getIsDefault());
        if (asDefault) {
            addressDao.clearDefault(customer.getCustomerId(), operator);
        } else if (Boolean.TRUE.equals(existing.getIsDefault())) {
            // 已生效的默认地址不允许通过编辑取消，避免出现「没有默认地址」的中间态
            asDefault = true;
        }
        MallCustomerAddressEntity row = new MallCustomerAddressEntity();
        apply(row, customer, form, location);
        row.setId(id);
        row.setIsDefault(asDefault);
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(operator);
        addressDao.update(row);
        return addressDao.listByCustomer(customer.getCustomerId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<MallAddressVO> setDefault(RequestMallCustomer customer, Long id) {
        String operator = ScmOperator.current();
        if (addressDao.detailById(customer.getCustomerId(), id) == null) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_NOT_FOUND);
        }
        addressDao.clearDefault(customer.getCustomerId(), operator);
        addressDao.setDefault(customer.getCustomerId(), id, operator);
        return addressDao.listByCustomer(customer.getCustomerId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<MallAddressVO> remove(RequestMallCustomer customer, Long id) {
        String operator = ScmOperator.current();
        MallCustomerAddressEntity existing = lockOwned(customer, id);
        addressDao.remove(customer.getCustomerId(), id, operator);
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            Long next = addressDao.firstAddressId(customer.getCustomerId());
            if (next != null) {
                addressDao.setDefault(customer.getCustomerId(), next, operator);
            }
        }
        return addressDao.listByCustomer(customer.getCustomerId());
    }

    private MallCustomerAddressEntity lockOwned(RequestMallCustomer customer, Long id) {
        MallCustomerAddressEntity row = addressDao.selectByIdForUpdate(id);
        if (row == null || !row.getCustomerId().equals(customer.getCustomerId())) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_NOT_FOUND);
        }
        return row;
    }

    private static void apply(MallCustomerAddressEntity row, RequestMallCustomer customer, MallAddressSaveForm form,
        MallMapLocationVO location) {
        if ((form.getLongitude() == null) != (form.getLatitude() == null)) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_COORDINATE_INVALID);
        }
        if (location.getProvinceCode() == null || location.getCityCode() == null || location.getDistrictCode() == null
            || location.getProvinceName() == null || location.getCityName() == null
            || location.getDistrictName() == null) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_COORDINATE_INVALID);
        }
        row.setCustomerId(customer.getCustomerId());
        row.setReceiverName(form.getReceiverName().trim());
        row.setReceiverPhone(form.getReceiverPhone().trim());
        row.setProvinceCode(location.getProvinceCode());
        row.setProvinceName(location.getProvinceName());
        row.setCityCode(location.getCityCode());
        row.setCityName(location.getCityName());
        row.setDistrictCode(location.getDistrictCode());
        row.setDistrictName(location.getDistrictName());
        row.setDetailAddress(form.getDetailAddress().trim());
        row.setLongitude(form.getLongitude());
        row.setLatitude(form.getLatitude());
        row.setGeomCrs(form.getLongitude() == null ? null : ScmGeoCoordinateSystemEnum.GCJ02.name());
    }
}
