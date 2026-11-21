const test = require("node:test");
const assert = require("node:assert/strict");
const { runVipTeam, AGENTS } = require("./agents");

test("four-agent team runs even when optional persistent memory is unavailable", async () => {
  const prompts = [];
  const askAI = async messages => { prompts.push(messages); return "تحليل تجريبي"; };
  const memory = {
    getAgentMemory: async () => { throw new Error("DATABASE_URL not configured"); },
    saveCandidate: async () => { throw new Error("database unavailable"); }
  };
  const result = await runVipTeam({ goal: "حلل مهمة اختبارية" }, askAI, memory);
  assert.equal(result.team.length, 4);
  assert.equal(AGENTS.length, 4);
  assert.equal(prompts.length, 5);
  assert.equal(result.memoryStatus, "unavailable_or_partial");
  assert.match(result.decision, /تحليل تجريبي/);
});

test("four-agent team records candidate memories when storage is available", async () => {
  const saved = [];
  const askAI = async () => "نتيجة مستقلة";
  const memory = {
    getAgentMemory: async agentId => ({ agentId, lastVerified: null }),
    saveCandidate: async (agentId, content) => { saved.push({ agentId, content }); }
  };
  const result = await runVipTeam({ goal: "مهمة ثانية" }, askAI, memory);
  assert.equal(result.team.length, 4);
  assert.equal(saved.length, 4);
  assert.equal(result.memoryStatus, "saved");
});

test("one unavailable agent does not discard the other agent reports", async () => {
  let calls = 0;
  const askAI = async () => {
    calls += 1;
    if (calls === 1) throw new Error("temporary quota failure");
    return "نتيجة متاحة";
  };
  const memory = { getAgentMemory: async () => null };
  const result = await runVipTeam({ goal: "مهمة جزئية" }, askAI, memory);
  assert.equal(result.team.length, 4);
  assert.equal(result.team.filter(item => item.status === "failed").length, 1);
  assert.equal(result.team.filter(item => item.result === "نتيجة متاحة").length, 3);
});
