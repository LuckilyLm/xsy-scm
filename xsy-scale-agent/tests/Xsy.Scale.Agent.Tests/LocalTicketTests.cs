using System.Text;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Logging.Abstractions;
using Xsy.Scale.Agent;

/// <summary>本地凭条的事务、幂等、恢复与隔离验证；不访问真实驱动。</summary>
internal static class LocalTicketTests
{
    /// <summary>返回隔离本地用例。</summary>
    /// <param name="root">非空项目临时目录，每例创建独立SQLite库。</param>
    /// <returns>可独立运行的用例集合，失败抛异常。</returns>
    internal static (string Name, Func<Task> Run)[] Cases(string root) =>
    [
        ("新票冻结溯源链接跨重启补打不采用后来配置", () => Run(() =>
        {
            var c = new Context(root);
            c.Policy.TraceUrlTemplate = "https://trace.example.invalid/t/{eventKey}";
            var record = c.Record();
            var first = c.Store.TakeLocalTicket()!;
            c.Store.FinishLocalTicket(first, new("TEST_ONLY"));
            Equal(record.EventKey, first.Snapshot.TraceReference);
            Equal(TraceLink.Build(c.Policy.TraceUrlTemplate, record.EventKey), first.Snapshot.TraceUrl);
            c.Policy.TraceUrlTemplate = "https://new.example.invalid/{eventKey}";
            var next = c.Workstation.Reprint(c.Scope, new(Guid.NewGuid().ToString("N"), record.EventKey, "核对后补打"));
            Equal(first.Snapshot, next.Snapshot);
            var reopened = new StateStore(c.Store.DirectoryPath);
            Equal(first.Snapshot, reopened.LocalTickets(c.Scope).First().Snapshot);
            Equal(1, c.Baskets.Summary(c.Scope).BasketCount);
        })),
        ("旧任务缓存兼容可空身份且大ID不变为数字", () => Run(() =>
        {
            const string json = """{"taskItemId":"9007199254740994","productName":"生菜","specName":"散装","saleUnit":"kg","plannedQuantity":"10","sortedQuantity":"0","productType":"NON_STANDARD","active":true,"taskItemVersion":0}""";
            var old = WireJson.Decode<TaskItem>(json);
            Equal<string?>(null, old.CustomerId); Equal<string?>(null, old.SkuId);
            var current = old with { SkuId = "9007199254740995", CustomerId = "9007199254740997" };
            Equal(current, WireJson.Decode<TaskItem>(WireJson.Encode(current)));
        })),
        ("真实两位小数帧按原净重保存并冻结标签不按50克取整", () => Run(() =>
        {
            foreach (string weight in new[] { "8.55", "8.84", "8.86", "1.01" })
            {
                var c = new Context(root, $"sn000{weight}kg\r\n");
                var record = c.Record(); var ticket = c.Store.LocalTickets(c.Scope).Single();
                Equal(weight, record.Measurement.RawValue); Equal(weight, record.Measurement.NetWeight);
                Equal(weight, ticket.Snapshot.NetWeight); Equal("IDENTITY", record.Measurement.ConversionRule);
                // 重启读取及标签实际渲染都使用冻结值，不能只验证页面按钮已放开。
                var reopened = new StateStore(c.Store.DirectoryPath);
                Equal(weight, reopened.LocalTickets(c.Scope).Single().Snapshot.NetWeight);
                using var preview = TicketLayout.Preview(ticket);
                Equal(480, preview.Width); Equal(320, preview.Height);
                Equal("TEST_ONLY", new ExerciseTicketPrinter().Submit(ticket).State);
            }
        })),
        ("本地凭条与筐及命令原子回滚", () => Run(() =>
        {
            var c = new Context(root);
            c.Sql("CREATE TRIGGER refuse_ticket BEFORE INSERT ON local_tickets BEGIN SELECT RAISE(ABORT,'test interruption'); END;");
            try { c.Record(); throw new Exception("Expected rollback"); } catch (SqliteException) { }
            Equal(0, c.Baskets.Summary(c.Scope).BasketCount); Equal(0, c.Baskets.Queue(c.Scope).Length);
            Equal(0, c.Store.LocalTickets(c.Scope).Length);
            c.Sql("DROP TRIGGER refuse_ticket;"); c.Record(); Equal(1, c.Store.LocalTickets(c.Scope).Length);
        })),
        ("同保存操作只生成一张冻结凭条", () => Run(() =>
        {
            var c = new Context(root); var request = c.Request();
            var first = c.Baskets.Record(request, "operator"); var second = c.Baskets.Record(request, "operator");
            Equal(first.EventKey, second.EventKey); Equal(1, c.Store.LocalTickets(c.Scope).Length);
            var ticket = c.Store.LocalTickets(c.Scope).Single(); Equal(first.Measurement.NetWeight, ticket.Snapshot.NetWeight);
            Equal("测试客户", ticket.Snapshot.CustomerName); Equal("QUEUED", second.PrintState);
            Equal("WAITING_CONTRACT", second.QueueState);
            Equal("TEST_ONLY", new ExerciseTicketPrinter().Submit(ticket).State);
            Throws("FIELD_PRINT_BLOCKED", () => new ExerciseTicketPrinter().Submit(ticket with { Snapshot = ticket.Snapshot with { TestOnly = false } }));
            Throws("TEST_PRINT_BLOCKED", () => new DriverTicketPrinter().Submit(ticket));
        })),
        ("打印中断跨重启只转未知不自动重打", () => Run(() =>
        {
            var c = new Context(root); c.Record(); var attempt = c.Store.TakeLocalTicket()!;
            Equal("SUBMITTING", attempt.State);
            var restored = new StateStore(c.Store.DirectoryPath);
            Equal("UNKNOWN", restored.LocalTickets(c.Scope).Single().State);
            Equal("UNKNOWN", restored.BasketRecords(c.Scope, 0, 50).Items.Single().PrintState);
            Equal<LocalTicket?>(null, restored.TakeLocalTicket());
        })),
        ("补打追加尝试且不增加重量旧回执不覆盖新状态", () => Run(() =>
        {
            var c = new Context(root); var basket = c.Record(); var first = c.Store.TakeLocalTicket()!;
            c.Store.FinishLocalTicket(first, new("UNKNOWN"), "PRINT_RESULT_UNKNOWN");
            var request = new ReprintRequest("reprint-1", basket.EventKey, "核对后补打");
            var next = c.Store.ReprintLocalTicket(c.Scope, request, "NEW-PRINTER", 80, 200);
            Equal(next.AttemptId, c.Store.ReprintLocalTicket(c.Scope, request).AttemptId);
            Equal(first.Snapshot, next.Snapshot); Equal("NEW-PRINTER", next.PrinterName); Equal(80, next.PaperWidthMm);
            Equal(1, c.Baskets.Summary(c.Scope).BasketCount); Equal(5m, decimal.Parse(c.Baskets.Summary(c.Scope).TotalQuantity));
            c.Store.FinishLocalTicket(first, new("SUBMITTED_TO_SPOOLER"));
            Equal("QUEUED", c.Store.BasketRecords(c.Scope, 0, 50).Items.Single().PrintState);
            var running = c.Store.TakeLocalTicket()!; c.Store.FinishLocalTicket(running, new("TEST_ONLY"));
            Equal("TEST_ONLY", c.Store.LocalTickets(c.Scope).Last().State);
            Equal<LocalTicket?>(null, c.Store.TakeLocalTicket());
            Throws("OPERATION_CONFLICT", () => c.Store.ReprintLocalTicket(c.Scope, request with { Reason = "改变内容" }));
        })),
        ("尚有打印执行时拒绝重复补打", () => Run(() =>
        {
            var c = new Context(root); var basket = c.Record();
            Throws("PRINT_IN_PROGRESS", () => c.Workstation.Reprint(c.Scope, new("repeat", basket.EventKey, "重印")));
            var taken = c.Store.TakeLocalTicket()!;
            Throws("PRINT_IN_PROGRESS", () => c.Workstation.Reprint(c.Scope, new("repeat", basket.EventKey, "重印")));
            c.Store.FinishLocalTicket(taken, new("FAILED"), "PRINTER_UNAVAILABLE");
            Equal("QUEUED", c.Workstation.Reprint(c.Scope, new("repeat", basket.EventKey, "重印")).State);
        })),
        ("补打操作号不能被旧采集和新采集复用", () => Run(() =>
        {
            var c = new Context(root); var basket = c.Record(); var taken = c.Store.TakeLocalTicket()!;
            c.Store.FinishLocalTicket(taken, new("TEST_ONLY"));
            var request = new ReprintRequest("reprint-used", basket.EventKey, "破损"); c.Workstation.Reprint(c.Scope, request);
            Throws("OPERATION_CONFLICT", () => c.Baskets.Record(c.Request() with { OperationId = request.OperationId }, "operator"));
            Throws("OPERATION_CONFLICT", () => c.Captures.Capture(c.Request() with { OperationId = request.OperationId }));
        })),
        ("补打要求有效员工授权并隔离历史范围", () => Run(() =>
        {
            var c = new Context(root); var basket = c.Record(); var taken = c.Store.TakeLocalTicket()!;
            c.Store.FinishLocalTicket(taken, new("TEST_ONLY"));
            Throws("TASK_ITEM_NOT_VISIBLE", () => c.Workstation.Tickets(c.Scope with { OperatorSessionId = "another" }));
            c.Store.CacheTasks("operator", c.Batch() with { OperatorSessionExpiresAt = DateTimeOffset.UtcNow.AddMinutes(-1) });
            Equal(1, c.Workstation.Tickets(c.Scope).Length);
            Throws("OPERATOR_SESSION_EXPIRED", () => c.Workstation.Reprint(c.Scope, new("retry", basket.EventKey, "破损")));
        })),
        ("现场演练不能混用状态库维护码不泄露", () => Run(() =>
        {
            var c = new Context(root); c.Workstation.ValidateMode(); c.Record(); c.Workstation.ValidateMode();
            var field = new WorkstationService(c.Options, new(), c.Store, c.Captures, c.Baskets);
            Invalid(() => field.ValidateMode()); Throws("MAINTENANCE_REQUIRED", () => c.Workstation.RequireMaintenance("bad"));
            c.Workstation.RequireMaintenance("test-secret");
            Equal(false, WireJson.Encode(c.Workstation.Bootstrap()).Contains("test-secret", StringComparison.Ordinal));
            var old = new Context(root); old.Record(); Invalid(() => old.Workstation.ValidateMode());
        })),
        ("驱动异常队列只执行一次且保持未知", async () =>
        {
            var c = new Context(root); c.Record(); var printer = new InterruptedPrinter();
            using var worker = new LocalTicketWorker(c.Store, printer, c.Policy, NullLogger<LocalTicketWorker>.Instance);
            await worker.StartAsync(CancellationToken.None);
            for (int i = 0; i < 100 && c.Store.LocalTickets(c.Scope).Single().State != "UNKNOWN"; i++) await Task.Delay(20);
            await worker.StopAsync(CancellationToken.None);
            Equal(1, printer.Calls); Equal("UNKNOWN", c.Store.LocalTickets(c.Scope).Single().State);
            Equal<LocalTicket?>(null, c.Store.TakeLocalTicket());
        })
    ];

    /// <summary>每例拥有自己的缓存、采样和数据目录。</summary>
    private sealed class Context
    {
        internal readonly StateStore Store;
        internal readonly AgentOptions Options = new() { DeviceCode = "TEST-DEVICE", StationCode = "TEST-STATION", PrinterName = "FAKE", AllowLoopbackPlatform = true,
            PlatformBaseUrl = "http://127.0.0.1:33611" };
        internal readonly WorkstationOptions Policy = new() { LocalTicketsEnabled = true, LocalTest = true, MaintenanceCode = "test-secret" };
        internal readonly BasketScope Scope = new("operator", "work1", "task1", "item1");
        internal readonly CaptureService Captures;
        internal readonly BasketService Baskets;
        internal readonly WorkstationService Workstation;
        private readonly WeightReading sample;
        internal Context(string root, string frame = "SN  5.000kg\r\n")
        {
            sample = SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes(frame));
            Store = new(Path.Combine(root, Guid.NewGuid().ToString("N"))); Store.CacheTasks("operator", Batch());
            var state = new ScaleState(); state.Set(sample); Captures = new(Options, state, Store);
            Baskets = new(Captures, Store, Options, Policy); Workstation = new(Options, Policy, Store, Captures, Baskets);
        }
        internal TaskBatch Batch() => new(DateTimeOffset.UtcNow.AddHours(1), [new("task1", "TASK001", "0",
            [new("item1", "生菜", "散装", "kg", "10", "0", "NON_STANDARD", true, 0, "测试客户", "ORDER001")],
            "work1", "0", DateTimeOffset.UtcNow.AddHours(1), "TEST-DEVICE")]);
        internal CaptureRequest Request() => new(Guid.NewGuid().ToString("N"), sample.SampleId, "task1", "item1", "operator", "work1", "0", 0);
        internal BasketRecord Record() => Baskets.Record(Request(), "operator");
        internal void Sql(string sql)
        {
            using var db = new SqliteConnection($"Data Source={Path.Combine(Store.DirectoryPath, "agent.db")}"); db.Open();
            using var command = db.CreateCommand(); command.CommandText = sql; command.ExecuteNonQuery();
        }
    }
    /// <summary>模拟驱动已开始执行后通信中断。</summary>
    private sealed class InterruptedPrinter : ITicketPrinter
    {
        internal int Calls;
        public TicketOutput Submit(LocalTicket ticket) { Calls++; throw new IOException("test-only driver interruption"); }
    }
    private static Task Run(Action action) { action(); return Task.CompletedTask; }
    private static void Equal<T>(T expected, T actual)
    { if (!EqualityComparer<T>.Default.Equals(expected, actual)) throw new Exception($"Expected {expected}, actual {actual}"); }
    private static void Throws(string code, Action action)
    {
        try { action(); } catch (AgentFault fault) when (fault.Code == code) { return; }
        throw new Exception($"Expected AgentFault {code}");
    }
    private static void Invalid(Action action)
    { try { action(); } catch (InvalidOperationException) { return; } throw new Exception("Expected mode rejection"); }
}
