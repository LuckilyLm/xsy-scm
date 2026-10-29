namespace Xsy.Scale.Agent;

/// <summary>市斤协议别名与展示规则；同单位别名只改编码，不改变重量数值。</summary>
internal static class MassUnits
{
    /// <summary>判断是否是大陆市斤，未知或空值返回false，不把磅、公斤或件归为斤。</summary>
    /// <param name="unit">设备或任务单位；允许sj、斤、市斤，空值表示未知。</param>
    /// <returns>是否与500克的市斤相同。</returns>
    internal static bool IsJin(string? unit) => unit is "sj" or "斤" or "市斤";

    /// <summary>匹配同一物理单位，禁止仅通过修改标签把公斤数值当成斤。</summary>
    /// <param name="raw">已识别设备单位；空值不匹配。</param>
    /// <param name="business">任务快照中的销售单位；空值不匹配。</param>
    /// <returns>单位相同或均为市斤别名时true。</returns>
    internal static bool Matches(string? raw, string? business) =>
        !string.IsNullOrEmpty(raw) && !string.IsNullOrEmpty(business) && (raw == business || IsJin(raw) && IsJin(business));

    /// <summary>格式化用户可见单位，不改变持久化的原始协议编码。</summary>
    /// <param name="unit">冻结单位；空值显示未知，公斤历史保留原单位。</param>
    /// <returns>市斤统一显示斤，其他单位原样显示。</returns>
    internal static string Display(string? unit) => IsJin(unit) ? "斤" : unit ?? "未知";
}
