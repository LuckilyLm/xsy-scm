package com.xsy.scm.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerSkuAliasForm;
import com.xsy.scm.customer.domain.form.CustomerDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.customer.domain.form.CustomerSellerReassignForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerDetailVO;
import com.xsy.scm.customer.domain.vo.CustomerFrequentSkuVO;
import com.xsy.scm.customer.domain.vo.CustomerOptionVO;
import com.xsy.scm.customer.domain.vo.CustomerSkuAliasVO;
import com.xsy.scm.customer.domain.vo.CustomerVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerQueryService;
import com.xsy.scm.customer.service.CustomerSkuAliasService;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.order.permission.OrderPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCM 客户档案。
 *
 * <p>
 * 写接口都记操作日志，并按客户域权限校验。
 */
@RestController
@RequestMapping("/scm/customer")
@Tag(name = "SCM 客户档案")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    private final CustomerQueryService customerQueryService;
    private final CustomerSkuAliasService customerSkuAliasService;

    @GetMapping("/{customerId}/sku-aliases")
    @SaCheckPermission(CustomerPermission.QUERY)
    public ResponseDTO<List<CustomerSkuAliasVO>> skuAliases(@PathVariable Long customerId) {
        return ResponseDTO.ok(customerSkuAliasService.list(customerId));
    }

    @PostMapping("/sku-alias/save")
    @SaCheckPermission(CustomerPermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> saveSkuAlias(@Valid @RequestBody CustomerSkuAliasForm form) {
        customerSkuAliasService.save(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/query")
    @SaCheckPermission(CustomerPermission.QUERY)
    public ResponseDTO<PageResult<CustomerVO>> query(@Valid @RequestBody CustomerQueryForm form) {
        return ResponseDTO.ok(customerQueryService.query(form));
    }

    @GetMapping("/detail/{customerId}")
    @SaCheckPermission(CustomerPermission.QUERY)
    public ResponseDTO<CustomerDetailVO> detail(@PathVariable Long customerId) {
        return ResponseDTO.ok(customerQueryService.detail(customerId));
    }

    /**
     * 客户常购商品包含历史成交价，读取时必须同时拥有客户查询和订单查询权限。
     */
    @GetMapping("/{customerId}/frequent-skus")
    @SaCheckPermission(value = {CustomerPermission.QUERY, OrderPermission.QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<CustomerFrequentSkuVO>> frequentSkus(@PathVariable Long customerId,
        @RequestParam(defaultValue = "90") int days, @RequestParam(defaultValue = "20") int limit) {
        return ResponseDTO.ok(customerQueryService.frequentSkus(customerId, days, limit));
    }

    @PostMapping("/add")
    @SaCheckPermission(CustomerPermission.ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerAddForm form) {
        return ResponseDTO.ok(customerService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(CustomerPermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerUpdateForm form) {
        customerService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/updateStatus")
    @SaCheckPermission(CustomerPermission.STATUS)
    @OperateLog
    public ResponseDTO<String> updateStatus(@Valid @RequestBody CustomerStatusForm form) {
        customerService.updateStatus(form);
        return ResponseDTO.ok();
    }

    /**
     * 客户改派只能走这个接口：单独权限、版本校验、单独记日志。
     */
    @PostMapping("/reassignSeller")
    @SaCheckPermission(CustomerPermission.ASSIGN)
    @OperateLog
    public ResponseDTO<String> reassignSeller(@Valid @RequestBody CustomerSellerReassignForm form) {
        customerService.reassignSeller(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(CustomerPermission.DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerDeleteForm form) {
        customerService.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/option/list")
    @SaCheckPermission(CustomerPermission.QUERY)
    public ResponseDTO<List<CustomerOptionVO>> optionList() {
        return ResponseDTO.ok(customerQueryService.optionList());
    }
}
