using Microsoft.Data.Sqlite;

namespace Xsy.Scale.Agent;

/// <summary>
/// 多筐命令的派发状态。这些状态只表示本机派发进度，不是平台业务结论：
/// 平台是否已确认分拣量由完成任务的结果回答，不能由这里推断。
/// </summary>
internal static class BasketCommandState
{
    /// <summary>已落盘但尚未获得平台受理；旧版本留下的记录也是这个状态。</summary>
    internal const string Waiting = "WAITING_CONTRACT";

    /// <summary>可重试的失败（网络、5xx、超时）；下一轮继续，不改内容。</summary>
    internal const string Retry = "SYNC_RETRY";

    /// <summary>平台已受理。重复发送不再改变任何业务事实。</summary>
    internal const string Sent = "SENT";

    /// <summary>平台明确拒绝（冲突、单位不符、集合变化、任务失效），必须人工处理。</summary>
    internal const string NeedsReview = "NEEDS_REVIEW";
}

/// <summary>
/// 多筐命令派发所需的本地读写。按 sequence 升序发送，保证筐先于完成命令到达平台。
/// </summary>
internal sealed partial class StateStore
{
    /// <summary>
    /// 取待发送的多筐命令。按 sequence 升序：采集命令必然先于同范围的完成命令落盘，
    /// 因此顺序发送即可保证平台先收到筐、再收到完成。
    /// </summary>
    internal PendingBasketCommand[] PendingBasketCommands(int limit)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = """
            SELECT sequence,operation_id,scope_key,kind,event_key,request_hash,payload FROM basket_commands
            WHERE state IN ($waiting,$retry) ORDER BY sequence LIMIT $limit
            """;
        command.Parameters.AddWithValue("$waiting", BasketCommandState.Waiting);
        command.Parameters.AddWithValue("$retry", BasketCommandState.Retry);
        command.Parameters.AddWithValue("$limit", limit);
        using var rows = command.ExecuteReader();
        var result = new List<PendingBasketCommand>();
        while (rows.Read())
            result.Add(new(rows.GetInt64(0), rows.GetString(1), rows.GetString(2), rows.GetString(3),
                rows.IsDBNull(4) ? null : rows.GetString(4), rows.GetString(5), rows.GetString(6)));
        return result.ToArray();
    }

    /// <summary>
    /// 该范围是否仍有未获平台受理的采集命令。完成命令必须等全部筐落地，
    /// 否则平台会按不完整的集合求和。
    /// </summary>
    internal bool HasUnsentCaptures(string scopeKey)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = """
            SELECT COUNT(*) FROM basket_commands
            WHERE scope_key=$scope AND kind='CAPTURE_BASKET' AND state<>$sent
            """;
        command.Parameters.AddWithValue("$scope", scopeKey);
        command.Parameters.AddWithValue("$sent", BasketCommandState.Sent);
        return Convert.ToInt64(command.ExecuteScalar()) != 0;
    }

    /// <summary>更新派发状态。已发送的记录不回退，避免晚到的失败覆盖成功事实。</summary>
    internal void SetBasketCommandState(long sequence, string state)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "UPDATE basket_commands SET state=$state WHERE sequence=$seq AND state<>$sent";
        command.Parameters.AddWithValue("$state", state);
        command.Parameters.AddWithValue("$seq", sequence);
        command.Parameters.AddWithValue("$sent", BasketCommandState.Sent);
        command.ExecuteNonQuery();
    }

    /// <summary>
    /// 把该范围剩余待发送命令一并转为需人工处理。用于完成命令被平台拒绝时，
    /// 让同范围的筐也停止自动重试，避免继续堆积无法收敛的请求。
    /// </summary>
    internal void ParkBasketCommands(string scopeKey)
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = """
            UPDATE basket_commands SET state=$review
            WHERE scope_key=$scope AND state IN ($waiting,$retry)
            """;
        command.Parameters.AddWithValue("$review", BasketCommandState.NeedsReview);
        command.Parameters.AddWithValue("$scope", scopeKey);
        command.Parameters.AddWithValue("$waiting", BasketCommandState.Waiting);
        command.Parameters.AddWithValue("$retry", BasketCommandState.Retry);
        command.ExecuteNonQuery();
    }

    /// <summary>队列是否已全部派发完成，用于工作台显示是否还有待同步内容。</summary>
    internal int PendingBasketCommandCount()
    {
        using var db = Open(); using var command = db.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM basket_commands WHERE state IN ($waiting,$retry)";
        command.Parameters.AddWithValue("$waiting", BasketCommandState.Waiting);
        command.Parameters.AddWithValue("$retry", BasketCommandState.Retry);
        return Convert.ToInt32(command.ExecuteScalar());
    }
}
