package net.lab1024.sa.admin.module.scm.product.domain.form;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;
/** 批量上下架（status）与批量主档启停（masterStatus）共用一个入口，至少给一个目标状态。 */
@Data
public class ProductSpuBatchStatusForm {
@NotEmpty @Size(max=200) @Valid private List<ProductBatchItemForm> items;
@Pattern(regexp="ON_SHELF|OFF_SHELF") private String status;
@Pattern(regexp="ENABLED|DISABLED|ARCHIVED") private String masterStatus;

    /** 两个目标状态可以只给一个（只改在售 / 只改主档），但全空就是一次无效命令。 */
    @AssertTrue(message="status 与 masterStatus 至少提供一个")
    @JsonIgnore
    public boolean isTargetPresent() { return status!=null || masterStatus!=null; }
}
