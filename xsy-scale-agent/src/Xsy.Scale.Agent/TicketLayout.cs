using System.Drawing;
using System.Drawing.Imaging;

namespace Xsy.Scale.Agent;

/// <summary>预览与Windows实纸共用毫米版式；字段完整放不下时拒绝，不裁切或缩小到不可读。</summary>
internal static class TicketLayout
{
    /// <summary>生成203dpi标签预览，不访问打印机。</summary>
    /// <param name="ticket">非空冻结凭条，纸张范围沿用工作台约束。</param>
    /// <returns>由调用方释放的位图，绘制失败时释放后抛出错误。</returns>
    /// <exception cref="AgentFault">纸张不合法或完整内容超出纸张。</exception>
    internal static Bitmap Preview(LocalTicket ticket)
    {
        if (ticket.PaperWidthMm is < 40 or > 110 || ticket.PaperHeightMm is < 40 or > 300)
            throw new AgentFault("PAPER_SIZE_INVALID", "纸张尺寸不在支持范围");
        var bitmap = new Bitmap((int)Math.Round(ticket.PaperWidthMm / 25.4 * 203),
            (int)Math.Round(ticket.PaperHeightMm / 25.4 * 203), PixelFormat.Format24bppRgb);
        try
        {
            bitmap.SetResolution(203, 203);
            using var graphics = Graphics.FromImage(bitmap);
            graphics.Clear(Color.White);
            Draw(graphics, ticket);
            return bitmap;
        }
        catch { bitmap.Dispose(); throw; }
    }

    /// <summary>先测量所有字段再绘制；60×40用紧凑模板，其他尺寸保留长凭条字段。</summary>
    /// <param name="graphics">调用方持有的非空绘图上下文，实际打印已补偿硬边距。</param>
    /// <param name="ticket">非空冻结数据；未知皮重显示未知，补打仍使用原始记录。</param>
    /// <exception cref="AgentFault">任一字段或完整内容无法在纸内显示。</exception>
    internal static void Draw(Graphics graphics, LocalTicket ticket)
    {
        graphics.PageUnit = GraphicsUnit.Millimeter;
        var snapshot = ticket.Snapshot;
        if (snapshot.TemplateVersion is "LOCAL_PENDING_TRACE_V2" or "HARDWARE_TEST_6040_TRACE_V2" or "PLATFORM_TRACE_V1")
        { DrawTrace(graphics, ticket); return; }
        bool compact = ticket.PaperWidthMm == 60 && ticket.PaperHeightMm == 40;
        string status = snapshot.TemplateVersion == "HARDWARE_TEST_6040_V1" ? "设备联动测试 · 非业务记录"
            : snapshot.TestOnly ? "演练样张 · 不出纸" : "待同步 · 非平台正式单据";
        using var normal = new Font("Microsoft YaHei", compact ? 7 : 8);
        // 真实记录号包含设备前缀，须预留两行；保留7pt正文，避免只用短测试编号时漏掉高度溢出。
        using var title = new Font("Microsoft YaHei", compact ? 7.5f : 11, FontStyle.Bold);
        using var weight = new Font("Microsoft YaHei", compact ? 12 : 17, FontStyle.Bold);
        using var format = (StringFormat)StringFormat.GenericTypographic.Clone();
        format.Trimming = StringTrimming.None;
        format.FormatFlags |= StringFormatFlags.NoClip;
        (string Text, Font Font)[] lines = compact
            ? [($"鲜蔬园 · 第{snapshot.BasketNumber}筐" + (ticket.Reprint ? " · 补打" : ""), title), (status, normal),
                ("客户：" + (snapshot.CustomerName ?? "平台未提供"), normal),
                (snapshot.ProductName + " / " + snapshot.SpecName, title),
                ("净重 " + snapshot.NetWeight + " " + snapshot.Unit, weight),
                ("订单：" + (snapshot.OrderNo ?? snapshot.TaskNo), normal),
                (snapshot.CapturedAt.ToOffset(TimeSpan.FromHours(8)).ToString("yyyy-MM-dd HH:mm:ss"), normal),
                ("设备：" + snapshot.DeviceCode + "  皮重：" + (snapshot.TareWeight ?? "未知"), normal),
                ("记录：" + snapshot.EventKey, normal)]
            : [("鲜蔬园 · 称重凭条", title), (status, normal),
                ((ticket.Reprint ? "补打 · " : "") + "第 " + snapshot.BasketNumber + " 筐", normal),
                ("客户：" + (snapshot.CustomerName ?? "平台未提供"), normal),
                ("订单：" + (snapshot.OrderNo ?? snapshot.TaskNo), normal),
                (snapshot.ProductName + " " + snapshot.SpecName, normal),
                (snapshot.NetWeight + " " + snapshot.Unit, weight),
                ("皮重：" + (snapshot.TareWeight ?? "未知"), normal),
                (snapshot.CapturedAt.ToOffset(TimeSpan.FromHours(8)).ToString("yyyy-MM-dd HH:mm:ss"), normal),
                ("设备：" + snapshot.DeviceCode, normal), ("记录：" + snapshot.EventKey, normal)];
        float width = ticket.PaperWidthMm - 4, y = 2, gap = compact ? .05f : 1f;
        var measured = new List<(string Text, Font Font, RectangleF Bounds)>();
        foreach (var line in lines)
        {
            SizeF size = graphics.MeasureString(line.Text, line.Font, new SizeF(width, 1000), format,
                out int fitted, out _);
            if (fitted != line.Text.Length || size.Width > width + .05f || y + size.Height > ticket.PaperHeightMm - 2)
                throw new AgentFault("TICKET_TOO_LONG", "完整凭条放不下当前纸张，请核对字段长度或纸型后补打");
            measured.Add((line.Text, line.Font, new RectangleF(2, y, width, size.Height + .02f)));
            y += size.Height + gap;
        }
        foreach (var line in measured) graphics.DrawString(line.Text, line.Font, Brushes.Black, line.Bounds, format);
    }

    /// <summary>新票为二维码独立保留静区；旧票仍由原模板渲染，不因升级改变补打。</summary>
    /// <param name="graphics">调用方提供的非空毫米绘图上下文。</param>
    /// <param name="ticket">新模板冻结票；完整字段溢出时拒绝，不裁切。</param>
    /// <exception cref="AgentFault">纸张或完整内容不足、二维码过密。</exception>
    private static void DrawTrace(Graphics graphics, LocalTicket ticket)
    {
        var s = ticket.Snapshot;
        using var normal = new Font("Microsoft YaHei", 7);
        using var title = new Font("Microsoft YaHei", 7.5f, FontStyle.Bold);
        using var weight = new Font("Microsoft YaHei", 12, FontStyle.Bold);
        using var format = (StringFormat)StringFormat.GenericTypographic.Clone();
        format.Trimming = StringTrimming.None;
        format.FormatFlags |= StringFormatFlags.NoClip;
        float fullWidth = ticket.PaperWidthMm - 4, leftWidth = fullWidth - 17.5f, y = 2;
        var measured = new List<(string Text, Font Font, RectangleF Bounds)>();
        // 先完成所有测量，最后一次性画内容，避免发生半张票。
        foreach (var line in new[] {
            ($"鲜蔬园 · 第{s.BasketNumber}筐" + (ticket.Reprint ? " · 补打" : ""), title),
            (s.TemplateVersion == "PLATFORM_TRACE_V1" ? "包装码标签 · 不代表出库" : s.TemplateVersion == "HARDWARE_TEST_6040_TRACE_V2" ? "设备联动测试 · 非业务记录" : s.TestOnly ? "演练样张 · 不出纸" : "待同步 · 非平台正式单据", normal),
            ("客户：" + (s.CustomerName ?? "平台未提供"), normal),
            (s.ProductName + " / " + s.SpecName, title) })
            Add(line.Item1, line.Item2, 2, fullWidth);
        float qrTop = y;
        foreach (var line in new[] { ("净重 " + s.NetWeight + " " + s.Unit, weight),
            ("订单：" + (s.OrderNo ?? s.TaskNo), normal),
            (s.CapturedAt.ToOffset(TimeSpan.FromHours(8)).ToString("yyyy-MM-dd HH:mm:ss"), normal),
            (s.DeviceCode + " 皮重" + (s.TareWeight ?? "未知"), normal) })
            Add(line.Item1, line.Item2, 2, leftWidth);
        y = Math.Max(y, qrTop + 15.5f);
        Add(s.TemplateVersion == "PLATFORM_TRACE_V1" ? "码：" + s.TraceReference + " 批次：" + s.LotNo : "记录：" + s.EventKey, normal, 2, fullWidth);
        using var qr = s.TraceUrl == null ? null : TraceLink.Matrix(s.TraceUrl);
        float qrLeft = ticket.PaperWidthMm - 17.5f;
        // 15.5mm含标准四模块白边，文字始终位于该区域以外。
        foreach (var line in measured) graphics.DrawString(line.Text, line.Font, Brushes.Black, line.Bounds, format);
        if (qr == null)
        {
            using var centered = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
            graphics.DrawString("查询入口\n待配置", normal, Brushes.Black, new RectangleF(qrLeft, qrTop, 15.5f, 15.5f), centered);
        }
        else
        {
            float module = 15.5f / qr.ModuleMatrix.Count;
            graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.None;
            for (int row = 0; row < qr.ModuleMatrix.Count; row++)
                for (int col = 0; col < qr.ModuleMatrix.Count; col++)
                    if (qr.ModuleMatrix[row][col]) graphics.FillRectangle(Brushes.Black,
                        qrLeft + col * module, qrTop + row * module, module, module);
        }

        // 本地函数只负责布局测量，所有字段都必须完整落在纸内。
        void Add(string text, Font font, float x, float width)
        {
            SizeF size = graphics.MeasureString(text, font, new SizeF(width, 1000), format, out int fitted, out _);
            if (fitted != text.Length || size.Width > width + .05f || y + size.Height > ticket.PaperHeightMm - 2)
                throw new AgentFault("TICKET_TOO_LONG", "完整凭条放不下当前纸张，请核对字段长度或纸型后补打");
            measured.Add((text, font, new RectangleF(x, y, width, size.Height + .02f)));
            y += size.Height + .05f;
        }
    }
}
