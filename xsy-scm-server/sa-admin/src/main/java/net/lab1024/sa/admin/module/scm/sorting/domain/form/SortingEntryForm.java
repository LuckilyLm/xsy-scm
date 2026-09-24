package net.lab1024.sa.admin.module.scm.sorting.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量录入分拣结果：允许一次只处理任务里的部分明细（边称边录是常态），
 * 但每条提交行都必须属于本任务且带它自己读到的版本。
 */
@Data
public class SortingEntryForm {

    @NotEmpty
    private List<@Valid SortingEntryItemForm> items;
}
