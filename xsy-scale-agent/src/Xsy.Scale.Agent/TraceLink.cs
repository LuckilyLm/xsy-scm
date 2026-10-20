using QRCoder;

namespace Xsy.Scale.Agent;

/// <summary>本地记录到未来平台公开页面的链接适配；不签发平台包装码或证明溯源业务已上线。</summary>
internal static class TraceLink
{
    /// <summary>替换唯一占位符并检查标签二维码容量；不访问网址或调用平台。</summary>
    /// <param name="template">空值/空白表示待配置；非空须是含一个{eventKey}的HTTPS链接模板。</param>
    /// <param name="eventKey">非空本地原始记录号，按URL组件编码；不得替换为平台正式unitCode。</param>
    /// <returns>可冻结的完整链接；未配置时为null。</returns>
    /// <exception cref="AgentFault">模板无效或无法在15.5mm二维码中保持至少0.30mm模块。</exception>
    internal static string? Build(string? template, string eventKey)
    {
        if (string.IsNullOrWhiteSpace(template)) return null;
        string value = template.Trim();
        if (value.Length > 240 || value.Split("{eventKey}").Length != 2)
            throw new AgentFault("INVALID_TRACE_TEMPLATE", "查询地址须包含一个{eventKey}占位符");
        string result = value.Replace("{eventKey}", Uri.EscapeDataString(eventKey), StringComparison.Ordinal);
        if (result.Contains('{') || result.Contains('}') || result.Any(char.IsControl)
            || !Uri.TryCreate(result, UriKind.Absolute, out var uri) || uri.Scheme != "https"
            || string.IsNullOrWhiteSpace(uri.Host) || !string.IsNullOrEmpty(uri.UserInfo)
            || (value.IndexOf('#') >= 0 && value.IndexOf("{eventKey}", StringComparison.Ordinal) > value.IndexOf('#')))
            throw new AgentFault("INVALID_TRACE_TEMPLATE", "请配置HTTPS查询页，记录号占位符须在路径或查询参数中");
        // 禁止把事件号拼进主机名；域名、凭证与正式业务授权由平台实施人员负责。
        string probe = value.Replace("{eventKey}", "XSY-REFERENCE", StringComparison.Ordinal);
        if (!Uri.TryCreate(probe, UriKind.Absolute, out var fixedUri) || uri.Authority != fixedUri.Authority)
            throw new AgentFault("INVALID_TRACE_TEMPLATE", "查询地址的主机名必须固定");
        using var qr = Matrix(result);
        return result;
    }

    /// <summary>按标准库生成含四模块静区的矩阵；超出短标签容量时明确拒绝。</summary>
    /// <param name="url">非空已校验冻结链接；不允许自动缩网址或裁切。</param>
    /// <returns>调用方须释放的矩阵。</returns>
    /// <exception cref="AgentFault">二维码过密，不满足当前标签的模块尺寸。</exception>
    internal static QRCodeData Matrix(string url)
    {
        var data = QRCodeGenerator.GenerateQrCode(url, QRCodeGenerator.ECCLevel.M);
        if (data.ModuleMatrix.Count > 49)
        {
            data.Dispose();
            throw new AgentFault("TRACE_URL_TOO_LONG", "查询地址过长，请由平台提供更短的公开查询链接");
        }
        return data;
    }
}
