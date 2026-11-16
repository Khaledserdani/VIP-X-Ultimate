const AGENTS = [
  { id: "structure", name: "مساعد الفهم والتحليل", instruction: "افهم أي مهمة يطلبها المستخدم، حلل التطبيق أو المحتوى أو المشكلة، استخرج المتطلبات والحقائق والقيود، واقترح أفضل مسار. لا تفترض أن المهمة محصورة في APK أو النقر." },
  { id: "security", name: "مساعد الأمن والجودة", instruction: "راجع السلامة والخصوصية والصلاحيات والموثوقية والمخاطر لكل نوع مهمة. اقترح بديلاً عملياً عند وجود مانع، ولا تتجاوز حماية النظام أو خصوصية الغير." },
  { id: "design", name: "مساعد الحلول والتجربة", instruction: "ابحث عن أفضل تجربة وحل للمستخدم. اقترح تحسينات عملية وطرقاً بديلة وأدوات مناسبة بحسب المهمة، وليس فقط تعديلات الواجهة." },
  { id: "engineering", name: "مساعد التنفيذ والتكامل", instruction: "حوّل الهدف إلى خطة قابلة للتنفيذ باستخدام قدرات Android والتكاملات وواجهات APIs والملفات والشبكة المتاحة. حدد المتطلبات والنتائج واختبارات النجاح، ولا تدّع تنفيذ شيء لم يحدث." }
];

async function runAgent(agent, context, askAI, memory) {
  const prior = memory && memory.lastVerified ? "آخر نتيجة مثبتة:\n" + memory.lastVerified.content : "لا توجد نتيجة مثبتة سابقة.";
  return {
    id: agent.id,
    name: agent.name,
    result: await askAI([
      { role: "system", content: "أنت عضو مستقل ومتعاون في فريق VIP X متعدد التخصصات. مهمتك مساعدة المستخدم في أي مجال مشروع ومسموح، لا تقتصر على تطبيق Android. فكر بعمق، اقترح أدوات وخيارات، واطلب من زملائك ضمنياً ما يلزم عبر تقارير واضحة. افصل الحقائق عن الافتراضات، ولا تخترع نتائج أو قدرات غير متاحة. " + agent.instruction },
      { role: "user", content: context + "\n\n" + prior }
    ])
  };
}

async function runVipTeam(input, askAI, memoryStore) {
  const context = [
    "المهمة: " + input.goal,
    "التطبيق أو المجال: " + (input.appName || "غير محدد"),
    "معرّف الحزمة إن وجد: " + (input.packageName || "غير متوفر"),
    "السياق والأدلة المتاحة: " + (input.evidence || "لا توجد أدلة إضافية"),
    "القدرات والأدوات المتاحة: " + (input.capabilities || "المحادثة والتحليل وتكاملات النظام المصرح بها"),
    "القيود والتفضيلات: " + (input.constraints || "احرص على أفضل نتيجة عملية")
  ].join("\n");
  const memories = await Promise.all(AGENTS.map(a => memoryStore.getAgentMemory(a.id)));
  const reports = await Promise.all(AGENTS.map((a, i) => runAgent(a, context, askAI, memories[i])));
  for (const r of reports) await memoryStore.saveCandidate(r.id, r.result, {
    appName: input.appName || "general",
    packageName: input.packageName || "general",
    source: "vip-team"
  });
  const decision = await askAI([
    { role: "system", content: "أنت قائد فريق VIP X. نسّق مساعدين مستقلين لإنجاز أي مهمة مفوضة، لا تحصر الحل في APK أو أربع نقرات. اختر أفضل مسار عملي، قسّم المهمة إلى مراحل، وحدد الأدوات والتكاملات اللازمة والنتائج المتوقعة واختبارات التحقق. ميّز ما يمكن تنفيذه الآن عن ما يحتاج صلاحية أو خدمة أو تأكيداً من المستخدم. لا تدّع تنفيذ إجراءات أو الوصول إلى بيانات غير متاحة." },
    { role: "user", content: context + "\n\n" + reports.map(r => r.name + ":\n" + r.result).join("\n\n") }
  ]);
  return { team: reports, decision };
}
module.exports = { AGENTS, runVipTeam };
