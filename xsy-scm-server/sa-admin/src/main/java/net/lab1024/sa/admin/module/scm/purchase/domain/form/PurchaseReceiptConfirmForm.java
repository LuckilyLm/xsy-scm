package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;

/**
 * 确认采购收货（W5 Target Design §4.3 / §7.2）。
 *
 * <p>**必须提交本收货单的全部活动行**（`PURCHASE_RECEIPT_ITEM_INCOMPLETE(40998)`），
 * 不允许只提交子集 —— 修 A-D5/G11 的「确认了但仍有 0 数量行」歧义。
 *
 * <p>标品：`actualWeight` / `weightSource` / `correctionReason` 必须全空；
 * 非标品：`actualWeight` 必填且 &gt; 0，`weightSource` 必须 == `MANUAL`，有效数量取实重。
 */
@Data
public class PurchaseReceiptConfirmForm {
    @NotNull private Long id;
    @NotNull @Min(0) private Integer version;
    @Valid @NotEmpty @Size(max=500) private List<Item> items;

    /** 收货单行的本次确认数据。 */
    @Data
    public static class Item {
        @NotNull private Long receiptItemId;
        @NotNull @Min(0) private Integer version;
        @NotBlank @JsonDeserialize(using=ScmStrictDecimalStringDeserializer.class) private String receivedQuantity;
        /** 非标品必填且 &gt; 0；标品必须为空（不是 `0.0000`）。 */
        @JsonDeserialize(using=ScmStrictDecimalStringDeserializer.class) private String actualWeight;
        /** 非标品必须为 `MANUAL`；标品必须为空。 */
        @Pattern(regexp="MANUAL") private String weightSource;
        @Size(max=500) private String correctionReason;
    }
}
