package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务分页结果。
 *
 * <p>
 * {@code operatorSessionExpiresAt} 让秤端知道离线操作还能持续多久：会话过期后即使缓存里还有任务， 也只能查看历史，不能再写业务。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScaleJobPageVO {

    private OffsetDateTime operatorSessionExpiresAt;

    private List<ScaleJobVO> items;

    private String nextCursor;
}
