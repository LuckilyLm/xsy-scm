using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Logging.Abstractions;
using Xsy.Scale.Agent;

// 直接运行的本地回归入口，所有平台和打印输出均使用隔离替身。
string testRoot = args.Length > 0 ? args[0] : throw new ArgumentException("请传入项目 .codex-tmp 下的测试目录");
Directory.CreateDirectory(testRoot);
int passed = 0;
var cases = new (string Name, Func<Task> Run)[]
{
    ("串口拆包粘包与短噪声恢复", () => Sync(() =>
    {
        var decoder = new SoheFrameDecoder();
        Equal(0, decoder.Feed(Encoding.ASCII.GetBytes("noise\nSN  4.")).Count);
        var readings = decoder.Feed(Encoding.ASCII.GetBytes("850kg\r\nsn  2.500kg\r\n"));
        Equal(2, readings.Count); Equal(4.85m, readings[0].RawWeight); Equal(true, readings[1].StableFlag);
    })),
    ("未知单位和非法帧不得被当作公斤", () => Sync(() =>
    {
        Throws("UNKNOWN_UNIT", () => SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes("SN  4.850lb\r\n")));
        Throws("INVALID_FRAME", () => SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes("XX  4.850kg\r\n")));
    })),
    ("符号占位与负重保留原始事实", () => Sync(() =>
    {
        Equal(12.5m, SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes("SN0 12.50kg\r\n")).RawWeight);
        Equal(-1.25m, SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes("WN- 1.250kg\r\n")).RawWeight);
    })),
    ("AD反向字段解析但不伪造净重模式", () => Sync(() =>
    {
        var decoder = new SoheFrameDecoder(true);
        var reading = decoder.Feed(Convert.FromHexString("02413D3030302E313030000103")).Single();
        Equal(1m, reading.RawWeight); Equal("UNKNOWN", reading.NetGrossFlag);
    })),
    ("厂家HTTP成功不等于稳定", () => Sync(() =>
    {
        var reading = ScaleReader.ParseHttp("""{"netWeight":"4.850","tareWeight":"0.250","Code":1}""",
            new AgentOptions { HttpUnit = "kg" });
        Equal<bool?>(null, reading.StableFlag); Equal("UNKNOWN", reading.StabilitySource);
        var state = new ScaleState(); state.Set(reading);
        Throws("WEIGHT_UNACCEPTABLE", () => state.Freeze(reading.SampleId, Options()));
    })),
    ("失联立即取消旧快照", () => Sync(() =>
    {
        var state = new ScaleState(); var sample = Reading(); state.Set(sample); state.Fail("SCALE_DISCONNECTED");
        Throws("SCALE_UNAVAILABLE", () => state.Freeze(sample.SampleId, Options()));
    })),
    ("过期采样与最新不稳定状态不能采集", () => Sync(() =>
    {
        var state = new ScaleState(); var old = Reading() with { CapturedAt = DateTimeOffset.UtcNow.AddSeconds(-5) }; state.Set(old);
        Throws("STALE_READING", () => state.Freeze(old.SampleId, Options()));
        var good = Reading(); state.Set(good); state.Set(Reading() with { StableFlag = false });
        Throws("WEIGHT_UNACCEPTABLE", () => state.Freeze(good.SampleId, Options()));
    })),
    ("相同稳定重量的新帧不造成点击竞态", () => Sync(() =>
    {
        var state = new ScaleState(); var first = Reading(); state.Set(first); state.Set(Reading());
        Equal(first.SampleId, state.Freeze(first.SampleId, Options()).SampleId);
        state.Set(Reading() with { RawWeight = 5m });
        Throws("SAMPLE_CHANGED", () => state.Freeze(first.SampleId, Options()));
    })),
    ("去皮置零期间不能使用旧读数", () => Sync(() =>
    {
        var state = new ScaleState(); var sample = Reading(); state.Set(sample);
        state.BlockFor(TimeSpan.FromSeconds(1)); state.Set(Reading());
        Equal<WeightReading?>(null, state.Snapshot().Reading);
        Throws("SCALE_UNAVAILABLE", () => state.Freeze(sample.SampleId, Options()));
    })),
    ("取消分度过滤后仍保留最小称量和最大量程守卫", () => Sync(() =>
    {
        foreach (decimal weight in new[] { 0m, 0.5m, 0.95m, 150.01m, 151m, -1m })
        {
            var state = new ScaleState(); var sample = Reading() with { RawWeight = weight }; state.Set(sample);
            Throws("METROLOGY_LIMIT", () => state.Freeze(sample.SampleId, Options()));
        }
    })),
    ("同一操作重试只落一条事件并持久保留键", () => Sync(() =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample);
        var request = Request(sample); var first = context.Capture.Capture(request);
        context.State.Fail("SCALE_DISCONNECTED");
        Equal(first.EventKey, context.Capture.Capture(request).EventKey);
        var reopened = new StateStore(context.Store.DirectoryPath);
        Equal(first.EventKey, reopened.Events().Single().EventKey);
        Equal("CAPTURED_LOCAL", reopened.Events().Single().State);
    })),
    ("两次真实称重即使重量相同也各有事件", () => Sync(() =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample);
        var first = context.Capture.Capture(Request(sample));
        var second = context.Capture.Capture(Request(sample));
        if (first.EventKey == second.EventKey) throw new Exception("Distinct operations must not be merged by weight");
        Equal(2L, context.Store.QueueDepth());
    })),
    ("重用操作编号更改任务被拒绝", () => Sync(() =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample);
        var request = Request(sample); context.Capture.Capture(request);
        Throws("OPERATION_CONFLICT", () => context.Capture.Capture(request with { SampleId = "different" }));
    })),
    ("员工缓存隔离与到期不扩权", () => Sync(() =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample);
        Throws("OPERATOR_SESSION_EXPIRED", () => context.Capture.Capture(Request(sample) with { OperatorSessionId = "another" }));
        context.Store.CacheTasks("operator", Batch() with { OperatorSessionExpiresAt = DateTimeOffset.UtcNow.AddSeconds(-1) });
        Throws("OPERATOR_SESSION_EXPIRED", () => context.Capture.Capture(Request(sample)));
    })),
    ("销售单位不匹配不得隐式转换", () => Sync(() =>
    {
        var context = Context(); var sample = Reading() with { RawUnit = "sj", RawWeight = 4m }; context.State.Set(sample);
        Throws("UNIT_MISMATCH", () => context.Capture.Capture(Request(sample)));
        Equal(0L, context.Store.QueueDepth());
    })),
    ("未领取作业或上下文版本变化不得采集", () => Sync(() =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample);
        Throws("JOB_CONTEXT_STALE", () => context.Capture.Capture(Request(sample) with { JobVersion = "1" }));
        Throws("TASK_ITEM_STALE", () => context.Capture.Capture(Request(sample) with { TaskItemVersion = 1 }));
        context.Store.CacheTasks("operator", Batch() with { Tasks = [Batch().Tasks[0] with { ClaimedByDeviceCode = null }] });
        Throws("JOB_CONTEXT_STALE", () => context.Capture.Capture(Request(sample)));
        context.Store.CacheTasks("operator", Batch() with { Tasks = [Batch().Tasks[0] with { ExpiresAt = DateTimeOffset.UtcNow.AddMinutes(-1) }] });
        Throws("JOB_CONTEXT_STALE", () => context.Capture.Capture(Request(sample)));
        Equal(0L, context.Store.QueueDepth());
    })),
    ("按交接jobs路径分页并保留员工授权期限", async () =>
    {
        var context = Context(); int pages = 0;
        using var client = Client(context.Store, request =>
        {
            Equal("/scm/device/scale/v1/jobs", request.RequestUri!.AbsolutePath);
            Equal("operator", request.Headers.GetValues("X-Operator-Session").Single());
            pages++;
            if (pages == 2 && !request.RequestUri.Query.Contains("cursor=next")) throw new Exception("missing cursor");
            return Task.FromResult(Json(new JobPage(Batch().OperatorSessionExpiresAt,
                [Batch().Tasks[0] with { JobId = "work" + pages }], pages == 1 ? "next" : null)));
        });
        TaskBatch batch = await new PlatformClient(Options(), context.Store, client).Tasks("operator", default);
        Equal(2, pages); Equal(2, batch.Tasks.Length); Equal("work2", batch.Tasks[1].JobId);
    }),
    ("领取释放通过平台返回上下文更新缓存", async () =>
    {
        var context = Context();
        using var client = Client(context.Store, request =>
        {
            bool claiming = request.RequestUri!.AbsolutePath.EndsWith("/claim");
            return Task.FromResult(Json(Batch().Tasks[0] with { ClaimedByDeviceCode = claiming ? "TEST-DEVICE" : null }));
        });
        var platform = new PlatformClient(Options(), context.Store, client);
        await platform.ChangeJob("work1", "release", "operator", default);
        Equal<string?>(null, context.Store.CachedTasks("operator")!.Tasks[0].ClaimedByDeviceCode);
        await platform.ChangeJob("work1", "claim", "operator", default);
        Equal("TEST-DEVICE", context.Store.CachedTasks("operator")!.Tasks[0].ClaimedByDeviceCode);
    }),
    ("上传响应丢失后按原事件键补传", async () =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample);
        LocalEvent first = context.Capture.Capture(Request(sample));
        var keys = new List<string>();
        int calls = 0;
        using var client = Client(context.Store, async request =>
        {
            using var doc = JsonDocument.Parse(await request.Content!.ReadAsStringAsync());
            keys.Add(doc.RootElement.GetProperty("eventKey").GetString()!);
            Equal("Bearer", request.Headers.Authorization?.Scheme);
            if (++calls == 1) throw new HttpRequestException("response lost after server commit");
            Equal("4.850", doc.RootElement.GetProperty("rawValue").GetString());
            Equal("NET", doc.RootElement.GetProperty("weightKind").GetString());
            Equal("DEVICE", doc.RootElement.GetProperty("stabilitySource").GetString());
            Equal("0", doc.RootElement.GetProperty("jobVersion").GetString());
            Equal(0L, doc.RootElement.GetProperty("taskItemVersion").GetInt64());
            if (!request.Headers.Contains("X-Request-Id")) throw new Exception("missing trace id");
            return Json(new { measurementId = "9007199254740993", status = "PENDING", duplicated = true });
        });
        var platform = new PlatformClient(Options(), context.Store, client);
        var worker = Worker(context.Store, platform);
        await worker.UploadOne(default); Equal("RETRY_WAIT", context.Store.Events().Single().State);
        Due(context.Store);
        await worker.UploadOne(default);
        Equal(2, keys.Count); Equal(first.EventKey, keys[1]);
        Equal("SERVER_PENDING", context.Store.Events().Single().State);
        Equal("9007199254740993", context.Store.Events().Single().EventId);
    }),
    ("业务拒绝停止自动重试并保留事实", async () =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample); context.Capture.Capture(Request(sample));
        using var client = Client(context.Store, _ => Task.FromResult(new HttpResponseMessage(HttpStatusCode.Conflict)
        { Content = new StringContent("""{"code":"TASK_ITEM_STALE"}""") }));
        await Worker(context.Store, new PlatformClient(Options(), context.Store, client)).UploadOne(default);
        Equal("NEEDS_REVIEW", context.Store.Events().Single().State); Equal(0L, context.Store.QueueDepth());
    }),
    ("凭证撤销暂停任务而不丢记录", async () =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample); context.Capture.Capture(Request(sample));
        using var client = Client(context.Store, _ => Task.FromResult(new HttpResponseMessage(HttpStatusCode.Unauthorized)));
        var platform = new PlatformClient(Options(), context.Store, client);
        await Worker(context.Store, platform).UploadOne(default);
        Equal("true", context.Store.Setting("suspended")); Equal<DeviceCredential?>(null, platform.Credential());
        Equal("RETRY_WAIT", context.Store.Events().Single().State);
    }),
    ("上传中重启恢复待传且不换键", () => Sync(() =>
    {
        var context = Context(); var sample = Reading(); context.State.Set(sample);
        var original = context.Capture.Capture(Request(sample)); context.Store.NextEvent();
        var reopened = new StateStore(context.Store.DirectoryPath);
        Equal("RETRY_WAIT", reopened.Events().Single().State); Equal(original.EventKey, reopened.Events().Single().EventKey);
    })),
    ("打印成功只记入队且重复任务不再出纸", async () =>
    {
        var context = Context(); using var client = PrintClient(context.Store);
        var printer = new FakePrinter(); var service = new PrintService(Options(), context.Store, new PlatformClient(Options(), context.Store, client), printer);
        await service.Execute(Job(), default); await service.Execute(Job(), default);
        Equal(1, printer.Calls); Equal("SUBMITTED_TO_SPOOLER", context.Store.Prints().Single().Ack?.Status);
    }),
    ("打印摘要或配置不匹配不调用打印机", async () =>
    {
        var context = Context(); using var client = PrintClient(context.Store); var printer = new FakePrinter();
        var service = new PrintService(Options(), context.Store, new PlatformClient(Options(), context.Store, client), printer);
        await service.Execute(Job() with { SnapshotChecksum = "sha256:" + new string('0', 64) }, default);
        Equal(0, printer.Calls); Equal("FAILED", context.Store.Prints().Single().Ack?.Status);
    }),
    ("平台授权新尝试才允许同一打印任务重印", async () =>
    {
        var context = Context(); using var client = PrintClient(context.Store); var printer = new FakePrinter();
        var service = new PrintService(Options(), context.Store, new PlatformClient(Options(), context.Store, client), printer);
        await service.Execute(Job(), default);
        await service.Execute(Job() with { PrintAttemptId = "attempt2", LeaseToken = "lease2" }, default);
        await service.Execute(Job() with { PrintAttemptId = "attempt2", LeaseToken = "lease2" }, default);
        Equal(2, printer.Calls); Equal(2, context.Store.Prints().Length);
    }),
    ("打印内容不能跨源泄露设备凭证", async () =>
    {
        var context = Context(); using var client = PrintClient(context.Store); var printer = new FakePrinter();
        var service = new PrintService(Options(), context.Store, new PlatformClient(Options(), context.Store, client), printer);
        await service.Execute(Job() with { ContentUrl = "https://untrusted.invalid/content" }, default);
        Equal(0, printer.Calls); Equal("INVALID_PRINT_URL", context.Store.Prints().Single().Ack?.ErrorCode);
    }),
    ("提交打印后异常标记未知不自动重打", async () =>
    {
        var context = Context(); using var client = PrintClient(context.Store); var printer = new FakePrinter { Fail = true };
        var service = new PrintService(Options(), context.Store, new PlatformClient(Options(), context.Store, client), printer);
        await service.Execute(Job(), default); await service.Execute(Job(), default);
        Equal(1, printer.Calls); Equal("UNKNOWN", context.Store.Prints().Single().Ack?.Status);
    }),
    ("打印执行中重启上报未知不再物理执行", async () =>
    {
        var context = Context(); context.Store.SavePrint(new LocalPrint(Job(), "execution-original", "SPOOL_SUBMITTING", null));
        var reopened = new StateStore(context.Store.DirectoryPath);
        Equal("UNKNOWN", reopened.Prints().Single().Ack?.Status);
        using var client = PrintClient(reopened); var printer = new FakePrinter();
        var service = new PrintService(Options(), reopened, new PlatformClient(Options(), reopened, client), printer);
        await service.Execute(Job(), default); Equal(0, printer.Calls);
    }),
    ("打印回执丢失只重传同一回执", async () =>
    {
        var context = Context(); int acknowledgements = 0; var bodies = new List<string>();
        using var client = Client(context.Store, async request =>
        {
            if (request.RequestUri!.AbsolutePath == "/content") return new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(PrintBytes()) };
            bodies.Add(await request.Content!.ReadAsStringAsync());
            using var body = JsonDocument.Parse(bodies[^1]);
            Equal("attempt1", body.RootElement.GetProperty("printAttemptId").GetString());
            Equal("SUBMITTED_TO_SPOOLER", body.RootElement.GetProperty("status").GetString());
            Equal("WINDOWS_SPOOLER", body.RootElement.GetProperty("feedbackSource").GetString());
            if (++acknowledgements == 1) throw new HttpRequestException("lost ack");
            return Json(new { });
        });
        var printer = new FakePrinter(); var service = new PrintService(Options(), context.Store, new PlatformClient(Options(), context.Store, client), printer);
        await service.Execute(Job(), default);
        try { await service.Acknowledge(default); throw new Exception("expected lost ack"); } catch (HttpRequestException) { }
        await service.Acknowledge(default);
        Equal(1, printer.Calls); Equal(bodies[0], bodies[1]); Equal("ACKED_SERVER", context.Store.Prints().Single().State);
    })
};

cases = cases.Concat(BasketTests.Cases(testRoot)).ToArray();
cases = cases.Concat(LocalTicketTests.Cases(testRoot)).ToArray();
cases = cases.Concat(TicketLayoutTests.Cases(testRoot)).ToArray();
cases = cases.Concat(ScaleControlTests.Cases()).ToArray();
foreach (var test in cases)
{
    try { await test.Run(); passed++; Console.WriteLine($"PASS {test.Name}"); }
    catch (Exception error) { Console.Error.WriteLine($"FAIL {test.Name}: {error}"); Environment.ExitCode = 1; }
}
Console.WriteLine($"{passed}/{cases.Length} passed");

AgentOptions Options() => new()
{
    DeviceCode = "TEST-DEVICE", StationCode = "TEST-STATION", PlatformBaseUrl = "http://127.0.0.1:33601", AllowLoopbackPlatform = true,
    PrinterName = "FAKE-PRINTER", PrinterCode = "TEST_LABEL", AllowedPrintFormats = ["TSPL"]
};
WeightReading Reading() => SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes("SN  4.850kg\r\n"));
CaptureRequest Request(WeightReading reading) => new(Guid.NewGuid().ToString("N"), reading.SampleId, "task1", "item1", "operator", "work1", "0", 0);
TaskBatch Batch() => new(DateTimeOffset.UtcNow.AddHours(1), [new("task1", "TASK001", "0",
    [new("item1", "生菜", "散装", "kg", "5.000", "0", "NON_STANDARD", true, 0)], "work1", "0", DateTimeOffset.UtcNow.AddHours(1), "TEST-DEVICE")]);
(StateStore Store, ScaleState State, CaptureService Capture) Context()
{
    var store = new StateStore(Path.Combine(testRoot, Guid.NewGuid().ToString("N")));
    store.CacheTasks("operator", Batch()); var state = new ScaleState();
    return (store, state, new CaptureService(Options(), state, store));
}
HttpClient Client(StateStore store, Func<HttpRequestMessage, Task<HttpResponseMessage>> handler)
{
    store.Setting("credential", StateStore.Protect(new DeviceCredential("test-only-token", DateTimeOffset.UtcNow.AddHours(1))));
    return new HttpClient(new FakeHandler(handler));
}
HttpClient PrintClient(StateStore store) => Client(store, _ => Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
{ Content = new ByteArrayContent(PrintBytes()) }));
byte[] PrintBytes() => Encoding.ASCII.GetBytes("SIZE 60 mm,40 mm\r\nCLS\r\nPRINT 1\r\n");
PrintJob Job() => new("job1", "attempt1", "source1", "TEST-DEVICE", "TEST_LABEL", "TSPL", "/content",
    "sha256:" + Convert.ToHexString(SHA256.HashData(PrintBytes())), 1, "lease1", DateTimeOffset.UtcNow.AddMinutes(2), DateTimeOffset.UtcNow.AddHours(1));
SyncWorker Worker(StateStore store, PlatformClient platform)
{
    var printer = new FakePrinter(); var options = Options();
    return new SyncWorker(options, store, platform, new ScaleState(), printer,
        new PrintService(options, store, platform, printer), NullLogger<SyncWorker>.Instance);
}
void Due(StateStore store)
{
    using var db = new SqliteConnection($"Data Source={Path.Combine(store.DirectoryPath, "agent.db")}"); db.Open();
    using var command = db.CreateCommand(); command.CommandText = "UPDATE events SET next_attempt='1970-01-01T00:00:00.0000000+00:00'"; command.ExecuteNonQuery();
}
static HttpResponseMessage Json(object data) => new(HttpStatusCode.OK)
{ Content = new StringContent(WireJson.Encode(new { code = "OK", data }), Encoding.UTF8, "application/json") };
static Task Sync(Action action) { action(); return Task.CompletedTask; }
static void Equal<T>(T expected, T actual)
{ if (!EqualityComparer<T>.Default.Equals(expected, actual)) throw new Exception($"Expected {expected}, actual {actual}"); }
static void Throws(string code, Action action)
{
    try { action(); } catch (AgentFault fault) when (fault.Code == code) { return; }
    throw new Exception($"Expected AgentFault {code}");
}

/// <summary>只在隔离回归中计数，绝不调用现场打印机。</summary>
internal sealed class FakePrinter : IRawPrinter
{
    internal int Calls { get; private set; }
    internal bool Fail { get; init; }
    public bool IsAvailable(string printerName) => true;
    public string Submit(string printerName, string documentName, byte[] bytes)
    { Calls++; if (Fail) throw new IOException("test interrupted output"); return "321"; }
}
/// <summary>只拦截测试中的 HTTP 请求，不访问正式平台。</summary>
internal sealed class FakeHandler(Func<HttpRequestMessage, Task<HttpResponseMessage>> handler) : HttpMessageHandler
{
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) => handler(request);
}
