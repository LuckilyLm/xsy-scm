using System.Drawing;
using System.Drawing.Imaging;
using Xsy.Scale.Agent;

/// <summary>实际GDI渲染与模式边界回归；不打开串口、不向任何打印队列发纸。</summary>
internal static class TicketLayoutTests
{
    /// <summary>提供短标签、溢出预检与硬件模式隔离的自动验证。</summary>
    /// <param name="root">非空临时目录，用于预览图和独立状态库。</param>
    /// <returns>失败时抛异常的本地用例列表。</returns>
    internal static (string Name, Func<Task> Run)[] Cases(string root) =>
    [
        ("新溯源标签待配置与二维码及补打完整渲染", () => Run(() =>
        {
            var original = Sample();
            string url = TraceLink.Build("https://trace.example.invalid/t/{eventKey}", original.Snapshot.EventKey)!;
            foreach (var (name, link, reprint) in new[] { ("trace-pending", (string?)null, false),
                ("trace-configured", (string?)url, false), ("trace-reprint", (string?)url, true) })
            {
                var ticket = original with { Reprint = reprint, Snapshot = original.Snapshot with {
                    TestOnly = true, TemplateVersion = "HARDWARE_TEST_6040_TRACE_V2",
                    TraceReference = original.Snapshot.EventKey, TraceUrl = link } };
                using var image = TicketLayout.Preview(ticket);
                image.Save(Path.Combine(root, name + ".png"), ImageFormat.Png);
            }
            File.WriteAllText(Path.Combine(root, "trace-expected.txt"), url);
        })),
        ("溯源地址空值及编码固定主机和短标签容量校验", () => Run(() =>
        {
            if (TraceLink.Build("  ", "local:1") != null) throw new Exception("空地址不能生成查询链接");
            string link = TraceLink.Build("https://trace.example.invalid/t/{eventKey}", "本地:1/a?b")!;
            if (!link.EndsWith(Uri.EscapeDataString("本地:1/a?b"), StringComparison.Ordinal)) throw new Exception("记录号未按URL组件编码");
            foreach (string bad in new[] { "http://example.invalid/{eventKey}", "https://{eventKey}.example.invalid/",
                "https://example.invalid/no-placeholder", "https://example.invalid/{eventKey}/{eventKey}",
                "https://user:secret@example.invalid/{eventKey}", "https://example.invalid/#{eventKey}" })
                Throws("INVALID_TRACE_TEMPLATE", () => TraceLink.Build(bad, "local:1"));
            Throws("TRACE_URL_TOO_LONG", () => TraceLink.Build("https://example.invalid/" + new string('a', 170) + "/{eventKey}", Sample().Snapshot.EventKey));
            Throws("TICKET_TOO_LONG", () => TicketLayout.Preview(Sample() with { Snapshot = Sample().Snapshot with {
                TemplateVersion = "LOCAL_PENDING_TRACE_V2", CustomerName = new string('菜', 500) } }));
        })),
        ("60×40正式与补打及硬件样张完整渲染", () => Run(() =>
        {
            foreach (var (name, ticket) in new[] { ("label-field", Sample()), ("label-reprint", Sample() with { Reprint = true }),
                ("label-hardware", Sample() with { Snapshot = Sample().Snapshot with { TestOnly = true, TemplateVersion = "HARDWARE_TEST_6040_V1" } }) })
            {
                using var preview = TicketLayout.Preview(ticket);
                if (preview.Width != 480 || preview.Height != 320) throw new Exception("标签像素尺寸错误");
                // 检查实绘像素仍在四边安全区内，并且重量行与底部记录行都有内容。
                int bottom = 0, left = preview.Width, right = 0, dark = 0;
                for (int y = 0; y < preview.Height; y++) for (int x = 0; x < preview.Width; x++)
                    if (preview.GetPixel(x, y).GetBrightness() < .5f) { dark++; bottom = y; left = Math.Min(left, x); right = Math.Max(right, x); }
                if (dark < 1000 || left < 14 || right > preview.Width - 14 || bottom > preview.Height - 14 || bottom < 240)
                    throw new Exception($"标签墨迹边界异常：{left},{right},{bottom},{dark}");
                preview.Save(Path.Combine(root, name + ".png"), ImageFormat.Png);
            }
        })),
        ("超长内容在接触打印队列前拒绝且不裁切", () => Run(() =>
        {
            var ticket = Sample() with { Snapshot = Sample().Snapshot with { CustomerName = new string('菜', 500) } };
            Throws("TICKET_TOO_LONG", () => new DriverTicketPrinter().Submit(ticket));
        })),
        ("旧长凭条仍可渲染且小纸拒绝溢出", () => Run(() =>
        {
            using var preview = TicketLayout.Preview(Sample() with { PaperWidthMm = 58, PaperHeightMm = 110 });
            preview.Save(Path.Combine(root, "label-long.png"), ImageFormat.Png);
            Throws("TICKET_TOO_LONG", () => TicketLayout.Preview(Sample() with { PaperWidthMm = 40, PaperHeightMm = 40 }));
        })),
        ("硬件打印器拒绝正式及普通演练凭条", () => Run(() =>
        {
            var printer = new HardwareTestTicketPrinter();
            Throws("HARDWARE_PRINT_BLOCKED", () => printer.Submit(Sample()));
            Throws("HARDWARE_PRINT_BLOCKED", () => printer.Submit(Sample() with { Snapshot = Sample().Snapshot with { TestOnly = true } }));
            Throws("TEST_PRINT_BLOCKED", () => new DriverTicketPrinter().Submit(Sample() with { Snapshot = Sample().Snapshot with { TestOnly = true } }));
        })),
        ("硬件测试与现场演练状态目录互斥", () => Run(() =>
        {
            var store = new StateStore(Path.Combine(root, Guid.NewGuid().ToString("N")));
            var options = new AgentOptions { DeviceCode = "TEST-HW-01", AllowLoopbackPlatform = true,
                PlatformBaseUrl = "http://127.0.0.1:33631", Protocol = "SOHE_RS232_V101", PrinterName = "NO-PRINT", AllowScaleControl = true };
            var policy = new WorkstationOptions { LocalTest = true, HardwareTest = true };
            var captures = new CaptureService(options, new ScaleState(), store);
            var baskets = new BasketService(captures, store, options, policy);
            var service = new WorkstationService(options, policy, store, captures, baskets);
            service.ValidateMode(); service.ValidateMode();
            if (store.Setting("workstation_mode") != "HARDWARE_TEST") throw new Exception("模式未冻结");
            policy.HardwareTest = false;
            Invalid(() => service.ValidateMode());
            policy.HardwareTest = true; policy.LocalTest = false;
            Invalid(() => service.ValidateMode());
            policy.LocalTest = true; options.Protocol = "SOHE_HTTP";
            Invalid(() => service.ValidateMode());
        }))
    ];

    /// <summary>常见任务加实际“设备前缀:32位GUID”记录号，不能以省略前缀让版式测试通过。</summary>
    internal static LocalTicket Sample() => new("attempt", "operation", "scope", new("TEST-HW-01:5b40745ae50d438f982b1808dd457c1a",
        "MOCK-TASK", "测试生菜", "测试散装", "演练客户 · 悦来餐厅", "TEST-20261016-001", 1,
        "1.250", "kg", null, "TEST-HW-01", DateTimeOffset.Parse("2026-10-20T12:00:00+08:00"), false),
        "QUEUED", "XSY-NO-PRINTER-EXPECTED", 60, 40, false, null, DateTimeOffset.UtcNow);
    private static Task Run(Action action) { action(); return Task.CompletedTask; }
    private static void Throws(string code, Action action)
    { try { action(); } catch (AgentFault fault) when (fault.Code == code) { return; } throw new Exception("Expected " + code); }
    private static void Invalid(Action action)
    { try { action(); } catch (InvalidOperationException) { return; } throw new Exception("Expected mode rejection"); }
}
