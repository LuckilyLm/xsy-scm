using System.Globalization;
using System.Text;
using Microsoft.Data.Sqlite;

namespace Xsy.Scale.Agent;

/// <summary>市斤和手输贯穿保存、打印、重试、授权与完成的本地回归，不访问真实设备。</summary>
internal static class ManualBasketTests
{
    /// <summary>提供新业务规则的可执行验收场景。</summary>
    /// <param name="root">非空项目临时目录；每个上下文新建隔离库，保留验证证据。</param>
    /// <returns>可与旧回归合并执行的测试集合。</returns>
    internal static (string Name, Func<Task> Run)[] Cases(string root) =>
    [
        ("只上传设备与手输均无打印任务，重启保留选择且共用累计完成", () => Run(() =>
        {
            var c = new Context(root); c.Set(8.55m);
            var device = c.Request() with { PrintRequested = false };
            var first = c.Service.Record(device, "operator");
            var manual = c.Manual("120.5") with { PrintRequested = false };
            var second = c.Service.RecordManual(manual, "operator");
            Equal("NOT_REQUESTED", first.PrintState); Equal("NOT_REQUESTED", second.PrintState);
            Equal(0, c.Store.LocalTickets(c.Scope).Length); Equal<LocalTicket?>(null, c.Store.TakeLocalTicket());
            var reopened = new StateStore(c.Store.DirectoryPath);
            var service = new BasketService(new CaptureService(c.Options, new ScaleState(), reopened), reopened, c.Options, c.Policy);
            Equal(first, service.Record(device, "operator")); Equal(second, service.RecordManual(manual, "operator"));
            Equal(true, service.Records(c.Scope).Items.All(r => r.PrintState == "NOT_REQUESTED"));
            Equal("129.05", service.Summary(c.Scope).TotalQuantity);
            Equal(2, service.Complete(c.Completion(), "operator").EventKeys.Length);
            Equal(true, service.Queue(c.Scope).All(q => q.State == "WAITING_CONTRACT"));
        })),
        ("同操作更改打印选择冲突，并发只上传只记一次", async () =>
        {
            var c = new Context(root); c.Set(8.55m); var device = c.Request() with { PrintRequested = false };
            await Task.WhenAll(Enumerable.Range(0, 10).Select(_ => Task.Run(() => c.Service.Record(device, "operator"))));
            Throws("OPERATION_CONFLICT", () => c.Service.Record(device with { PrintRequested = true }, "operator"));
            var manual = c.Manual("8.55") with { PrintRequested = false }; c.Service.RecordManual(manual, "operator");
            Throws("OPERATION_CONFLICT", () => c.Service.RecordManual(manual with { PrintRequested = true }, "operator"));
            Equal(2, c.Summary().BasketCount); Equal(0, c.Store.LocalTickets(c.Scope).Length);
        }),
        ("无打印机仍可只上传，两种动作混合只为要求打印的记录出票", () => Run(() =>
        {
            var c = new Context(root); c.Policy.LocalTest = false; c.Options.PrinterName = "";
            c.Set(8.55m); c.Service.Record(c.Request() with { PrintRequested = false }, "operator");
            c.Service.RecordManual(c.Manual("1") with { PrintRequested = false }, "operator");
            Throws("PRINTER_NOT_CONFIGURED", () => c.Service.RecordManual(c.Manual("1") with { PrintRequested = true }, "operator"));
            Equal(2, c.Summary().BasketCount); c.Options.PrinterName = "EXERCISE"; c.Policy.LocalTest = true;
            c.Service.RecordManual(c.Manual("2") with { PrintRequested = true }, "operator");
            Equal(1, c.Store.LocalTickets(c.Scope).Length); Equal("11.55", c.Summary().TotalQuantity);
            Equal("QUEUED", c.Store.LocalTickets(c.Scope).Single().State);
            Equal("SUBMITTING", c.Store.TakeLocalTicket()!.State);
        })),
        ("旧版本省略打印选择的请求摘要保持不变", () => Run(() =>
        {
            var c = new Context(root); c.Set(8.55m); var request = c.Request();
            var old = new { request.OperationId, request.SampleId, request.TaskId, request.TaskItemId,
                request.OperatorSessionId, request.JobId, request.JobVersion, request.TaskItemVersion };
            Equal(WireJson.Digest(old), WireJson.Digest(request));
            var manual = c.Manual("8.55");
            var oldManual = new { manual.OperationId, manual.TaskId, manual.TaskItemId, manual.OperatorSessionId,
                manual.JobId, manual.JobVersion, manual.TaskItemVersion, manual.NetWeight, manual.ConfirmedNetWeight, manual.Note };
            Equal(WireJson.Digest(oldManual), WireJson.Digest(manual));
            c.Service.Record(request, "operator"); c.Service.RecordManual(manual, "operator");
            Equal(2, c.Store.LocalTickets(c.Scope).Length);
        })),
        ("市斤别名保留8.55原重及厂家sj原帧", () => Run(() =>
        {
            foreach (string unit in new[] { "斤", "市斤", "sj" })
            {
                var c = new Context(root, unit); var row = c.Device(8.55m);
                Equal("sj", row.Measurement.RawUnit); Equal(unit, row.Measurement.BusinessUnit);
                Equal("8.55", row.Measurement.NetWeight); Equal("8.55", row.Measurement.RawValue);
                Equal("DEVICE", row.Measurement.EntrySource); Equal(true, row.Measurement.StableFlag);
                Equal("斤", c.Store.LocalTickets(c.Scope).Single().Snapshot.Unit);
            }
        })),
        ("取消固定量程后0.0001及380斤可保存，零负数仍拒绝", () => Run(() =>
        {
            var c = new Context(root);
            c.Device(.0001m); c.Device(.35m); c.Device(380m);
            Equal("380.3501", c.Summary().TotalQuantity);
            foreach (decimal weight in new[] { 0m, -1m })
                Throws("WEIGHT_NOT_POSITIVE", () => c.Device(weight));
            Equal(3, c.Summary().BasketCount);
        })),
        ("斤任务不能直接使用公斤数值", () => Run(() =>
        {
            var c = new Context(root); c.Set(8.55m, "kg");
            Throws("UNIT_MISMATCH", () => c.Service.Record(c.Request(), "operator"));
            Equal(0, c.Summary().BasketCount);
        })),
        ("实称8.55加手输120.5累计129.05，手输无伪造稳定原帧", () => Run(() =>
        {
            var c = new Context(root); c.Device(8.55m); c.State.Fail("SCALE_DISCONNECTED");
            var row = c.Service.RecordManual(c.Manual("120.5"), "operator");
            Equal("129.05", c.Summary().TotalQuantity); Equal(2, row.BasketNumber);
            Equal("MANUAL", row.Measurement.EntrySource); Equal(false, row.Measurement.StableFlag);
            Equal("NOT_APPLICABLE", row.Measurement.StabilitySource); Equal("MANUAL_ENTRY", row.Measurement.ProtocolType);
            Equal("", row.Measurement.ReadingSequence); Equal<string?>(null, row.Measurement.TareWeight);
            Equal("其他大秤称重", row.Measurement.EntryNote);
            var ticket = c.Store.LocalTickets(c.Scope).Single(t => t.Snapshot.EventKey == row.EventKey);
            Equal("MANUAL", ticket.Snapshot.EntrySource); Equal("120.5", ticket.Snapshot.NetWeight);
            using var image = TicketLayout.Preview(ticket);
            image.Save(Path.Combine(root, "manual-120.5-label.png"));
            using var db = new SqliteConnection($"Data Source={Path.Combine(c.Store.DirectoryPath, "agent.db")}"); db.Open();
            using var cmd = db.CreateCommand();cmd.CommandText = "SELECT raw_frame FROM basket_records WHERE basket_number=2";
            Equal("", (string)cmd.ExecuteScalar()!);
        })),
        ("手工重复请求幂等，改重改说明及跨来源复用编号均冲突", () => Run(() =>
        {
            var c = new Context(root); var request = c.Manual("8.55"); var first = c.Service.RecordManual(request, "operator");
            Equal(first, c.Service.RecordManual(request, "operator")); Equal(1, c.Summary().BasketCount);
            Throws("OPERATION_CONFLICT", () => c.Service.RecordManual(request with { NetWeight = "9.55" }, "operator"));
            Throws("OPERATION_CONFLICT", () => c.Service.RecordManual(request with { Note = "changed" }, "operator"));
            c.Set(8.55m);
            Throws("OPERATION_CONFLICT", () => c.Service.Record(c.Request() with { OperationId = request.OperationId }, "operator"));
            var deviceRequest = c.Request(); c.Service.Record(deviceRequest, "operator");
            Throws("OPERATION_CONFLICT", () => c.Service.RecordManual(c.Manual("8.55") with { OperationId = deviceRequest.OperationId }, "operator"));
            Equal(2, c.Summary().BasketCount); Equal(2, c.Store.LocalTickets(c.Scope).Length);
        })),
        ("并发手工确认只落一次，主动新录入相同重量仍为不同条", async () =>
        {
            var c = new Context(root); var request = c.Manual("120.50");
            var rows = await Task.WhenAll(Enumerable.Range(0, 10).Select(_ => Task.Run(() => c.Service.RecordManual(request, "operator"))));
            Equal(1, rows.Select(r => r.EventKey).Distinct().Count()); Equal(1, c.Store.LocalTickets(c.Scope).Length);
            c.Service.RecordManual(c.Manual("120.50"), "operator");
            Equal(2, c.Summary().BasketCount); Equal(241m, decimal.Parse(c.Summary().TotalQuantity, CultureInfo.InvariantCulture));
        }),
        ("人工净重必须确认且严格校验格式，不舍入非法小数", () => Run(() =>
        {
            var c = new Context(root);
            foreach (string value in new[] { "", "0", "-1", "NaN", "1e3", "1,000", "1.00001", "999999999999999", "." })
                Throws("INVALID_MANUAL_WEIGHT", () => c.Service.RecordManual(c.Manual(value), "operator"));
            Throws("NET_WEIGHT_CONFIRMATION_REQUIRED", () => c.Service.RecordManual(c.Manual("1") with { ConfirmedNetWeight = false }, "operator"));
            Throws("INVALID_MANUAL_NOTE", () => c.Service.RecordManual(c.Manual("1") with { Note = new string('字', 201) }, "operator"));
            Equal(0, c.Summary().BasketCount); Equal(0, c.Store.LocalTickets(c.Scope).Length);
            c.Service.RecordManual(c.Manual("0.0001"), "operator"); Equal("0.0001", c.Summary().TotalQuantity);
            c.Service.RecordManual(c.Manual("1") with { OperationId = new string('m',160) }, "operator");
            Equal(2, c.Summary().BasketCount);
        })),
        ("手输需员工头体一致及有效作业授权，不能绕过已完成状态", () => Run(() =>
        {
            var c = new Context(root); var request = c.Manual("120.5");
            Throws("OPERATOR_SESSION_MISMATCH", () => c.Service.RecordManual(request, "another"));
            Throws("TASK_ITEM_STALE", () => c.Service.RecordManual(request with { TaskItemVersion = 1 }, "operator"));
            c.Service.RecordManual(request, "operator");
            var done = c.Service.Complete(c.Completion(), "operator"); Equal("120.5", done.TotalQuantity);
            Equal("斤", done.Unit); Equal(1, done.EventKeys.Length);
            Throws("ITEM_LOCALLY_COMPLETED", () => c.Service.RecordManual(c.Manual("1"), "operator"));
            c.Store.CacheTasks("operator", c.Batch() with { OperatorSessionExpiresAt = DateTimeOffset.UtcNow.AddMinutes(-1) });
            Throws("OPERATOR_SESSION_EXPIRED", () => c.Service.RecordManual(request, "operator"));
            Equal(1, c.Summary().BasketCount);
        })),
        ("人工录入无网络重启恢复，补打不改重量来源及累计", () => Run(() =>
        {
            var c = new Context(root); var request = c.Manual("380.35"); var original = c.Service.RecordManual(request, "operator");
            var ticket = c.Store.TakeLocalTicket()!; c.Store.FinishLocalTicket(ticket, new("TEST_ONLY"));
            var reopened = new StateStore(c.Store.DirectoryPath);
            var capture = new CaptureService(c.Options, new ScaleState(), reopened);
            var service = new BasketService(capture, reopened, c.Options, c.Policy);
            var workstation = new WorkstationService(c.Options, c.Policy, reopened, capture, service);
            Equal(original.EventKey, service.RecordManual(request, "operator").EventKey);
            var reprint = workstation.Reprint(c.Scope, new("manual-reprint", original.EventKey, "原标签破损"));
            Equal(ticket.Snapshot, reprint.Snapshot); Equal("MANUAL", reprint.Snapshot.EntrySource);
            Equal(1, service.Summary(c.Scope).BasketCount); Equal("380.35", service.Summary(c.Scope).TotalQuantity);
            Equal(true, service.Queue(c.Scope).All(q => q.State == "WAITING_CONTRACT"));
        })),
        ("手工打印意图事务失败时没有半条记录，原操作可重试", () => Run(() =>
        {
            var c = new Context(root); var request = c.Manual("12.5");
            c.Sql("CREATE TRIGGER fail_manual_ticket BEFORE INSERT ON local_tickets BEGIN SELECT RAISE(ABORT,'test interruption'); END;");
            bool failed = false;try { c.Service.RecordManual(request, "operator"); } catch(SqliteException) { failed = true; }
            Equal(true, failed); Equal(0, c.Summary().BasketCount); Equal(0, c.Service.Queue(c.Scope).Length);
            c.Sql("DROP TRIGGER fail_manual_ticket;");
            Equal(1, c.Service.RecordManual(request, "operator").BasketNumber);
        })),
        ("手输只接受斤任务，旧票未标来源保持原快照", () => Run(() =>
        {
            var c = new Context(root, "kg");
            Throws("MANUAL_UNIT_MISMATCH", () => c.Service.RecordManual(c.Manual("8.55"), "operator"));
            const string old = """{"protocolVersion":"1","eventKey":"old","deviceCode":"old","stationCode":"old","sessionId":"old","jobId":"j","jobVersion":"0","taskId":"t","taskItemId":"i","taskItemVersion":0,"operatorSessionId":"o","capturedAt":"2026-10-20T00:00:00Z","rawValue":"8.55","rawUnit":"kg","netWeight":"8.55","businessUnit":"kg","weightKind":"NET","stableFlag":true,"stabilitySource":"DEVICE","conversionRule":"IDENTITY","tareWeight":null,"readingSequence":"1","protocolType":"SOHE_RS232_V101","agentVersion":"0.3.6"}""";
            var measurement = WireJson.Decode<Measurement>(old); Equal<string?>(null, measurement.EntrySource); Equal("kg", measurement.BusinessUnit);
        }))
    ];

    private sealed class Context
    {
        internal readonly StateStore Store;
        internal readonly ScaleState State = new();
        internal readonly AgentOptions Options = new() { DeviceCode = "TEST-HW-01", StationCode = "TEST-STATION", PrinterName = "EXERCISE" };
        internal readonly WorkstationOptions Policy = new() { LocalTest = true, LocalTicketsEnabled = true };
        internal readonly BasketService Service;
        internal readonly BasketScope Scope = new("operator", "job", "task", "item");
        private readonly string unit;
        private WeightReading? sample;
        internal Context(string root, string unit = "斤")
        {
            this.unit = unit; Store = new StateStore(Path.Combine(root, Guid.NewGuid().ToString("N")));
            Store.CacheTasks("operator", Batch());
            Service = new BasketService(new CaptureService(Options, State, Store), Store, Options, Policy);
        }
        internal TaskBatch Batch() => new(DateTimeOffset.UtcNow.AddHours(1), [new("task", "ORDER001", "0",
            [new("item", "生菜", "散装", unit, "200", "0", "NON_STANDARD", true, 0, "测试客户", "ORDER001")],
            "job", "0", DateTimeOffset.UtcNow.AddHours(1), Options.DeviceCode)]);
        internal void Set(decimal weight, string raw = "sj")
        {
            sample = SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes("sn0008.55sj\r\n")) with { RawWeight = weight, RawUnit = raw };
            State.Set(sample);
        }
        internal CaptureRequest Request() => new(Guid.NewGuid().ToString("N"), sample!.SampleId, "task", "item", "operator", "job", "0", 0);
        internal ManualBasketRequest Manual(string weight) => new(Guid.NewGuid().ToString("N"), "task", "item", "operator", "job", "0", 0, weight, true);
        internal BasketRecord Device(decimal weight) { Set(weight);return Service.Record(Request(), "operator"); }
        internal BasketSummary Summary() => Service.Summary(Scope);
        internal CompleteItemRequest Completion() => new(Guid.NewGuid().ToString("N"), "task", "item", "operator", "job", "0", 0, Summary().ManifestHash, "NORMAL", null);
        internal void Sql(string sql)
        {
            using var db = new SqliteConnection($"Data Source={Path.Combine(Store.DirectoryPath, "agent.db")}");db.Open();
            using var cmd = db.CreateCommand();cmd.CommandText = sql;cmd.ExecuteNonQuery();
        }
    }
    private static Task Run(Action action) { action();return Task.CompletedTask; }
    private static void Equal<T>(T expected,T actual)
    { if(!EqualityComparer<T>.Default.Equals(expected,actual))throw new Exception($"Expected {expected}, actual {actual}"); }
    private static void Throws(string code, Action action)
    { try { action(); } catch(AgentFault fault) when(fault.Code == code) { return; }throw new Exception($"Expected {code}"); }
}
