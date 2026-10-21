namespace Xsy.Scale.Agent;

/// <summary>现场启用的本地待同步凭条策略，不代表平台正式打印授权。</summary>
internal sealed class WorkstationOptions
{
    public bool LocalTicketsEnabled { get; set; }
    public int PaperWidthMm { get; set; } = 60;
    public int PaperHeightMm { get; set; } = 40;
    public string MaintenanceCode { get; set; } = "";
    public bool LocalTest { get; set; }
    public bool HardwareTest { get; set; }
    public string InitialOperatorSession { get; set; } = "";
    // 平台公开查询页尚未交付，空字符串表示只预留标签位置。
    public string TraceUrlTemplate { get; set; } = "";
}

/// <summary>每筐的冻结凭条；只从任务快照和真实采集事实生成，补打不取新重量。</summary>
internal sealed record TicketSnapshot(string EventKey, string TaskNo, string ProductName, string SpecName,
    string? CustomerName, string? OrderNo, int BasketNumber, string NetWeight, string Unit,
    string? TareWeight, string DeviceCode, DateTimeOffset CapturedAt, bool TestOnly, string TemplateVersion = "LOCAL_PENDING_V1",
    string? TraceReference = null, string? TraceUrl = null);

/// <summary>本地纸票的一次执行尝试；未知或失败不会自动再次发纸。</summary>
internal sealed record LocalTicket(string AttemptId, string OperationId, string ScopeKey, TicketSnapshot Snapshot,
    string State, string PrinterName, int PaperWidthMm, int PaperHeightMm, bool Reprint, string? Reason,
    DateTimeOffset CreatedAt, string? SpoolerJobId = null, string? ErrorCode = null);

/// <summary>一次人工补打请求，保留操作号/原筐/原因以便核查。</summary>
internal sealed record ReprintRequest(string OperationId, string EventKey, string Reason);

/// <summary>驱动接受与物理出纸分离；演练输出使用TEST_ONLY。</summary>
internal sealed record TicketOutput(string State, string? SpoolerJobId = null);

/// <summary>只表示当前Windows驱动提交能力，不提供正式平台业务确认。</summary>
internal interface ITicketPrinter
{
    /// <summary>执行一次冻结凭条，不在适配器内重试。</summary>
    /// <param name="ticket">非空持久尝试，包含冻结纸张、队列和内容；不得用新采样替换。</param>
    /// <returns>真实驱动提交状态或明确演练状态，不证明物理出纸。</returns>
    /// <exception cref="Exception">驱动拒绝、纸张配置不合法或打印I/O失败。</exception>
    TicketOutput Submit(LocalTicket ticket);
}
