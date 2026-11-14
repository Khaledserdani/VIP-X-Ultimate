const test = require("node:test");
const assert = require("node:assert/strict");
const { calculate, safeUrl, fetchText, webSearch } = require("./tools");

test("calculator handles precedence and parentheses", () => {
  assert.equal(calculate("2 + 3 * 4").result, 14);
  assert.equal(calculate("(2 + 3) * 4").result, 20);
  assert.equal(calculate("2^3^2").result, 512);
});
test("calculator handles unary minus and division", () => {
  assert.equal(calculate("-8 / 2").result, -4);
  assert.equal(calculate("10 % 4").result, 2);
});
test("calculator rejects code and malformed expressions", () => {
  assert.throws(() => calculate("process.exit()"));
  assert.throws(() => calculate("1/0"));
  assert.throws(() => calculate("(1+2"));
});

test("URL validator blocks private and non-HTTPS targets", async () => {
  await assert.rejects(() => safeUrl("http://example.com"));
  await assert.rejects(() => safeUrl("https://127.0.0.1"));
  await assert.rejects(() => safeUrl("https://localhost"));
});
test("web reader retrieves a public HTTPS page", async () => {
  const page = await fetchText("https://example.com");
  assert.match(page.text, /Example Domain/i);
}, { timeout: 25000 });
test("web search returns public results", async () => {
  const result = await webSearch("OpenAI");
  assert.ok(Array.isArray(result.results));
  assert.ok(result.results.length > 0);
}, { timeout: 25000 });
