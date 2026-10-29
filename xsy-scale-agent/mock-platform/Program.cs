using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

// 只在回环地址提供契约替身，不读取集团数据库，也不产生正式业务事实或打印任务。
var builder = WebApplication.CreateBuilder(args);
string listen = builder.Configuration["urls"] ?? "http://127.0.0.1:33601";
if (listen.Split(';').Any(url => !Uri.TryCreate(url, UriKind.Absolute, out var uri) || !uri.IsLoopback || uri.Scheme != "http"))
    throw new InvalidOperationException("模拟平台只能监听本机回环 HTTP");
builder.WebHost.UseUrls(listen);
var app = builder.Build();
bool grouping = builder.Configuration.GetValue<bool>("Grouping");
bool fixture = builder.Configuration.GetValue<bool>("Fixture");
decimal fixtureWeight = 5m;
bool fixtureStable = true, fixtureOnline = true, fixtureConnected = true;
bool fixtureControlChangesWeight = true;
int fixtureTareCalls = 0, fixtureZeroCalls = 0;
var fixtureGate = new object();
var events = new ConcurrentDictionary<string, (string Digest, string Id)>();
string? claimedDevice = null;
const string prefix = "/scm/device/scale/v1";
const string token = "MOCK-ONLY-DO-NOT-USE-IN-PRODUCTION";
app.Use(async (context, next) =>
{
    if (fixture && !fixtureOnline && context.Request.Path.StartsWithSegments(prefix))
    { context.Response.StatusCode = 503; await context.Response.WriteAsJsonAsync(new { code = "TEST_OFFLINE" }); return; }
    if (context.Request.Path.StartsWithSegments(prefix) && !context.Request.Path.Value!.EndsWith("/activate", StringComparison.Ordinal)
        && context.Request.Headers.Authorization != $"Bearer {token}")
    { context.Response.StatusCode = 401; await context.Response.WriteAsJsonAsync(new { code = "DEVICE_UNAUTHORIZED" }); return; }
    await next();
});
app.MapGet("/health", () => new { mode = "MOCK_ONLY", officialBusinessWrites = false });
app.MapPost(prefix + "/agents/activate", (JsonElement body) =>
    body.TryGetProperty("pairingCode", out var code) && code.GetString() == "MOCK-PAIR"
    ? Results.Ok(new { code = "OK", data = new { accessToken = token, expiresAt = DateTimeOffset.UtcNow.AddHours(1) } })
    : Results.BadRequest(new { code = "INVALID_PAIRING" }));
app.MapPost(prefix + "/agents/heartbeat", () => Results.Ok(new { code = "OK", data = new { } }));
app.MapGet(prefix + "/jobs", (HttpContext context) =>
{
    if (string.IsNullOrWhiteSpace(context.Request.Headers["X-Operator-Session"]))
        return Results.Unauthorized();
    return Results.Ok(new
    {
        code = "OK", data = new
        {
            operatorSessionExpiresAt = DateTimeOffset.UtcNow.AddHours(1), items = new[] { MockJob(claimedDevice, grouping) },
            nextCursor = (string?)null
        }
    });
});
app.MapGet(prefix + "/jobs/{jobId}", (string jobId) => jobId == "mock-job"
    ? Results.Ok(new { code = "OK", data = MockJob(claimedDevice, grouping) }) : Results.NotFound());
app.MapPost(prefix + "/jobs/{jobId}/{action}", (string jobId, string action, JsonElement body) =>
{
    if (jobId != "mock-job" || action is not ("claim" or "release")) return Results.NotFound();
    claimedDevice = action == "claim" ? body.GetProperty("deviceCode").GetString() : null;
    return Results.Ok(new { code = "OK", data = MockJob(claimedDevice, grouping) });
});
app.MapPost(prefix + "/measurements", (JsonElement body) =>
{
    string? key = body.TryGetProperty("eventKey", out var keyField) ? keyField.GetString() : null;
    if (string.IsNullOrWhiteSpace(key)) return Results.BadRequest(new { code = "INVALID_EVENT_KEY" });
    string digest = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(body.GetRawText())));
    var created = (digest, Guid.NewGuid().ToString("N"));
    var existing = events.GetOrAdd(key, created);
    if (existing.Digest != digest) return Results.Conflict(new { code = "EVENT_KEY_CONFLICT" });
    return Results.Ok(new { code = "OK", data = new { eventKey = key, measurementId = existing.Id, status = "PENDING",
        duplicated = existing.Id != created.Item2, receivedAt = DateTimeOffset.UtcNow } });
});
app.MapGet(prefix + "/print-jobs/pull", () => Results.Ok(new { code = "OK", data = new { items = Array.Empty<object>(), nextCursor = (string?)null } }));
app.MapGet("/test/events", () => Results.Ok(events.Select(item => new { eventKey = item.Key, eventId = item.Value.Id, status = "PENDING" })));
if (fixture)
{
    // 仅显式Fixture进程模拟厂家控制，不存在于真实秤端Agent。
    foreach (string action in new[] { "setTare", "zero" })
        app.MapGet("/api/" + action, () =>
        {
            lock (fixtureGate)
            {
                if (action == "setTare") fixtureTareCalls++; else fixtureZeroCalls++;
                if (fixtureControlChangesWeight) { fixtureWeight = 0; fixtureStable = true; }
                return Results.Json(new { Code = 1 }, new JsonSerializerOptions { PropertyNamingPolicy = null });
            }
        });
    app.MapPost("/test/control", (JsonElement body) =>
    {
        lock (fixtureGate) fixtureControlChangesWeight = body.GetProperty("changesWeight").GetBoolean();
        return Results.Ok(new { mode = "LOCAL_TEST" });
    });
    app.MapGet("/test/control", () =>
    { lock (fixtureGate) return Results.Ok(new { tareCalls = fixtureTareCalls, zeroCalls = fixtureZeroCalls }); });
    // 显式隔离演练才启用厂家HTTP替身；真实Agent没有任何注入重量的路由。
    app.MapGet("/api/Weight", () =>
    {
        lock (fixtureGate) {
        if (!fixtureConnected) return (IResult)Results.StatusCode(503);
        return Results.Json(new { Code = 1,
            netWeight = fixtureWeight.ToString(System.Globalization.CultureInfo.InvariantCulture), tareWeight = "0.500", stable = fixtureStable },
            new JsonSerializerOptions { PropertyNamingPolicy = null });
        }
    });
    app.MapPost("/test/scale", (JsonElement body) =>
    {
        if (!body.TryGetProperty("weight", out var weight) || !decimal.TryParse(weight.GetString(),
            System.Globalization.NumberStyles.AllowDecimalPoint | System.Globalization.NumberStyles.AllowLeadingSign,
            System.Globalization.CultureInfo.InvariantCulture, out decimal number)) return Results.BadRequest();
        lock (fixtureGate) { fixtureWeight = number; fixtureStable = body.TryGetProperty("stable", out var stable) && stable.GetBoolean();
            if (body.TryGetProperty("connected", out var connected)) fixtureConnected = connected.GetBoolean(); }
        return Results.Ok(new { mode = "LOCAL_TEST", physicalScale = false });
    });
    app.MapPost("/test/network", (JsonElement body) =>
    { fixtureOnline = body.GetProperty("online").GetBoolean(); return Results.Ok(new { mode = "LOCAL_TEST", online = fixtureOnline }); });
    app.MapGet("/", () => Results.Content("""
        <!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>鲜蔬园隔离演练控制台</title>
        <style>body{font:18px 'Microsoft YaHei';max-width:700px;margin:60px auto;background:#f5f7fa}button,input{font:inherit;padding:16px;margin:8px;border:1px solid #ccd7e5;border-radius:6px}button{cursor:pointer;background:white}</style>
        <h1>隔离演练控制台</h1><p>仅改变测试替身，不读取真实秤、不发纸、不连接正式平台。</p>
        <input id="weight" value="5.000" aria-label="演练重量（斤）"><button onclick="scale(true)">设置稳定重量</button><button onclick="scale(false)">设置不稳定</button>
        <p><button onclick="network(false)">模拟平台断网</button><button onclick="network(true)">恢复模拟平台</button></p><p id="result"></p>
        <script>async function scale(stable){await fetch('/test/scale',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({weight:document.getElementById('weight').value,stable})});document.getElementById('result').textContent='演练读数已调整'}async function network(online){await fetch('/test/network',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({online})});document.getElementById('result').textContent=online?'模拟平台已恢复':'模拟平台已断网，本机秤读数保持'}</script></html>
        """, "text/html; charset=utf-8"));
}
await app.RunAsync();

// 只给自动验证使用的最小授权投影；字段包装仍需深圳正式接口冻结。
static object MockJob(string? claimedDevice, bool grouping) => new
{
    taskId = "9007199254740993", taskNo = "MOCK-TASK", taskVersion = "0", jobId = "mock-job", jobVersion = "0",
    expiresAt = DateTimeOffset.UtcNow.AddHours(1), claimedByDeviceCode = claimedDevice,
    items = MockItems(grouping)
};

// 扩展场景只在显式Grouping演练启用，不更改现场默认三个任务。
static object[] MockItems(bool grouping)
{
    var items = new List<object> {
        MockItem("9007199254740994", "测试生菜", "测试散装", "sku-lettuce", "customer-1", "演练客户 · 悦来餐厅", "10.000"),
        MockItem("9007199254740995", "演练上海青", "散装", "sku-greens", "customer-1", "演练客户 · 悦来餐厅", "8.000"),
        MockItem("9007199254740996", "演练西红柿", "大果", "sku-tomato", "customer-1", "演练客户 · 悦来餐厅", "6.000") };
    if (grouping) items.AddRange([
        MockItem("9007199254740997", "测试生菜", "测试散装", "sku-lettuce", "customer-2", "演练客户 · 清风食堂", "12.000"),
        MockItem("9007199254740998", "测试生菜", "小颗精品", "sku-lettuce-small", "customer-3", "演练客户 · 悦来餐厅", "4.000"),
        MockItem("9007199254740999", "测试生菜", "旧任务散装", null, null, "同名旧客户", "2.000"),
        MockItem("9007199254741000", "测试生菜", "旧任务散装", null, null, "同名旧客户", "3.000") ]);
    return items.ToArray();
}

static object MockItem(string id, string name, string spec, string? sku, string? customer, string customerName, string planned) => new
{
    taskItemId = id, taskItemVersion = 0, productName = name, specName = spec, saleUnit = "斤",
    plannedQuantity = planned, sortedQuantity = "0.000", productType = "NON_STANDARD", active = true,
    customerName, orderNo = "TEST-20261016-001", skuId = sku, customerId = customer
};
