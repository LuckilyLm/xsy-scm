using Microsoft.Data.Sqlite;

namespace Xsy.Scale.Agent;

/// <summary>本地凭条尝试追加保存，与平台printAttemptId和正式票据分开。</summary>
internal sealed partial class StateStore
{
    /// <summary>演练首次标记前检查是否已有业务事实，拒绝把旧现场库当作演练库。</summary>
    /// <returns>任一采集、打印或本筐表非空即为true。</returns>
    internal bool HasStoredFacts()
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT (SELECT COUNT(*) FROM events)+(SELECT COUNT(*) FROM basket_records)+(SELECT COUNT(*) FROM print_jobs)";
        return Convert.ToInt64(command.ExecuteScalar()) != 0;
    }

    private void InitializeLocalTickets(SqliteConnection db)
    {
        using var command = db.CreateCommand();
        command.CommandText = """
            CREATE TABLE IF NOT EXISTS local_tickets (
              attempt_id TEXT PRIMARY KEY, operation_id TEXT NOT NULL UNIQUE, request_hash TEXT NOT NULL,
              event_key TEXT NOT NULL, scope_key TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL,
              spooler_job_id TEXT, error_code TEXT);
            CREATE INDEX IF NOT EXISTS ix_local_ticket_scope ON local_tickets(scope_key);
            CREATE INDEX IF NOT EXISTS ix_local_ticket_queue ON local_tickets(state);
            UPDATE basket_records SET print_state='UNKNOWN' WHERE event_key IN
              (SELECT event_key FROM local_tickets WHERE state='SUBMITTING');
            UPDATE local_tickets SET state='UNKNOWN',error_code='AGENT_RESTARTED' WHERE state='SUBMITTING';
            """;
        command.ExecuteNonQuery();
    }

    private static void InsertLocalTicket(SqliteConnection db, SqliteTransaction transaction, LocalTicket ticket, string hash)
    {
        using var command = db.CreateCommand(); command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO local_tickets(attempt_id,operation_id,request_hash,event_key,scope_key,payload,state)
            VALUES($id,$op,$hash,$event,$scope,$payload,'QUEUED');
            UPDATE basket_records SET print_state='QUEUED' WHERE event_key=$event;
            """;
        command.Parameters.AddWithValue("$id", ticket.AttemptId);
        command.Parameters.AddWithValue("$op", ticket.OperationId);
        command.Parameters.AddWithValue("$hash", hash);
        command.Parameters.AddWithValue("$event", ticket.Snapshot.EventKey);
        command.Parameters.AddWithValue("$scope", ticket.ScopeKey);
        command.Parameters.AddWithValue("$payload", WireJson.Encode(ticket));
        command.ExecuteNonQuery();
    }

    /// <summary>查询当前员工范围的本地凭条尝试，不读取其他员工记录。</summary>
    /// <param name="scope">非空已通过员工可见性检查的范围。</param>
    /// <returns>按追加顺序的全部尝试，状态由独立列覆盖冻结payload。</returns>
    internal LocalTicket[] LocalTickets(BasketScope scope)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT payload,state,spooler_job_id,error_code FROM local_tickets WHERE scope_key=$scope ORDER BY rowid";
        command.Parameters.AddWithValue("$scope", WireJson.Digest(scope));
        using var rows = command.ExecuteReader(); var result = new List<LocalTicket>();
        while (rows.Read()) result.Add(ReadTicket(rows));
        return result.ToArray();
    }

    /// <summary>原子取出下一次首次执行，提交前落盘SUBMITTING，崩溃后只恢复UNKNOWN。</summary>
    /// <returns>没有待执行尝试时为null；返回后不得按同尝试再次调用驱动。</returns>
    internal LocalTicket? TakeLocalTicket()
    {
        lock (gate)
        {
            using var db = Open(); using var transaction = db.BeginTransaction();
            using var command = db.CreateCommand(); command.Transaction = transaction;
            command.CommandText = "SELECT payload,state,spooler_job_id,error_code FROM local_tickets WHERE state='QUEUED' ORDER BY rowid LIMIT 1";
            LocalTicket ticket;
            using (var row = command.ExecuteReader()) { if (!row.Read()) return null; ticket = ReadTicket(row); }
            command.CommandText = """
                UPDATE local_tickets SET state='SUBMITTING' WHERE attempt_id=$id;
                UPDATE basket_records SET print_state='SUBMITTING' WHERE event_key=$event;
                """;
            command.Parameters.AddWithValue("$id", ticket.AttemptId);
            command.Parameters.AddWithValue("$event", ticket.Snapshot.EventKey);
            command.ExecuteNonQuery(); transaction.Commit();
            return ticket with { State = "SUBMITTING" };
        }
    }

    /// <summary>固定一次驱动观察结果，失败不重新入队。</summary>
    /// <param name="ticket">本次已经取出的非空尝试。</param>
    /// <param name="output">驱动观察状态，只允许SUBMITTED_TO_SPOOLER、TEST_ONLY、FAILED或UNKNOWN。</param>
    /// <param name="error">失败错误码，无错误时null，不存原始驱动异常文本。</param>
    internal void FinishLocalTicket(LocalTicket ticket, TicketOutput output, string? error = null)
    {
        if (output.State is not ("SUBMITTED_TO_SPOOLER" or "TEST_ONLY" or "FAILED" or "UNKNOWN"))
            throw new AgentFault("INVALID_PRINT_STATE", "本地凭条回执状态不合法");
        lock (gate)
        {
            using var db = Open(); using var transaction = db.BeginTransaction();
            using var command = db.CreateCommand(); command.Transaction = transaction;
            command.CommandText = """
                UPDATE local_tickets SET state=$state,spooler_job_id=$spool,error_code=$error
                  WHERE attempt_id=$id AND state='SUBMITTING';
                """;
            command.Parameters.AddWithValue("$state", output.State);
            command.Parameters.AddWithValue("$spool", (object?)output.SpoolerJobId ?? DBNull.Value);
            command.Parameters.AddWithValue("$error", (object?)error ?? DBNull.Value);
            command.Parameters.AddWithValue("$id", ticket.AttemptId);
            command.Parameters.AddWithValue("$event", ticket.Snapshot.EventKey);
            // 旧尝试的重复回执不能覆盖后续补打的最新状态。
            if (command.ExecuteNonQuery() == 1)
            {
                command.CommandText = "UPDATE basket_records SET print_state=$state WHERE event_key=$event";
                command.ExecuteNonQuery();
            }
            transaction.Commit();
        }
    }

    /// <summary>经员工再次明确授权补打，复用原始内容并追加新尝试，不改变筐或累计。</summary>
    /// <param name="scope">非空、当前员工有权操作的范围。</param>
    /// <param name="request">非空、操作号唯一且原因非空的补打请求。</param>
    /// <param name="printerName">新尝试的打印队列；null沿用原队列，不改变冻结重量。</param>
    /// <param name="paperWidthMm">新纸宽；null沿用原值，调用方负责40至110mm校验。</param>
    /// <param name="paperHeightMm">新纸高；null沿用原值，调用方负责40至300mm校验。</param>
    /// <returns>同请求重试返回既有尝试；同编号改内容则拒绝。</returns>
    /// <exception cref="AgentFault">没有原凭条、尚有未决执行、权限范围或幂等冲突。</exception>
    internal LocalTicket ReprintLocalTicket(BasketScope scope, ReprintRequest request,
        string? printerName = null, int? paperWidthMm = null, int? paperHeightMm = null)
    {
        lock (gate)
        {
            using var db = Open(); using var transaction = db.BeginTransaction();
            string key = WireJson.Digest(scope), hash = WireJson.Digest(request);
            using var command = db.CreateCommand(); command.Transaction = transaction;
            command.CommandText = "SELECT payload,state,spooler_job_id,error_code,request_hash FROM local_tickets WHERE operation_id=$op";
            command.Parameters.AddWithValue("$op", request.OperationId);
            using (var row = command.ExecuteReader())
            {
                if (row.Read())
                {
                    if (row.GetString(4) != hash || ReadTicket(row).ScopeKey != key)
                        throw new AgentFault("OPERATION_CONFLICT", "补打编号已使用，请核对原尝试");
                    return ReadTicket(row);
                }
            }
            if (HasBasketOperation(request.OperationId) || ByOperation(request.OperationId) != null)
                throw new AgentFault("OPERATION_CONFLICT", "补打编号不能复用采集或完成操作号");
            command.CommandText = "SELECT payload,state,spooler_job_id,error_code FROM local_tickets WHERE event_key=$event AND scope_key=$scope ORDER BY rowid DESC LIMIT 1";
            command.Parameters.Clear(); command.Parameters.AddWithValue("$event", request.EventKey); command.Parameters.AddWithValue("$scope", key);
            LocalTicket previous;
            using (var row = command.ExecuteReader())
            {
                if (!row.Read()) throw new AgentFault("LOCAL_TICKET_NOT_FOUND", "没有本商品可补打的本地凭条");
                previous = ReadTicket(row);
            }
            if (previous.State is "QUEUED" or "SUBMITTING")
                throw new AgentFault("PRINT_IN_PROGRESS", "当前尝试尚在处理，请等待结果");
            var ticket = previous with { AttemptId = Guid.NewGuid().ToString("N"), OperationId = request.OperationId,
                State = "QUEUED", Reprint = true, Reason = request.Reason, CreatedAt = DateTimeOffset.UtcNow,
                PrinterName = printerName ?? previous.PrinterName,
                PaperWidthMm = paperWidthMm ?? previous.PaperWidthMm, PaperHeightMm = paperHeightMm ?? previous.PaperHeightMm,
                SpoolerJobId = null, ErrorCode = null };
            InsertLocalTicket(db, transaction, ticket, hash); transaction.Commit();
            return ticket;
        }
    }

    private static LocalTicket ReadTicket(SqliteDataReader row) => WireJson.Decode<LocalTicket>(row.GetString(0)) with
    { State = row.GetString(1), SpoolerJobId = row.IsDBNull(2) ? null : row.GetString(2), ErrorCode = row.IsDBNull(3) ? null : row.GetString(3) };
}
