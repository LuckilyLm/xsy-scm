using System.Globalization;
using Microsoft.Data.Sqlite;

namespace Xsy.Scale.Agent;

/// <summary>多筐事实及离线意图独立存放，未冻结的新契约不能落入旧单次上报队列。</summary>
internal sealed partial class StateStore
{
    private void InitializeBaskets(SqliteConnection db)
    {
        using var command = db.CreateCommand();
        // 只增表和索引，保留旧采集/打印记录；范围键不随进程重启变化。
        command.CommandText = """
            CREATE TABLE IF NOT EXISTS basket_records (
              event_key TEXT PRIMARY KEY, operation_id TEXT NOT NULL UNIQUE, request_hash TEXT NOT NULL,
              scope_key TEXT NOT NULL, basket_number INTEGER NOT NULL, sample_id TEXT NOT NULL,
              payload TEXT NOT NULL, raw_frame TEXT NOT NULL, print_intent_id TEXT NOT NULL UNIQUE,
              print_state TEXT NOT NULL, UNIQUE(scope_key,basket_number), UNIQUE(scope_key,sample_id));
            CREATE INDEX IF NOT EXISTS ix_basket_scope ON basket_records(scope_key,basket_number);
            CREATE TABLE IF NOT EXISTS basket_commands (
              sequence INTEGER PRIMARY KEY AUTOINCREMENT, operation_id TEXT NOT NULL UNIQUE,
              scope_key TEXT NOT NULL, kind TEXT NOT NULL, event_key TEXT, request_hash TEXT NOT NULL,
              payload TEXT NOT NULL, state TEXT NOT NULL, created_at TEXT NOT NULL);
            CREATE INDEX IF NOT EXISTS ix_basket_command_scope ON basket_commands(scope_key,sequence);
            CREATE UNIQUE INDEX IF NOT EXISTS ux_basket_completion ON basket_commands(scope_key)
              WHERE kind='COMPLETE_ITEM';
            """;
        command.ExecuteNonQuery();
    }

    private bool HasBasketOperation(string operationId)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT (SELECT COUNT(*) FROM basket_commands WHERE operation_id=$id)+(SELECT COUNT(*) FROM local_tickets WHERE operation_id=$id)";
        command.Parameters.AddWithValue("$id", operationId);
        return Convert.ToInt64(command.ExecuteScalar()) != 0;
    }

    private bool HasBasketActivity(BasketScope scope)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM basket_records WHERE scope_key=$scope";
        command.Parameters.AddWithValue("$scope", WireJson.Digest(scope));
        return Convert.ToInt64(command.ExecuteScalar()) != 0;
    }

    /// <summary>保存一筐、上传命令及打印意图，任何一步失败均回滚。</summary>
    /// <param name="request">已经过员工/任务校验的完整采集请求；同操作必须保持原请求。</param>
    /// <param name="create">新操作才调用的真实样本冻结函数，不可为空；抛错时不落记录。</param>
    /// <param name="ticketFactory">显式启用本地凭条时提供的冻结函数；null保持等待授权且不建立打印任务。</param>
    /// <returns>新建或幂等复用的筐记录；状态明确等待正式扩展契约。</returns>
    /// <exception cref="AgentFault">请求冲突、重复样本、明细已完成或上下文变化。</exception>
    internal BasketRecord RecordBasket(CaptureRequest request, Func<LocalEvent> create, Func<BasketRecord, LocalTicket>? ticketFactory = null)
    {
        lock (gate)
        {
            var scope = new BasketScope(request.OperatorSessionId, request.JobId, request.TaskId, request.TaskItemId);
            string scopeKey = WireJson.Digest(scope), digest = WireJson.Digest(request);
            using var db = Open(); using var transaction = db.BeginTransaction();
            using var command = db.CreateCommand(); command.Transaction = transaction;
            command.CommandText = "SELECT request_hash,kind FROM basket_commands WHERE operation_id=$op";
            command.Parameters.AddWithValue("$op", request.OperationId);
            using (var row = command.ExecuteReader())
            {
                if (row.Read())
                {
                    if (row.GetString(0) != digest || row.GetString(1) != "CAPTURE_BASKET")
                        throw new AgentFault("OPERATION_CONFLICT", "操作编号已使用，不能更改目标或采样");
                    row.Close();
                    return ReadBaskets(db, transaction, scopeKey).Single(r => r.OperationId == request.OperationId);
                }
            }
            RequireNoLegacy(db, transaction, scope, request.OperationId);
            if (ReadCompletion(db, transaction, scopeKey) != null)
                throw new AgentFault("ITEM_LOCALLY_COMPLETED", "商品已在本机完成，需要平台授权重开后再称");
            BasketRecord[] records = ReadBaskets(db, transaction, scopeKey);
            if (records.Any(record => record.SampleId == request.SampleId))
                throw new AgentFault("SAMPLE_ALREADY_RECORDED", "此采样已保存为一筐，请换筐并等待新的有效采样");
            LocalEvent frozen = create();
            if (records.Length > 0) RequireBasketContext(records[0].Measurement, frozen.Measurement);
            string intentId = Guid.NewGuid().ToString("N");
            var record = new BasketRecord(frozen.EventKey, request.OperationId, records.Length + 1,
                request.SampleId, frozen.Measurement, intentId, "WAITING_AUTHORIZATION", "WAITING_CONTRACT");
            command.CommandText = """
                INSERT INTO basket_records(event_key,operation_id,request_hash,scope_key,basket_number,sample_id,
                  payload,raw_frame,print_intent_id,print_state)
                VALUES($event,$op,$hash,$scope,$number,$sample,$payload,$raw,$intent,'WAITING_AUTHORIZATION')
                """;
            command.Parameters.Clear();
            command.Parameters.AddWithValue("$event", record.EventKey);
            command.Parameters.AddWithValue("$op", request.OperationId);
            command.Parameters.AddWithValue("$hash", digest);
            command.Parameters.AddWithValue("$scope", scopeKey);
            command.Parameters.AddWithValue("$number", record.BasketNumber);
            command.Parameters.AddWithValue("$sample", request.SampleId);
            command.Parameters.AddWithValue("$payload", WireJson.Encode(frozen.Measurement));
            command.Parameters.AddWithValue("$raw", frozen.RawFrameHex);
            command.Parameters.AddWithValue("$intent", intentId);
            command.ExecuteNonQuery();
            InsertBasketCommand(db, transaction, request.OperationId, scopeKey, "CAPTURE_BASKET", record.EventKey,
                digest, WireJson.Encode(record));
            if (ticketFactory != null)
            {
                LocalTicket ticket = ticketFactory(record);
                InsertLocalTicket(db, transaction, ticket, digest);
                record = record with { PrintState = "QUEUED" };
            }
            transaction.Commit();
            return record;
        }
    }

    /// <summary>冻结全部筐集合和本地完成意图，不修改平台正式数量或状态。</summary>
    /// <param name="request">非空完成请求；ManifestHash 必须匹配最新全量累计响应。</param>
    /// <returns>已持久化的本地完成快照；同请求重试返回原始快照。</returns>
    /// <exception cref="AgentFault">空集合、摘要变化、重复完成或上下文冲突。</exception>
    internal ItemCompletion CompleteBaskets(CompleteItemRequest request)
    {
        lock (gate)
        {
            var scope = new BasketScope(request.OperatorSessionId, request.JobId, request.TaskId, request.TaskItemId);
            string key = WireJson.Digest(scope), digest = WireJson.Digest(request);
            using var db = Open(); using var transaction = db.BeginTransaction();
            using var command = db.CreateCommand(); command.Transaction = transaction;
            command.CommandText = "SELECT kind,request_hash,payload FROM basket_commands WHERE operation_id=$op";
            command.Parameters.AddWithValue("$op", request.OperationId);
            using (var row = command.ExecuteReader())
            {
                if (row.Read())
                {
                    if (row.GetString(0) != "COMPLETE_ITEM" || row.GetString(1) != digest)
                        throw new AgentFault("OPERATION_CONFLICT", "完成操作编号已使用，不能更改其内容");
                    return WireJson.Decode<ItemCompletion>(row.GetString(2));
                }
            }
            RequireNoLegacy(db, transaction, scope, request.OperationId);
            if (ReadCompletion(db, transaction, key) != null)
                throw new AgentFault("ITEM_LOCALLY_COMPLETED", "商品已在本机完成，请查询原完成结果");
            BasketRecord[] records = ReadBaskets(db, transaction, key);
            if (records.Length == 0) throw new AgentFault("NO_BASKETS", "请先保存至少一筐再完成商品");
            Measurement first = records[0].Measurement;
            if (first.JobVersion != request.JobVersion || first.TaskItemVersion != request.TaskItemVersion)
                throw new AgentFault("BASKET_CONTEXT_CHANGED", "任务版本已变化，请先处理已有筐记录");
            string[] keys = records.Select(record => record.EventKey).ToArray();
            if (WireJson.Digest(keys) != request.ManifestHash)
                throw new AgentFault("BASKET_MANIFEST_CHANGED", "称重集合已变化，请刷新累计后重新确认");
            string total = records.Sum(record => decimal.Parse(record.Measurement.NetWeight, CultureInfo.InvariantCulture))
                .ToString(CultureInfo.InvariantCulture);
            var result = new ItemCompletion(request.OperationId, request.JobId, request.JobVersion, request.TaskId,
                request.TaskItemId, request.TaskItemVersion, request.OperatorSessionId, keys, total,
                first.BusinessUnit, request.Result, request.Reason, DateTimeOffset.UtcNow);
            InsertBasketCommand(db, transaction, request.OperationId, key, "COMPLETE_ITEM", null, digest, WireJson.Encode(result));
            transaction.Commit();
            return result;
        }
    }

    /// <summary>查询完整范围累计，避免最近200条诊断记录截断造成少计。</summary>
    /// <param name="scope">非空且已校验的员工、作业和明细范围。</param>
    /// <param name="item">当前缓存明细；平台数量只作为缓存快照返回。</param>
    /// <param name="authorizationExpired">当前缓存授权是否过期，只影响展示，不能用于扩大新操作权限。</param>
    /// <returns>同一次数据库快照中的筐总数、十进制累计、摘要及本地完成状态。</returns>
    internal BasketSummary SummarizeBaskets(BasketScope scope, TaskItem item, bool authorizationExpired)
    {
        using var db = Open(); using var transaction = db.BeginTransaction(deferred: true);
        string key = WireJson.Digest(scope);
        BasketRecord[] rows = ReadBaskets(db, transaction, key);
        ItemCompletion? completion = ReadCompletion(db, transaction, key);
        return new BasketSummary(rows.Length, rows.Sum(row => decimal.Parse(row.Measurement.NetWeight, CultureInfo.InvariantCulture))
            .ToString(CultureInfo.InvariantCulture), rows.FirstOrDefault()?.Measurement.BusinessUnit ?? item.SaleUnit,
            item.PlannedQuantity, item.SortedQuantity, WireJson.Digest(rows.Select(row => row.EventKey).ToArray()),
            authorizationExpired, completion != null, completion, "WAITING_CONTRACT");
    }

    /// <summary>按筐序号分页读取，结果不包含原始帧或平台凭证。</summary>
    /// <param name="scope">已校验的非空范围，不跨员工会话查询。</param>
    /// <param name="after">排他筐序号，至少为0。</param>
    /// <param name="limit">页大小1至100。</param>
    /// <returns>当前页及下一页游标，空页的下一游标为null。</returns>
    internal BasketPage BasketRecords(BasketScope scope, int after, int limit)
    {
        using var db = Open();
        BasketRecord[] rows = ReadBaskets(db, null, WireJson.Digest(scope), after, limit + 1);
        return new BasketPage(rows.Take(limit).ToArray(), rows.Length > limit ? rows[limit - 1].BasketNumber : null);
    }

    /// <summary>读取持久化待对接命令，完成命令保留完整的依赖事件集合。</summary>
    /// <param name="scope">非空且已校验的当前员工范围。</param>
    /// <param name="after">排他命令序号，至少为0。</param>
    /// <param name="limit">最大返回数量1至100；按最后一项Sequence继续读取。</param>
    /// <returns>仅本地意图摘要，不代表已执行远程业务或物理打印。</returns>
    internal BasketCommand[] BasketQueue(BasketScope scope, long after, int limit)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = """
            SELECT sequence,operation_id,kind,state,event_key,payload,created_at FROM basket_commands
            WHERE scope_key=$scope AND sequence>$after ORDER BY sequence LIMIT $limit
            """;
        command.Parameters.AddWithValue("$scope", WireJson.Digest(scope));
        command.Parameters.AddWithValue("$after", after); command.Parameters.AddWithValue("$limit", limit);
        using var rows = command.ExecuteReader(); var result = new List<BasketCommand>();
        while (rows.Read()) result.Add(new(rows.GetInt64(0), rows.GetString(1), rows.GetString(2), rows.GetString(3),
            rows.IsDBNull(4) ? null : rows.GetString(4), rows.GetString(2) == "COMPLETE_ITEM"
                ? WireJson.Decode<ItemCompletion>(rows.GetString(5)).EventKeys : [], DateTimeOffset.Parse(rows.GetString(6))));
        return result.ToArray();
    }

    private static void InsertBasketCommand(SqliteConnection db, SqliteTransaction transaction, string operationId,
        string scopeKey, string kind, string? eventKey, string requestHash, string payload)
    {
        using var command = db.CreateCommand(); command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO basket_commands(operation_id,scope_key,kind,event_key,request_hash,payload,state,created_at)
            VALUES($op,$scope,$kind,$event,$hash,$payload,'WAITING_CONTRACT',$time)
            """;
        command.Parameters.AddWithValue("$op", operationId); command.Parameters.AddWithValue("$scope", scopeKey);
        command.Parameters.AddWithValue("$kind", kind); command.Parameters.AddWithValue("$event", (object?)eventKey ?? DBNull.Value);
        command.Parameters.AddWithValue("$hash", requestHash); command.Parameters.AddWithValue("$payload", payload);
        command.Parameters.AddWithValue("$time", DateTimeOffset.UtcNow.ToString("O")); command.ExecuteNonQuery();
    }

    private static BasketRecord[] ReadBaskets(SqliteConnection db, SqliteTransaction? transaction, string scopeKey,
        int after = 0, int? limit = null)
    {
        using var command = db.CreateCommand(); command.Transaction = transaction;
        command.CommandText = """
            SELECT r.event_key,r.operation_id,r.basket_number,r.sample_id,r.payload,r.print_intent_id,r.print_state,c.state
            FROM basket_records r JOIN basket_commands c ON c.operation_id=r.operation_id
            WHERE r.scope_key=$scope AND r.basket_number>$after ORDER BY r.basket_number
            """ + (limit.HasValue ? " LIMIT $limit" : "");
        command.Parameters.AddWithValue("$scope", scopeKey); command.Parameters.AddWithValue("$after", after);
        if (limit.HasValue) command.Parameters.AddWithValue("$limit", limit.Value);
        using var rows = command.ExecuteReader(); var result = new List<BasketRecord>();
        while (rows.Read()) result.Add(new(rows.GetString(0), rows.GetString(1), rows.GetInt32(2), rows.GetString(3),
            WireJson.Decode<Measurement>(rows.GetString(4)), rows.GetString(5), rows.GetString(6), rows.GetString(7)));
        return result.ToArray();
    }

    private static ItemCompletion? ReadCompletion(SqliteConnection db, SqliteTransaction? transaction, string scopeKey)
    {
        using var command = db.CreateCommand(); command.Transaction = transaction;
        command.CommandText = "SELECT payload FROM basket_commands WHERE scope_key=$scope AND kind='COMPLETE_ITEM'";
        command.Parameters.AddWithValue("$scope", scopeKey);
        return command.ExecuteScalar() is string json ? WireJson.Decode<ItemCompletion>(json) : null;
    }

    private static void RequireNoLegacy(SqliteConnection db, SqliteTransaction transaction, BasketScope scope, string operationId)
    {
        using var command = db.CreateCommand(); command.Transaction = transaction;
        command.CommandText = "SELECT COUNT(*) FROM local_tickets WHERE operation_id=$op";
        command.Parameters.AddWithValue("$op", operationId);
        if (Convert.ToInt64(command.ExecuteScalar()) != 0)
            throw new AgentFault("OPERATION_CONFLICT", "操作号已用于本地凭条尝试，不能更改用途");
        command.Parameters.Clear();
        command.CommandText = """
            SELECT COUNT(*) FROM events WHERE operation_id=$op OR
              (json_extract(payload,'$.operatorSessionId')=$session AND json_extract(payload,'$.jobId')=$job
              AND json_extract(payload,'$.taskId')=$task AND json_extract(payload,'$.taskItemId')=$item)
            """;
        command.Parameters.AddWithValue("$op", operationId); command.Parameters.AddWithValue("$session", scope.OperatorSessionId);
        command.Parameters.AddWithValue("$job", scope.JobId); command.Parameters.AddWithValue("$task", scope.TaskId);
        command.Parameters.AddWithValue("$item", scope.TaskItemId);
        if (Convert.ToInt64(command.ExecuteScalar()) != 0)
            throw new AgentFault("LEGACY_CAPTURE_EXISTS", "已有单次采集记录，请先与平台核对，不能混用累计流程");
    }

    private static void RequireBasketContext(Measurement first, Measurement next)
    {
        if (first.JobVersion != next.JobVersion || first.TaskItemVersion != next.TaskItemVersion
            || first.BusinessUnit != next.BusinessUnit || first.DeviceCode != next.DeviceCode)
            throw new AgentFault("BASKET_CONTEXT_CHANGED", "作业版本、设备或业务单位变化，请先处理原有筐记录");
    }
}
