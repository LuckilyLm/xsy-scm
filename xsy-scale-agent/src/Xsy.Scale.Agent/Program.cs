using System.Security.Cryptography;
using System.Text;
using Xsy.Scale.Agent;

// 不依赖终端当前目录，直接双击、自启和 dotnet run 都读取程序目录中的配置。
var builder = WebApplication.CreateBuilder(new WebApplicationOptions { Args = args, ContentRootPath = AppContext.BaseDirectory,
    WebRootPath = Path.Combine(AppContext.BaseDirectory, "wwwroot") });
builder.Configuration.AddJsonFile("appsettings.Local.json", optional: true, reloadOnChange: false)
    .AddEnvironmentVariables("XSY_").AddCommandLine(args);
var options = builder.Configuration.GetSection("Agent").Get<AgentOptions>() ?? new();
var workstationOptions = builder.Configuration.GetSection("Workstation").Get<WorkstationOptions>() ?? new();
string urls = builder.Configuration["Urls"] ?? "http://127.0.0.1:33600";
if (urls.Split(';').Any(url => !Uri.TryCreate(url, UriKind.Absolute, out Uri? uri) || !uri.IsLoopback || uri.Scheme != "http"))
    throw new InvalidOperationException("本机服务仅允许监听回环 HTTP 地址");
if (options.DivisionKg <= 0 || options.MinimumKg < 0 || options.MaximumKg < options.MinimumKg
    || options.ReadingMaxAgeSeconds is < 1 or > 30 || options.PlatformPollSeconds < 1 || options.HeartbeatSeconds < 1
    || options.PlatformTimeoutSeconds is < 1 or > 120
    || options.MaxPrintBytes is < 1 or > 10485760)
    throw new InvalidOperationException("设备采样、计量或重试配置不合法");
builder.WebHost.UseUrls(urls);
builder.WebHost.ConfigureKestrel(server => server.Limits.MaxRequestBodySize = 32 * 1024);
string directory = options.DataDirectory.Length > 0 ? options.DataDirectory
    : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "XSY", "ScaleAgent");
Directory.CreateDirectory(directory);
// 锁文件随进程退出自动释放，避免手工启动与登录任务同时恢复同一打印队列。
using var instanceLock = new FileStream(Path.Combine(directory, "agent.lock"), FileMode.OpenOrCreate,
    FileAccess.ReadWrite, FileShare.None);
var store = new StateStore(directory);
string keyFile = Path.Combine(store.DirectoryPath, "local-api-key.txt");
if (!File.Exists(keyFile)) File.WriteAllText(keyFile, Convert.ToHexString(RandomNumberGenerator.GetBytes(32)), Encoding.ASCII);
string localKey = File.ReadAllText(keyFile).Trim();
if (localKey.Length < 32) throw new InvalidOperationException("本机访问密钥无效");
builder.Services.AddSingleton(options);
builder.Services.AddSingleton(workstationOptions);
builder.Services.AddSingleton(store);
builder.Services.AddSingleton<ScaleState>();
builder.Services.AddSingleton<CaptureService>();
builder.Services.AddSingleton<BasketService>();
builder.Services.AddSingleton<WorkstationService>();
builder.Services.AddSingleton<ITicketPrinter>(_ => workstationOptions.HardwareTest
    ? new HardwareTestTicketPrinter() : workstationOptions.LocalTest ? new ExerciseTicketPrinter() : new DriverTicketPrinter());
builder.Services.AddHostedService<LocalTicketWorker>();
builder.Services.AddSingleton<ScaleReader>();
builder.Services.AddHostedService(service => service.GetRequiredService<ScaleReader>());
builder.Services.AddSingleton<IRawPrinter, WindowsRawPrinter>();
builder.Services.AddSingleton(new HttpClient(new HttpClientHandler { AllowAutoRedirect = false, UseCookies = false })
{ Timeout = TimeSpan.FromSeconds(options.PlatformTimeoutSeconds) });
builder.Services.AddSingleton<PlatformClient>();
builder.Services.AddSingleton<TraceLabelService>();
builder.Services.AddSingleton<PrintService>();
builder.Services.AddHostedService<SyncWorker>();

// 集团工作台跨源访问时只能配置已核实的完整源，且仍需本机密钥；默认不开放 CORS。
string[] allowedOrigins = builder.Configuration.GetSection("LocalAllowedOrigins").Get<string[]>() ?? [];
if (allowedOrigins.Any(origin => !Uri.TryCreate(origin, UriKind.Absolute, out var uri)
    || uri.GetLeftPart(UriPartial.Authority) != origin || uri.Scheme is not ("https" or "http")))
    throw new InvalidOperationException("工作台来源必须是完整且明确的 HTTP/HTTPS 源");
builder.Services.AddCors(cors => cors.AddDefaultPolicy(policy =>
{
    if (allowedOrigins.Length > 0) policy.WithOrigins(allowedOrigins).WithHeaders("Content-Type", "X-Agent-Key", "X-Operator-Session")
        .WithMethods("GET", "POST");
}));

var app = builder.Build();
app.Services.GetRequiredService<WorkstationService>().ValidateMode();
// 浏览器仅获得进程生命周期的HttpOnly会话，不向脚本或URL泄露本机API密钥。
string workstationCookie = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
app.UseCors();
app.Use(async (context, next) =>
{
    try
    {
        if (!Uri.TryCreate("http://" + context.Request.Host, UriKind.Absolute, out var hostUri) || !hostUri.IsLoopback)
        { context.Response.StatusCode = 400; return; }
        context.Response.Headers["X-Frame-Options"] = "DENY";
        context.Response.Headers["X-Content-Type-Options"] = "nosniff";
        if (context.Request.Path == "/" || context.Request.Path == "/index.html")
        {
            context.Response.Headers.CacheControl = "no-store";
            context.Response.Cookies.Append("XSY-Workstation", workstationCookie,
                new CookieOptions { HttpOnly = true, SameSite = SameSiteMode.Strict, IsEssential = true, Path = "/" });
        }
        if (context.Request.Path.StartsWithSegments("/api/agent"))
        {
            byte[] supplied = Encoding.UTF8.GetBytes(context.Request.Headers["X-Agent-Key"].ToString());
            string origin = context.Request.Headers.Origin.ToString();
            bool sameOrigin = origin.Length == 0 || origin == "http://" + context.Request.Host;
            bool uiSession = context.Request.Cookies["XSY-Workstation"] == workstationCookie
                && context.Request.Headers["X-Workstation-Request"] == "1" && sameOrigin;
            if (!uiSession && !CryptographicOperations.FixedTimeEquals(supplied, Encoding.UTF8.GetBytes(localKey)))
            { context.Response.StatusCode = 401; await context.Response.WriteAsJsonAsync(new { code = "LOCAL_UNAUTHORIZED" }); return; }
            context.Response.Headers.CacheControl = "no-store";
            if (uiSession && context.Request.Path == "/api/agent/activate")
                app.Services.GetRequiredService<WorkstationService>().RequireMaintenance(context.Request.Headers["X-Maintenance-Code"]);
        }
        await next();
    }
    catch (Exception error) when (!context.Response.HasStarted)
    {
        string code = error switch
        {
            AgentFault fault => fault.Code, PlatformFault fault => fault.Code,
            OperationCanceledException => "REQUEST_TIMEOUT", HttpRequestException => "PLATFORM_UNAVAILABLE", _ => "AGENT_ERROR"
        };
        context.Response.StatusCode = error switch {
            AgentFault => 409,
            PlatformFault { Code: "DEVICE_UNAUTHORIZED" or "OPERATOR_SESSION_INVALID" or "OPERATOR_SESSION_DEVICE_MISMATCH" } => 401,
            PlatformFault { Retryable: false } => 422,
            _ => 503
        };
        await context.Response.WriteAsJsonAsync(new { code, message = error is AgentFault or PlatformFault ? error.Message : "请求未完成，请查看连接状态" });
    }
});
app.UseDefaultFiles();
app.UseStaticFiles();

app.MapGet("/api/agent/workstation", (WorkstationService workstation) => Results.Ok(workstation.Bootstrap()));
app.MapPost("/api/agent/workstation/session", async (WorkstationSession input, WorkstationService workstation,
    PlatformClient platform, CancellationToken cancellation) =>
{
    if (string.IsNullOrWhiteSpace(input.OperatorSessionId) || input.OperatorSessionId.Length > 160)
        throw new AgentFault("OPERATOR_SESSION_REQUIRED", "请提供平台签发的员工会话引用");
    TaskBatch batch = await platform.Tasks(input.OperatorSessionId, cancellation);
    if (batch.OperatorSessionExpiresAt <= DateTimeOffset.UtcNow) throw new AgentFault("OPERATOR_SESSION_EXPIRED", "员工授权已过期");
    store.CacheTasks(input.OperatorSessionId, batch); workstation.SaveSession(input.OperatorSessionId);
    return Results.Ok(new { operatorSessionId = input.OperatorSessionId });
});
app.MapGet("/api/agent/workstation/devices", (WorkstationService workstation) => Results.Ok(workstation.Devices()));
app.MapPost("/api/agent/workstation/config", (DeviceSetup input, HttpContext context, WorkstationService workstation) =>
{
    workstation.RequireMaintenance(context.Request.Headers["X-Maintenance-Code"]);
    workstation.SaveSetup(input); return Results.Ok(new { saved = true, restartRequired = true });
});
app.MapGet("/api/agent/local-tickets", (string jobId, string taskId, string taskItemId, HttpContext context, WorkstationService workstation) =>
    Results.Ok(workstation.Tickets(new(Session(context), jobId, taskId, taskItemId))));
app.MapPost("/api/agent/local-tickets/reprint", async (string jobId, string taskId, string taskItemId,
    ReprintRequest input, HttpContext context, WorkstationService workstation, PlatformClient platform, CancellationToken cancellation) => {
    var scope = new BasketScope(Session(context), jobId, taskId, taskItemId);
    var previous = workstation.Tickets(scope).LastOrDefault(ticket => ticket.Snapshot.EventKey == input.EventKey);
    if (previous?.Snapshot.TemplateVersion == "PLATFORM_TRACE_V1")
        await platform.TraceLabel(previous.Snapshot.TraceReference!, scope.OperatorSessionId, cancellation);
    return Results.Ok(workstation.Reprint(scope, input));
});

app.MapGet("/health", () => new { status = "running", version = WireJson.Version });
app.MapGet("/api/agent/status", (ScaleState scale, PlatformClient platform) =>
{
    var current = scale.Snapshot();
    bool connected = current.Reading != null && DateTimeOffset.UtcNow - current.Reading.CapturedAt
        <= TimeSpan.FromSeconds(options.ReadingMaxAgeSeconds);
    return Results.Ok(new { deviceCode = options.DeviceCode, version = WireJson.Version,
        deviceAuthorized = platform.Credential() != null, scaleConnected = connected,
        error = connected ? current.Error : current.Error ?? "STALE_READING", reading = connected ? current.Reading : null,
        control = scale.ControlSnapshot(), controlHistory = scale.ControlHistory() });
});
app.MapPost("/api/agent/activate", async (PairingInput input, PlatformClient platform, CancellationToken cancellation) =>
{
    await platform.Activate(input.PairingCode, cancellation);
    return Results.Ok(new { activated = true });
});
app.MapGet("/api/agent/tasks", async (HttpContext context, PlatformClient platform, CancellationToken cancellation) =>
{
    string session = Session(context);
    try
    {
        TaskBatch result = await platform.Tasks(session, cancellation);
        if (result.Tasks == null || result.OperatorSessionExpiresAt <= DateTimeOffset.UtcNow)
            throw new AgentFault("INVALID_TASK_RESPONSE", "平台任务缺少有效的员工会话期限");
        store.CacheTasks(session, result);
        return Results.Ok(new { cached = false, data = result });
    }
    catch (Exception error) when (error is HttpRequestException or OperationCanceledException
        || error is PlatformFault { Retryable: true })
    {
        TaskBatch? cached = store.CachedTasks(session);
        if (cached == null) throw new AgentFault("TASK_CACHE_EMPTY", "没有当前员工的缓存任务，请先联网获取");
        return Results.Ok(new { cached = true, data = cached });
    }
});
app.MapPost("/api/agent/captures", (CaptureRequest input, CaptureService captures) =>
{
    LocalEvent record = captures.Capture(input);
    return Results.Ok(new { record.EventKey, record.State, record.EventId,
        rawWeight = record.Measurement.RawValue, record.Measurement.RawUnit, record.Measurement.CapturedAt });
});
app.MapGet("/api/agent/events", (HttpContext context) =>
{
    string session = Session(context);
    return Results.Ok(store.Events().Where(record => record.Measurement.OperatorSessionId == session).Select(record => new
    { record.EventKey, record.State, record.EventId, record.LastError, record.Measurement.TaskId, record.Measurement.TaskItemId,
        rawWeight = record.Measurement.RawValue, record.Measurement.RawUnit, record.Measurement.CapturedAt }));
});
app.MapPost("/api/agent/baskets", (CaptureRequest input, HttpContext context, BasketService baskets) =>
    Results.Ok(baskets.Record(input, Session(context))));
app.MapGet("/api/agent/baskets", (string jobId, string taskId, string taskItemId, int? after, int? limit,
    HttpContext context, BasketService baskets) =>
    Results.Ok(baskets.Records(new(Session(context), jobId, taskId, taskItemId), after ?? 0, limit ?? 50)));
app.MapGet("/api/agent/basket-summary", (string jobId, string taskId, string taskItemId,
    HttpContext context, BasketService baskets) =>
    Results.Ok(baskets.Summary(new(Session(context), jobId, taskId, taskItemId))));
app.MapGet("/api/agent/basket-queue", (string jobId, string taskId, string taskItemId, long? after, int? limit,
    HttpContext context, BasketService baskets) =>
    Results.Ok(baskets.Queue(new(Session(context), jobId, taskId, taskItemId), after ?? 0, limit ?? 50)));
app.MapPost("/api/agent/item-completions", (CompleteItemRequest input, HttpContext context, BasketService baskets) =>
    Results.Ok(new { state = "WAITING_CONTRACT", locallyCompleted = true, completion = baskets.Complete(input, Session(context)) }));
app.MapGet("/api/agent/trace-lots", async (string jobId, string taskId, string taskItemId, HttpContext context,
    TraceLabelService labels, CancellationToken cancellation) =>
    Results.Ok(await labels.Lots(new(Session(context), jobId, taskId, taskItemId), cancellation)));
app.MapPost("/api/agent/trace-labels", async (string jobId, string taskId, string taskItemId, TraceLabelRequest input,
    HttpContext context, TraceLabelService labels, CancellationToken cancellation) =>
    Results.Ok(await labels.Print(new(Session(context), jobId, taskId, taskItemId), input, cancellation)));
app.MapGet("/api/agent/jobs/{jobId}", async (string jobId, HttpContext context, PlatformClient platform, CancellationToken cancellation) =>
    Results.Ok(await platform.Job(jobId, Session(context), cancellation)));
app.MapPost("/api/agent/jobs/{jobId}/{action}", async (string jobId, string action, HttpContext context,
    PlatformClient platform, CancellationToken cancellation) =>
{
    if (action is not ("claim" or "release")) throw new AgentFault("INVALID_COMMAND", "仅支持领取或释放平台作业");
    return Results.Ok(await platform.ChangeJob(jobId, action, Session(context), cancellation));
});
app.MapGet("/api/agent/prints", () => Results.Ok(store.Prints().Select(job => new
{ job.Job.PrintJobId, job.Job.PrintAttemptId, job.Job.PrintRecordId, job.State, status = job.Ack?.Status, errorCode = job.Ack?.ErrorCode })));
app.MapPost("/api/agent/scale/{command}", async (string command, ScaleReader reader, HttpContext context, CancellationToken cancellation) =>
{
    TaskBatch? authorized = store.CachedTasks(Session(context));
    if (authorized == null || authorized.OperatorSessionExpiresAt <= DateTimeOffset.UtcNow || store.Setting("suspended") == "true")
        throw new AgentFault("OPERATOR_SESSION_EXPIRED", "请先获取当前员工的授权任务");
    var control = await reader.Control(command, cancellation);
    return Results.Ok(new { status = "REQUEST_SENT", message = "等待设备后续稳定零读数", control });
});
app.MapGet("/api/agent/weight/stream", async (HttpContext context, ScaleState scale) =>
{
    context.Response.ContentType = "text/event-stream";
    context.Response.Headers.CacheControl = "no-store";
    while (!context.RequestAborted.IsCancellationRequested)
    {
        var snapshot = scale.Snapshot();
        bool fresh = snapshot.Reading != null && DateTimeOffset.UtcNow - snapshot.Reading.CapturedAt
            <= TimeSpan.FromSeconds(options.ReadingMaxAgeSeconds);
        await context.Response.WriteAsync("data: " + WireJson.Encode(new { reading = fresh ? snapshot.Reading : null,
            error = fresh ? snapshot.Error : snapshot.Error ?? "STALE_READING", control = scale.ControlSnapshot() }) + "\n\n", context.RequestAborted);
        await context.Response.Body.FlushAsync(context.RequestAborted);
        await Task.Delay(250, context.RequestAborted);
    }
});
await app.RunAsync();

static string Session(HttpContext context)
{
    string session = context.Request.Headers["X-Operator-Session"].ToString();
    if (string.IsNullOrWhiteSpace(session) || session.Length > 160)
        throw new AgentFault("OPERATOR_SESSION_REQUIRED", "请提供平台签发的当前员工会话标识");
    return session;
}

/// <summary>一次性管理员配对码；该接口不会接收员工密码或 Cookie。</summary>
internal sealed record PairingInput(string PairingCode);
