package com.vipx.ultimate

import android.content.Context

internal data class VipLocalAgentReport(
    val id: String,
    val name: String,
    val result: String
)

internal data class VipLocalTeamReport(
    val agents: List<VipLocalAgentReport>,
    val decision: String
) {
    fun asArabicText(): String = buildString {
        append("فريق VIP X المحلي • أربعة مساعدين\n\n")
        agents.forEachIndexed { index, report ->
            append(index + 1).append(") ").append(report.name).append("\n")
            append(report.result).append("\n\n")
        }
        append("قرار المنسّق النهائي\n").append(decision)
    }
}

/** Four specialist agents powered by the same on-device Qwen model; no cloud API. */
internal object VipLocalAgentTeam {
    private data class Agent(val id: String, val name: String, val instruction: String)

    private val agents = listOf(
        Agent(
            "analysis", "مساعد الفهم والتحليل",
            "حلّل الهدف بعمق، استخرج الحقائق والمتطلبات والقيود والافتراضات، وحدد المعلومات الناقصة دون اختراعها."
        ),
        Agent(
            "security", "مساعد الأمن والجودة",
            "راجع الخصوصية والأمان والموثوقية والمخاطر. اذكر القيود والاختبارات الضرورية واقترح بديلاً آمناً عند وجود مانع."
        ),
        Agent(
            "experience", "مساعد الحلول والتجربة",
            "اقترح أفضل حل عملي وتجربة استخدام، وقارن البدائل المفيدة واختر الأنسب مع تبرير مختصر."
        ),
        Agent(
            "engineering", "مساعد التنفيذ والتكامل",
            "حوّل الهدف إلى خطوات هندسية قابلة للتنفيذ، وحدد المكونات والتبعيات والاختبارات، ولا تدّع تنفيذ شيء لم يحدث."
        )
    )

    suspend fun run(context: Context, goal: String): VipLocalTeamReport {
        require(goal.isNotBlank()) { "اكتب المهمة أولاً." }
        val reports = mutableListOf<VipLocalAgentReport>()
        val prior = context.getSharedPreferences("vipx_local_agent_memory", Context.MODE_PRIVATE)
        val history = agents.joinToString("\n") { agent ->
            prior.getString("verified_${agent.id}", null)?.let { "${agent.name}: $it" }.orEmpty()
        }.ifBlank { "لا توجد نتائج سابقة معتمدة." }

        for (agent in agents) {
            val prompt = """
                أنت ${agent.name}، عضو متخصص مستقل ضمن فريق VIP X.
                مهمتك: ${agent.instruction}
                قواعد: أجب بالعربية الواضحة. افصل الحقائق عن الافتراضات. لا تدّع الوصول إلى الإنترنت أو تنفيذ إجراءات. لا توافق زملاءك تلقائياً؛ قدّم رأيك المستقل.
                سياق سابق معتمد للفريق:
                $history
                مهمة المستخدم:
                $goal
                قدّم تقريراً مركزاً يتضمن التحليل والنتيجة وأهم التحفظات.
            """.trimIndent()
            val result = LocalLlmRuntime.generate(prompt)
            reports += VipLocalAgentReport(agent.id, agent.name, result)
            prior.edit().putString("candidate_${agent.id}", result).apply()
        }

        val synthesis = buildString {
            append("مهمة المستخدم:\n").append(goal).append("\n\n")
            reports.forEach { append(it.name).append(":\n").append(it.result).append("\n\n") }
        }
        val decision = LocalLlmRuntime.generate(
            """
                أنت منسّق فريق VIP X. اجمع تقارير أربعة مساعدين مستقلين في قرار واحد.
                وازن بين الدقة والأمان والفائدة. إذا اختلفوا فاشرح الاختلاف. أعطِ خطة عملية مرتبة واختبارات نجاح واضحة.
                لا تدّع تنفيذ أي خطوة. أجب بالعربية.
                $synthesis
            """.trimIndent()
        )
        prior.edit().putString("last_team_report", VipLocalTeamReport(reports, decision).asArabicText()).apply()
        return VipLocalTeamReport(reports, decision)
    }

    fun lastReport(context: Context): String? =
        context.getSharedPreferences("vipx_local_agent_memory", Context.MODE_PRIVATE)
            .getString("last_team_report", null)

    /** Promote candidate reports only after the user explicitly confirms their validation. */
    fun confirmCurrentResults(context: Context) {
        val prefs = context.getSharedPreferences("vipx_local_agent_memory", Context.MODE_PRIVATE)
        agents.forEach { agent ->
            prefs.getString("candidate_${agent.id}", null)?.let {
                prefs.edit().putString("verified_${agent.id}", it).apply()
            }
        }
    }
}
