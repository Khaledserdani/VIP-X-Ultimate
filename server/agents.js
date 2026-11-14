const AGENTS = [
  { id: "structure", name: "مساعد تحليل البنية", instruction: "حلل البنية والأدلة المتاحة فقط. لا تدّع فحص APK أو المصدر إن لم يصلك." },
  { id: "security", name: "مساعد الأمن والتوافق", instruction: "راجع الصلاحيات والتوقيع والتبعيات والتوافق والاختبارات. لا تقترح تجاوز DRM أو المصادقة أو الاشتراكات." },
  { id: "design", name: "مساعد تصميم VIP", instruction: "صمم تجربة VIP ذات قيمة فعلية وواجهة راقية، وميّز بين تعديل داخلي وتطبيق مرافق." },
  { id: "engineering", name: "مساعد التنفيذ والبناء", instruction: "حوّل الهدف إلى خطوات تنفيذ وملفات وأدوات وبناء وتوقيع واختبارات قبول." }
];
async function runAgent(agent, context, askAI, memory) {
  const prior = memory && memory.lastVerified ? "آخر نتيجة مثبتة:\n" + memory.lastVerified.content : "لا توجد نتيجة مثبتة سابقة.";
  return { id: agent.id, name: agent.name, result: await askAI([
    { role: "system", content: "أنت عضو متخصص في فريق VIP X. أجب بالعربية بدقة ولا تخترع نتائج فحص. قارن المقترحات بآخر نتيجة مثبتة ولا تستبدلها دون أدلة واختبارات. " + agent.instruction },
    { role: "user", content: context + "\n\n" + prior }
  ]) };
}
async function runVipTeam(input, askAI, memoryStore) {
  const context = [
    "التطبيق: " + input.appName, "الحزمة: " + input.packageName, "الهدف: " + input.goal,
    "هل وصل APK فعلي؟ " + (input.apkAttached ? "نعم" : "لا"),
    "أدلة الفحص: " + (input.evidence || "لا توجد"),
    "المطلوب: أفضل مسار عملي لتجربة VIP أو تعديل مصرح به مع إبقاء التطبيق الأصلي سليماً."
  ].join("\n");
  const memories = await Promise.all(AGENTS.map(a => memoryStore.getAgentMemory(a.id)));
  const reports = await Promise.all(AGENTS.map((a,i) => runAgent(a, context, askAI, memories[i])));
  for (const r of reports) await memoryStore.saveCandidate(r.id, r.result, { appName: input.appName, packageName: input.packageName, source: "vip-team" });
  const decision = await askAI([
    { role: "system", content: "أنت منسق فريق VIP X. ادمج التقارير وافصل الحقائق عن الافتراضات واختر مساراً هندسياً واحداً مع خطوات واختبارات. لا تدّع إنشاء APK لم يتم بناؤه، ولا تعتمد ذاكرة جديدة كحقيقة مثبتة." },
    { role: "user", content: context + "\n\n" + reports.map(r => r.name + ":\n" + r.result).join("\n\n") }
  ]);
  return { team: reports, decision };
}
module.exports = { AGENTS, runVipTeam };
