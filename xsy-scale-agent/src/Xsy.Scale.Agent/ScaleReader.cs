using System.IO.Ports;
using System.Text.Json;

namespace Xsy.Scale.Agent;

/// <summary>封装实际串口或厂家 HTTP，业务接口不直接操作设备节点。</summary>
internal sealed class ScaleReader(AgentOptions options, ScaleState state, ILogger<ScaleReader> logger) : BackgroundService
{
    private SerialPort? port;
    private SoheFrameDecoder? activeDecoder;
    private readonly object serialGate = new();
    private readonly SemaphoreSlim httpGate = new(1, 1);
    private readonly HttpClient http = new(new HttpClientHandler { AllowAutoRedirect = false })
    { Timeout = TimeSpan.FromSeconds(2) };

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (options.Protocol == "SOHE_HTTP") await PollHttp(stoppingToken);
                else await PollSerial(stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested) { break; }
            catch (Exception error)
            {
                state.Fail(error is AgentFault fault ? fault.Code : "SCALE_DISCONNECTED");
                logger.LogWarning("称重读取中断，错误类型 {Type}；稍后重连", error.GetType().Name);
                try { await Task.Delay(TimeSpan.FromSeconds(3), stoppingToken); }
                catch (OperationCanceledException) { break; }
            }
            finally { lock (serialGate) { port?.Dispose(); port = null; activeDecoder = null; } }
        }
    }

    private async Task PollSerial(CancellationToken cancellation)
    {
        if (string.IsNullOrWhiteSpace(options.SerialPort)) throw new AgentFault("SCALE_NOT_CONFIGURED", "尚未配置串口");
        bool ad = options.Protocol == "SOHE_AD";
        if (!ad && options.Protocol != "SOHE_RS232_V101") throw new AgentFault("PROTOCOL_UNSUPPORTED", "未知称重协议");
        byte address = checked((byte)options.AdAddress.Single());
        var decoder = new SoheFrameDecoder(ad, address);
        lock (serialGate)
        {
            port = new SerialPort(options.SerialPort, options.BaudRate, Parity.None, 8, StopBits.One)
            { ReadTimeout = 200, WriteTimeout = 1000, Handshake = Handshake.None };
            port.Open();
            activeDecoder = decoder;
        }
        var bytes = new byte[256];
        var lastRead = DateTimeOffset.MinValue;
        while (!cancellation.IsCancellationRequested)
        {
            if (ad)
            {
                lock (serialGate) port!.Write(new byte[] { 2, address, 0x4e, 3 }, 0, 4);
            }
            DateTimeOffset deadline = DateTimeOffset.UtcNow.AddMilliseconds(ad ? 500 : 200);
            bool received = false;
            do
            {
                int count;
                lock (serialGate)
                {
                    try { count = port!.Read(bytes, 0, bytes.Length); }
                    catch (TimeoutException) { count = 0; }
                    // 读取、组帧与发布共用锁，避免控制前读出的字节在控制后才成为新样本。
                    if (count > 0)
                        foreach (WeightReading reading in decoder.Feed(bytes.AsSpan(0, count)))
                        { state.Set(reading); lastRead = DateTimeOffset.UtcNow; received = true; }
                    if (DateTimeOffset.UtcNow - lastRead > TimeSpan.FromSeconds(options.ReadingMaxAgeSeconds))
                        state.Fail("SCALE_TIMEOUT");
                }
            } while (ad && !received && DateTimeOffset.UtcNow < deadline && !cancellation.IsCancellationRequested);
            await Task.Delay(ad ? 100 : 10, cancellation);
        }
    }

    private async Task PollHttp(CancellationToken cancellation)
    {
        if (!Uri.TryCreate(options.HttpScaleUrl, UriKind.Absolute, out Uri? uri) || !uri.IsLoopback)
            throw new AgentFault("INVALID_SCALE_URL", "厂家辅助程序地址必须为本机回环地址");
        while (!cancellation.IsCancellationRequested)
        {
            await httpGate.WaitAsync(cancellation);
            try
            {
                using HttpResponseMessage response = await http.GetAsync(new Uri(uri, "/api/Weight"), cancellation);
                response.EnsureSuccessStatusCode();
                string json = await response.Content.ReadAsStringAsync(cancellation);
                state.Set(ParseHttp(json, options));
            }
            finally { httpGate.Release(); }
            await Task.Delay(200, cancellation);
        }
    }

    internal static WeightReading ParseHttp(string json, AgentOptions options)
    {
        using JsonDocument doc = JsonDocument.Parse(json);
        JsonElement root = doc.RootElement;
        if (!root.TryGetProperty("Code", out JsonElement code) || code.GetInt32() != 1)
            throw new AgentFault("HTTP_SCALE_FAILED", "厂家辅助程序未返回有效读数");
        SoheFrameDecoder.ValidateUnit(options.HttpUnit);
        decimal net = SoheFrameDecoder.Number(root.GetProperty("netWeight").GetString() ?? "");
        decimal? tare = root.TryGetProperty("tareWeight", out var field)
            ? SoheFrameDecoder.Number(field.GetString() ?? "") : null;
        bool? stable = null;
        if (!string.IsNullOrWhiteSpace(options.HttpStableField) && root.TryGetProperty(options.HttpStableField, out var flag)
            && flag.ValueKind is JsonValueKind.True or JsonValueKind.False) stable = flag.GetBoolean();
        return SoheFrameDecoder.Reading(net, options.HttpUnit, stable,
            stable == null ? "UNKNOWN" : "DEVICE_HTTP", "NET", tare, []);
    }

    /// <summary>只发送一次人工控制，并等待后续读数观测，不自动重试设备命令。</summary>
    /// <param name="command">仅tare或zero，空值及其他指令拒绝。</param>
    /// <param name="cancellation">本次请求取消信号；发送中取消记为结果未确认。</param>
    /// <returns>发送后的WAITING状态；不代表设备已执行。</returns>
    /// <exception cref="AgentFault">未启用、失联、不稳定、重复操作或发送结果未知。</exception>
    internal async Task<ScaleControlStatus> Control(string command, CancellationToken cancellation)
    {
        if (!options.AllowScaleControl) throw new AgentFault("CONTROL_DISABLED", "现场尚未启用去皮置零");
        if (command is not ("tare" or "zero")) throw new AgentFault("INVALID_COMMAND", "仅支持人工去皮和置零");
        if (options.Protocol == "SOHE_HTTP")
        {
            var uri = new Uri(options.HttpScaleUrl);
            if (!uri.IsLoopback) throw new AgentFault("INVALID_SCALE_URL", "厂家辅助程序必须在本机");
            await httpGate.WaitAsync(cancellation);
            try
            {
                var operation = state.BeginControl(command, options.ReadingMaxAgeSeconds);
                try
                {
                    using var response = await http.GetAsync(new Uri(uri, command == "tare" ? "/api/setTare" : "/api/zero"), cancellation);
                    response.EnsureSuccessStatusCode();
                    using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellation));
                    if (body.RootElement.GetProperty("Code").GetInt32() != 1) throw new IOException("control rejected");
                    return state.ControlSent(operation.ControlId);
                }
                catch (Exception)
                {
                    state.ControlUnconfirmed(operation.ControlId);
                    throw new AgentFault("CONTROL_RESULT_UNKNOWN", "命令结果未确认，请核对设备；不会自动重发");
                }
            }
            finally { httpGate.Release(); }
        }
        else lock (serialGate)
        {
            byte[] bytes = SoheControlProtocol.Encode(command, options.Protocol, options.AdAddress);
            if (port?.IsOpen != true) throw new AgentFault("SCALE_DISCONNECTED", "电子秤串口未连接");
            cancellation.ThrowIfCancellationRequested();
            var operation = state.BeginControl(command, options.ReadingMaxAgeSeconds);
            try
            {
                port.DiscardInBuffer(); activeDecoder?.Reset();
                port.Write(bytes, 0, bytes.Length); port.BaseStream.Flush();
                return state.ControlSent(operation.ControlId);
            }
            catch (Exception)
            {
                state.ControlUnconfirmed(operation.ControlId);
                throw new AgentFault("CONTROL_RESULT_UNKNOWN", "命令结果未确认，请核对设备；不会自动重发");
            }
        }
    }

    /// <summary>释放厂家 HTTP 连接池和后台生命周期资源；不执行任何设备控制。</summary>
    public override void Dispose() { http.Dispose(); base.Dispose(); }
}
