export default {
  async fetch(request, env) {
    const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "POST, OPTIONS", "Access-Control-Allow-Headers": "Authorization, Content-Type" };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" } });
    if (new URL(request.url).pathname !== "/api/chat" || request.method !== "POST") return json({ error: "Not found" }, 404);
    if (!env.APP_ACCESS_TOKEN || !env.OPENAI_API_KEY) return json({ error: "Server secrets are not configured" }, 503);
    if (request.headers.get("Authorization") !== `Bearer ${env.APP_ACCESS_TOKEN}`) return json({ error: "Unauthorized" }, 401);
    try {
      const body = await request.json();
      const message = String(body.message || "").trim();
      if (!message || message.length > 8000) return json({ error: "Invalid message" }, 400);
      const history = Array.isArray(body.history) ? body.history.slice(-12).map(m => ({ role: m.role === "assistant" ? "assistant" : "user", content: String(m.content || "").slice(0, 8000) })) : [];
      const upstream = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST", headers: { "Authorization": `Bearer ${env.OPENAI_API_KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify({ model: env.AI_MODEL || "gpt-4o-mini", messages: [{ role: "system", content: "You are VIP X Ultimate\u0027s helpful Arabic-first assistant. Be accurate, safe, and concise. Never claim to unlock paid features in third-party apps." }, ...history, { role: "user", content: message }], temperature: 0.4 })
      });
      const data = await upstream.json();
      if (!upstream.ok) return json({ error: data.error?.message || "AI provider error" }, 502);
      return json({ reply: data.choices?.[0]?.message?.content || "No response" });
    } catch (_) { return json({ error: "Request failed" }, 500); }
  }
};