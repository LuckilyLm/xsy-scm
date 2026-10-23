using System.Text.Json.Serialization;

namespace Xsy.Scale.Agent;

/// <summary>本机多筐作业范围；员工会话来自平台，不能用任务编号代替作业编号。</summary>
internal sealed record BasketScope(string OperatorSessionId, string JobId, string TaskId, string TaskItemId);

/// <summary>每筐原始事实及独立处理意图；打印意图不是平台授权的物理打印尝试。</summary>
internal sealed record BasketRecord(string EventKey, string OperationId, int BasketNumber, string SampleId,
    Measurement Measurement, string PrintIntentId, string PrintState, string QueueState, string? LastError = null);

/// <summary>完成商品只引用已保存筐集合，不接受手填累计或再次读取设备。</summary>
internal sealed record CompleteItemRequest(string OperationId, string TaskId, string TaskItemId,
    string OperatorSessionId, string JobId, string JobVersion, [property: JsonRequired] long TaskItemVersion,
    string ManifestHash, string Result, string? Reason);

/// <summary>已持久化的本地完成意图；没有平台回执时不代表正式商品完成。</summary>
internal sealed record ItemCompletion(string OperationId, string JobId, string JobVersion, string TaskId,
    string TaskItemId, long TaskItemVersion, string OperatorSessionId, string[] EventKeys,
    string TotalQuantity, string Unit, string Result, string? Reason, DateTimeOffset CompletedAt);

/// <summary>精确本地累计；平台快照为缓存值，不能与本地累计直接相加。</summary>
internal sealed record BasketSummary(int BasketCount, string TotalQuantity, string Unit,
    string PlannedQuantity, string PlatformSnapshotQuantity, string ManifestHash, bool AuthorizationExpired,
    bool LocallyCompleted, ItemCompletion? Completion, string QueueState, string? LastError = null);

/// <summary>
/// 平台筐上报请求。只从已冻结的测量事实与命令字段组装，不重新读秤、不改写重量。
/// </summary>
/// <remarks>
/// 设备编码与员工身份不由本机提交：平台按设备凭证与员工会话判定，避免一台秤冒充另一台。
/// </remarks>
internal sealed record PlatformBasketUpload(string EventKey, string OperationId, int BasketNo, string TaskId,
    string TaskItemId, long TaskItemVersion, string JobId, string JobVersion, DateTimeOffset CapturedAt,
    string RawValue, string RawUnit, string NetWeight, string BusinessUnit, string WeightKind,
    bool StableFlag, string StabilitySource, string ConversionRule, string? TareWeight,
    string ReadingSequence, string ProtocolType, string AgentVersion)
{
    internal static PlatformBasketUpload From(Measurement measurement, string operationId, int basketNo) => new(
        measurement.EventKey, operationId, basketNo, measurement.TaskId, measurement.TaskItemId,
        measurement.TaskItemVersion, measurement.JobId, measurement.JobVersion, measurement.CapturedAt,
        measurement.RawValue, measurement.RawUnit, measurement.NetWeight, measurement.BusinessUnit,
        measurement.WeightKind, measurement.StableFlag, measurement.StabilitySource, measurement.ConversionRule,
        measurement.TareWeight, measurement.ReadingSequence, measurement.ProtocolType, measurement.AgentVersion);
}

/// <summary>
/// 平台集合完成请求。累计总量不提交：平台按筐重算，本机只证明筐集合与摘要一致。
/// </summary>
internal sealed record PlatformItemCompletion(string OperationId, string JobId, string JobVersion,
    string TaskId, string TaskItemId, long TaskItemVersion, string ManifestHash, string Result, string? Reason)
{
    internal static PlatformItemCompletion From(ItemCompletion completion, string manifestHash) => new(
        completion.OperationId, completion.JobId, completion.JobVersion, completion.TaskId,
        completion.TaskItemId, completion.TaskItemVersion, manifestHash, completion.Result, completion.Reason);
}

/// <summary>待发送的多筐命令；状态只表示本机派发进度，不是平台业务结论。</summary>
internal sealed record PendingBasketCommand(long Sequence, string OperationId, string ScopeKey, string Kind,
    string? EventKey, string RequestHash, string Payload);

/// <summary>本地记录分页，累计另查全量投影，不能从当前页计算总量。</summary>
internal sealed record BasketPage(BasketRecord[] Items, int? NextAfter);

/// <summary>待对接命令摘要；完成依赖列出未获正式回执的本筐事件键。</summary>
internal sealed record BasketCommand(long Sequence, string OperationId, string Kind, string State, string? EventKey,
    string[] DependsOnEventKeys, DateTimeOffset CreatedAt);
