using System.Drawing;
using System.Drawing.Printing;

namespace Xsy.Scale.Agent;

/// <summary>通过Windows已安装驱动打印本地待同步凭条，不依赖某种标签机指令语言。</summary>
internal sealed class DriverTicketPrinter : ITicketPrinter
{
    /// <inheritdoc />
    public TicketOutput Submit(LocalTicket ticket)
    {
        if (ticket.Snapshot.TestOnly) throw new AgentFault("TEST_PRINT_BLOCKED", "演练凭条不能交给真实打印驱动");
        return WindowsTicketOutput.Submit(ticket);
    }
}

/// <summary>显式硬件测试才使用的输出器；真实读数配测试任务，所有纸张保留非业务标识。</summary>
internal sealed class HardwareTestTicketPrinter : ITicketPrinter
{
    /// <inheritdoc />
    public TicketOutput Submit(LocalTicket ticket)
    {
        if (!ticket.Snapshot.TestOnly || ticket.Snapshot.TemplateVersion is not ("HARDWARE_TEST_6040_V1" or "HARDWARE_TEST_6040_TRACE_V2")
            || !ticket.Snapshot.DeviceCode.StartsWith("TEST-", StringComparison.Ordinal)
            || ticket.PaperWidthMm != 60 || ticket.PaperHeightMm != 40)
            throw new AgentFault("HARDWARE_PRINT_BLOCKED", "硬件测试只接受隔离的60×40测试凭条");
        return WindowsTicketOutput.Submit(ticket);
    }
}

/// <summary>Windows队列提交公共实现；预检与实际绘制共享版式，驱动执行异常不自动重印。</summary>
internal static class WindowsTicketOutput
{
    /// <summary>预检完整内容后提交一次打印，不回退其他打印机或修改全局纸型。</summary>
    /// <param name="ticket">已由调用方核实模式的冻结凭条；队列为空或内容放不下时拒绝。</param>
    /// <returns>仅表示Windows接受，不证明物理出纸。</returns>
    /// <exception cref="AgentFault">驱动/版式拒绝，或执行后结果未知。</exception>
    internal static TicketOutput Submit(LocalTicket ticket)
    {
        // 在调用Print之前完整测量，避免内容溢出时已产生部分标签。
        using (var preview = TicketLayout.Preview(ticket)) { }
        using var document = new PrintDocument();
        document.PrinterSettings.PrinterName = ticket.PrinterName;
        document.PrinterSettings.Copies = 1;
        if (!document.PrinterSettings.IsValid) throw new AgentFault("PRINTER_UNAVAILABLE", "Windows打印队列不可用");
        document.DocumentName = "鲜蔬园待同步称重凭条-" + ticket.Snapshot.BasketNumber;
        document.PrintController = new StandardPrintController();
        var paper = document.PrinterSettings.PaperSizes.Cast<PaperSize>().FirstOrDefault(size =>
            Math.Abs(size.Width * .254 - ticket.PaperWidthMm) < .6 && Math.Abs(size.Height * .254 - ticket.PaperHeightMm) < .6);
        document.DefaultPageSettings.PaperSize = paper ?? new PaperSize("XSY-LOCAL-TICKET",
            (int)Math.Round(ticket.PaperWidthMm / .254), (int)Math.Round(ticket.PaperHeightMm / .254));
        document.DefaultPageSettings.Landscape = false;
        document.DefaultPageSettings.Margins = new Margins(0, 0, 0, 0);
        document.PrintPage += (_, page) =>
        {
            Graphics graphics = page.Graphics ?? throw new AgentFault("PRINT_RENDER_FAILED", "驱动没有提供绘图页面");
            if (Math.Abs(page.PageSettings.PaperSize.Width * .254 - ticket.PaperWidthMm) > .6
                || Math.Abs(page.PageSettings.PaperSize.Height * .254 - ticket.PaperHeightMm) > .6)
                throw new AgentFault("PAPER_SIZE_MISMATCH", "驱动未采用请求的纸张尺寸，请核对纸型");
            graphics.PageUnit = GraphicsUnit.Millimeter;
            graphics.TranslateTransform(-page.PageSettings.HardMarginX * .254f, -page.PageSettings.HardMarginY * .254f);
            var area = page.PageSettings.PrintableArea;
            var content = new RectangleF(2, 2, ticket.PaperWidthMm - 4, ticket.PaperHeightMm - 4);
            var printable = new RectangleF(area.X * .254f, area.Y * .254f, area.Width * .254f, area.Height * .254f);
            if (!printable.Contains(content)) throw new AgentFault("PRINT_MARGIN_MISMATCH", "打印机可用区域不足，请核对硬边距");
            TicketLayout.Draw(graphics, ticket);
            page.HasMorePages = false;
        };
        // 从驱动执行开始，异常也可能已经产生部分纸张，不能当作确定失败自动重印。
        try { document.Print(); }
        catch (Exception) { throw new AgentFault("PRINT_RESULT_UNKNOWN", "驱动执行中断，请核对实际出纸后再决定是否补打"); }
        return new("SUBMITTED_TO_SPOOLER");
    }
}

/// <summary>仅隔离演练可注入的打印替身，从不调用系统打印队列。</summary>
internal sealed class ExerciseTicketPrinter : ITicketPrinter
{
    /// <inheritdoc />
    public TicketOutput Submit(LocalTicket ticket)
    {
        if (!ticket.Snapshot.TestOnly) throw new AgentFault("FIELD_PRINT_BLOCKED", "演练打印器拒绝现场数据");
        return new("TEST_ONLY");
    }
}

/// <summary>本地凭条后台执行一次，失败和中断均交人工处理，不自动重发纸张。</summary>
internal sealed class LocalTicketWorker(StateStore store, ITicketPrinter printer, WorkstationOptions policy,
    ILogger<LocalTicketWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                LocalTicket? ticket = policy.LocalTicketsEnabled ? store.TakeLocalTicket() : null;
                if (ticket != null)
                {
                    TicketOutput output;
                    string? errorCode = null;
                    try { output = printer.Submit(ticket); }
                    catch (Exception error)
                    {
                        errorCode = error is AgentFault fault ? fault.Code : "PRINT_RESULT_UNKNOWN";
                        output = new(errorCode == "PRINT_RESULT_UNKNOWN" ? "UNKNOWN" : "FAILED");
                        logger.LogWarning("本地凭条驱动提交失败，错误类型 {Type}", error.GetType().Name);
                    }
                    store.FinishLocalTicket(ticket, output, errorCode);
                    continue;
                }
            }
            catch (Exception error) { logger.LogWarning("本地凭条队列暂不可用，错误类型 {Type}", error.GetType().Name); }
            try { await Task.Delay(400, stoppingToken); } catch (OperationCanceledException) { break; }
        }
    }
}
