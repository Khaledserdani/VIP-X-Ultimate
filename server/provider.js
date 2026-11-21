const GEMINI_API_KEY = process.env.GEMINI_API_KEY || "";
const GROQ_API_KEY = process.env.GROQ_API_KEY || "";
const AI_PROVIDER = String(process.env.AI_PROVIDER || "auto").toLowerCase();
const GEMINI_MODEL = "gemini-3.1-flash-lite";
const GROQ_MODEL = "llama-3.3-70b-versatile";

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
    const value = String(message.content ?? "");
    if (!value) continue;
    if (message.role === "system" || message.role === "developer") {
      system.push(value);
      continue;
    }
    const role = message.role === "assistant" ? "model" : "user";
    const last = contents[contents.length - 1];
    if (last && last.role === role) last.parts[0].text += "\n\n" + value;
    else contents.push({ role, parts: [{ text: value }] });
  }
  return { system, contents };
}

function retryAfterOf(response) {
  const raw = response.headers.get("retry-after");
  if (!raw) return 0;
  const seconds = Number(raw);
  if (Number.isFinite(seconds) && seconds > 0) return seconds;
  const date = Date.parse(raw);
  return Number.isFinite(date) ? Math.max(1, Math.ceil((date - Date.now()) / 1000)) : 0;
}

function canFallback(error) {
  return error && (error.status === 408 || error.status === 429 || error.status >= 500 || error.status === 502 || error.status === 504);
}

async function requestJson(url, options, providerName) {
  let response;
  try {
    response = await fetch(url, { ...options, signal: AbortSignal.timeout(45000) });
  } catch (cause) {
    const timeout = cause && (cause.name === "TimeoutError" || cause.name === "AbortError");
    throw providerError("تعذر الاتصال بمزود " + providerName + (timeout ? " بسبب انتهاء المهلة" : ""), timeout ? 504 : 502);
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const retryAfter = retryAfterOf(response);
    const status = response.status >= 500 ? 502 : response.status;
    const detail = String(data.error?.message || data.error?.status || "").slice(0, 180);
    throw providerError("رفض مزود " + providerName + " الطلب (HTTP " + response.status + ")" + (detail ? ": " + detail : ""), status, retryAfter);
  }
  return data;
}

async function askGemini(messages) {
  const { system, contents } = normalizeGeminiMessages(messages);
  if (!contents.length) throw providerError("لا توجد رسالة صالحة لإرسالها إلى الذكاء الاصطناعي", 400);
  const data = await requestJson(
    "https://generativelanguage.googleapis.com/v1beta/models/" + encodeURIComponent(GEMINI_MODEL) + ":generateContent",
    {
      method: "POST",
      headers: { "x-goog-api-key": GEMINI_API_KEY, "Content-Type": "application/json" },
      body: JSON.stringify({
        ...(system.length ? { systemInstruction: { parts: [{ text: system.join("\n\n") }] } } : {}),
        contents,
        generationConfig: { temperature: 0.35 }
      })
    },
    "Gemini"
  );
  const reply = (data.candidates?.[0]?.content?.parts || []).map(part => part.text || "").join("").trim();
  if (!reply) throw providerError("لم تصل إجابة نصية من Gemini", 502);
  return reply;
}

async function askGroq(messages) {
  const data = await requestJson("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: { "Authorization": "Bearer " + GROQ_API_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({ model: GROQ_MODEL, messages, temperature: 0.35 })
  }, "Groq");
  const reply = data.choices?.[0]?.message?.content;
  if (!reply) throw providerError("لم تصل إجابة نصية من Groq", 502);
  return String(reply);
}

function configuredProviders() {
  const all = {
    gemini: GEMINI_API_KEY ? { id: "gemini", freeTier: true, run: askGemini } : null,
    groq: GROQ_API_KEY ? { id: "groq", freeTier: true, run: askGroq } : null
  };
  const order = AI_PROVIDER === "groq" ? ["groq", "gemini"] : ["gemini", "groq"];
  return order.map(id => all[id]).filter(Boolean);
}

function providerConfigured() { return configuredProviders().length > 0; }
function selectedProvider() { return configuredProviders()[0]?.id || "none"; }

async function askAI(messages) {
  const providers = configuredProviders();
  if (!providers.length) {
    throw providerError("لا يوجد مزود مجاني مضبوط. أضف GEMINI_API_KEY أو GROQ_API_KEY إلى أسرار الخادم. لا يدعم هذا الإصدار أي مزود مدفوع.", 503);
  }
  const failures = [];
  for (let index = 0; index < providers.length; index++) {
    const provider = providers[index];
    try {
      return await provider.run(messages);
    } catch (error) {
      failures.push(provider.id + ": " + error.message);
      if (!canFallback(error) || index === providers.length - 1) {
        error.message = failures.join(" | ");
        throw error;
      }
    }
  }
  throw providerError(failures.join(" | "), 502);
}

module.exports = { askAI, providerConfigured, selectedProvider, normalizeGeminiMessages, configuredProviders, canFallback };
