const test = require("node:test");
const assert = require("node:assert/strict");

process.env.AI_PROVIDER = "auto";
process.env.ALLOW_PAID_AI = "true"; // legacy paid flag must be ignored
process.env.GEMINI_API_KEY = "test-gemini-key";
process.env.GROQ_API_KEY = "test-groq-key";
process.env.OPENAI_API_KEY = "test-openai-key";
process.env.GEMINI_MODEL = "gemini-3.1-flash-lite";
process.env.GROQ_MODEL = "llama-3.3-70b-versatile";

const { askAI, normalizeGeminiMessages, providerConfigured, selectedProvider, configuredProviders, canFallback } = require("./provider");

test("only free providers are selectable even when legacy paid flags are present", () => {
  assert.equal(providerConfigured(), true);
  assert.equal(selectedProvider(), "gemini");
  assert.deepEqual(configuredProviders().map(p => p.id), ["gemini", "groq"]);
});

test("system and assistant messages are converted to Gemini roles", () => {
  const result = normalizeGeminiMessages([
    { role: "system", content: "System rule" },
    { role: "user", content: "Hello" },
    { role: "assistant", content: "Hi" },
    { role: "assistant", content: "More" },
    { role: "user", content: "Continue" }
  ]);
  assert.deepEqual(result.system, ["System rule"]);
  assert.deepEqual(result.contents, [
    { role: "user", parts: [{ text: "Hello" }] },
    { role: "model", parts: [{ text: "Hi\n\nMore" }] },
    { role: "user", parts: [{ text: "Continue" }] }
  ]);
});

test("Gemini success returns its answer without calling another provider", async () => {
  const originalFetch = global.fetch;
  let calls = 0;
  global.fetch = async (url, options) => {
    calls++;
    assert.match(String(url), /gemini-2\.5-flash:generateContent/);
    assert.equal(options.headers["x-goog-api-key"], "test-gemini-key");
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "Gemini reply" }] } }] }), { status: 200 });
  };
  try {
    assert.equal(await askAI([{ role: "user", content: "Test" }]), "Gemini reply");
    assert.equal(calls, 1);
  } finally { global.fetch = originalFetch; }
});

test("temporary Gemini quota failure falls back to configured free Groq", async () => {
  const originalFetch = global.fetch;
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push(String(url));
    if (String(url).includes("generativelanguage.googleapis.com")) {
      return new Response(JSON.stringify({ error: { message: "RESOURCE_EXHAUSTED" } }), { status: 429, headers: { "retry-after": "30" } });
    }
    assert.match(String(url), /api\.groq\.com\/openai\/v1\/chat\/completions/);
    assert.equal(options.headers.Authorization, "Bearer test-groq-key");
    return new Response(JSON.stringify({ choices: [{ message: { content: "Groq fallback reply" } }] }), { status: 200 });
  };
  try {
    assert.equal(await askAI([{ role: "user", content: "Test" }]), "Groq fallback reply");
    assert.equal(calls.length, 2);
  } finally { global.fetch = originalFetch; }
});

test("authentication/configuration errors do not trigger pointless provider failover", async () => {
  const originalFetch = global.fetch;
  let calls = 0;
  global.fetch = async () => { calls++; return new Response(JSON.stringify({ error: { message: "bad key" } }), { status: 401 }); };
  try {
    await assert.rejects(askAI([{ role: "user", content: "Test" }]), error => error.status === 401);
    assert.equal(calls, 1);
  } finally { global.fetch = originalFetch; }
});

test("paid providers are never selectable, even if legacy paid flags are enabled", () => {
  assert.deepEqual(configuredProviders().map(p => p.id), ["gemini", "groq"]);\n  assert.equal(configuredProviders().some(p => p.freeTier === false), false);
  assert.equal(canFallback({ status: 429 }), true);
  assert.equal(canFallback({ status: 401 }), false);
});
