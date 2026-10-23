using System.IO.Ports;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;
using System.Drawing.Printing;

namespace Xsy.Scale.Agent;

/// <summary>维护界面允许修改的设备配置，不接收凭据、路径或任意JSON。</summary>
internal sealed record DeviceSetup(string Protocol, string SerialPort, int BaudRate, string HttpScaleUrl,
    string HttpUnit, string HttpStableField, string PrinterName, bool LocalTicketsEnabled,
    int PaperWidthMm, int PaperHeightMm, string? TraceUrlTemplate = null);

/// <summary>平台签发的员工会话引用；不是员工密码或本地新建身份。</summary>
internal sealed record WorkstationSession(string OperatorSessionId);

/// <summary>本地工作台编排、只读诊断及受维护码约束的配置，平台业务继续保持独立。</summary>
internal sealed class WorkstationService(AgentOptions options, WorkstationOptions policy, StateStore store,
    CaptureService captures, BasketService baskets)
{
    /// <summary>确保演练和现场数据目录不会交叉使用。</summary>
    /// <exception cref="InvalidOperationException">模式混用、演练地址/设备不合格或纸张不合理。</exception>
    internal void ValidateMode()
    {
        TraceLink.Build(policy.TraceUrlTemplate, options.DeviceCode + ":" + new string('a', 32));
        if (policy.PaperWidthMm is < 40 or > 110 || policy.PaperHeightMm is < 40 or > 300)
            throw new InvalidOperationException("本地凭条纸宽40至110mm，纸高40至300mm");
        if (policy.LocalTest && (!options.DeviceCode.StartsWith("TEST-", StringComparison.Ordinal)
            || !options.AllowLoopbackPlatform || !Uri.TryCreate(options.PlatformBaseUrl, UriKind.Absolute, out var uri) || !uri.IsLoopback))
            throw new InvalidOperationException("演练必须使用TEST-设备编码和回环测试平台");
        if (policy.HardwareTest && (!policy.LocalTest || options.Protocol != "SOHE_RS232_V101"
            || policy.PaperWidthMm != 60 || policy.PaperHeightMm != 40
            || string.IsNullOrWhiteSpace(options.PrinterName)))
            throw new InvalidOperationException("硬件联动测试必须隔离使用真实RS232、60×40标签和指定打印机");
        string mode = policy.HardwareTest ? "HARDWARE_TEST" : policy.LocalTest ? "LOCAL_TEST" : "FIELD";
        string? previous = store.Setting("workstation_mode");
        if (previous != null && previous != mode) throw new InvalidOperationException("现场与演练不能共用数据目录");
        if (previous == null && policy.LocalTest && store.HasStoredFacts())
            throw new InvalidOperationException("不能把已有事实的数据目录指定为演练");
        store.Setting("workstation_mode", mode);
    }

    /// <summary>返回工作台可公开的本机信息，不返回本机密钥、维护码或设备Token。</summary>
    /// <returns>包含当前会话引用及有限设备配置的匿名投影；未设置会话时为空字符串。</returns>
    internal object Bootstrap() => new
    {
        version = WireJson.Version, deviceCode = options.DeviceCode, stationCode = options.StationCode,
        localTest = policy.LocalTest, hardwareTest = policy.HardwareTest,
        operatorSessionId = CurrentSession(), localTicketsEnabled = policy.LocalTicketsEnabled,
        maintenanceAvailable = !string.IsNullOrEmpty(policy.MaintenanceCode),
        protocol = options.Protocol, serialPort = options.SerialPort, baudRate = options.BaudRate,
        httpScaleUrl = options.HttpScaleUrl, httpUnit = options.HttpUnit, httpStableField = options.HttpStableField,
        printerName = options.PrinterName, paperWidthMm = policy.PaperWidthMm, paperHeightMm = policy.PaperHeightMm,
        minimumKg = options.MinimumKg.ToString(System.Globalization.CultureInfo.InvariantCulture),
        maximumKg = options.MaximumKg.ToString(System.Globalization.CultureInfo.InvariantCulture),
        divisionKg = options.DivisionKg.ToString(System.Globalization.CultureInfo.InvariantCulture),
        allowScaleControl = options.AllowScaleControl,
        traceUrlTemplate = policy.TraceUrlTemplate, traceConfigured = !string.IsNullOrWhiteSpace(policy.TraceUrlTemplate)
    };

    /// <summary>读取经过DPAPI保护的上次会话引用，演练初始值仅从本机配置获取。</summary>
    /// <returns>未设置时为空字符串；不自行创造员工身份。</returns>
    internal string CurrentSession()
    {
        string? value = store.Setting("workstation_operator");
        return value == null ? policy.InitialOperatorSession : StateStore.Unprotect<string>(value);
    }

    /// <summary>平台验证任务成功后记录当前会话引用；不保存员工密码或Cookie。</summary>
    /// <param name="session">非空且不超过160字符的平台会话，未授权不得调用本方法保存。</param>
    internal void SaveSession(string session)
    {
        if (string.IsNullOrWhiteSpace(session) || session.Length > 160)
            throw new AgentFault("OPERATOR_SESSION_REQUIRED", "请输入平台签发的员工授权引用");
        store.Setting("workstation_operator", StateStore.Protect(session));
    }

    /// <summary>维护操作使用独立配置码，普通操作员只可查看设备信息。</summary>
    /// <param name="supplied">请求头维护码，缺失/空值或未配置均拒绝。</param>
    /// <exception cref="AgentFault">维护码未启用或不匹配。</exception>
    internal void RequireMaintenance(string? supplied)
    {
        if (string.IsNullOrEmpty(policy.MaintenanceCode) || string.IsNullOrEmpty(supplied)
            || !CryptographicOperations.FixedTimeEquals(SHA256.HashData(Encoding.UTF8.GetBytes(supplied)),
                SHA256.HashData(Encoding.UTF8.GetBytes(policy.MaintenanceCode))))
            throw new AgentFault("MAINTENANCE_REQUIRED", "请由维护人员输入有效维护码");
    }

    /// <summary>列举本机端口与Windows驱动队列；查询不占用串口或执行打印。</summary>
    /// <returns>可用名称数组，不把存在队列当作物理打印机在线。</returns>
    internal object Devices() => new { serialPorts = SerialPort.GetPortNames().Order().ToArray(),
        printers = PrinterSettings.InstalledPrinters.Cast<string>().Order().ToArray() };

    /// <summary>只写入受限设备配置，当前进程不热切换，重启后统一生效。</summary>
    /// <param name="request">非空设备参数；字符串须合法，纸张为毫米，不含业务数据库或凭据。</param>
    /// <exception cref="AgentFault">输入不合法；写盘错误向上返回未知结果，不声称已重启。</exception>
    internal void SaveSetup(DeviceSetup request)
    {
        if (request.TraceUrlTemplate != null)
            TraceLink.Build(request.TraceUrlTemplate, options.DeviceCode + ":" + new string('a', 32));
        if (request.Protocol is not ("SOHE_RS232_V101" or "SOHE_AD" or "SOHE_HTTP")
            || request.SerialPort == null || request.SerialPort.Length > 40
            || request.BaudRate is not (9600 or 19200 or 38400 or 57600 or 115200)
            || request.PrinterName == null || request.PrinterName.Length > 200
            || request.PaperWidthMm is < 40 or > 110 || request.PaperHeightMm is < 40 or > 300
            || request.HttpStableField == null || request.HttpStableField.Length > 80
            || !Uri.TryCreate(request.HttpScaleUrl, UriKind.Absolute, out var scaleUri) || !scaleUri.IsLoopback
            || scaleUri.Scheme != "http" || !string.IsNullOrEmpty(scaleUri.UserInfo)
            || request.HttpUnit is not ("" or "kg" or "g" or "sj"))
            throw new AgentFault("INVALID_DEVICE_SETUP", "请检查协议、串口、厂家本机地址、纸张和单位设置");
        string path = Path.Combine(AppContext.BaseDirectory, "appsettings.Local.json");
        JsonObject root = File.Exists(path) ? JsonNode.Parse(File.ReadAllText(path))?.AsObject() ?? new() : new();
        JsonObject agent = root["Agent"]?.AsObject() ?? new();
        root["Agent"] = agent;
        agent["Protocol"] = request.Protocol; agent["SerialPort"] = request.SerialPort;
        agent["BaudRate"] = request.BaudRate; agent["HttpScaleUrl"] = request.HttpScaleUrl;
        agent["HttpUnit"] = request.HttpUnit; agent["HttpStableField"] = request.HttpStableField;
        agent["PrinterName"] = request.PrinterName;
        JsonObject workstation = root["Workstation"]?.AsObject() ?? new(); root["Workstation"] = workstation;
        workstation["LocalTicketsEnabled"] = request.LocalTicketsEnabled;
        workstation["PaperWidthMm"] = request.PaperWidthMm; workstation["PaperHeightMm"] = request.PaperHeightMm;
        // 旧客户端缺字段时保留配置；显式空字符串才关闭新票查询链接。
        if (request.TraceUrlTemplate != null) workstation["TraceUrlTemplate"] = request.TraceUrlTemplate.Trim();
        string temporary = path + ".pending";
        File.WriteAllText(temporary, root.ToJsonString(new System.Text.Json.JsonSerializerOptions { WriteIndented = true }), new UTF8Encoding(false));
        File.Move(temporary, path, true);
    }

    /// <summary>查询本地凭条，沿用本商品历史可见性。</summary>
    /// <param name="scope">非空当前员工缓存中的作业和明细。</param>
    /// <returns>原始冻结内容及每次执行状态。</returns>
    internal LocalTicket[] Tickets(BasketScope scope) { baskets.Summary(scope); return store.LocalTickets(scope); }

    /// <summary>确认补打原因及当前员工范围，追加执行而不修改原筐。</summary>
    /// <param name="scope">当前非空员工范围，必须仍有有效授权和领取租约。</param>
    /// <param name="request">非空请求，操作号及事件键不超过160字符；原因1至500字符。</param>
    /// <returns>持久补打意图，尚不证明出纸。</returns>
    /// <exception cref="AgentFault">策略未启用、权限失效或参数/原凭条不合格。</exception>
    internal LocalTicket Reprint(BasketScope scope, ReprintRequest request)
    {
        bool formalLabel = store.LocalTickets(scope).LastOrDefault(ticket => ticket.Snapshot.EventKey == request.EventKey)
            ?.Snapshot.TemplateVersion == "PLATFORM_TRACE_V1";
        if (!policy.LocalTicketsEnabled && !formalLabel) throw new AgentFault("LOCAL_TICKET_DISABLED", "本机尚未启用待同步凭条");
        if (new[] { request.OperationId, request.EventKey }.Any(s => string.IsNullOrWhiteSpace(s) || s.Length > 160)
            || string.IsNullOrWhiteSpace(request.Reason) || request.Reason.Length > 500)
            throw new AgentFault("INVALID_REPRINT", "请提供原记录、唯一操作号和不超过500字符的补打原因");
        TaskSnapshot? job = store.CachedTasks(scope.OperatorSessionId)?.Tasks.FirstOrDefault(t => t.JobId == scope.JobId && t.TaskId == scope.TaskId);
        TaskItem? item = job?.Items.FirstOrDefault(i => i.TaskItemId == scope.TaskItemId);
        if (job == null || item == null) throw new AgentFault("TASK_ITEM_NOT_VISIBLE", "当前员工不可见该记录");
        captures.ValidateContext(new(request.OperationId, "reprint-without-sampling", scope.TaskId, scope.TaskItemId,
            scope.OperatorSessionId, scope.JobId, job.JobVersion, item.TaskItemVersion));
        if (!policy.LocalTest && string.IsNullOrWhiteSpace(options.PrinterName))
            throw new AgentFault("PRINTER_NOT_CONFIGURED", "请先配置本地凭条打印机");
        return store.ReprintLocalTicket(scope, request with { Reason = request.Reason.Trim() },
            options.PrinterName, policy.PaperWidthMm, policy.PaperHeightMm);
    }
}
