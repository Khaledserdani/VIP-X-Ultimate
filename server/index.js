const http = require("node:http");

const PORT = Number(process.env.PORT || 3000);
const APP_ACCESS_TOKEN = process.env.APP_ACCESS_TOKEN || "";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const AI_MODEL = process.env.AI_MODEL || "gpt-4o-mini";

function send(res, status, body) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "Content-Type, Authorization"
  });
  res.end(JSON.stringify(body));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, {});
  const url = new URL(req.url, "http://localhost");

  if (req.method === "GET" && url.pathname === "/health") {
    return send(res, 200, {
      status: "ok",
      service: "VIP X Ultimate Cloud",
      aiConfigured: Boolean(OPENAI_API_KEY),
      accessConfigured: Boolean(APP_ACCESS_TOKEN)
    });
  }

  if (req.method !== "POST" || url.pathname !== "/api/chat") {
    return send(res, 404, { error: "المسار غير موجود" });
  }

  if (!APP_ACCESS_TOKEN || !OPENAI_API_KEY) {
    return send(res, 503, { error: "الخادم منشور، لكن إعدادات الوصول ومفتاح الذكاء الاصطناعي لم تكتمل بعد." });
  }

  const auth = req.headers.authorization || "";
  const supplied = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (supplied.length !== APP_ACCESS_TOKEN.length ||
      !require("node:crypto").timingSafeEqual(Buffer.from(supplied), Buffer.from(APP_ACCESS_TOKEN))) {
    return send(res, 401, { error: "رمز الوصول غير صحيح" });
  }

  try {
    let raw = "";
    for await (const chunk of req) {
      raw += chunk;
      if (raw.length > 1_000_000) return send(res, 413, { error: "الطلب كبير جداً" });
    }
    const body = JSON.parse(raw || "{}");
    if (!Array.isArray(body.messages) || body.messages.length === 0) {
      return send(res, 400, { error: "أرسل قائمة رسائل صحيحة" });
    }

    const upstream = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + OPENAI_API_KEY,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: AI_MODEL,
        messages: body.messages.slice(-20),
        temperature: 0.7
      }),
      signal: AbortSignal.timeout(60000)
    });

    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      return send(res, 502, { error: "تعذر على مزود الذكاء الاصطناعي إكمال الطلب", providerStatus: upstream.status });
    }
    const reply = data.choices && data.choices[0] && data.choices[0].message
      ? data.choices[0].message.content : "";
    if (!reply) return send(res, 502, { error: "لم تصل إجابة من مزود الذكاء الاصطناعي" });
    return send(res, 200, { reply });
  } catch (error) {
    return send(res, 500, { error: "حدث خطأ داخلي أثناء معالجة الطلب" });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log("VIP X Ultimate Cloud listening on port " + PORT);
});
