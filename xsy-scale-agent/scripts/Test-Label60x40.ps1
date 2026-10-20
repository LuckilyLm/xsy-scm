<#
.SYNOPSIS
用Windows驱动生成60×40毫米测试标签；显式指定-Print时仅提交一次单页打印。
.PARAMETER PrinterName
目标Windows队列，默认X7；找不到时拒绝打印，不回退到默认打印机。
.PARAMETER OutputDirectory
结果父目录；省略时使用脚本旁printer-check，每次创建独立目录。
.PARAMETER Print
显式提交一张测试标签；省略时只生成预览，绝不调用打印。
.OUTPUTS
preview.png、printer.json及result.json。返回0表示预览完成或已提交队列，不代表物理出纸；失败返回1。
#>
param([string]$PrinterName='X7', [string]$OutputDirectory='', [switch]$Print)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing

if (-not ('Xsy.Scale.Diagnostics.Label6040' -as [type])) {
    Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Printing;

namespace Xsy.Scale.Diagnostics
{
    /// <summary>固定60×40毫米的设备测试样张，预览与打印使用同一绘制方法，无业务数据。</summary>
    public static class Label6040
    {
        /// <summary>绘制单张设备测试标签，不改变打印队列或业务状态。</summary>
        /// <param name="graphics">非空绘图上下文；以毫米为单位，原点由调用方设置。</param>
        /// <param name="testCode">非空的本次测试编号，不超过24字符，不是称重记录编号。</param>
        /// <param name="timestamp">非空的本次本地测试时间字符串，仅供纸张核对。</param>
        public static void Draw(Graphics graphics, string testCode, string timestamp)
        {
            if (graphics == null || String.IsNullOrEmpty(testCode) || testCode.Length > 24 || String.IsNullOrEmpty(timestamp))
                throw new ArgumentException("测试绘图参数不完整");
            graphics.PageUnit = GraphicsUnit.Millimeter;
            using (var pen = new Pen(Color.Black, 0.20f))
            using (var title = new Font("Microsoft YaHei", 10f, FontStyle.Bold))
            using (var normal = new Font("Microsoft YaHei", 7.5f))
            using (var weight = new Font("Microsoft YaHei", 12f, FontStyle.Bold))
            using (var small = new Font("Microsoft YaHei", 6.5f))
            using (var format = new StringFormat(StringFormatFlags.NoWrap))
            {
                // 四边保留2mm，实纸可直观看到裁切或驱动原点偏移。
                graphics.DrawRectangle(pen, 2f, 2f, 56f, 36f);
                graphics.DrawString("鲜蔬园 · 打印测试", title, Brushes.Black, new RectangleF(4f, 3f, 52f, 5.5f), format);
                graphics.DrawString("60×40毫米  中文 ABC 123", normal, Brushes.Black, new RectangleF(4f, 9f, 52f, 4f), format);
                graphics.DrawString("示例净重 1.250 kg", weight, Brushes.Black, new RectangleF(4f, 14f, 52f, 6f), format);
                graphics.DrawString("编号 " + testCode, small, Brushes.Black, new RectangleF(4f, 21f, 52f, 3.5f), format);
                graphics.DrawString("设备测试 · 非业务记录", normal, Brushes.Black, new RectangleF(4f, 25.5f, 52f, 4f), format);
                graphics.DrawString(timestamp, small, Brushes.Black, new RectangleF(4f, 31f, 52f, 3.5f), format);
            }
        }

        /// <summary>为单页PrintDocument附加相同版面，驱动报告错误纸型时拒绝继续绘制。</summary>
        /// <param name="document">非空、已指定打印机及60×40纸型的文档；调用方负责释放。</param>
        /// <param name="testCode">非空的本次测试编号，不超过24字符。</param>
        /// <param name="timestamp">非空的本地测试时间。</param>
        public static void Attach(PrintDocument document, string testCode, string timestamp)
        {
            if (document == null) throw new ArgumentNullException("document");
            document.PrintPage += delegate(object sender, PrintPageEventArgs page)
            {
                float width = page.PageBounds.Width * 25.4f / 100f;
                float height = page.PageBounds.Height * 25.4f / 100f;
                if (Math.Abs(width - 60f) > 0.6f || Math.Abs(height - 40f) > 0.6f)
                    throw new InvalidOperationException("驱动报告纸张不是60×40mm，请核对X7纸张设置");
                page.Graphics.PageUnit = GraphicsUnit.Millimeter;
                // GDI原点位于硬件可打印区，抵消硬边距以对应预览的物理纸张原点。
                page.Graphics.TranslateTransform(-page.PageSettings.HardMarginX * 25.4f / 100f,
                    -page.PageSettings.HardMarginY * 25.4f / 100f);
                Draw(page.Graphics, testCode, timestamp);
                page.HasMorePages = false;
            };
        }
    }
}
'@
}

$runId=(Get-Date -Format 'yyyyMMddHHmmss') + '-' + [guid]::NewGuid().ToString('N')
$testCode='T' + (Get-Date -Format 'HHmmss') + '-' + $runId.Substring($runId.Length-4)
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) { $OutputDirectory=Join-Path $PSScriptRoot 'printer-check' }
$reportRoot=Join-Path ([IO.Path]::GetFullPath($OutputDirectory)) $runId
New-Item -ItemType Directory -Path $reportRoot -ErrorAction Stop | Out-Null
$resultFile=Join-Path $reportRoot 'result.json'
$result=[ordered]@{
    testCode=$testCode; result='PREPARING'; startedAt=[DateTimeOffset]::Now.ToString('o')
    printerName=$PrinterName; widthMm=60; heightMm=40; copiesRequested=1
    printRequested=$false; physicalOutputVerified=$false; error=$null
}
$document=$null
$exitCode=1
try {
    Write-Host '鲜蔬园 · X7 60×40毫米标签测试' -ForegroundColor Cyan
    $bitmap=[Drawing.Bitmap]::new(709,472)
    try {
        $bitmap.SetResolution(300,300)
        $graphics=[Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([Drawing.Color]::White)
            [Xsy.Scale.Diagnostics.Label6040]::Draw($graphics,$testCode,(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
        } finally { $graphics.Dispose() }
        $bitmap.Save((Join-Path $reportRoot 'preview.png'),[Drawing.Imaging.ImageFormat]::Png)
    } finally { $bitmap.Dispose() }
    if (-not $Print) {
        $result.result='PREVIEW_ONLY'
        Write-Host '仅生成预览，未调用打印机。'
        $exitCode=0
    } else {
        if ([string]::IsNullOrWhiteSpace($PrinterName)) { throw '未指定打印机队列' }
        $document=[Drawing.Printing.PrintDocument]::new()
        $document.PrinterSettings.PrinterName=$PrinterName
        if (-not $document.PrinterSettings.IsValid) { throw ('找不到打印机队列：' + $PrinterName) }
        $document.PrinterSettings.Copies=1
        $document.PrinterSettings.Collate=$false
        $document.DocumentName='XSY-60x40-TEST-' + $testCode
        $document.PrintController=[Drawing.Printing.StandardPrintController]::new()
        $default=$document.DefaultPageSettings
        $profile=[ordered]@{
            printerName=$PrinterName; documentName=$document.DocumentName
            defaultPaper=@{name=$default.PaperSize.PaperName; widthMm=($default.PaperSize.Width*25.4/100); heightMm=($default.PaperSize.Height*25.4/100); landscape=$default.Landscape}
            defaultHardMarginMm=@{x=($default.HardMarginX*25.4/100); y=($default.HardMarginY*25.4/100)}
            paperSizes=@($document.PrinterSettings.PaperSizes | ForEach-Object { @{name=$_.PaperName; widthMm=($_.Width*25.4/100); heightMm=($_.Height*25.4/100)} })
        }
        $printer=Get-CimInstance Win32_Printer | Where-Object { $_.Name -eq $PrinterName } | Select-Object -First 1
        if ($null -ne $printer) { $profile.windows=@{driver=$printer.DriverName; port=$printer.PortName; workOffline=$printer.WorkOffline; status=$printer.PrinterStatus} }
        # 队列中有旧任务时先保留现场，不清队列或反复补发，避免恢复打印后出现多张。
        $jobs=@(Get-CimInstance Win32_PrintJob | Where-Object { $_.Name.StartsWith($PrinterName + ',', [StringComparison]::OrdinalIgnoreCase) })
        $profile.pendingJobs=$jobs.Count
        $profile | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $reportRoot 'printer.json') -Encoding UTF8
        if ($jobs.Count -gt 0) { throw 'X7队列已有未完成任务，请先查看队列，本检查未添加新任务。' }
        if ($null -ne $printer -and $printer.WorkOffline) { throw 'X7处于脱机状态，请检查打印机连接。' }
        $paper=$document.PrinterSettings.PaperSizes | Where-Object { [Math]::Abs($_.Width*25.4/100-60) -le 0.6 -and [Math]::Abs($_.Height*25.4/100-40) -le 0.6 } | Select-Object -First 1
        if ($null -eq $paper) { $paper=[Drawing.Printing.PaperSize]::new('XSY-LABEL-60x40',236,157) }
        $document.DefaultPageSettings.PaperSize=$paper
        $document.DefaultPageSettings.Landscape=$false
        $document.DefaultPageSettings.Margins=[Drawing.Printing.Margins]::new(0,0,0,0)
        $document.OriginAtMargins=$false
        [Xsy.Scale.Diagnostics.Label6040]::Attach($document,$testCode,(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
        $result.result='SUBMITTING'; $result.printRequested=$true
        $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $resultFile -Encoding UTF8
        Write-Host ('正在向 ' + $PrinterName + ' 提交一张测试标签，编号 ' + $testCode)
        $document.Print()
        $result.result='SUBMITTED_TO_SPOOLER'
        Write-Host 'Windows已接受打印。请核对实际出纸、中文、重量、四边框和间隙停纸。' -ForegroundColor Green
        $exitCode=0
    }
} catch {
    $result.result=if ($result.printRequested) { 'UNKNOWN' } else { 'FAILED_PRECHECK' }
    $result.error=$_.Exception.Message
    Write-Host ('测试未完成：' + $_.Exception.Message) -ForegroundColor Red
    if ($result.printRequested) { Write-Host '打印结果未知，请先核对实际纸张与队列，再决定是否重新测试。' }
} finally {
    if ($null -ne $document) { $document.Dispose() }
    $result.finishedAt=[DateTimeOffset]::Now.ToString('o')
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $resultFile -Encoding UTF8
    Write-Host ('结果目录：' + $reportRoot)
}
exit $exitCode
