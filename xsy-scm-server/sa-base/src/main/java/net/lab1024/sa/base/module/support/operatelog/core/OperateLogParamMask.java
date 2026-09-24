package net.lab1024.sa.base.module.support.operatelog.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;

import java.util.regex.Pattern;

/**
 * 操作日志参数写库前的敏感字段脱敏。
 *
 * <p>日志一旦带凭据明文，就等于把明文交给所有持日志读取权的人：数据库行、API 响应、浏览器
 * Network 三处同时留痕，前端展示层遮不住。因此在切面唯一的参数序列化出口先遮一次，
 * 前端 {@code operate-log-mask.ts} 继续作为存量历史数据的第二层兜底。
 *
 * <p>只按 JSON 键名替换值，不删键、不改结构、不动数字类型，
 * 所以 {@code spuId} / {@code customerId} 这类业务对象键不受影响，
 * 操作日志的结构化精确筛选口径保持有效。
 */
final class OperateLogParamMask {
    /**
     * 与前端展示层遮罩保持同一份口径，避免两侧各判一套键名。
     */
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(password|passwd|pwd|token|secret|credential|authorization|accesstoken|refreshtoken"
                    + "|sessiontoken|apikey|api_key)", Pattern.CASE_INSENSITIVE);

    private static final String MASKED_VALUE = "******";

    /**
     * 解析不出结构时唯一允许写库的载荷：只留下「这里发生过脱敏失败」的事实，不含任何原文。
     */
    private static final String UNPARSEABLE_PAYLOAD = "[{\"operateLogParamMasked\":\"UNPARSEABLE_PAYLOAD\"}]";

    private OperateLogParamMask() {
    }

    /**
     * 遮蔽 JSON 文本里所有命中敏感键名的值；非 JSON（脏值 / 截断）原样返回，不额外造错误。
     *
     * <p>看似 JSON 但解析失败时返回不含原文的占位载荷，而不是原文。
     */
    static String mask(String json) {
        if (json == null || json.isEmpty() || (json.charAt(0) != '{' && json.charAt(0) != '[')) {
            return json;
        }
        try {
            Object node = JSON.parse(json, Feature.OrderedField);
            if (!maskNode(node)) {
                return json;
            }
            return JSON.toJSONString(node);
        } catch (RuntimeException e) {
            // 本方法在切面唯一的写库出口上：原文一旦落库就永久留存，前端 operate-log-mask.ts 只能遮展示、遮不住数据库行，
            // 也遮不住持日志读取权的人。所以这里不能沿用「留给第二层兜底」——与 FileKeyVoSerializer、
            // StocktakeSnapshotSigner 同口径：去掉任何写出原始 value 的分支，宁可丢这条参数的可读性。
            return UNPARSEABLE_PAYLOAD;
        }
    }

    /**
     * @return 是否真的替换过，用于避免对不含敏感键的日志重排 JSON
     */
    private static boolean maskNode(Object node) {
        boolean masked = false;
        if (node instanceof JSONArray array) {
            for (Object item : array) {
                masked |= maskNode(item);
            }
        } else if (node instanceof JSONObject object) {
            for (String key : object.keySet()) {
                if (SECRET_KEY.matcher(key).find() && object.get(key) != null) {
                    object.put(key, MASKED_VALUE);
                    masked = true;
                } else {
                    masked |= maskNode(object.get(key));
                }
            }
        }
        return masked;
    }
}
