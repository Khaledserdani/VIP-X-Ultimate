const http = require("node:http");
const crypto = require("node:crypto");
const { runVipTeam, AGENTS } = require("./agents");
const memoryStore = require("./memory");

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

function authorized(req) {
  const auth = req.headers.authorization || "";
  const supplied = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!APP_ACCESS_TOKEN || supplied.length !== APP_ACCESS_TOKEN.length) return false;
  return crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(APP_ACCESS_TOKEN));
}

async function readBody(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 100_000) throw new Error("الطلب كبير جداً");
  }
  return JSON.parse(raw || "{}");
}

async function askAI(messages) {
  if (!OPENAI_API_KEY) throw new Error("مفتاح الذكاء الاصطناعي غير مضبوط على الخادم");
  const upstream = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: { "Authorization": "Bearer " + OPENAI_API_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({ model: AI_MODEL, messages, temperature: 0.45 }),
    signal: AbortSignal.timeout(80000)
  });
  const data = await upstream.json().catch(() => ({}));
  if (!upstream.ok) throw new Error("تعذر إكمال طلب الذكاء الاصطناعي (مزود الخدمة: " + upstream.status + ")");
  const reply = data.choices?.[0]?.message?.content;
  if (!reply) throw new Error("لم تصل إجابة من الذكاء الاصطناعي");
  return reply;
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, {});
  const url = new URL(req.url, "http://localhost");

  if (req.method === "GET" && url.pathname === "/health") {
    return send(res, 200, { status: "ok", service: "VIP X Ultimate Cloud", aiConfigured: Boolean(OPENAI_API_KEY), accessConfigured: Boolean(APP_ACCESS_TOKEN), memoryConfigured: Boolean(process.env.DATABASE_URL), agents: AGENTS.map(({id,name}) => ({id,name})) });
  }

  if (req.method !== "POST" || !["/api/chat", "/api/vip-plan", "/api/vip-team"].includes(url.pathname) || url.pathname.startsWith("/api/agent-memory/")) {
    return send(res, 404, { error: "المسار غير موجود" });
  }
  if (!authorized(req)) return send(res, 401, { error: "رمز الوصول غير صحيح أو لم يُضبط على الخادم" });

  try {
    if (url.pathname.startsWith("/api/agent-memory/")) {
      if (!process.env.DATABASE_URL) return send(res, 503, { error: "قاعدة الذاكرة الدائمة غير مهيأة" });
      const parts = url.pathname.split("/").filter(Boolean);
      const agentId = parts[2];
      if (!AGENTS.some(a => a.id === agentId)) return send(res, 404, { error: "المساعد غير معروف" });
      if (req.method === "GET") return send(res, 200, await memoryStore.getAgentMemory(agentId));
      if (req.method !== "POST") return send(res, 405, { error: "الطريقة غير مدعومة" });
      const b = await readBody(req);
      if (parts[3] === "promote") {
        const v = b.verification || {};
        if (v.passed !== true || !Array.isArray(v.tests) || v.tests.length === 0 || !String(v.evidence || "").trim())
          return send(res, 400, { error: "يلزم نجاح اختبارات موثقة وأدلة قبل تثبيت النتيجة" });
        return send(res, 200, await memoryStore.promoteVersion(agentId, Number(b.versionId), v));
      }
      if (parts[3] === "rollback") return send(res, 200, await memoryStore.rollbackToLastVerified(agentId));
      return send(res, 404, { error: "عملية الذاكرة غير معروفة" });
    }
    const body = await readBody(req);
    let messages;
    if (url.pathname === "/api/vip-team") {
      const appName = String(body.appName || "").trim().slice(0, 100);
      const packageName = String(body.packageName || "").trim().slice(0, 180);
      const goal = String(body.goal || "").trim().slice(0, 2000);
      const evidence = String(body.evidence || "").trim().slice(0, 12000);
      if (!appName || !packageName || !goal) return send(res, 400, { error: "بيانات التطبيق والهدف مطلوبة" });
      const result = await runVipTeam({ appName, packageName, goal, evidence, apkAttached: false }, askAI, memoryStore);
      return send(res, 200, result);
    } else if (url.pathname === "/api/vip-plan") {
      const appName = String(body.appName || "").trim().slice(0, 100);
      const packageName = String(body.packageName || "").trim().slice(0, 180);
      const goal = String(body.goal || "").trim().slice(0, 2000);
      if (!appName || !packageName || !goal) return send(res, 400, { error: "بيانات التطبيق والهدف مطلوبة" });
      messages = [
        {
          role: "system",
          content: "أنت مهندس Android وخبير توافق وأمن تطبيقات. أعد تحليلاً عربياً عملياً لتصميم تجربة VIP قانونية ومتوافقة للتطبيق المحدد. لا تدّع أنك فحصت ملفات التطبيق أو شفرته؛ المتاح فقط الاسم ومعرّف الحزمة وهدف المستخدم. لا تقترح تجاوز DRM أو تسجيل الدخول أو الاشتراكات أو الحماية أو نسخ بيانات خاصة. وضّح ما يمكن تنفيذه كتطبيق مرافق مستقل، وما يتطلب مصدر التطبيق أو إذن المطور. اقترح ميزات ملموسة قليلة، مع خطة تنفيذ واختبارات قبول. لا تعد بإنتاج APK معدّل من تطبيق مغلق بمجرد قراءة الحزمة. اجعل الإجابة منظمة ومختصرة."
        },
        {
          role: "user",
          content: "التطبيق المختار: " + appName + "\nمعرّف الحزمة: " + packageName + "\nهدف نسخة VIP: " + goal + "\nقدّم دراسة جدوى وخطة ميزات وتنفيذ متوافقة."
        }
      ];
    } else {
      if (!Array.isArray(body.messages) || body.messages.length === 0) return send(res, 400, { error: "أرسل قائمة رسائل صحيحة" });
      messages = body.messages.slice(-20);
    }
    const reply = await askAI(messages);
    return send(res, 200, { reply });
  } catch (error) {
    const message = error.message || "حدث خطأ داخلي أثناء المعالجة";
    const status = message.includes("كبير") ? 413 : 502;
    return send(res, status, { error: message });
  }
});

server.listen(PORT, "0.0.0.0", () => console.log("VIP X Ultimate Cloud listening on port " + PORT));
