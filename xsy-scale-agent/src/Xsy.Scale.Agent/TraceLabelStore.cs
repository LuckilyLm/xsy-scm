namespace Xsy.Scale.Agent;

internal sealed partial class StateStore
{
    internal BasketRecord TraceBasket(BasketScope scope, string eventKey)
    {
        using var db = Open();
        return ReadBaskets(db, null, WireJson.Digest(scope)).SingleOrDefault(row => row.EventKey == eventKey)
            ?? throw new AgentFault("BASKET_NOT_FOUND", "本作业中没有这条称重记录");
    }

    internal LocalTicket? TraceTicket(BasketScope scope, TraceLabelRequest request)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT payload,state,spooler_job_id,error_code,request_hash FROM local_tickets WHERE operation_id=$op";
        command.Parameters.AddWithValue("$op", request.OperationId);
        using var row = command.ExecuteReader();
        if (!row.Read()) return null;
        LocalTicket ticket = ReadTicket(row);
        if (ticket.ScopeKey != WireJson.Digest(scope) || row.GetString(4) != WireJson.Digest(request))
            throw new AgentFault("OPERATION_CONFLICT", "打印请求内容已变化，请核对原尝试");
        return ticket;
    }

    internal LocalTicket SaveTraceTicket(BasketScope scope, TraceLabelRequest request, LocalTicket ticket)
    {
        lock (gate)
        {
            LocalTicket? existing = TraceTicket(scope, request);
            if (existing != null) return existing;
            if (HasBasketOperation(request.OperationId) || ByOperation(request.OperationId) != null)
                throw new AgentFault("OPERATION_CONFLICT", "打印编号不能复用称重操作编号");
            var attempts = LocalTickets(scope).Where(value => value.Snapshot.EventKey == request.EventKey).ToArray();
            if (attempts.Any(value => value.State is "QUEUED" or "SUBMITTING"))
                throw new AgentFault("PRINT_IN_PROGRESS", "该筐仍有打印任务，请等待后重试");
            if (attempts.Any(value => value.Snapshot.TemplateVersion == "PLATFORM_TRACE_V1"))
                throw new AgentFault("TRACE_LABEL_EXISTS", "该筐已有正式标签，请核对后使用补打");
            using var db = Open(); using var transaction = db.BeginTransaction();
            InsertLocalTicket(db, transaction, ticket, WireJson.Digest(request));
            transaction.Commit();
            return ticket;
        }
    }
}
