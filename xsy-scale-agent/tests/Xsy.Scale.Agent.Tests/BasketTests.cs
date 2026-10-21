using System.Globalization;
using System.Text;
using Microsoft.Data.Sqlite;
using Xsy.Scale.Agent;

/// <summary>验证多筐事务、累计、隔离及重启恢复；只使用临时库和内存中的设备采样。</summary>
internal static class BasketTests
{
    /// <summary>提供与既有回归入口共同运行的多筐验收场景。</summary>
    /// <param name="root">项目.codex-tmp下的非空测试目录，每个场景使用独立子目录。</param>
    /// <returns>无现场设备或真实网络依赖的测试集合。</returns>
    internal static (string Name, Func<Task> Run)[] Cases(string root) =>
    [
        ("多筐5加4累计9，完成不再取重或打印", () => Run(() =>
        {
            var c = new Context(root); var first = c.Record(5m); var second = c.Record(4m);
            Equal(9m, decimal.Parse(c.Summary().TotalQuantity, CultureInfo.InvariantCulture));
            Equal(2, c.Summary().BasketCount); Equal(1, first.BasketNumber); Equal(2, second.BasketNumber);
            var request = c.Completion() with { Result = "SHORT", Reason = "实物短少1kg" };
            c.State.Fail("SCALE_DISCONNECTED");
            ItemCompletion done = c.Service.Complete(request, "operator");
            Equal(9m, decimal.Parse(done.TotalQuantity, CultureInfo.InvariantCulture));
            Equal(2, done.EventKeys.Length); Equal(true, c.Summary().LocallyCompleted);
            Equal(WireJson.Encode(done), WireJson.Encode(c.Service.Complete(request, "operator")));
            Equal(3, c.Service.Queue(c.Scope).Length);
            Equal("COMPLETE_ITEM", c.Service.Queue(c.Scope)[2].Kind);
            Equal(string.Join(',', done.EventKeys), string.Join(',', c.Service.Queue(c.Scope)[2].DependsOnEventKeys));
            Equal(0, c.Store.Prints().Length);
            c.Set(3m);
            Throws("ITEM_LOCALLY_COMPLETED", () => c.Service.Record(c.Request(), "operator"));
            Throws("ITEM_LOCALLY_COMPLETED", () => c.Service.Complete(request with { OperationId = Guid.NewGuid().ToString("N") }, "operator"));
            Throws("OPERATION_CONFLICT", () => c.Service.Complete(request with { Reason = "更改原完成原因" }, "operator"));
        })),
        ("多筐同操作重试断线仍返回原记录", () => Run(() =>
        {
            var c = new Context(root); c.Set(5m); var request = c.Request();
            var first = c.Service.Record(request, "operator"); c.State.Fail("SCALE_DISCONNECTED");
            Equal(first, c.Service.Record(request, "operator")); Equal(1, c.Summary().BasketCount);
            Throws("OPERATION_CONFLICT", () => c.Service.Record(request with { SampleId = "changed" }, "operator"));
        })),
        ("相同采样新操作拒绝，相同重量新采样保留两筐", () => Run(() =>
        {
            var c = new Context(root); c.Set(4m); c.Service.Record(c.Request(), "operator");
            Throws("SAMPLE_ALREADY_RECORDED", () => c.Service.Record(c.Request(), "operator"));
            c.Record(4m); Equal(2, c.Summary().BasketCount); Equal("8", c.Summary().TotalQuantity);
        })),
        ("旧摘要不能完成并发新增后的商品集合", () => Run(() =>
        {
            var c = new Context(root); c.Record(5m); var request = c.Completion(); c.Record(4m);
            Throws("BASKET_MANIFEST_CHANGED", () => c.Service.Complete(request, "operator"));
            Equal(false, c.Summary().LocallyCompleted); Equal(2, c.Service.Queue(c.Scope).Length);
            Equal(2, c.Service.Complete(c.Completion(), "operator").EventKeys.Length);
        })),
        ("筐和队列事务中断均不留下半条记录", () => Run(() =>
        {
            var c = new Context(root); c.Set(5m); var request = c.Request();
            c.Sql("CREATE TRIGGER fail_command BEFORE INSERT ON basket_commands BEGIN SELECT RAISE(ABORT,'test-only interruption'); END;");
            bool interrupted = false;
            try { c.Service.Record(request, "operator"); } catch (SqliteException) { interrupted = true; }
            Equal(true, interrupted); Equal(0, c.Summary().BasketCount); Equal(0, c.Service.Queue(c.Scope).Length);
            c.Sql("DROP TRIGGER fail_command;");
            Equal(1, c.Service.Record(request, "operator").BasketNumber);
            Equal(1, c.Service.Queue(c.Scope).Length);
        })),
        ("并发重复点击只有一筐一个打印意图", async () =>
        {
            var c = new Context(root); c.Set(5m); var request = c.Request();
            var records = await Task.WhenAll(Enumerable.Range(0, 12).Select(_ => Task.Run(() => c.Service.Record(request, "operator"))));
            Equal(1, records.Select(r => r.EventKey).Distinct().Count());
            Equal(1, records.Select(r => r.PrintIntentId).Distinct().Count()); Equal(1, c.Summary().BasketCount);
        }),
        ("离线队列及完成跨重启保留原事实和依赖", () => Run(() =>
        {
            var c = new Context(root); var first = c.Record(5m); c.Record(4m);
            var request = c.Completion(); var done = c.Service.Complete(request, "operator");
            var reopened = new StateStore(c.Store.DirectoryPath);
            var service = new BasketService(new CaptureService(c.Options, new ScaleState(), reopened), reopened, c.Options);
            var summary = service.Summary(c.Scope);
            Equal("9", summary.TotalQuantity); Equal(true, summary.LocallyCompleted);
            Equal(done.CompletedAt, summary.Completion!.CompletedAt);
            Equal(first.EventKey, service.Records(c.Scope).Items[0].EventKey);
            Equal(first.Measurement.SessionId, service.Records(c.Scope).Items[0].Measurement.SessionId);
            Equal(3, service.Queue(c.Scope).Length);
            Equal(true, service.Queue(c.Scope).All(q => q.State == "WAITING_CONTRACT"));
            Equal(first.PrintIntentId, service.Records(c.Scope).Items[0].PrintIntentId);
            Equal("WAITING_AUTHORIZATION", service.Records(c.Scope).Items[0].PrintState);
            Equal(done.OperationId, service.Complete(request, "operator").OperationId);
            Equal<LocalEvent?>(null, reopened.NextEvent());
        })),
        ("多筐与旧单次队列双向隔离", () => Run(() =>
        {
            var c = new Context(root); c.Record(5m); c.Set(4m);
            Throws("BASKET_WORKFLOW_REQUIRED", () => c.Captures.Capture(c.Request()));
            Equal<LocalEvent?>(null, c.Store.NextEvent()); Equal(0L, c.Store.QueueDepth());
            var legacy = new Context(root); legacy.Set(5m); var old = legacy.Captures.Capture(legacy.Request());
            legacy.Set(4m);
            Throws("LEGACY_CAPTURE_EXISTS", () => legacy.Service.Record(legacy.Request(), "operator"));
            var reopened = new StateStore(legacy.Store.DirectoryPath);
            Equal(old.EventKey, reopened.Events().Single().EventKey);
            Equal(0, legacy.Summary().BasketCount);
        })),
        ("超过200筐仍全量累计并按游标分页", () => Run(() =>
        {
            var c = new Context(root);
            for (int i = 0; i < 205; i++) c.Record(1.05m);
            Equal(205, c.Summary().BasketCount); Equal("215.25", c.Summary().TotalQuantity);
            int count = 0, after = 0;
            do
            {
                var page = c.Service.Records(c.Scope, after, 100); count += page.Items.Length;
                after = page.NextAfter ?? 0;
            } while (after != 0);
            Equal(205, count);
            var done = c.Service.Complete(c.Completion(), "operator");
            Equal(205, done.EventKeys.Length); Equal("215.25", done.TotalQuantity);
            Equal(0, c.Service.Records(c.Scope, 205).Items.Length);
        })),
        ("员工头体一致且历史按会话范围隔离", () => Run(() =>
        {
            var c = new Context(root); c.Set(5m);
            Throws("OPERATOR_SESSION_MISMATCH", () => c.Service.Record(c.Request(), "another"));
            c.Record(5m); c.Store.CacheTasks("another", c.Batch());
            var other = c.Scope with { OperatorSessionId = "another" };
            Equal(0, c.Service.Records(other).Items.Length); Equal(0, c.Service.Queue(other).Length);
            Throws("TASK_ITEM_NOT_VISIBLE", () => c.Service.Summary(c.Scope with { TaskItemId = "unseen" }));
        })),
        ("授权过期可查询但不新增或完成", () => Run(() =>
        {
            var c = new Context(root); c.Record(5m); var finish = c.Completion();
            c.Store.CacheTasks("operator", c.Batch() with { OperatorSessionExpiresAt = DateTimeOffset.UtcNow.AddMinutes(-1) });
            Equal(true, c.Summary().AuthorizationExpired); Equal(1, c.Service.Records(c.Scope).Items.Length);
            c.Set(4m); Throws("OPERATOR_SESSION_EXPIRED", () => c.Service.Record(c.Request(), "operator"));
            Throws("OPERATOR_SESSION_EXPIRED", () => c.Service.Complete(finish, "operator"));
        })),
        ("任务新版本不能混入原多筐集合", () => Run(() =>
        {
            var c = new Context(root); c.Record(5m); var batch = c.Batch();
            c.Store.CacheTasks("operator", batch with { Tasks = [batch.Tasks[0] with
                { Items = [batch.Tasks[0].Items[0] with { TaskItemVersion = 1 }] }] });
            c.Set(4m);
            Throws("BASKET_CONTEXT_CHANGED", () => c.Service.Record(c.Request() with { TaskItemVersion = 1 }, "operator"));
            Throws("BASKET_CONTEXT_CHANGED", () => c.Service.Complete(c.Completion() with { TaskItemVersion = 1 }, "operator"));
            Equal(1, c.Summary().BasketCount);
        })),
        ("完成参数校验和空商品不落队列", () => Run(() =>
        {
            var c = new Context(root); var finish = c.Completion();
            Throws("NO_BASKETS", () => c.Service.Complete(finish, "operator"));
            c.Record(5m); finish = c.Completion();
            Throws("INVALID_MANIFEST", () => c.Service.Complete(finish with { ManifestHash = "bad" }, "operator"));
            Throws("INVALID_SORTING_RESULT", () => c.Service.Complete(finish with { Result = "invented" }, "operator"));
            Throws("SORTING_REASON_REQUIRED", () => c.Service.Complete(finish with { Result = "SHORT", Reason = " " }, "operator"));
            Throws("INVALID_PAGINATION", () => c.Service.Records(c.Scope, -1));
            Throws("INVALID_PAGINATION", () => c.Service.Queue(c.Scope, limit: 101));
            Equal(1, c.Service.Queue(c.Scope).Length);
        })),
        ("多筐命令按序派发，筐未受理不提交完成", () => Run(() =>
        {
            var c = new Context(root);
            c.Record(8.84m); c.Record(8.86m);
            c.Service.Complete(c.Completion(), "operator");
            PendingBasketCommand[] pending = c.Store.PendingBasketCommands(10);
            Equal(3, pending.Length);
            Equal("CAPTURE_BASKET", pending[0].Kind);
            Equal("CAPTURE_BASKET", pending[1].Kind);
            Equal("COMPLETE_ITEM", pending[2].Kind);
            // 完成命令必须等全部筐落地，否则平台按不完整集合求和。
            Equal(true, c.Store.HasUnsentCaptures(pending[2].ScopeKey));
            c.Store.SetBasketCommandState(pending[0].Sequence, BasketCommandState.Sent);
            Equal(true, c.Store.HasUnsentCaptures(pending[2].ScopeKey));
            c.Store.SetBasketCommandState(pending[1].Sequence, BasketCommandState.Sent);
            Equal(false, c.Store.HasUnsentCaptures(pending[2].ScopeKey));
            Equal(1, c.Store.PendingBasketCommands(10).Length);
            Equal("COMPLETE_ITEM", c.Store.PendingBasketCommands(10)[0].Kind);
        })),
        ("已发送命令不回退，晚到失败不覆盖成功事实", () => Run(() =>
        {
            var c = new Context(root);
            c.Record(5m);
            PendingBasketCommand first = c.Store.PendingBasketCommands(10)[0];
            c.Store.SetBasketCommandState(first.Sequence, BasketCommandState.Sent);
            c.Store.SetBasketCommandState(first.Sequence, BasketCommandState.Retry);
            Equal(0, c.Store.PendingBasketCommands(10).Length);
            Equal("SENT", c.Service.Queue(c.Scope)[0].State);
        })),
        ("平台拒绝时同范围命令转人工且不再自动重试", () => Run(() =>
        {
            var c = new Context(root);
            c.Record(5m); c.Record(4m);
            c.Service.Complete(c.Completion(), "operator");
            PendingBasketCommand[] pending = c.Store.PendingBasketCommands(10);
            c.Store.ParkBasketCommands(pending[0].ScopeKey);
            Equal(0, c.Store.PendingBasketCommands(10).Length);
            Equal(0, c.Store.PendingBasketCommandCount());
            Equal("NEEDS_REVIEW", c.Service.Queue(c.Scope)[0].State);
            Equal("NEEDS_REVIEW", c.Service.Queue(c.Scope)[2].State);
        })),
        ("派发请求不重算重量，累计由平台按筐复核", () => Run(() =>
        {
            var c = new Context(root);
            BasketRecord first = c.Record(8.84m);
            PlatformBasketUpload upload = PlatformBasketUpload.From(first.Measurement, first.OperationId, first.BasketNumber);
            Equal(1, upload.BasketNo);
            Equal(first.Measurement.NetWeight, upload.NetWeight);
            Equal(first.EventKey, upload.EventKey);
            Equal("kg", upload.BusinessUnit);
            var completion = c.Completion() with { Result = "SHORT", Reason = "实物短少1.16kg" };
            c.Service.Complete(completion, "operator");
            PlatformItemCompletion request = PlatformItemCompletion.From(
                c.Summary().Completion!, c.Summary().ManifestHash);
            Equal(completion.ManifestHash, request.ManifestHash);
            Equal("SHORT", request.Result);
            Equal("实物短少1.16kg", request.Reason);
        }))
    ];

    /// <summary>每个场景独占临时库和内存采样，避免测试间授权、队列或设备状态串扰。</summary>
    private sealed class Context
    {
        internal readonly StateStore Store;
        internal readonly ScaleState State = new();
        internal readonly AgentOptions Options = new() { DeviceCode = "TEST-DEVICE", StationCode = "TEST-STATION" };
        internal readonly CaptureService Captures;
        internal readonly BasketService Service;
        internal readonly BasketScope Scope = new("operator", "work1", "task1", "item1");
        private WeightReading? sample;

        internal Context(string root)
        {
            Store = new StateStore(Path.Combine(root, Guid.NewGuid().ToString("N")));
            Store.CacheTasks("operator", Batch());
            Captures = new CaptureService(Options, State, Store);
            Service = new BasketService(Captures, Store, Options);
        }

        internal TaskBatch Batch() => new(DateTimeOffset.UtcNow.AddHours(1), [new("task1", "TASK001", "0",
            [new("item1", "生菜", "散装", "kg", "10", "0", "NON_STANDARD", true, 0)], "work1", "0",
            DateTimeOffset.UtcNow.AddHours(1), "TEST-DEVICE")]);
        internal void Set(decimal weight)
        {
            sample = SoheFrameDecoder.ParseRs232(Encoding.ASCII.GetBytes("SN  5.000kg\r\n")) with { RawWeight = weight };
            State.Set(sample);
        }
        internal CaptureRequest Request() => new(Guid.NewGuid().ToString("N"), sample!.SampleId, "task1", "item1", "operator", "work1", "0", 0);
        internal BasketRecord Record(decimal weight) { Set(weight); return Service.Record(Request(), "operator"); }
        internal BasketSummary Summary() => Service.Summary(Scope);
        internal CompleteItemRequest Completion() => new(Guid.NewGuid().ToString("N"), "task1", "item1", "operator",
            "work1", "0", 0, Summary().ManifestHash, "NORMAL", null);
        internal void Sql(string sql)
        {
            using var db = new SqliteConnection($"Data Source={Path.Combine(Store.DirectoryPath, "agent.db")}"); db.Open();
            using var command = db.CreateCommand(); command.CommandText = sql; command.ExecuteNonQuery();
        }
    }

    private static Task Run(Action action) { action(); return Task.CompletedTask; }
    private static void Equal<T>(T expected, T actual)
    { if (!EqualityComparer<T>.Default.Equals(expected, actual)) throw new Exception($"Expected {expected}, actual {actual}"); }
    private static void Throws(string code, Action action)
    {
        try { action(); } catch (AgentFault fault) when (fault.Code == code) { return; }
        throw new Exception($"Expected AgentFault {code}");
    }
}
