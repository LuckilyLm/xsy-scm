using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.Cryptography;

namespace Xsy.Scale.Agent;

/// <summary>打印传输只证明 Windows 接受，不推断纸张已输出。</summary>
internal interface IRawPrinter
{
    /// <summary>只检查指定 Windows 队列；空名称返回 false，不代表硬件在线。</summary>
    /// <param name="printerName">已配置的 Windows 队列名称；为空时不可用。</param>
    /// <returns>队列是否可以打开。</returns>
    bool IsAvailable(string printerName);
    /// <summary>提交一份冻结的 RAW 内容；调用异常可能已出纸，禁止自动重打。</summary>
    /// <param name="printerName">非空且经过现场确认的 Windows 队列名。</param>
    /// <param name="documentName">非空诊断名称，不得含凭证。</param>
    /// <param name="bytes">非空、非 null 的已校验内容，份数由内容本身冻结。</param>
    /// <returns>Windows 打印作业编号，不表示物理完成。</returns>
    /// <exception cref="Exception">提交失败或结果未知，由调用方持久保存待核查。</exception>
    string Submit(string printerName, string documentName, byte[] bytes);
}

/// <summary>向已配置的 Windows 队列发送服务端冻结的打印指令，不执行任意下载程序。</summary>
internal sealed class WindowsRawPrinter : IRawPrinter
{
    /// <summary>检查 Windows 队列能否打开，不表示物理设备在线或纸张已输出。</summary>
    /// <param name="printerName">现场配置的完整队列名；为空返回 false。</param>
    /// <returns>Windows 是否允许打开该队列。</returns>
    public bool IsAvailable(string printerName)
    {
        if (string.IsNullOrWhiteSpace(printerName) || !OpenPrinter(printerName, out nint handle, 0)) return false;
        ClosePrinter(handle); return true;
    }

    /// <summary>提交冻结指令；返回后台任务号，不能当作物理完成证明。</summary>
    /// <param name="printerName">已验证的完整 Windows 队列名，不允许为空。</param>
    /// <param name="documentName">用于诊断的任务名称，不包含凭证。</param>
    /// <param name="bytes">摘要校验后的非空打印指令，不允许为 null。</param>
    /// <returns>Windows 后台打印任务号。</returns>
    /// <exception cref="Win32Exception">队列无法打开或创建任务失败。</exception>
    /// <exception cref="AgentFault">任务可能已部分提交，输出结果未知。</exception>
    public string Submit(string printerName, string documentName, byte[] bytes)
    {
        if (!OpenPrinter(printerName, out nint handle, 0)) throw new Win32Exception(Marshal.GetLastWin32Error());
        try
        {
            var info = new DocInfo { DocumentName = documentName, DataType = "RAW" };
            int id = StartDocPrinter(handle, 1, ref info);
            if (id == 0) throw new Win32Exception(Marshal.GetLastWin32Error());
            try
            {
                if (!StartPagePrinter(handle) || !WritePrinter(handle, bytes, bytes.Length, out int written)
                    || written != bytes.Length || !EndPagePrinter(handle))
                    throw new AgentFault("PRINT_OUTCOME_UNKNOWN", "打印队列未确认完整接收，请核查后补打");
            }
            finally { if (!EndDocPrinter(handle)) throw new AgentFault("PRINT_OUTCOME_UNKNOWN", "打印任务结束状态未知"); }
            return id.ToString(System.Globalization.CultureInfo.InvariantCulture);
        }
        finally { ClosePrinter(handle); }
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct DocInfo
    {
        [MarshalAs(UnmanagedType.LPWStr)] public string DocumentName;
        [MarshalAs(UnmanagedType.LPWStr)] public string? OutputFile;
        [MarshalAs(UnmanagedType.LPWStr)] public string DataType;
    }

    [DllImport("winspool.drv", EntryPoint = "OpenPrinterW", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool OpenPrinter(string name, out nint handle, nint defaults);
    [DllImport("winspool.drv", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool ClosePrinter(nint handle);
    [DllImport("winspool.drv", EntryPoint = "StartDocPrinterW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int StartDocPrinter(nint handle, int level, ref DocInfo info);
    [DllImport("winspool.drv", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool StartPagePrinter(nint handle);
    [DllImport("winspool.drv", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool EndPagePrinter(nint handle);
    [DllImport("winspool.drv", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool EndDocPrinter(nint handle);
    [DllImport("winspool.drv", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool WritePrinter(nint handle, byte[] bytes, int count, out int written);
}

/// <summary>物理执行与网络回执分离；先存执行意图，断网或重启均不重复发纸。</summary>
internal sealed class PrintService(AgentOptions options, StateStore store, PlatformClient platform, IRawPrinter printer)
{
    internal async Task Execute(PrintJob job, CancellationToken cancellation)
    {
        if (string.IsNullOrWhiteSpace(job.PrintJobId) || string.IsNullOrWhiteSpace(job.PrintAttemptId))
            throw new AgentFault("INVALID_PRINT_ATTEMPT", "平台必须提供任务及尝试编号");
        if (store.HasPrint(job.PrintAttemptId)) return;
        var local = new LocalPrint(job, $"{options.DeviceCode}:{job.PrintAttemptId}:{Guid.NewGuid():N}", "CLAIMED_LOCAL", null);
        store.SavePrint(local);
        string status = "FAILED"; string? errorCode = null; string? spoolId = null;
        bool attempted = false;
        try
        {
            if (job.DeviceCode != options.DeviceCode || string.IsNullOrWhiteSpace(job.LeaseToken)
                || job.LeaseExpiresAt <= DateTimeOffset.UtcNow.AddSeconds(5) || job.ExpiresAt <= DateTimeOffset.UtcNow
                || job.PrinterCode != options.PrinterCode || string.IsNullOrWhiteSpace(options.PrinterCode)
                || !options.AllowedPrintFormats.Contains(job.Format, StringComparer.Ordinal) || job.Copies is < 1 or > 10
                || job.Format is not ("TSPL" or "ZPL" or "ESC_POS"))
                throw new AgentFault("PRINT_PROFILE_MISMATCH", "打印机配置、格式或任务租约无效");
            byte[] bytes = await platform.Download(job, cancellation);
            string checksum = Convert.ToHexString(SHA256.HashData(bytes));
            if (bytes.Length == 0 || !string.Equals("sha256:" + checksum, job.SnapshotChecksum, StringComparison.OrdinalIgnoreCase))
                throw new AgentFault("PRINT_CHECKSUM_MISMATCH", "打印内容摘要不匹配");
            if (!printer.IsAvailable(options.PrinterName)) throw new AgentFault("PRINTER_UNAVAILABLE", "打印机不可用");
            if (job.LeaseExpiresAt <= DateTimeOffset.UtcNow.AddSeconds(5)) throw new AgentFault("LEASE_EXPIRED", "打印任务租约已过期");
            // 必须在任何物理调用之前持久化，崩溃恢复不能重新调用 Submit。
            store.SavePrint(local with { State = "SPOOL_SUBMITTING" });
            attempted = true;
            spoolId = printer.Submit(options.PrinterName, $"XSY {job.PrintJobId}/{job.PrintAttemptId}", bytes);
            status = "SUBMITTED_TO_SPOOLER";
        }
        catch (Exception error)
        {
            status = attempted ? "UNKNOWN" : "FAILED";
            errorCode = error switch { AgentFault fault => fault.Code, PlatformFault fault => fault.Code, _ => "PRINT_EXECUTION_FAILED" };
        }
        var ack = new PrintAck(job.PrintJobId, job.PrintAttemptId, options.DeviceCode, options.PrinterCode,
            job.LeaseToken, status, spoolId, DateTimeOffset.UtcNow,
            status == "SUBMITTED_TO_SPOOLER" ? "WINDOWS_SPOOLER" : "AGENT", errorCode,
            errorCode == null ? null : "打印执行失败或结果未知，请核查后按平台权限处理");
        store.SavePrint(local with { State = "ACK_PENDING", Ack = ack });
    }

    internal async Task Acknowledge(CancellationToken cancellation)
    {
        foreach (LocalPrint job in store.Prints("ACK_PENDING").Take(10))
        {
            try
            {
                await platform.Ack(job.Ack!, cancellation);
                store.SavePrint(job with { State = "ACKED_SERVER" });
            }
            catch (PlatformFault fault) when (!fault.Retryable && fault.Code != "DEVICE_UNAUTHORIZED")
            { store.SavePrint(job with { State = "NEEDS_REVIEW" }); }
        }
    }
}
