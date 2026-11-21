const http = require("node:http");
const crypto = require("node:crypto");
const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const execFileAsync = promisify(execFile);
const { runVipTeam, AGENTS } = require("./agents");
const { executeToolCall, TOOL_REGISTRY } = require("./tools");
const memoryStore = require("./memory");

const PORT = Number(process.env.PORT || 3000);
const APP_ACCESS_TOKEN = process.env.APP_ACCESS_TOKEN || "";
const { askAI, providerConfigured, selectedProvider } = require("./provider");

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

function isNoOpGoal(goal) {
  const value = String(goal || "").trim().toLowerCase().replace(/[ًٌٍَُِّْـ]/g, "");
  return /^(لا\s*(?:تنجز|تفعل|تعمل|تنفذ|تقم\s+ب|تسوي)\s*(?:اي\s*)?(?:شي(?:ء|ئا)?|شئ)[.!؟\s]*|لا\s*(?:شي(?:ء|ئا)?|شئ)[.!؟\s]*|توقف(?:\s+فقط)?[.!؟\s]*|do\s+nothing[.!\s]*)$/.test(value);
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, {});
  const url = new URL(req.url, "http://localhost");

  if (req.method === "GET" && url.pathname === "/health") {
    return send(res, 200, { status: "ok", service: "VIP X Ultimate Cloud", aiConfigured: providerConfigured(), aiProvider: selectedProvider(), accessConfigured: Boolean(APP_ACCESS_TOKEN), memoryConfigured: Boolean(process.env.DATABASE_URL), agents: AGENTS.map(({id,name}) => ({id,name})) });
  }

  if ((req.method !== "POST" && !(req.method === "GET" && (url.pathname === "/api/session" || url.pathname.startsWith("/api/agent-memory/")))) || (!["/api/chat", "/api/vip-plan", "/api/vip-team", "/api/apk/analyze", "/api/in-app/plan", "/api/mission"].includes(url.pathname) && url.pathname !== "/api/session" && !url.pathname.startsWith("/api/agent-memory/"))) {
    return send(res, 404, { error: "المسار غير موجود" });
  }
  if (!authorized(req)) return send(res, 401, { error: "رمز الوصول غير صحيح أو لم يُضبط على الخادم" });

  try {
    if (req.method === "GET" && url.pathname === "/api/session") {
      return send(res, 200, {
        authenticated: true,
        aiConfigured: providerConfigured(),
        memoryConfigured: Boolean(process.env.DATABASE_URL),
        agents: AGENTS.map(({ id, name }) => ({ id, name }))
      });
    }
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
    if (url.pathname === "/api/mission") {
      const goal = String(body.task || body.goal || "").trim().slice(0, 3000);
      if (!goal) return send(res, 400, { error: "اكتب المهمة المطلوبة أولاً" });
      // A direct no-op is a valid user instruction; never send it to the AI provider.
      if (isNoOpGoal(goal)) {
        return send(res, 200, {
          mission: goal,
          decision: "تم احترام طلبك. لن أنفّذ أي إجراء.",
          team: [],
          execution: [],
          availableTools: TOOL_REGISTRY.map(({id,name,description}) => ({id,name,description})),
          actions: []
        });
      }

      const context = [
        "المهمة: " + goal,
        "التطبيق أو المجال: " + String(body.appName || "غير محدد").slice(0, 120),
        "معرّف الحزمة: " + String(body.packageName || "غير متوفر").slice(0, 220),
        "السياق والأدلة: " + String(body.context || body.screenContext || "لا توجد أدلة إضافية").slice(0, 10000),
        "القدرات: " + String(body.capabilities || "بحث ويب وقراءة صفحات عامة وحساب آمن وتحليل APK وأوامر Android المصرح بها"),
        "القيود: " + String(body.constraints || "احمِ بيانات المستخدم ولا تدّع تنفيذ ما لم يحدث")
      ].join("\n");
      // The Android mission screen explicitly requests the four-agent team.
      // Memory is optional; runVipTeam degrades gracefully when no database is configured.
      const teamResult = body.useTeam === true
        ? await runVipTeam({
            appName: String(body.appName || "غير محدد").slice(0, 120),
            packageName: String(body.packageName || "").slice(0, 220),
            goal,
            evidence: context
          }, askAI, memoryStore)
        : { team: [], decision: "", memoryStatus: "not_requested" };
      const teamEvidence = teamResult.team.map(item => item.name + ": " + item.result).join("\n\n");
      let rawPlan;
      try {
        rawPlan = await askAI([
          { role: "system", content: "أنت قائد VIP X ومساعد عملي متعدد المجالات. حلل الطلب مباشرة ولا تستدع فريقاً افتراضياً لكل مهمة. أخرج JSON فقط: decision نص عربي واضح، toolCalls مصفوفة حتى 3 عناصر، actions مصفوفة حتى 5 عناصر. أدوات toolCalls المسموحة: web_search مع {query}، web_fetch مع {url}، calculator مع {expression}. إجراءات actions المسموحة: OPEN_URL و SHARE_TEXT و TAP_TEXT و SCROLL_FORWARD و SCROLL_BACK و BACK. لا تخترع نتائج أو قدرات، ولا تدّع تنفيذ إجراء لم يحدث. لا تنشئ إجراءات محلية إلا عند وجود سبب واضح؛ TAP_TEXT يتطلب نصاً حرفياً موجوداً في سياق الشاشة. OPEN_URL يتطلب رابط HTTPS معروفاً في الطلب أو أدلة المستخدم. لا شراء أو دفع أو حذف أو مراسلة تلقائية أو تسجيل دخول. إذا لم تحتج أدوات فأعد toolCalls فارغة." },
          { role: "user", content: context + (teamEvidence ? "\n\nتقارير فريق VIP X المستقل:\n" + teamEvidence + "\n\nتوجيه منسق الفريق:\n" + teamResult.decision : "") }
        ]);
      } catch (error) {
        const fallback = teamEvidence
          ? "تعذر إعداد خطة إضافية بسبب حصة أو اتصال المزود. احتفظت بتقارير الفريق أدناه؛ لم تُنفّذ إجراءات داخل التطبيقات."
          : "تعذر الاتصال بمزود الذكاء الاصطناعي المجاني. لم تُنفّذ أي إجراءات.";
        return send(res, 200, {
          mission: goal,
          decision: fallback + (teamResult.decision ? "\\n" + teamResult.decision : ""),
          team: teamResult.team,
          memoryStatus: teamResult.memoryStatus,
          execution: [],
          availableTools: TOOL_REGISTRY.map(({id,name,description}) => ({id,name,description})),
          actions: [],
          warning: "provider_unavailable"
        });
      }
      let plan = {};
      try { plan = JSON.parse(rawPlan.replace(/^\`\`\`(?:json)?\s*/i, "").replace(/\s*\`\`\`$/i, "")); }
      catch {
        return send(res, 200, { mission: goal, decision: rawPlan, team: teamResult.team, memoryStatus: teamResult.memoryStatus, execution: [], availableTools: TOOL_REGISTRY, actions: [] });
      }

      const executionResults = [];
      const calls = Array.isArray(plan.toolCalls) ? plan.toolCalls.slice(0, 3) : [];
      for (const call of calls) {
        try {
          executionResults.push(await executeToolCall({ name: call.name, arguments: call.arguments || {} }));
        } catch (error) {
          executionResults.push({ tool: call.name || "unknown", error: error.message || "تعذر تنفيذ الأداة" });
        }
      }

      let decision = String(plan.decision || "تم تحليل طلبك.");
      if (executionResults.some(item => item.output)) {
        decision = await askAI([
          { role: "system", content: "أجب بالعربية اعتماداً على نتائج الأدوات الفعلية فقط. وضّح ما تم وما لم يتم، وضع روابط المصادر عند توفرها. لا تخترع نتائج." },
          { role: "user", content: "المهمة: " + goal + "\nالخطة الأولية: " + decision + "\nنتائج الأدوات: " + JSON.stringify(executionResults).slice(0, 16000) }
        ]);
      }

      const allowedLocal = new Set(["OPEN_URL", "SHARE_TEXT", "TAP_TEXT", "SCROLL_FORWARD", "SCROLL_BACK", "BACK"]);
      const actions = (Array.isArray(plan.actions) ? plan.actions : []).slice(0, 5).filter(a => {
        if (!a || !allowedLocal.has(a.type)) return false;
        const target = String(a.target || "").trim();
        if (a.type === "OPEN_URL") return /^https:\/\//i.test(target) &&
          (String(body.context || "").includes(target) || String(body.screenContext || "").includes(target) || goal.includes(target));
        if (a.type === "SHARE_TEXT") return target.length > 0 && target.length <= 4000;
        if (a.type === "TAP_TEXT") return target.length > 0 && String(body.context || body.screenContext || "").includes(target);
        return true;
      }).map(a => ({ type: a.type, target: String(a.target || "").slice(0, 4000), reason: String(a.reason || "").slice(0, 240) }));

      return send(res, 200, {
        mission: goal,
        decision,
        team: teamResult.team,
        memoryStatus: teamResult.memoryStatus,
        execution: executionResults,
        availableTools: TOOL_REGISTRY.map(({id,name,description}) => ({id,name,description})),
        actions
      });
    }
    if (url.pathname === "/api/in-app/plan") {
      const task = String(body.task || "").trim().slice(0, 1500);
      const appName = String(body.appName || "التطبيق المفتوح").trim().slice(0, 100);
      const packageName = String(body.packageName || "").trim().slice(0, 200);
      const screenContext = String(body.screenContext || "").trim().slice(0, 3500);
      if (!task || !packageName) return send(res, 400, { error: "المهمة ومعرّف التطبيق مطلوبان" });
      const team = await runVipTeam({
        appName, packageName, goal: task,
        evidence: "سياق الواجهة الظاهر بموافقة المستخدم:\n" + screenContext,
        apkAttached: false
      }, askAI, memoryStore);
      const structured = await askAI([
        { role: "system", content: "حوّل تقارير فريق VIP X إلى خطة إجراءات JSON فقط دون Markdown. أعد كائناً يحتوي على summary كنص و actions كمصفوفة من إجراءات لها type و target و reason. الأنواع المسموحة حصراً: TAP_TEXT و SCROLL_FORWARD و SCROLL_BACK و BACK. حد أقصى 5 إجراءات. TAP_TEXT يحتاج نص زر ظاهر حرفياً في سياق الشاشة. لا تخترع أزراراً أو أسماء عناصر. إذا لم يوجد عنصر واضح أعد actions فارغة واطلب توضيحاً في summary. لا تنشئ إجراءات لكلمات المرور أو الدفع أو التحويلات المالية أو حذف الحسابات أو تغيير إعدادات الأمان أو تجاوز تسجيل الدخول والحماية. لا تضع نصوصاً داخل حقول الإدخال. لا تدّع تنفيذ أي خطوة." },
        { role: "user", content: "التطبيق: " + appName + "\nالحزمة: " + packageName + "\nمهمة المستخدم: " + task + "\nسياق الشاشة: " + screenContext + "\nتقارير الفريق: " + team.team.map(x => x.name + ": " + x.result).join("\n") + "\nقرار المنسق: " + team.decision }
      ]);
      let plan;
      try { plan = JSON.parse(structured.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "")); }
      catch { throw new Error("لم ينتج الفريق خطة إجراءات بصيغة صحيحة؛ لم ينفذ أي إجراء"); }
      const allowed = new Set(["TAP_TEXT", "SCROLL_FORWARD", "SCROLL_BACK", "BACK"]);
      const actions = Array.isArray(plan.actions) ? plan.actions.slice(0, 5).filter(a =>
        a && allowed.has(a.type) && (a.type !== "TAP_TEXT" || (typeof a.target === "string" && a.target.trim().length > 0))
      ).map(a => ({ type: a.type, target: String(a.target || "").slice(0, 120), reason: String(a.reason || "").slice(0, 240) })) : [];
      return send(res, 200, { summary: String(plan.summary || "خطة الفريق جاهزة"), actions, team: team.team.map(a => ({ id: a.id, name: a.name, result: a.result })) });
    }
    if (url.pathname === "/api/vip-team") {
      const appName = String(body.appName || "").trim().slice(0, 100);
      const packageName = String(body.packageName || "").trim().slice(0, 200);
      const goal = String(body.goal || "").trim().slice(0, 2000);
      const evidence = String(body.evidence || "").trim().slice(0, 12000);
      if (!appName || !packageName || !goal) return send(res, 400, { error: "بيانات التطبيق والهدف مطلوبة" });
      const result = await runVipTeam({ appName, packageName, goal, evidence, apkAttached: Boolean(evidence) }, askAI, memoryStore);
      return send(res, 200, result);
    } else if (url.pathname === "/api/vip-plan") {
      const appName = String(body.appName || "").trim().slice(0, 100);
      const packageName = String(body.packageName || "").trim().slice(0, 200);
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
    const status = message.includes("كبير") ? 413 : (Number.isInteger(error.status) ? error.status : 502);
    if (status === 429 && error.retryAfter) res.setHeader("retry-after", String(error.retryAfter));
    return send(res, status, { error: message, retryAfterSeconds: error.retryAfter || null });
  }
});

server.listen(PORT, "0.0.0.0", () => console.log("VIP X Ultimate Cloud listening on port " + PORT));
