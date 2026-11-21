const FREE_MODEL = "gemini-4-hydrogen";

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Authorization, Content-Type"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    const json = (data, status = 200) => new Response(JSON.stringify(data), {
      status,
      headers: { ...cors, "Content-Type": "application/json; charset=utf-8" }
    });

    if (new URL(request.url).pathname !== "/api/chat" || request.method !== "POST") {
      return json({ error: "Not found" }, 404);
    }
    if (!env.APP_ACCESS_TOKEN || !env.GEMINI_API_KEY) {
      return json({ error: "يلزم إعداد GEMINI_API_KEY وAPP_ACCESS_TOKEN في أسرار Worker." }, 503);
    }
    if (request.headers.get("Authorization") !== `Bearer ${env.APP_ACCESS_TOKEN}`) {
      return json({ error: "Unauthorized" }, 401);
    }

    try {
      const body = await request.json();
      const message = String(body.message || "").trim();
      if (!message || message.length > 8000) return json({ error: "Invalid message" }, 400);

      const history = Array.isArray(body.history)
        ? body.history.slice(-12).map(item => ({
            role: item.role === "assistant" ? "model" : "user",
            parts: [{ text: String(item.content || "").slice(0, 4000) }]
          })).filter(item => item.parts[0].text.trim().length > 0)
        : [];
      history.push({ role: "user", parts: [{ text: message }] });

      // Fixed to a model listed in Google's free tier. No paid-provider fallback exists.
      const upstream = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${FREE_MODEL}:generateContent`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": env.GEMINI_API_KEY
          },
          body: JSON.stringify({
            systemInstruction: {
              parts: [{
                text: "You are VIP X Ultimate's Arabic-first assistant. Be accurate, safe, concise, and clear. Never claim to unlock paid features in third-party apps. If uncertain, say so."
              }]
            },
            contents: history,
            generationConfig: { temperature: 0.4, maxOutputTokens: 2048 }
          })
        }
      );

      const data = await upstream.json().catch(() => ({}));
      if (!upstream.ok) {
        const status = upstream.status === 429 ? 429 : 502;
        return json({
          error: upstream.status === 429
            ? "وصلنا إلى حد الاستخدام المجاني حالياً. لا توجد أي محاولة للتحويل إلى خدمة مدفوعة؛ جرّب لاحقاً."
            : "تعذر الاتصال بخدمة Gemini المجانية. لم يتم استخدام أي مزوّد مدفوع."
        }, status);
      }

      const reply = (data.candidates?.[0]?.content?.parts || [])
        .map(part => part.text || "")
        .join("")
        .trim();
      if (!reply) return json({ error: "لم تصل إجابة نصية من الخدمة المجانية." }, 502);
      return json({ reply, provider: "Gemini Free Tier", model: FREE_MODEL });
    } catch (_) {
      return json({ error: "فشل الاتصال. بقيت الوظائف المحلية متاحة ولم يُستدعَ أي مزوّد مدفوع." }, 500);
    }
  }
};
