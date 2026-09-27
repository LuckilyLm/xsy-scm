package com.xsy.scm.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.customer.domain.form.CustomerSellerReassignForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerDetailVO;
import com.xsy.scm.customer.domain.vo.CustomerFrequentSkuVO;
import com.xsy.scm.customer.domain.vo.CustomerOptionVO;
import com.xsy.scm.customer.domain.vo.CustomerVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerQueryService;
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
 * <p>写端点全部带 {@code @OperateLog}（修正 legacy 客户域零操作日志的缺陷 D3），
 * 全部带 {@code @SaCheckPermission}（修正 legacy 零权限注解的缺陷 D2）。
 */
@RestController
@RequestMapping("/scm/customer")
@Tag(name = "SCM 客户档案")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    private final CustomerQueryService customerQueryService;

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
     * 客户「常购商品」（Wave 7 客户 360°，只读聚合）。
     *
     * <p>取数源是订单事实，价格字段沿用订单查看规则，因此<b>同时</b>要求 {@code scm:customer:query}
     * 与 {@code scm:order:query}（{@link SaMode#AND}）：没有订单查看权的人不能仅凭客户权限读到历史成交价。
     */
    @GetMapping("/{customerId}/frequent-skus")
    @SaCheckPermission(value = {CustomerPermission.QUERY, OrderPermission.QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<CustomerFrequentSkuVO>> frequentSkus(@PathVariable Long customerId,
                                                                 @RequestParam(defaultValue = "90") int days,
                                                                 @RequestParam(defaultValue = "20") int limit) {
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
     * 改派客户业务归属。
     *
     * <p>独立端点 + 独立权限（{@code scm:customer:assign}）：{@code /update} 一律不改 {@code seller_id}，
     * 归属变更必须带着乐观锁版本走这里，才能留下单独的操作日志并且不让编辑表单顺带挪走数据。
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
