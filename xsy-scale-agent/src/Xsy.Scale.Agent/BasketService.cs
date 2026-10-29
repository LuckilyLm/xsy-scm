namespace Xsy.Scale.Agent;

/// <summary>本机多筐编排，只持久保存事实和意图，不代替平台的分拣状态机。</summary>
internal sealed class BasketService(CaptureService captures, StateStore store, AgentOptions options, WorkstationOptions? workstation = null)
{
    /// <summary>采集一筐并建立待对接队列，尚未授权的打印不会执行。</summary>
    /// <param name="request">非空采集请求，重量只能引用真实采样，不能手填。</param>
    /// <param name="session">来自请求头的非空员工会话，必须与请求体相同。</param>
    /// <returns>持久筐记录和等待对接/打印授权的状态。</returns>
    /// <exception cref="AgentFault">会话不匹配、设备或任务校验失败、采样重复或已完成。</exception>
    internal BasketRecord Record(CaptureRequest request, string session)
    {
        MatchSession(session, request.OperatorSessionId);
        TaskItem item = captures.ValidateContext(request);
        WorkstationOptions policy = workstation ?? new();
        return store.RecordBasket(request, () => captures.CreateEvent(request, captures.ValidateContext(request)),
            policy.LocalTicketsEnabled && request.PrintRequested != false ? record => CreateTicket(record, item, request) : null);
    }

    /// <summary>保存其他大秤已称好的人工净重，不读取本机秤，不伪造设备稳定事实。</summary>
    /// <param name="input">完整任务上下文；净重为正数市斤、最多四位小数，必须显式确认已扣皮重；备注可空。</param>
    /// <param name="session">有效员工会话，非空且必须与请求体一致。</param>
    /// <returns>与设备记录共享序号、累计和打印事务的独立记录；重复请求返回原记录。</returns>
    /// <exception cref="AgentFault">数字非法、未确认净重、单位错误、越权、操作号冲突或明细已完成。</exception>
    internal BasketRecord RecordManual(ManualBasketRequest input, string session)
    {
        MatchSession(session, input.OperatorSessionId);
        // 这是人工记录键，不是设备采样编号；完整手工请求参与摘要，改重量重试必须报冲突。
        var context = new CaptureRequest(input.OperationId, "MANUAL:" + WireJson.Digest(input.OperationId), input.TaskId,
            input.TaskItemId, input.OperatorSessionId, input.JobId, input.JobVersion, input.TaskItemVersion, input.PrintRequested);
        TaskItem item = captures.ValidateContext(context);
        if (!MassUnits.IsJin(item.SaleUnit))
            throw new AgentFault("MANUAL_UNIT_MISMATCH", "手工录入仅用于按斤核算的任务，请核对商品销售单位");
        if (!input.ConfirmedNetWeight)
            throw new AgentFault("NET_WEIGHT_CONFIRMATION_REQUIRED", "请核对客户、商品，并确认输入的是已扣除筐重的净重");
        string text = input.NetWeight?.Trim() ?? "";
        if (!System.Text.RegularExpressions.Regex.IsMatch(text, @"^[0-9]{1,14}(\.[0-9]{1,4})?$")
            || !decimal.TryParse(text, System.Globalization.NumberStyles.AllowDecimalPoint,
                System.Globalization.CultureInfo.InvariantCulture, out decimal weight) || weight <= 0)
            throw new AgentFault("INVALID_MANUAL_WEIGHT", "请输入大于零的净重，最多14位整数和4位小数，不自动舍入");
        if (input.Note?.Length > 200)
            throw new AgentFault("INVALID_MANUAL_NOTE", "称重说明不能超过200字");
        WorkstationOptions policy = workstation ?? new();
        return store.RecordBasket(context, () =>
        {
            captures.ValidateContext(context);
            string eventKey = $"{options.DeviceCode}:{Guid.NewGuid():N}";
            string value = weight.ToString(System.Globalization.CultureInfo.InvariantCulture);
            var measurement = new Measurement("1", eventKey, options.DeviceCode, options.StationCode, store.SessionId,
                input.JobId, input.JobVersion, input.TaskId, input.TaskItemId, input.TaskItemVersion, session,
                DateTimeOffset.UtcNow, value, "斤", value, item.SaleUnit, "NET", false, "NOT_APPLICABLE",
                "IDENTITY", null, "", "MANUAL_ENTRY", WireJson.Version, "MANUAL",
                string.IsNullOrWhiteSpace(input.Note) ? "其他大秤称重" : input.Note.Trim());
            return new LocalEvent(eventKey, input.OperationId, "", measurement, "", "CAPTURED_LOCAL", 0,
                DateTimeOffset.UtcNow, null, null);
        }, policy.LocalTicketsEnabled && input.PrintRequested != false ? record => CreateTicket(record, item, context) : null, WireJson.Digest(input));
    }

    // 两种来源共用同一冻结模板；来源随票保存，补打和升级不会改写历史。
    private LocalTicket CreateTicket(BasketRecord record, TaskItem item, CaptureRequest request)
    {
        WorkstationOptions policy = workstation ?? new();
        if (!policy.LocalTest && string.IsNullOrWhiteSpace(options.PrinterName))
            throw new AgentFault("PRINTER_NOT_CONFIGURED", "请先配置凭条打印机，或关闭本地凭条后只保存重量");
        string taskNo = store.CachedTasks(request.OperatorSessionId)!.Tasks.First(t => t.JobId == request.JobId).TaskNo;
        var snapshot = new TicketSnapshot(record.EventKey, taskNo, item.ProductName, item.SpecName,
            item.CustomerName, item.OrderNo, record.BasketNumber, record.Measurement.NetWeight,
            MassUnits.Display(record.Measurement.BusinessUnit), record.Measurement.TareWeight, options.DeviceCode,
            record.Measurement.CapturedAt, policy.LocalTest,
            policy.HardwareTest ? "HARDWARE_TEST_6040_TRACE_V2" : "LOCAL_PENDING_TRACE_V2",
            record.EventKey, TraceLink.Build(policy.TraceUrlTemplate, record.EventKey), record.Measurement.EntrySource);
        return new(record.PrintIntentId, record.OperationId,
            WireJson.Digest(new BasketScope(request.OperatorSessionId, request.JobId, request.TaskId, request.TaskItemId)),
            snapshot, "QUEUED", options.PrinterName, policy.PaperWidthMm, policy.PaperHeightMm,
            false, null, DateTimeOffset.UtcNow);
    }

    /// <summary>保存商品完成意图，不取新重量、不调用员工接受接口。</summary>
    /// <param name="request">非空请求；摘要来自最新累计，结果沿用平台现有枚举。</param>
    /// <param name="session">头中的有效员工会话引用，必须与请求体相同。</param>
    /// <returns>固定筐集合、本地累计与完成时间，等待平台扩展契约确认。</returns>
    /// <exception cref="AgentFault">会话失效、结果不合法、缺少原因、集合变化或已完成。</exception>
    internal ItemCompletion Complete(CompleteItemRequest request, string session)
    {
        MatchSession(session, request.OperatorSessionId);
        captures.ValidateContext(new(request.OperationId, "completion-without-sampling", request.TaskId,
            request.TaskItemId, request.OperatorSessionId, request.JobId, request.JobVersion, request.TaskItemVersion));
        if (request.ManifestHash == null || request.ManifestHash.Length != 64 || !request.ManifestHash.All(Uri.IsHexDigit))
            throw new AgentFault("INVALID_MANIFEST", "请使用最新累计响应中的完整筐集合摘要");
        if (request.Result is not ("NORMAL" or "SHORT" or "OUT_OF_STOCK" or "OVER"))
            throw new AgentFault("INVALID_SORTING_RESULT", "请选择平台支持的分拣结果");
        string? reason = string.IsNullOrWhiteSpace(request.Reason) ? null : request.Reason.Trim();
        if (reason?.Length > 500 || (request.Result != "NORMAL" && reason == null))
            throw new AgentFault("SORTING_REASON_REQUIRED", "非正常结果需填写不超过500字符的原因");
        return store.CompleteBaskets(request with { Reason = reason });
    }

    /// <summary>查询当前范围的完整本地累计和平台缓存快照。</summary>
    /// <param name="scope">非空当前会话范围，任务和明细必须存在于该会话缓存。</param>
    /// <returns>过期缓存允许查询但显式标记失效，不授予新操作权限。</returns>
    /// <exception cref="AgentFault">范围缺失或任务不在会话可见范围。</exception>
    internal BasketSummary Summary(BasketScope scope)
    {
        var context = ReadContext(scope);
        return store.SummarizeBaskets(scope, context.Item, context.Expired);
    }

    /// <summary>分页查询本商品已落盘筐记录，不从当前页推算累计。</summary>
    /// <param name="scope">非空并属于当前员工缓存的范围。</param>
    /// <param name="after">排他筐号，默认0，不可负数。</param>
    /// <param name="limit">页大小1至100，默认50。</param>
    /// <returns>按筐号升序的当前页及排他下一游标。</returns>
    /// <exception cref="AgentFault">权限范围不可见或分页越界。</exception>
    internal BasketPage Records(BasketScope scope, int after = 0, int limit = 50)
    {
        ReadContext(scope);
        if (after < 0 || limit is < 1 or > 100)
            throw new AgentFault("INVALID_PAGINATION", "筐号至少为0，页大小为1至100");
        return store.BasketRecords(scope, after, limit);
    }

    /// <summary>分页查询本地上传/完成意图，未冻结正式接口时只显示等待对接。</summary>
    /// <param name="scope">非空当前员工可见范围。</param>
    /// <param name="after">排他命令序号，默认0，不可负数。</param>
    /// <param name="limit">返回上限1至100，默认50。</param>
    /// <returns>命令摘要；完成命令携带必须先获平台受理的筐事件键。</returns>
    /// <exception cref="AgentFault">可见范围或分页参数不合格。</exception>
    internal BasketCommand[] Queue(BasketScope scope, long after = 0, int limit = 50)
    {
        ReadContext(scope);
        if (after < 0 || limit is < 1 or > 100)
            throw new AgentFault("INVALID_PAGINATION", "命令序号至少为0，页大小为1至100");
        return store.BasketQueue(scope, after, limit);
    }

    private (TaskItem Item, bool Expired) ReadContext(BasketScope scope)
    {
        if (new[] { scope.OperatorSessionId, scope.JobId, scope.TaskId, scope.TaskItemId }
            .Any(s => string.IsNullOrWhiteSpace(s) || s.Length > 160))
            throw new AgentFault("INVALID_BASKET_SCOPE", "请提供完整员工会话、作业、任务及明细");
        TaskBatch? cache = store.CachedTasks(scope.OperatorSessionId);
        TaskSnapshot? job = cache?.Tasks.FirstOrDefault(t => t.JobId == scope.JobId && t.TaskId == scope.TaskId);
        TaskItem? item = job?.Items.FirstOrDefault(i => i.TaskItemId == scope.TaskItemId);
        if (item == null || job == null)
            throw new AgentFault("TASK_ITEM_NOT_VISIBLE", "当前员工缓存中没有该任务明细");
        bool expired = cache!.OperatorSessionExpiresAt <= DateTimeOffset.UtcNow || job.ExpiresAt <= DateTimeOffset.UtcNow
            || job.ClaimedByDeviceCode != options.DeviceCode || !item.Active || store.Setting("suspended") == "true";
        return (item, expired);
    }

    private static void MatchSession(string header, string body)
    {
        if (string.IsNullOrWhiteSpace(header) || header.Length > 160 || header != body)
            throw new AgentFault("OPERATOR_SESSION_MISMATCH", "请求头与请求体员工会话必须一致");
    }
}
