using System.Globalization;
using System.Text;

namespace Xsy.Scale.Agent;

/// <summary>以结束符组帧，支持拆包、粘包和噪声恢复，不假设一次串口读取就是一帧。</summary>
internal sealed class SoheFrameDecoder(bool adModule = false, byte address = 0x41)
{
    private readonly List<byte> buffer = [];

    /// <summary>硬件控制前丢弃半帧，防止将控制前后的串口字节拼成可采集样本。</summary>
    internal void Reset() => buffer.Clear();

    internal IReadOnlyList<WeightReading> Feed(ReadOnlySpan<byte> bytes)
    {
        buffer.AddRange(bytes.ToArray());
        var readings = new List<WeightReading>();
        while (true)
        {
            if (adModule)
            {
                int start = buffer.IndexOf(0x02);
                if (start < 0) { buffer.Clear(); break; }
                if (start > 0) buffer.RemoveRange(0, start);
                if (buffer.Count < 13) break;
                if (buffer[12] != 0x03 || buffer[1] != address || buffer[2] != 0x3d)
                { buffer.RemoveAt(0); continue; }
                byte[] frame = buffer.GetRange(0, 13).ToArray();
                buffer.RemoveRange(0, 13);
                readings.Add(ParseAd(frame, address));
            }
            else
            {
                int end = buffer.IndexOf(0x0a);
                if (end < 0) break;
                byte[] frame = end >= 12 ? buffer.GetRange(end - 12, 13).ToArray() : [];
                buffer.RemoveRange(0, end + 1);
                if (frame.Length == 13) readings.Add(ParseRs232(frame));
            }
        }
        if (buffer.Count > 4096) buffer.Clear();
        return readings;
    }

    internal static WeightReading ParseRs232(ReadOnlySpan<byte> frame)
    {
        if (frame.Length != 13 || frame[11] != 13 || frame[12] != 10 || frame.ContainsAnyInRange((byte)128, byte.MaxValue))
            throw new AgentFault("INVALID_FRAME", "重量帧长度、编码或结束符不正确");
        char stability = char.ToUpperInvariant((char)frame[0]);
        char mode = char.ToUpperInvariant((char)frame[1]);
        if (stability is not ('S' or 'W') || mode is not ('N' or 'G'))
            throw new AgentFault("INVALID_FRAME", "重量帧状态不正确");
        string unit = Encoding.ASCII.GetString(frame[9..11]).Trim().ToLowerInvariant();
        ValidateUnit(unit);
        string raw = Encoding.ASCII.GetString(frame[2..9]);
        // 协议首位 0 是正号占位，内部空格需要去除；禁止解析指数和区域性逗号。
        if (raw[0] == '0') raw = raw[1..];
        decimal weight = Number(raw.Replace(" ", ""));
        return Reading(weight, unit, stability == 'S', "DEVICE_FRAME", mode == 'N' ? "NET" : "GROSS", null, frame);
    }

    internal static WeightReading ParseAd(ReadOnlySpan<byte> frame, byte address)
    {
        if (frame.Length != 13 || frame[0] != 2 || frame[1] != address || frame[2] != 0x3d || frame[12] != 3
            || frame[10] > 1 || frame[11] > 1)
            throw new AgentFault("INVALID_FRAME", "AD 模块帧状态不正确");
        char[] raw = Encoding.ASCII.GetString(frame[3..10]).ToCharArray();
        Array.Reverse(raw);
        // AD 响应未证明净毛重模式，实机确认前标为未知，避免把毛重当净重接受。
        return Reading(Number(new string(raw).Trim()), "kg", frame[11] == 1, "DEVICE_FRAME", "UNKNOWN", null, frame);
    }

    internal static WeightReading Reading(decimal weight, string unit, bool? stable, string source,
        string mode, decimal? tare, ReadOnlySpan<byte> raw) => new(Guid.NewGuid().ToString("N"), "",
            weight, unit, stable, source, mode, tare, DateTimeOffset.UtcNow, Convert.ToHexString(raw));

    internal static decimal Number(string text)
    {
        if (string.IsNullOrWhiteSpace(text) || text.Any(c => c is not (>= '0' and <= '9') and not '.' and not '-' and not '+')
            || !decimal.TryParse(text, NumberStyles.AllowLeadingSign | NumberStyles.AllowDecimalPoint,
                CultureInfo.InvariantCulture, out decimal value))
            throw new AgentFault("INVALID_WEIGHT", "设备重量不是合法十进制数");
        return value;
    }

    internal static void ValidateUnit(string unit)
    {
        if (unit is not ("kg" or "g" or "sj")) throw new AgentFault("UNKNOWN_UNIT", "设备重量单位未知");
    }

    internal static decimal InKg(decimal weight, string unit) => unit switch
    { "kg" => weight, "g" => weight / 1000m, "sj" => weight / 2m, _ => throw new AgentFault("UNKNOWN_UNIT", "设备重量单位未知") };
}

/// <summary>最新采样和连接状态原子更新，失联或非法帧立即取消旧采样的可采集性。</summary>
internal sealed partial class ScaleState(TimeProvider? timeProvider = null)
{
    private readonly object gate = new();
    private WeightReading? reading;
    private readonly Queue<WeightReading> recent = new();
    private string? error = "SCALE_NOT_CONFIGURED";
    private long sequence;
    private DateTimeOffset blockedUntil;

    internal void Set(WeightReading next)
    {
        lock (gate)
        {
            if (clock.GetUtcNow() < blockedUntil || control?.State == "SENDING") return;
            reading = next with { ReadingSequence = (++sequence).ToString(CultureInfo.InvariantCulture) };
            recent.Enqueue(reading);
            while (recent.Count > 100) recent.Dequeue();
            error = null;
            ObserveControl(reading);
        }
    }
    internal void Fail(string code) { lock (gate) { error = code; reading = null; recent.Clear();
        if (control?.State is "SENDING" or "WAITING") {
            blockedUntil = clock.GetUtcNow().AddMilliseconds(500);
            SetControl(control with { State = "UNCONFIRMED", ErrorCode = code }); } } }
    internal void BlockFor(TimeSpan duration)
    { lock (gate) { blockedUntil = clock.GetUtcNow().Add(duration); error = "SCALE_CONTROL_PENDING"; reading = null; recent.Clear(); } }
    internal (WeightReading? Reading, string? Error) Snapshot() { lock (gate) { UpdateControlTimeout(); return (reading, error); } }

    internal WeightReading Freeze(string sampleId, AgentOptions options)
    {
        lock (gate)
        {
            UpdateControlTimeout();
            if (control != null && control.State != "ZERO_OBSERVED")
                throw new AgentFault(control.State is "SENDING" or "WAITING" ? "CONTROL_IN_PROGRESS" : "CONTROL_UNCONFIRMED",
                    "请先核对去皮或置零后的稳定零读数");
            if (error != null || reading == null) throw new AgentFault("SCALE_UNAVAILABLE", "电子秤未连接或读数无效");
            WeightReading? selected = recent.FirstOrDefault(sample => sample.SampleId == sampleId);
            if (selected == null || selected.RawWeight != reading.RawWeight || selected.RawUnit != reading.RawUnit)
                throw new AgentFault("SAMPLE_CHANGED", "重量已更新，请重新采集");
            // 连续稳定帧可以有不同采样编号；重量变动或最新状态不稳定时仍拒绝旧快照。
            if (clock.GetUtcNow() - selected.CapturedAt > TimeSpan.FromSeconds(options.ReadingMaxAgeSeconds)
                || clock.GetUtcNow() - reading.CapturedAt > TimeSpan.FromSeconds(options.ReadingMaxAgeSeconds))
                throw new AgentFault("STALE_READING", "重量数据已过期");
            if (reading.StableFlag != true || reading.NetGrossFlag != "NET" || selected.StableFlag != true || selected.NetGrossFlag != "NET")
                throw new AgentFault("WEIGHT_UNACCEPTABLE", "请等待可信的稳定净重");
            SoheFrameDecoder.ValidateUnit(reading.RawUnit);
            // 用户取消软件量程门槛；零/负数不是本次商品净重，仍不能产生业务记录。
            if (reading.RawWeight <= 0)
                throw new AgentFault("WEIGHT_NOT_POSITIVE", "请称得大于零的净重后记录");
            return selected;
        }
    }
}
