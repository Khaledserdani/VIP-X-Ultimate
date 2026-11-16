const test = require("node:test");
const assert = require("node:assert/strict");

process.env.AI_PROVIDER = "gemini";
process.env.GEMINI_API_KEY = "test-gemini-key";
process.env.GEMINI_MODEL = "gemini-3.8-flash";
const { askAI, normalizeGeminiMessages, providerConfigured, selectedProvider } = require("./provider");

test("Gemini provider is selected and configured from its own key", () => {
  assert.equal(selectedProvider(), "gemini");
  assert.equal(providerConfigured(), true);
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

test("Gemini response text is returned through the unchanged askAI interface", async () => {
  const originalFetch = global.fetch;
  global.fetch = async (url, options) => {
    assert.match(String(url), /gemini-3\.8-flash:generateContent/);
    assert.equal(options.headers["x-goog-api-key"], "test-gemini-key");
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "VIP X test reply" }] } }]
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    assert.equal(await askAI([{ role: "user", content: "Test" }]), "VIP X test reply");
  } finally {
    global.fetch = originalFetch;
  }
});

test("Gemini free-tier exhaustion is reported as a retryable quota limit", async () => {
  const originalFetch = global.fetch;
  global.fetch = async () => new Response(JSON.stringify({
    error: { message: "RESOURCE_EXHAUSTED" }
  }), { status: 429, headers: { "retry-after": "30" } });
  try {
    await assert.rejects(
      askAI([{ role: "user", content: "Test" }]),
      error => error.status === 429 && error.retryAfter === 30 && /الحد المجاني/.test(error.message)
    );
  } finally {
    global.fetch = originalFetch;
  }
});
