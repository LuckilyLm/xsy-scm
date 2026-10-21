using System.Text;
using Xsy.Scale.Agent;

/// <summary>厂家指令及控制采样隔离回归；不打开任何真实串口或打印队列。</summary>
internal static class ScaleControlTests
{
    /// <summary>返回本地控制正常、边界与故障恢复用例。</summary>
    /// <returns>只用虚拟时钟及真实形状串口帧的可执行用例。</returns>
    internal static (string Name, Func<Task> Run)[] Cases() =>
    [
        ("厂家去皮置零严格按协议编码无回车", () => Run(() =>
        {
            Equal("54", Convert.ToHexString(SoheControlProtocol.Encode("tare", "SOHE_RS232_V101", "")));
            Equal("5A", Convert.ToHexString(SoheControlProtocol.Encode("zero", "SOHE_RS232_V101", "")));
            Equal("02415403", Convert.ToHexString(SoheControlProtocol.Encode("tare", "SOHE_AD", "A")));
            Equal("02425A03", Convert.ToHexString(SoheControlProtocol.Encode("zero", "SOHE_AD", "B")));
            Throws("INVALID_COMMAND", () => SoheControlProtocol.Encode("calibrate", "SOHE_RS232_V101", "A"));
            Throws("INVALID_ADDRESS", () => SoheControlProtocol.Encode("zero", "SOHE_AD", "AB"));
            Throws("PROTOCOL_UNSUPPORTED", () => SoheControlProtocol.Encode("zero", "other", "A"));
        })),
        ("控制清旧样本与500ms隔离且拒绝重复命令", () => Run(() =>
        {
            var (clock, state) = Context(); var old = Reading(clock); state.Set(old);
            var control = state.BeginControl("tare", 2);
            Equal("SENDING", control.State); Equal<WeightReading?>(null, state.Snapshot().Reading);
            Throws("CONTROL_IN_PROGRESS", () => state.BeginControl("zero", 2));
            state.Set(Reading(clock, 0)); Equal<WeightReading?>(null, state.Snapshot().Reading);
            state.ControlSent(control.ControlId); clock.Advance(499); state.Set(Reading(clock, 0));
            Equal("WAITING", state.ControlSnapshot()!.State);
            Throws("CONTROL_IN_PROGRESS", () => state.Freeze(old.SampleId, new()));
            clock.Advance(1); var zero = Reading(clock, 0); state.Set(zero);
            Equal("ZERO_OBSERVED", state.ControlSnapshot()!.State); Equal(zero.SampleId, state.ControlSnapshot()!.ObservedSampleId);
            var next = Reading(clock, 8.84m); state.Set(next);
            Equal(8.84m, state.Freeze(next.SampleId, new()).RawWeight);
            Throws("SAMPLE_CHANGED", () => state.Freeze(old.SampleId, new()));
            Equal<decimal?>(null, state.Snapshot().Reading!.TareWeight);
        })),
        ("去皮需稳定净重零不能用毛重或不稳定零替代", () => Run(() =>
        {
            var (clock, state) = Context(); state.Set(Reading(clock, .28m, "GROSS"));
            state.ControlSent(state.BeginControl("tare", 2).ControlId); clock.Advance(500);
            state.Set(Reading(clock, 0, "GROSS")); Equal("WAITING", state.ControlSnapshot()!.State);
            state.Set(Reading(clock, 0, stable: false)); Equal("WAITING", state.ControlSnapshot()!.State);
            state.Set(Reading(clock, 0)); Equal("ZERO_OBSERVED", state.ControlSnapshot()!.State);
            Throws("METROLOGY_LIMIT", () => state.Freeze(state.Snapshot().Reading!.SampleId, new()));
        })),
        ("置零可观察毛重零但不放行毛重采集", () => Run(() =>
        {
            var (clock, state) = Context(); state.Set(Reading(clock, .1m, "GROSS"));
            state.ControlSent(state.BeginControl("zero", 2).ControlId); clock.Advance(500);
            var zero = Reading(clock, 0, "GROSS"); state.Set(zero);
            Equal("ZERO_OBSERVED", state.ControlSnapshot()!.State); Equal("GROSS", state.ControlSnapshot()!.ObservedMode);
            Throws("WEIGHT_UNACCEPTABLE", () => state.Freeze(zero.SampleId, new()));
        })),
        ("五秒未见零值继续阻止采集后续真实零可恢复", () => Run(() =>
        {
            var (clock, state) = Context(); state.Set(Reading(clock));
            state.ControlSent(state.BeginControl("tare", 2).ControlId); clock.Advance(5000);
            var sample = Reading(clock, 8.84m); state.Set(sample);
            Equal("UNCONFIRMED", state.ControlSnapshot()!.State);
            Equal("CONTROL_NOT_CONFIRMED", state.ControlSnapshot()!.ErrorCode);
            Throws("CONTROL_UNCONFIRMED", () => state.Freeze(sample.SampleId, new()));
            Equal(1, state.ControlHistory().Length);
            state.Set(Reading(clock, 0)); Equal("ZERO_OBSERVED", state.ControlSnapshot()!.State);
            sample = Reading(clock, 8.86m); state.Set(sample); Equal(8.86m, state.Freeze(sample.SampleId, new()).RawWeight);
        })),
        ("失联或发送结果未知保持阻断但允许明确再次操作", () => Run(() =>
        {
            var (clock, state) = Context(); state.Set(Reading(clock));
            var control = state.BeginControl("tare", 2); state.Fail("SCALE_DISCONNECTED");
            Equal("UNCONFIRMED", state.ControlSnapshot()!.State);
            Throws("CONTROL_CHANGED", () => state.ControlSent(control.ControlId));
            state.ControlUnconfirmed(control.ControlId); clock.Advance(500); state.Set(Reading(clock));
            Throws("CONTROL_UNCONFIRMED", () => state.Freeze(state.Snapshot().Reading!.SampleId, new()));
            control = state.BeginControl("zero", 2); state.ControlUnconfirmed(control.ControlId);
            clock.Advance(500); state.Set(Reading(clock, 0));
            // 未确认发送是否发生，不用偶然零值冒充这次成功；须再明确操作。
            Equal("UNCONFIRMED", state.ControlSnapshot()!.State);
            control = state.BeginControl("zero", 2); state.ControlSent(control.ControlId); clock.Advance(500);
            state.Set(Reading(clock, 0)); Equal("ZERO_OBSERVED", state.ControlSnapshot()!.State);
        })),
        ("无效陈旧不稳定采样拒绝控制且不创建历史", () => Run(() =>
        {
            var (clock, state) = Context();
            Throws("SCALE_UNAVAILABLE", () => state.BeginControl("tare", 2));
            state.Set(Reading(clock, stable: false)); Throws("WEIGHT_UNACCEPTABLE", () => state.BeginControl("tare", 2));
            state.Set(Reading(clock)); clock.Advance(2001); Throws("SCALE_UNAVAILABLE", () => state.BeginControl("zero", 2));
            Throws("INVALID_COMMAND", () => state.BeginControl("invalid", 2)); Equal(0, state.ControlHistory().Length);
        })),
        ("零观测拒绝旧时间戳与未知单位保留最近20次", () => Run(() =>
        {
            var (clock, state) = Context();
            for (int i = 0; i < 22; i++)
            {
                state.Set(Reading(clock)); var control = state.BeginControl("zero", 2); state.ControlSent(control.ControlId);
                var old = Reading(clock, 0); clock.Advance(500); state.Set(old);
                Equal("WAITING", state.ControlSnapshot()!.State);
                state.Set(Reading(clock, 0) with { RawUnit = "unknown" }); Equal("WAITING", state.ControlSnapshot()!.State);
                state.Set(Reading(clock, 0)); Equal("ZERO_OBSERVED", state.ControlSnapshot()!.State);
            }
            Equal(20, state.ControlHistory().Length);
            var history = state.ControlHistory(); history[0] = history[0] with { State = "corrupted" };
            Equal("ZERO_OBSERVED", state.ControlHistory()[0].State);
        })),
        ("串口控制清半帧不把旧尾字节拼成新样本", () => Run(() =>
        {
            var decoder = new SoheFrameDecoder();
            Equal(0, decoder.Feed(Encoding.ASCII.GetBytes("sn0008.")).Count); decoder.Reset();
            Equal(0, decoder.Feed(Encoding.ASCII.GetBytes("84kg\r\n")).Count);
            var readings = decoder.Feed(Encoding.ASCII.GetBytes("sn0000.00kg\r\n"));
            Equal(1, readings.Count); Equal(0m, readings[0].RawWeight);
        }))
    ];

    private static (TestClock Clock, ScaleState State) Context() { var clock = new TestClock(); return (clock, new ScaleState(clock)); }
    private static WeightReading Reading(TestClock clock, decimal weight = 5m, string mode = "NET", bool stable = true)
        => SoheFrameDecoder.Reading(weight, "kg", stable, "DEVICE_FRAME", mode, null, []) with { CapturedAt = clock.GetUtcNow() };
    private static Task Run(Action action) { action(); return Task.CompletedTask; }
    private static void Equal<T>(T expected, T actual)
    { if (!EqualityComparer<T>.Default.Equals(expected, actual)) throw new Exception($"Expected {expected}, actual {actual}"); }
    private static void Throws(string code, Action action)
    { try { action(); } catch (AgentFault fault) when (fault.Code == code) { return; } throw new Exception("Expected " + code); }

    /// <summary>只推进本地回归时间，不等待真实设备或修改系统时钟。</summary>
    private sealed class TestClock : TimeProvider
    {
        private DateTimeOffset now = DateTimeOffset.UtcNow;
        public override DateTimeOffset GetUtcNow() => now;
        internal void Advance(int milliseconds) => now = now.AddMilliseconds(milliseconds);
    }
}
