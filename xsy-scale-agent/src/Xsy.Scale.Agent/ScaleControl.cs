using System.Globalization;

namespace Xsy.Scale.Agent;

/// <summary>一次人工秤控制的观测结果；零读数仅为后续设备观测，不冒充硬件执行回执。</summary>
internal sealed record ScaleControlStatus(string ControlId, string Command, string State,
    DateTimeOffset RequestedAt, DateTimeOffset? SentAt = null, DateTimeOffset? ObservedAt = null,
    string? ObservedSampleId = null, string? ObservedWeight = null, string? ObservedUnit = null,
    string? ObservedMode = null, string? ErrorCode = null);

/// <summary>厂家控制字节编码；不提供标定或其他任意串口写入能力。</summary>
internal static class SoheControlProtocol
{
    /// <summary>按已配置协议生成去皮或置零指令，不附加回车换行。</summary>
    /// <param name="command">仅tare或zero，不允许null、空白或其他指令。</param>
    /// <param name="protocol">仅SOHE_RS232_V101或SOHE_AD；其他值拒绝。</param>
    /// <param name="address">AD时必须是单字节ASCII地址；RS232时不使用，可为空。</param>
    /// <returns>RS232单字节或AD四字节协议帧。</returns>
    /// <exception cref="AgentFault">命令、协议或AD地址不合法。</exception>
    internal static byte[] Encode(string command, string protocol, string address)
    {
        byte instruction = command switch { "tare" => 0x54, "zero" => 0x5a,
            _ => throw new AgentFault("INVALID_COMMAND", "仅支持人工去皮和置零") };
        if (protocol == "SOHE_RS232_V101") return [instruction];
        if (protocol != "SOHE_AD") throw new AgentFault("PROTOCOL_UNSUPPORTED", "当前协议不支持串口控制");
        if (address == null || address.Length != 1 || address[0] > 127)
            throw new AgentFault("INVALID_ADDRESS", "AD地址必须为单个ASCII字符");
        return [2, (byte)address[0], instruction, 3];
    }
}

/// <summary>秤控制期间隔离旧样本，并用后续真实稳定零读数解除采集阻断。</summary>
internal sealed partial class ScaleState
{
    private readonly TimeProvider clock = timeProvider ?? TimeProvider.System;
    private ScaleControlStatus? control;
    private readonly List<ScaleControlStatus> controlHistory = [];

    /// <summary>开始一次明确的人工控制，清空可采集的旧样本。</summary>
    /// <param name="command">仅tare或zero；空值或其他指令拒绝。</param>
    /// <param name="maxAgeSeconds">当前采样时效，必须为正数；陈旧、不稳或失联时不发命令。</param>
    /// <returns>新的SENDING操作；存在未结束操作则拒绝，不自动重复发送。</returns>
    /// <exception cref="AgentFault">指令、采样或当前操作状态不允许控制。</exception>
    internal ScaleControlStatus BeginControl(string command, int maxAgeSeconds)
    {
        lock (gate)
        {
            if (command is not ("tare" or "zero")) throw new AgentFault("INVALID_COMMAND", "仅支持人工去皮和置零");
            UpdateControlTimeout();
            if (control?.State is "SENDING" or "WAITING") throw new AgentFault("CONTROL_IN_PROGRESS", "正在等待上次操作的设备读数，请勿重复点击");
            if (maxAgeSeconds <= 0 || error != null || reading == null || clock.GetUtcNow() - reading.CapturedAt > TimeSpan.FromSeconds(maxAgeSeconds))
                throw new AgentFault("SCALE_UNAVAILABLE", "请等待电子秤连接并返回新读数");
            if (reading.StableFlag != true) throw new AgentFault("WEIGHT_UNACCEPTABLE", "请等待重量稳定后去皮或置零");
            control = new(Guid.NewGuid().ToString("N"), command, "SENDING", clock.GetUtcNow());
            controlHistory.Add(control); if (controlHistory.Count > 20) controlHistory.RemoveAt(0);
            reading = null; recent.Clear(); error = "SCALE_CONTROL_PENDING";
            blockedUntil = DateTimeOffset.MaxValue;
            return control;
        }
    }

    /// <summary>发送完成后开始观察窗口；不因写入成功而声称硬件执行成功。</summary>
    /// <param name="controlId">本次操作的非空ID；不匹配当前操作时拒绝。</param>
    /// <returns>WAITING快照，后续至少隔离500ms再观察新帧。</returns>
    /// <exception cref="AgentFault">操作已经被替代或ID不匹配。</exception>
    internal ScaleControlStatus ControlSent(string controlId)
    {
        lock (gate)
        {
            if (control == null || control.ControlId != controlId || control.State != "SENDING") throw new AgentFault("CONTROL_CHANGED", "控制操作已变化");
            DateTimeOffset sentAt = clock.GetUtcNow(); blockedUntil = sentAt.AddMilliseconds(500);
            SetControl(control with { State = "WAITING", SentAt = sentAt }); return control!;
        }
    }

    /// <summary>发送异常保留未确认状态，不重试命令，不恢复旧采样。</summary>
    /// <param name="controlId">本次操作ID；过期或不匹配则不覆盖新操作。</param>
    internal void ControlUnconfirmed(string controlId)
    {
        lock (gate)
        {
            if (control?.ControlId != controlId) return;
            blockedUntil = clock.GetUtcNow().AddMilliseconds(500);
            SetControl(control with { State = "UNCONFIRMED", ErrorCode = "CONTROL_RESULT_UNKNOWN" });
        }
    }

    /// <summary>读取当前控制结果；没有执行过控制时返回null。</summary>
    internal ScaleControlStatus? ControlSnapshot() { lock (gate) { UpdateControlTimeout(); return control; } }

    /// <summary>返回本进程最近20次控制的只读副本；重启后不保留，也不伪造历史成功。</summary>
    internal ScaleControlStatus[] ControlHistory() { lock (gate) { UpdateControlTimeout(); return controlHistory.ToArray(); } }

    private void SetControl(ScaleControlStatus value) { control = value; controlHistory[^1] = value; }
    private void UpdateControlTimeout()
    {
        if (control?.State == "WAITING" && control.SentAt is { } sentAt && clock.GetUtcNow() - sentAt >= TimeSpan.FromSeconds(5))
            SetControl(control with { State = "UNCONFIRMED", ErrorCode = "CONTROL_NOT_CONFIRMED" });
    }
    private void ObserveControl(WeightReading next)
    {
        UpdateControlTimeout();
        if (control?.State is not ("WAITING" or "UNCONFIRMED") || control.SentAt == null
            || next.CapturedAt < blockedUntil || next.CapturedAt > clock.GetUtcNow() || clock.GetUtcNow() - next.CapturedAt > TimeSpan.FromSeconds(2)
            || next.StableFlag != true || next.RawWeight != 0 || next.RawUnit is not ("kg" or "g" or "sj")
            || (control.Command == "tare" ? next.NetGrossFlag != "NET" : next.NetGrossFlag is not ("NET" or "GROSS"))) return;
        SetControl(control with { State = "ZERO_OBSERVED", ObservedAt = clock.GetUtcNow(), ObservedSampleId = next.SampleId,
            ObservedWeight = next.RawWeight.ToString(CultureInfo.InvariantCulture), ObservedUnit = next.RawUnit,
            ObservedMode = next.NetGrossFlag, ErrorCode = null });
    }
}
