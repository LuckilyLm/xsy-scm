using System.Text.Json.Serialization;

namespace Xsy.Scale.Agent;

/// <summary>本机多筐作业范围；员工会话来自平台，不能用任务编号代替作业编号。</summary>
internal sealed record BasketScope(string OperatorSessionId, string JobId, string TaskId, string TaskItemId);

/// <summary>每筐原始事实及独立处理意图；打印意图不是平台授权的物理打印尝试。</summary>
internal sealed record BasketRecord(string EventKey, string OperationId, int BasketNumber, string SampleId,
    Measurement Measurement, string PrintIntentId, string PrintState, string QueueState);

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
    bool LocallyCompleted, ItemCompletion? Completion, string QueueState);

/// <summary>本地记录分页，累计另查全量投影，不能从当前页计算总量。</summary>
internal sealed record BasketPage(BasketRecord[] Items, int? NextAfter);

/// <summary>待对接命令摘要；完成依赖列出未获正式回执的本筐事件键。</summary>
internal sealed record BasketCommand(long Sequence, string OperationId, string Kind, string State, string? EventKey,
    string[] DependsOnEventKeys, DateTimeOffset CreatedAt);
