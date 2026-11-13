const AGENTS = [
  { id: "structure", name: "مساعد تحليل البنية", instruction: "حلل البنية والأدلة المتاحة فقط. لا تدّع فحص APK أو المصدر إن لم يصلك." },
  { id: "security", name: "مساعد الأمن والتوافق", instruction: "راجع الصلاحيات والتوقيع والتبعيات والتوافق والاختبارات. لا تقترح تجاوز DRM أو المصادقة أو الاشتراكات." },
  { id: "design", name: "مساعد تصميم VIP", instruction: "صمم تجربة VIP ذات قيمة فعلية وواجهة راقية، وميّز بين تعديل داخلي وتطبيق مرافق." },
  { id: "engineering", name: "مساعد التنفيذ والبناء", instruction: "حوّل الهدف إلى خطوات تنفيذ وملفات وأدوات وبناء وتوقيع واختبارات قبول." }
];

async function runAgent(agent, context, askAI) {
  const result = await askAI([
    { role: "system", content: "أنت عضو متخصص في فريق VIP X. أجب بالعربية، بدقة، ولا تخترع نتائج فحص. " + agent.instruction },
    { role: "user", content: context }
  ]);
  return { id: agent.id, name: agent.name, result };
}

async function runVipTeam(input, askAI) {
  const context = [
    "التطبيق: " + input.appName,
    "الحزمة: " + input.packageName,
    "الهدف: " + input.goal,
    "هل وصل APK فعلي؟ " + (input.apkAttached ? "نعم" : "لا"),
    "أدلة الفحص: " + (input.evidence || "لا توجد"),
    "المطلوب: أفضل مسار عملي لتجربة VIP أو تعديل مصرح به مع إبقاء التطبيق الأصلي سليماً."
  ].join("\n");
  const reports = await Promise.all(AGENTS.map(agent => runAgent(agent, context, askAI)));
  const decision = await askAI([
    { role: "system", content: "أنت منسق فريق VIP X. ادمج التقارير، افصل الحقائق عن الافتراضات، واختر مساراً هندسياً واحداً مع خطوات التنفيذ والمتطلبات والاختبارات. لا تدّع إنشاء APK لم يتم بناؤه." },
    { role: "user", content: context + "\n\n" + reports.map(r => r.name + ":\n" + r.result).join("\n\n") }
  ]);
  return { team: reports, decision };
}
module.exports = { AGENTS, runVipTeam };
