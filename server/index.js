const http = require("node:http");
const crypto = require("node:crypto");
const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const execFileAsync = promisify(execFile);
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

async function readRaw(req, limit = 120 * 1024 * 1024) {
  const chunks = []; let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw new Error("حجم ملف APK يتجاوز الحد المسموح (120 ميغابايت)");
    chunks.push(chunk);
  }
  if (!size) throw new Error("ملف APK فارغ");
  return Buffer.concat(chunks, size);
}

async function analyzeApk(buffer) {
  const work = await fs.mkdtemp(path.join(os.tmpdir(), "vipx-apk-"));
  const apk = path.join(work, "input.apk");
  const decoded = path.join(work, "decoded");
  try {
    await fs.writeFile(apk, buffer, { mode: 0o600 });
    const hash = crypto.createHash("sha256").update(buffer).digest("hex");
    const { stdout: listing } = await execFileAsync("unzip", ["-Z1", apk], { timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
    const entries = listing.split(/\r?\n/).filter(Boolean);
    const dexFiles = entries.filter(x => x === "classes.dex" || (x.startsWith("classes") && x.endsWith(".dex")));
    const nativeLibs = entries.filter(x => x.startsWith("lib/") && x.split("/").length >= 3);
    const resources = entries.includes("resources.arsc");
    let signature = "لم يتم التحقق من التوقيع";
    try {
      const sig = await execFileAsync("apksigner", ["verify", "--verbose", apk], { timeout: 30000, maxBuffer: 1024 * 1024 });
      signature = "توقيع APK صالح: " + (sig.stdout || sig.stderr || "تم التحقق");
    } catch (e) { signature = "تعذر إثبات صلاحية التوقيع: " + String(e.stderr || e.message).slice(0, 500); }
    await execFileAsync("apktool", ["d", "-f", "-s", "-o", decoded, apk], { timeout: 120000, maxBuffer: 4 * 1024 * 1024 });
    const manifest = await fs.readFile(path.join(decoded, "AndroidManifest.xml"), "utf8");
    const packageName = (manifest.match(/package="([^"]+)"/) || [])[1] || null;
    const minSdk = (manifest.match(/android:minSdkVersion="([^"]+)"/) || [])[1] || null;
    const targetSdk = (manifest.match(/android:targetSdkVersion="([^"]+)"/) || [])[1] || null;
    const permissions = [...manifest.matchAll(/<uses-permission[^>]+android:name="([^"]+)"/g)].map(m => m[1]);
    const activities = [...manifest.matchAll(/<activity[^>]*android:name="([^"]+)"/g)].map(m => m[1]);
    const findings = [];
    if (permissions.some(p => /READ_SMS|RECEIVE_SMS|SEND_SMS|READ_CALL_LOG|WRITE_CALL_LOG|RECORD_AUDIO|CAMERA|ACCESS_FINE_LOCATION/.test(p)))
      findings.push("يطلب التطبيق صلاحيات حساسة؛ راجع ضرورة كل صلاحية قبل أي إعادة بناء.");
    if (!dexFiles.length) findings.push("لم يتم العثور على ملفات DEX قياسية؛ قد يكون APK غير مكتمل أو يستخدم بنية غير معتادة.");
    if (!packageName) findings.push("تعذر استخراج معرّف الحزمة من AndroidManifest.xml.");
    return {
      analysisVersion: "1.0", file: { sizeBytes: buffer.length, sha256: hash },
      packageName, minSdk, targetSdk, signature,
      structure: { dexFiles, nativeLibraries: nativeLibs, hasResourcesTable: resources, totalEntries: entries.length },
      permissions, activities,
      rebuildAssessment: {
        decodedSuccessfully: true,
        canAttemptRebuild: Boolean(packageName && dexFiles.length),
        separateInstallRequiresNewPackageId: true,
        caveats: ["إعادة التوقيع بمفتاح جديد قد تعطل تسجيل الدخول أو خدمات المطور أو فحوص سلامة التطبيق.", "المكتبات الأصلية والاعتماد على توقيع المطور قد تمنع عمل النسخة المعدلة.", "لا تُجرى تعديلات أو تجاوزات على DRM أو الاشتراكات أو المصادقة."]
      },
      findings
    };
  } finally { await fs.rm(work, { recursive: true, force: true }); }
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

  if ((req.method !== "POST" && !(req.method === "GET" && url.pathname.startsWith("/api/agent-memory/"))) || (!["/api/chat", "/api/vip-plan", "/api/vip-team", "/api/apk/analyze"].includes(url.pathname) && !url.pathname.startsWith("/api/agent-memory/"))) {
    return send(res, 404, { error: "المسار غير موجود" });
  }
  if (!authorized(req)) return send(res, 401, { error: "رمز الوصول غير صحيح أو لم يُضبط على الخادم" });

  try {
    if (url.pathname === "/api/apk/analyze") {
      const type = String(req.headers["content-type"] || "");
      if (!type.includes("application/vnd.android.package-archive") && !type.includes("application/octet-stream"))
        return send(res, 415, { error: "أرسل ملف APK بنوع application/vnd.android.package-archive" });
      const apkBuffer = await readRaw(req);
      const analysis = await analyzeApk(apkBuffer);
      return send(res, 200, { analysis });
    }
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
    const packageName = (manifest.match(/package="([^"]+)"/) || [])[1] || null;
      const goal = String(body.goal || "").trim().slice(0, 2000);
      const evidence = String(body.evidence || "").trim().slice(0, 12000);
      if (!appName || !packageName || !goal) return send(res, 400, { error: "بيانات التطبيق والهدف مطلوبة" });
      const result = await runVipTeam({ appName, packageName, goal, evidence, apkAttached: false }, askAI, memoryStore);
      return send(res, 200, result);
    } else if (url.pathname === "/api/vip-plan") {
      const appName = String(body.appName || "").trim().slice(0, 100);
    const packageName = (manifest.match(/package="([^"]+)"/) || [])[1] || null;
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
