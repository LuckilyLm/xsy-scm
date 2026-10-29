using System.Text.Json;
using System.Text.Json.Serialization;
using System.Security.Cryptography;
using System.Text;

namespace Xsy.Scale.Agent;

/// <summary>设备与协议配置；串口、打印格式和平台地址必须依据现场资料填写。</summary>
internal sealed class AgentOptions
{
    public string DeviceCode { get; set; } = "";
    public string StationCode { get; set; } = "";
    public string DataDirectory { get; set; } = "";
    public string PlatformBaseUrl { get; set; } = "";
    public bool AllowLoopbackPlatform { get; set; }
    public int PlatformPollSeconds { get; set; } = 3;
    public int PlatformTimeoutSeconds { get; set; } = 15;
    public int HeartbeatSeconds { get; set; } = 15;
    public string SerialPort { get; set; } = "";
    public string Protocol { get; set; } = "SOHE_RS232_V101";
    public int BaudRate { get; set; } = 9600;
    public string AdAddress { get; set; } = "A";
    public string HttpScaleUrl { get; set; } = "http://127.0.0.1:33582";
    public string HttpUnit { get; set; } = "";
    public string HttpStableField { get; set; } = "";
    public bool AllowScaleControl { get; set; }
    public int ReadingMaxAgeSeconds { get; set; } = 2;
    /// <summary>铭牌标称分度，仅作为设备资料展示，不限制原始净重的小数或步进。</summary>
    public decimal DivisionKg { get; set; } = 0.05m;
    public string PrinterName { get; set; } = "";
    public string PrinterCode { get; set; } = "";
    public string[] AllowedPrintFormats { get; set; } = [];
    public int MaxPrintBytes { get; set; } = 1024 * 1024;
}

/// <summary>真实设备采样；未知稳定性和未知皮重用 null 表达，禁止补造。</summary>
internal sealed record WeightReading(string SampleId, string ReadingSequence, decimal RawWeight,
    string RawUnit, bool? StableFlag, string StabilitySource, string NetGrossFlag,
    decimal? TareWeight, DateTimeOffset CapturedAt, string RawFrameHex);

/// <summary>界面引用采样和任务，不能自行填写最终业务重量。</summary>
internal sealed record CaptureRequest(string OperationId, string SampleId, string TaskId,
    string TaskItemId, string OperatorSessionId, string JobId, string JobVersion, [property: JsonRequired] long TaskItemVersion,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] bool? PrintRequested = null);

/// <summary>拟议 v1 上报结构；数值以十进制字符串传输，不混用员工上报 DTO。</summary>
internal sealed record Measurement(string ProtocolVersion, string EventKey, string DeviceCode,
    string StationCode, string SessionId, string JobId, string JobVersion, string TaskId,
    string TaskItemId, long TaskItemVersion, string OperatorSessionId, DateTimeOffset CapturedAt,
    string RawValue, string RawUnit, string NetWeight, string BusinessUnit, string WeightKind,
    bool StableFlag, string StabilitySource, string ConversionRule,
    string? TareWeight, string ReadingSequence, string ProtocolType, string AgentVersion,
    string? EntrySource = null, string? EntryNote = null);

/// <summary>本机记录保留原始报文和请求摘要；上报后仍不代表业务已接受。</summary>
internal sealed record LocalEvent(string EventKey, string OperationId, string RequestHash,
    Measurement Measurement, string RawFrameHex, string State, int Attempts,
    DateTimeOffset NextAttemptAt, string? EventId, string? LastError);

/// <summary>任务只读投影；缺少会话到期时间时不得建立离线操作权限。</summary>
internal sealed record TaskBatch(DateTimeOffset OperatorSessionExpiresAt, TaskSnapshot[] Tasks);
/// <summary>任务分页投影；游标只用于拉取，不作为业务任务或租约标识。</summary>
internal sealed record JobPage(DateTimeOffset OperatorSessionExpiresAt, TaskSnapshot[] Items, string? NextCursor);
/// <summary>平台作业上下文；领取设备、租约和版本均由平台提供。</summary>
internal sealed record TaskSnapshot(string TaskId, string TaskNo, string TaskVersion, TaskItem[] Items,
    string JobId, string JobVersion, DateTimeOffset ExpiresAt, string? ClaimedByDeviceCode);
/// <summary>明细的版本和单位随采集冻结，防止重开任务后误用旧选择。</summary>
internal sealed record TaskItem(string TaskItemId, string ProductName, string SpecName, string SaleUnit,
    string PlannedQuantity, string SortedQuantity, string ProductType, bool Active, [property: JsonRequired] long TaskItemVersion,
    string? CustomerName = null, string? OrderNo = null, string? SkuId = null, string? CustomerId = null);

/// <summary>短期设备身份与员工会话分离，凭证在本机使用 DPAPI 加密。</summary>
internal sealed record DeviceCredential(string AccessToken, DateTimeOffset ExpiresAt);

/// <summary>服务端冻结打印任务，只有租约有效且摘要匹配才允许交给打印机。</summary>
internal sealed record PrintJob(string PrintJobId, string PrintAttemptId, string PrintRecordId,
    string DeviceCode, string PrinterCode, string Format, string ContentUrl, string SnapshotChecksum,
    int Copies, string LeaseToken, DateTimeOffset LeaseExpiresAt, DateTimeOffset ExpiresAt);
/// <summary>待执行打印分页；同一任务的授权重印使用新的 printAttemptId。</summary>
internal sealed record PrintPage(PrintJob[] Items, string? NextCursor);

/// <summary>一次物理打印尝试的固定回执，重传不重新执行打印。</summary>
internal sealed record PrintAck(string PrintJobId, string PrintAttemptId, string DeviceCode,
    string PrinterCode, string LeaseToken, string Status, string? WindowsSpoolerJobId,
    DateTimeOffset ObservedAt, string FeedbackSource, string? ErrorCode, string? ErrorMessage);
/// <summary>每次平台打印尝试的本地持久状态，业务任务编号不能替代尝试编号。</summary>
internal sealed record LocalPrint(PrintJob Job, string ExecutionId, string State, PrintAck? Ack);

/// <summary>可向操作端返回的稳定错误码，不包含设备凭证或远程原始响应。</summary>
internal sealed class AgentFault(string code, string message) : Exception(message)
{
    public string Code { get; } = code;
}

/// <summary>全模块采用同一 JSON 规则，金额重量字段由 DTO 显式保持字符串。</summary>
internal static class WireJson
{
    internal static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web);
    internal const string Version = "0.4.1";
    internal static string Encode<T>(T value) => JsonSerializer.Serialize(value, Options);
    internal static T Decode<T>(string json) => JsonSerializer.Deserialize<T>(json, Options)
        ?? throw new AgentFault("INVALID_RESPONSE", "接口数据为空");

    /// <summary>为本地不可变请求和有序事件集合生成确定性摘要。</summary>
    /// <param name="value">非空可序列化对象；字段和数组次序必须在重试时保持不变。</param>
    /// <returns>大写 SHA256 十六进制字符串；不作为授权凭证。</returns>
    internal static string Digest<T>(T value) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(Encode(value))));
}
