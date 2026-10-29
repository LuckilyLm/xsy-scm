using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;

namespace Xsy.Scale.Agent;

/// <summary>只调用设备 v1 草案接口；不持有员工 Cookie，不提供人工接受业务命令。</summary>
internal sealed class PlatformClient(AgentOptions options, StateStore store, HttpClient http)
{
    internal const string Prefix = "/scm/device/scale/v1";

    internal Uri BaseUri()
    {
        if (!Uri.TryCreate(options.PlatformBaseUrl, UriKind.Absolute, out Uri? uri)
            || !string.IsNullOrEmpty(uri.UserInfo) || !string.IsNullOrEmpty(uri.Query)
            || (uri.Scheme != "https" && !(options.AllowLoopbackPlatform && uri.IsLoopback && uri.Scheme == "http")))
            throw new AgentFault("PLATFORM_NOT_CONFIGURED", "请配置 HTTPS 平台地址，模拟平台仅允许回环 HTTP");
        return uri;
    }

    internal DeviceCredential? Credential()
    {
        string? protectedValue = store.Setting("credential");
        if (protectedValue == null || store.Setting("suspended") == "true") return null;
        DeviceCredential result = StateStore.Unprotect<DeviceCredential>(protectedValue);
        return result.ExpiresAt > DateTimeOffset.UtcNow ? result : null;
    }

    internal async Task Activate(string code, CancellationToken cancellation)
    {
        if (string.IsNullOrWhiteSpace(code) || code.Length > 200 || string.IsNullOrWhiteSpace(options.DeviceCode))
            throw new AgentFault("INVALID_PAIRING", "请填写设备编码和有效配对码");
        DeviceCredential credential = await Send<DeviceCredential>(HttpMethod.Post, "/agents/activate",
            new { deviceCode = options.DeviceCode, installationId = store.InstallationId, pairingCode = code,
                protocolVersion = "1", agentVersion = WireJson.Version }, cancellation, authenticate: false);
        if (string.IsNullOrWhiteSpace(credential.AccessToken) || credential.ExpiresAt <= DateTimeOffset.UtcNow)
            throw new AgentFault("INVALID_CREDENTIAL", "平台未返回有效的限时设备凭证");
        store.Setting("credential", StateStore.Protect(credential));
        store.Setting("suspended", "false");
    }

    internal async Task<TaskBatch> Tasks(string session, CancellationToken cancellation)
    {
        var tasks = new List<TaskSnapshot>();
        string? cursor = null;
        var cursors = new HashSet<string>();
        DateTimeOffset expires = DateTimeOffset.MaxValue;
        // 有界分页避免错误游标把设备端锁在无限循环里；不默默缓存不完整授权列表。
        for (int page = 0; page < 20; page++)
        {
            string query = "/jobs?limit=100" + (cursor == null ? "" : "&cursor=" + Uri.EscapeDataString(cursor));
            JobPage result = await Send<JobPage>(HttpMethod.Get, query, null, cancellation, session);
            if (result.Items == null || result.OperatorSessionExpiresAt <= DateTimeOffset.UtcNow)
                throw new AgentFault("INVALID_TASK_RESPONSE", "平台任务缺少有效的员工会话期限");
            expires = expires < result.OperatorSessionExpiresAt ? expires : result.OperatorSessionExpiresAt;
            tasks.AddRange(result.Items);
            if (string.IsNullOrEmpty(result.NextCursor)) return new TaskBatch(expires, tasks.ToArray());
            cursor = result.NextCursor;
            if (!cursors.Add(cursor)) break;
        }
        throw new AgentFault("INVALID_TASK_RESPONSE", "任务分页超过上限或游标重复，请缩小任务范围");
    }

    internal Task<TaskSnapshot> Job(string jobId, string session, CancellationToken cancellation) =>
        Send<TaskSnapshot>(HttpMethod.Get, $"/jobs/{Uri.EscapeDataString(jobId)}", null, cancellation, session);

    internal async Task<TaskSnapshot> ChangeJob(string jobId, string action, string session, CancellationToken cancellation)
    {
        TaskBatch? cache = store.CachedTasks(session);
        TaskSnapshot? current = cache?.Tasks.FirstOrDefault(task => task.JobId == jobId);
        if (current == null || cache!.OperatorSessionExpiresAt <= DateTimeOffset.UtcNow)
            throw new AgentFault("OPERATOR_SESSION_EXPIRED", "请先联网获取当前员工任务");
        TaskSnapshot changed = await Send<TaskSnapshot>(HttpMethod.Post,
            $"/jobs/{Uri.EscapeDataString(jobId)}/{action}",
            new { deviceCode = options.DeviceCode, jobVersion = current.JobVersion }, cancellation, session);
        if (changed.JobId != jobId) throw new AgentFault("INVALID_TASK_RESPONSE", "平台返回了不匹配的作业上下文");
        store.CacheTasks(session, cache with { Tasks = cache.Tasks.Select(task => task.JobId == jobId ? changed : task).ToArray() });
        return changed;
    }

    internal Task<JsonElement> Upload(Measurement measurement, CancellationToken cancellation) =>
        Send<JsonElement>(HttpMethod.Post, "/measurements", measurement, cancellation);

    internal Task<PrintPage> PrintJobs(CancellationToken cancellation) =>
        Send<PrintPage>(HttpMethod.Get, "/print-jobs/pull?limit=5", null, cancellation);

    internal Task<PrintJob> Claim(PrintJob job, CancellationToken cancellation) =>
        Send<PrintJob>(HttpMethod.Post, $"/print-jobs/{Uri.EscapeDataString(job.PrintJobId)}/claim",
            new { deviceCode = options.DeviceCode, printAttemptId = job.PrintAttemptId }, cancellation);

    internal Task<JsonElement> Ack(PrintAck ack, CancellationToken cancellation) =>
        Send<JsonElement>(HttpMethod.Post, $"/print-jobs/{Uri.EscapeDataString(ack.PrintJobId)}/ack", ack, cancellation);

    internal Task<JsonElement> Heartbeat(ScaleState state, IRawPrinter printer, CancellationToken cancellation)
    {
        var snapshot = state.Snapshot();
        return Send<JsonElement>(HttpMethod.Post, "/agents/heartbeat", new
        {
            deviceCode = options.DeviceCode, agentVersion = WireJson.Version, protocolType = options.Protocol,
            scaleConnected = snapshot.Reading != null && DateTimeOffset.UtcNow - snapshot.Reading.CapturedAt
                <= TimeSpan.FromSeconds(options.ReadingMaxAgeSeconds),
            lastWeightAt = snapshot.Reading?.CapturedAt, printerConnected = (bool?)null,
            printerQueueAvailable = printer.IsAvailable(options.PrinterName), queueDepth = store.QueueDepth(),
            sentAt = DateTimeOffset.UtcNow
        }, cancellation);
    }

    internal async Task<byte[]> Download(PrintJob job, CancellationToken cancellation)
    {
        Uri origin = BaseUri();
        if (!Uri.TryCreate(origin, job.ContentUrl, out Uri? target) || target.Scheme != origin.Scheme
            || target.Host != origin.Host || target.Port != origin.Port || target.UserInfo.Length != 0)
            throw new AgentFault("INVALID_PRINT_URL", "打印内容必须来自已配置的平台同源地址");
        using var request = new HttpRequestMessage(HttpMethod.Get, target);
        Authorize(request);
        request.Headers.Add("X-Request-Id", Guid.NewGuid().ToString("N"));
        // ResponseHeadersRead 不约束后续流读取，必须另外限制整个下载的时长。
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
        deadline.CancelAfter(TimeSpan.FromSeconds(options.PlatformTimeoutSeconds));
        cancellation = deadline.Token;
        using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellation);
        if (!response.IsSuccessStatusCode) throw Failure(response.StatusCode, "PRINT_DOWNLOAD_FAILED");
        if (response.Content.Headers.ContentLength > options.MaxPrintBytes)
            throw new AgentFault("PRINT_TOO_LARGE", "打印内容超过允许大小");
        await using Stream stream = await response.Content.ReadAsStreamAsync(cancellation);
        using var memory = new MemoryStream();
        var buffer = new byte[8192];
        int count;
        while ((count = await stream.ReadAsync(buffer, cancellation)) != 0)
        {
            if (memory.Length + count > options.MaxPrintBytes) throw new AgentFault("PRINT_TOO_LARGE", "打印内容超过允许大小");
            memory.Write(buffer, 0, count);
        }
        return memory.ToArray();
    }

    private async Task<T> Send<T>(HttpMethod method, string path, object? body, CancellationToken cancellation,
        string? operatorSession = null, bool authenticate = true)
    {
        // Base URL 可包含网关前缀，所有设备端点仍处于固定 v1 命名空间。
        Uri origin = BaseUri();
        string basePath = origin.AbsolutePath.TrimEnd('/');
        using var request = new HttpRequestMessage(method, new Uri(origin, basePath + Prefix + path));
        if (authenticate) Authorize(request);
        request.Headers.Add("X-Agent-Version", WireJson.Version);
        request.Headers.Add("X-Protocol-Version", "1");
        request.Headers.Add("X-Request-Id", Guid.NewGuid().ToString("N"));
        if (operatorSession != null) request.Headers.Add("X-Operator-Session", operatorSession);
        if (body != null) request.Content = JsonContent.Create(body, options: WireJson.Options);
        using var response = await http.SendAsync(request, cancellation);
        if (!response.IsSuccessStatusCode)
        {
            string code = "PLATFORM_REJECTED";
            try
            {
                using var error = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellation));
                if (error.RootElement.TryGetProperty("code", out var field) && field.ValueKind == JsonValueKind.String)
                    code = field.GetString() ?? code;
            }
            catch (JsonException) { }
            throw Failure(response.StatusCode, code);
        }
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellation));
        JsonElement root = doc.RootElement;
        string? result = root.TryGetProperty("code", out var resultCode) && resultCode.ValueKind == JsonValueKind.String
            ? resultCode.GetString() : null;
        if (result is not ("OK" or "DUPLICATED"))
        {
            if (result == "DEVICE_UNAUTHORIZED") store.Setting("suspended", "true");
            throw new PlatformFault(result ?? "INVALID_RESPONSE", false);
        }
        if (!root.TryGetProperty("data", out var data)) throw new PlatformFault("INVALID_RESPONSE", true);
        return data.Deserialize<T>(WireJson.Options) ?? throw new PlatformFault("INVALID_RESPONSE", true);
    }

    private void Authorize(HttpRequestMessage request)
    {
        DeviceCredential? credential = Credential();
        if (credential == null) throw new PlatformFault("DEVICE_UNAUTHORIZED", false);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", credential.AccessToken);
    }

    private PlatformFault Failure(HttpStatusCode status, string code)
    {
        if (status == HttpStatusCode.Unauthorized || code == "DEVICE_UNAUTHORIZED")
        { store.Setting("suspended", "true"); return new PlatformFault("DEVICE_UNAUTHORIZED", false); }
        bool retry = (int)status >= 500 || status is HttpStatusCode.RequestTimeout or HttpStatusCode.TooManyRequests;
        return new PlatformFault(code, retry);
    }
}

/// <summary>平台失败只暴露约定错误码，明确区分可重试和人工处理。</summary>
internal sealed class PlatformFault(string code, bool retryable) : Exception(code)
{
    internal string Code { get; } = code;
    internal bool Retryable { get; } = retryable;
}
