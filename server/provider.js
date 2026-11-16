const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || "";
const AI_PROVIDER = String(process.env.AI_PROVIDER || (GEMINI_API_KEY ? "gemini" : "openai")).toLowerCase();
const OPENAI_MODEL = process.env.AI_MODEL || "gpt-4o-mini";
const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-3.8-flash";

function providerConfigured() {
  if (AI_PROVIDER === "gemini") return Boolean(GEMINI_API_KEY);
  if (AI_PROVIDER === "openai") return Boolean(OPENAI_API_KEY);
  return false;
}

function selectedProvider() {
  return AI_PROVIDER;
}

function providerError(message, status, retryAfter) {
  const error = new Error(message);
  error.status = status;
  if (retryAfter > 0) error.retryAfter = retryAfter;
  return error;
}

function normalizeGeminiMessages(messages) {
  const system = [];
  const contents = [];
  for (const message of messages || []) {
    const text = String(message.content ?? "");
    if (!text) continue;
    if (message.role === "system" || message.role === "developer") {
      system.push(text);
      continue;
    }
    const role = message.role === "assistant" ? "model" : "user";
    const last = contents[contents.length - 1];
    if (last && last.role === role) last.parts[0].text += "\n\n" + text;
    else contents.push({ role, parts: [{ text }] });
  }
  return { system, contents };
}

async function askGemini(messages) {
  const { system, contents } = normalizeGeminiMessages(messages);
  if (!contents.length) throw providerError("لا توجد رسالة صالحة لإرسالها إلى الذكاء الاصطناعي", 400);
  let response;
  try {
    response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/" + encodeURIComponent(GEMINI_MODEL) + ":generateContent",
      {
        method: "POST",
        headers: { "x-goog-api-key": GEMINI_API_KEY, "Content-Type": "application/json" },
        body: JSON.stringify({
          ...(system.length ? { systemInstruction: { parts: [{ text: system.join("\n\n") }] } } : {}),
          contents,
          generationConfig: { temperature: 0.35 }
        }),
        signal: AbortSignal.timeout(80000)
      }
    );
  } catch (cause) {
    throw providerError(
      "تعذر الاتصال بمزود Gemini؛ حاول لاحقاً",
      cause && cause.name === "TimeoutError" ? 504 : 502
    );
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const retryAfter = Number(response.headers.get("retry-after") || 0);
    const providerMessage = String(data.error?.message || "");
    const message = response.status === 429
      ? "وصلت إلى الحد المجاني المؤقت لـ Gemini. انتظر تجدد الحصة أو خفّض عدد الطلبات؛ لا يلزم شحن رصيد مدفوع."
      : "رفض مزود Gemini الطلب (HTTP " + response.status + ")" + (providerMessage ? ": " + providerMessage.slice(0, 240) : "");
    throw providerError(message, response.status === 429 ? 429 : (response.status >= 500 ? 502 : response.status), retryAfter);
  }
  const reply = (data.candidates?.[0]?.content?.parts || [])
    .map(part => part.text || "")
    .join("")
    .trim();
  if (!reply) {
    const reason = data.promptFeedback?.blockReason || data.candidates?.[0]?.finishReason || "";
    throw providerError("لم تصل إجابة نصية من Gemini" + (reason ? " (" + reason + ")" : ""), 502);
  }
  return reply;
}

async function askOpenAI(messages) {
  let response;
  try {
    response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: { "Authorization": "Bearer " + OPENAI_API_KEY, "Content-Type": "application/json" },
      body: JSON.stringify({ model: OPENAI_MODEL, messages, temperature: 0.35 }),
      signal: AbortSignal.timeout(80000)
    });
  } catch (cause) {
    throw providerError("تعذر الاتصال بمزود OpenAI؛ حاول لاحقاً", cause && cause.name === "TimeoutError" ? 504 : 502);
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const code = data.error?.code || data.error?.type || "";
    const quota = response.status === 429 && /quota|billing|insufficient_quota/i.test(String(code) + " " + String(data.error?.message || ""));
    const retryAfter = Number(response.headers.get("retry-after") || 0);
    const message = quota
      ? "نفدت حصة OpenAI API أو لم تُفعّل الفوترة. يمكن تحويل الخدمة إلى مزود مجاني بإضافة GEMINI_API_KEY في إعدادات الخادم."
      : response.status === 429
        ? "تجاوزت طلبات OpenAI الحد المؤقت؛ انتظر قليلاً ثم أعد المحاولة."
        : "رفض مزود OpenAI الطلب (HTTP " + response.status + ")";
    throw providerError(message, response.status === 429 ? 429 : (response.status >= 500 ? 502 : response.status), retryAfter);
  }
  const reply = data.choices?.[0]?.message?.content;
  if (!reply) throw providerError("لم تصل إجابة من OpenAI", 502);
  return reply;
}

async function askAI(messages) {
  if (!providerConfigured()) {
    if (AI_PROVIDER === "gemini") throw providerError("أضف مفتاح GEMINI_API_KEY المجاني إلى متغيرات خادم Railway", 503);
    if (AI_PROVIDER === "openai") throw providerError("مفتاح OpenAI API غير مضبوط على الخادم", 503);
    throw providerError("مزود الذكاء الاصطناعي غير مضبوط", 503);
  }
  if (AI_PROVIDER === "gemini") return askGemini(messages);
  if (AI_PROVIDER === "openai") return askOpenAI(messages);
  throw providerError("قيمة AI_PROVIDER غير مدعومة؛ استخدم gemini أو openai", 500);
}

module.exports = { askAI, providerConfigured, selectedProvider, normalizeGeminiMessages };
