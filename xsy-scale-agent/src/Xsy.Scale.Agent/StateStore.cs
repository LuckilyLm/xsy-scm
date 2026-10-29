using System.Security.Cryptography;
using System.Text;
using Microsoft.Data.Sqlite;

namespace Xsy.Scale.Agent;

/// <summary>本机持久事实及队列；上传与物理打印都在本地落盘之后执行。</summary>
internal sealed partial class StateStore
{
    private readonly string connectionString;
    private readonly object gate = new();
    internal string DirectoryPath { get; }
    internal string InstallationId { get; }
    internal string SessionId { get; } = Guid.NewGuid().ToString("N");

    internal StateStore(string directory)
    {
        DirectoryPath = Path.GetFullPath(directory);
        Directory.CreateDirectory(DirectoryPath);
        connectionString = new SqliteConnectionStringBuilder
        { DataSource = Path.Combine(DirectoryPath, "agent.db"), DefaultTimeout = 5 }.ToString();
        using var db = Open();
        using var command = db.CreateCommand();
        command.CommandText = """
            PRAGMA journal_mode=WAL;
            PRAGMA synchronous=FULL;
            CREATE TABLE IF NOT EXISTS events (
              event_key TEXT PRIMARY KEY, operation_id TEXT NOT NULL UNIQUE, request_hash TEXT NOT NULL,
              payload TEXT NOT NULL, raw_frame TEXT NOT NULL, state TEXT NOT NULL,
              attempts INTEGER NOT NULL DEFAULT 0, next_attempt TEXT NOT NULL, event_id TEXT, last_error TEXT);
            CREATE TABLE IF NOT EXISTS task_cache (session_id TEXT PRIMARY KEY, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS settings (name TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS print_jobs (attempt_id TEXT PRIMARY KEY, protected_job TEXT NOT NULL,
              execution_id TEXT NOT NULL, state TEXT NOT NULL, protected_ack TEXT);
            UPDATE events SET state='RETRY_WAIT' WHERE state='UPLOADING';
            """;
        command.ExecuteNonQuery();
        InitializeBaskets(db);
        InitializeLocalTickets(db);
        InstallationId = Setting("installation_id") ?? Guid.NewGuid().ToString("N");
        Setting("installation_id", InstallationId);
        // 提交打印意图后崩溃，无法证明纸张是否输出，恢复时只上报 UNKNOWN，不再发纸。
        foreach (LocalPrint job in Prints("CLAIMED_LOCAL").Concat(Prints("SPOOL_SUBMITTING")))
            SavePrint(job with { State = "ACK_PENDING", Ack = new PrintAck(job.Job.PrintJobId,
                job.Job.PrintAttemptId, job.Job.DeviceCode, job.Job.PrinterCode, job.Job.LeaseToken, "UNKNOWN",
                null, DateTimeOffset.UtcNow, "AGENT", "AGENT_RESTARTED", "执行阶段中断，出纸结果需核查") });
    }

    private SqliteConnection Open()
    {
        var db = new SqliteConnection(connectionString);
        db.Open();
        using var command = db.CreateCommand();
        command.CommandText = "PRAGMA synchronous=FULL; PRAGMA busy_timeout=5000;";
        command.ExecuteNonQuery();
        return db;
    }

    private void Execute(string sql, params (string Name, object? Value)[] values)
    {
        using var db = Open();
        using var command = db.CreateCommand();
        command.CommandText = sql;
        foreach (var value in values) command.Parameters.AddWithValue(value.Name, value.Value ?? DBNull.Value);
        command.ExecuteNonQuery();
    }

    internal LocalEvent? ByOperation(string operationId)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT * FROM events WHERE operation_id=$id";
        command.Parameters.AddWithValue("$id", operationId);
        using var row = command.ExecuteReader();
        return row.Read() ? ReadEvent(row) : null;
    }

    internal LocalEvent Capture(CaptureRequest request, Func<LocalEvent> create)
    {
        lock (gate)
        {
            if (HasBasketOperation(request.OperationId))
                throw new AgentFault("OPERATION_CONFLICT", "操作编号已用于多筐作业，不能改为单次采集");
            if (HasBasketActivity(new(request.OperatorSessionId, request.JobId, request.TaskId, request.TaskItemId)))
                throw new AgentFault("BASKET_WORKFLOW_REQUIRED", "当前明细已使用多筐流程，请继续使用多筐接口");
            string digest = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(WireJson.Encode(request))));
            LocalEvent? previous = ByOperation(request.OperationId);
            if (previous != null)
            {
                if (previous.RequestHash != digest) throw new AgentFault("OPERATION_CONFLICT", "同一采集编号不能更改重量或目标任务");
                return previous;
            }
            LocalEvent next = create() with { RequestHash = digest };
            Execute("""
                INSERT INTO events(event_key,operation_id,request_hash,payload,raw_frame,state,next_attempt)
                VALUES($key,$op,$hash,$payload,$raw,'CAPTURED_LOCAL',$next)
                """, ("$key", next.EventKey), ("$op", next.OperationId), ("$hash", digest),
                ("$payload", WireJson.Encode(next.Measurement)), ("$raw", next.RawFrameHex), ("$next", next.NextAttemptAt.ToString("O")));
            return next;
        }
    }

    internal LocalEvent[] Events()
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT * FROM events ORDER BY rowid DESC LIMIT 200";
        using var rows = command.ExecuteReader(); var result = new List<LocalEvent>();
        while (rows.Read()) result.Add(ReadEvent(rows));
        return result.ToArray();
    }

    internal LocalEvent? NextEvent()
    {
        lock (gate)
        {
            using var db = Open(); using var command = db.CreateCommand();
            command.CommandText = """
                SELECT * FROM events WHERE state IN ('CAPTURED_LOCAL','RETRY_WAIT') AND next_attempt <= $now
                ORDER BY rowid LIMIT 1
                """;
            command.Parameters.AddWithValue("$now", DateTimeOffset.UtcNow.ToString("O"));
            using var row = command.ExecuteReader();
            if (!row.Read()) return null;
            var result = ReadEvent(row);
            row.Close();
            Execute("UPDATE events SET state='UPLOADING' WHERE event_key=$key", ("$key", result.EventKey));
            return result;
        }
    }

    internal void FinishEvent(LocalEvent record, string state, string? eventId, string? error)
    {
        int attempts = record.Attempts + 1;
        double delay = Math.Min(300, Math.Pow(2, Math.Min(attempts, 8))) + Random.Shared.NextDouble();
        Execute("""
            UPDATE events SET state=$state,attempts=$attempts,next_attempt=$next,event_id=$id,last_error=$error
            WHERE event_key=$key
            """, ("$state", state), ("$attempts", attempts), ("$next", DateTimeOffset.UtcNow.AddSeconds(delay).ToString("O")),
            ("$id", eventId), ("$error", error), ("$key", record.EventKey));
    }

    internal void CacheTasks(string session, TaskBatch batch) => Execute("""
        INSERT INTO task_cache VALUES($id,$data) ON CONFLICT(session_id) DO UPDATE SET payload=excluded.payload
        """, ("$id", session), ("$data", WireJson.Encode(batch)));

    internal TaskBatch? CachedTasks(string session)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT payload FROM task_cache WHERE session_id=$id";
        command.Parameters.AddWithValue("$id", session);
        return command.ExecuteScalar() is string json ? WireJson.Decode<TaskBatch>(json) : null;
    }

    internal string? Setting(string name)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT value FROM settings WHERE name=$name";
        command.Parameters.AddWithValue("$name", name);
        return command.ExecuteScalar() as string;
    }

    internal void Setting(string name, string value) => Execute("""
        INSERT INTO settings VALUES($name,$value) ON CONFLICT(name) DO UPDATE SET value=excluded.value
        """, ("$name", name), ("$value", value));

    internal static string Protect<T>(T value) => Convert.ToBase64String(ProtectedData.Protect(
        Encoding.UTF8.GetBytes(WireJson.Encode(value)), null, DataProtectionScope.CurrentUser));
    internal static T Unprotect<T>(string value) => WireJson.Decode<T>(Encoding.UTF8.GetString(
        ProtectedData.Unprotect(Convert.FromBase64String(value), null, DataProtectionScope.CurrentUser)));

    internal void SavePrint(LocalPrint job) => Execute("""
        INSERT INTO print_jobs VALUES($id,$job,$execution,$state,$ack)
        ON CONFLICT(attempt_id) DO UPDATE SET state=excluded.state,protected_ack=excluded.protected_ack
        """, ("$id", job.Job.PrintAttemptId), ("$job", Protect(job.Job)), ("$execution", job.ExecutionId),
        ("$state", job.State), ("$ack", job.Ack == null ? null : Protect(job.Ack)));

    internal bool HasPrint(string id)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM print_jobs WHERE attempt_id=$id";
        command.Parameters.AddWithValue("$id", id);
        return Convert.ToInt64(command.ExecuteScalar()) > 0;
    }

    internal long QueueDepth()
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM events WHERE state IN ('CAPTURED_LOCAL','RETRY_WAIT','UPLOADING')";
        return Convert.ToInt64(command.ExecuteScalar());
    }

    internal LocalPrint[] Prints(string? state = null)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = state == null ? "SELECT * FROM print_jobs ORDER BY rowid DESC LIMIT 200"
            : "SELECT * FROM print_jobs WHERE state=$state ORDER BY rowid";
        if (state != null) command.Parameters.AddWithValue("$state", state);
        using var rows = command.ExecuteReader(); var result = new List<LocalPrint>();
        while (rows.Read()) result.Add(new LocalPrint(Unprotect<PrintJob>(rows.GetString(1)), rows.GetString(2),
            rows.GetString(3), rows.IsDBNull(4) ? null : Unprotect<PrintAck>(rows.GetString(4))));
        return result.ToArray();
    }

    private static LocalEvent ReadEvent(SqliteDataReader row) => new(row.GetString(0), row.GetString(1),
        row.GetString(2), WireJson.Decode<Measurement>(row.GetString(3)), row.GetString(4), row.GetString(5),
        row.GetInt32(6), DateTimeOffset.Parse(row.GetString(7)), row.IsDBNull(8) ? null : row.GetString(8),
        row.IsDBNull(9) ? null : row.GetString(9));
}

/// <summary>人工采集只冻结设备事实，平台仍是员工授权和正式分拣结果的最终校验方。</summary>
internal sealed class CaptureService(AgentOptions options, ScaleState state, StateStore store)
{
    internal LocalEvent Capture(CaptureRequest request)
    {
        TaskItem target = ValidateContext(request);
        return store.Capture(request, () => CreateEvent(request, target));
    }

    /// <summary>校验当前员工、已领取作业及版本，供单次与多筐流程复用。</summary>
    /// <param name="request">字段完整且版本非负的采集上下文；为空或过期时拒绝，不读取设备。</param>
    /// <returns>平台缓存中的有效非标品明细。</returns>
    /// <exception cref="AgentFault">授权、设备配置或任务上下文不合格。</exception>
    internal TaskItem ValidateContext(CaptureRequest request)
    {
        if (string.IsNullOrWhiteSpace(options.DeviceCode) || options.DeviceCode.Length > 64
            || string.IsNullOrWhiteSpace(options.StationCode) || options.StationCode.Length > 64)
            throw new AgentFault("DEVICE_NOT_CONFIGURED", "请配置设备编码并完成绑定");
        if (new[] { request.OperationId, request.SampleId, request.TaskId, request.TaskItemId, request.OperatorSessionId,
                request.JobId, request.JobVersion }.Any(s => string.IsNullOrWhiteSpace(s) || s.Length > 160)
            || request.TaskItemVersion < 0)
            throw new AgentFault("INVALID_CAPTURE", "采集编号、采样、任务和员工会话必须完整");
        TaskBatch? cache = store.CachedTasks(request.OperatorSessionId);
        if (cache == null || cache.OperatorSessionExpiresAt <= DateTimeOffset.UtcNow || store.Setting("suspended") == "true")
            throw new AgentFault("OPERATOR_SESSION_EXPIRED", "请联网刷新当前员工有权操作的任务");
        TaskSnapshot? job = cache.Tasks.FirstOrDefault(t => t.TaskId == request.TaskId && t.JobId == request.JobId);
        if (job == null || job.JobVersion != request.JobVersion || job.ClaimedByDeviceCode != options.DeviceCode
            || job.ExpiresAt <= DateTimeOffset.UtcNow)
            throw new AgentFault("JOB_CONTEXT_STALE", "请先领取当前作业，租约或版本已失效时需联网刷新");
        TaskItem? target = job.Items
            .FirstOrDefault(i => i.TaskItemId == request.TaskItemId && i.Active);
        if (target == null || target.ProductType != "NON_STANDARD" || target.TaskItemVersion != request.TaskItemVersion)
            throw new AgentFault("TASK_ITEM_STALE", "请选择当前员工有权操作的非标品任务明细");
        return target;
    }

    /// <summary>冻结一笔真实采样但不写库，由调用方把事实和关联意图一起提交。</summary>
    /// <param name="request">已校验的非空采集请求；SampleId 必须引用当前有效采样。</param>
    /// <param name="target">与请求关联的有效平台明细，不允许为空；单位须相同或为市斤编码别名。</param>
    /// <returns>尚未持久化的不可变采集事实。</returns>
    /// <exception cref="AgentFault">样本失效、不稳、非净重或单位不符。</exception>
    internal LocalEvent CreateEvent(CaptureRequest request, TaskItem target)
    {
            WeightReading reading = state.Freeze(request.SampleId, options);
            if (!MassUnits.Matches(reading.RawUnit, target.SaleUnit))
                throw new AgentFault("UNIT_MISMATCH", "请将电子秤切换为任务对应单位；sj表示市斤，不将公斤数值直接作为斤");
            string eventKey = $"{options.DeviceCode}:{Guid.NewGuid():N}";
            string weight = reading.RawWeight.ToString(System.Globalization.CultureInfo.InvariantCulture);
            var measurement = new Measurement("1", eventKey, options.DeviceCode, options.StationCode, store.SessionId,
                request.JobId, request.JobVersion, request.TaskId, request.TaskItemId, request.TaskItemVersion,
                request.OperatorSessionId, reading.CapturedAt, weight, reading.RawUnit, weight, target.SaleUnit, "NET",
                true, reading.StabilitySource is "DEVICE_FRAME" or "DEVICE_HTTP" ? "DEVICE" : reading.StabilitySource, "IDENTITY",
                reading.TareWeight?.ToString(System.Globalization.CultureInfo.InvariantCulture),
                reading.ReadingSequence, options.Protocol, WireJson.Version, "DEVICE");
            return new LocalEvent(eventKey, request.OperationId, "", measurement, reading.RawFrameHex,
                "CAPTURED_LOCAL", 0, DateTimeOffset.UtcNow, null, null);
    }
}
