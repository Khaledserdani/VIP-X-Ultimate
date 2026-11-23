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
  assert.equal(calculate("-2^2").result, -4);
  assert.equal(calculate("(-2)^2").result, 4);
  assert.equal(calculate("2^-2").result, 0.25);
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
test("URL validator blocks private, loopback, and mapped IPv6 targets", async () => {
  await assert.rejects(() => safeUrl("https://[::1]"));
  await assert.rejects(() => safeUrl("https://[fc00::1]"));
  await assert.rejects(() => safeUrl("https://[fe80::1]"));
  await assert.rejects(() => safeUrl("https://[::ffff:127.0.0.1]"));
  await assert.rejects(() => safeUrl("https://[::ffff:7f00:1]"));
  await assert.rejects(() => safeUrl("https://[2001:db8::1]"));
});
test("URL validator rejects documentation, benchmarking, and reserved IPv4 ranges", async () => {
  for (const ip of ["192.0.2.1", "198.18.0.1", "198.51.100.7", "203.0.113.9", "192.88.99.1"]) {
    await assert.rejects(() => safeUrl("https://" + ip));
  }
});
test("web reader retrieves a public HTTPS page", async () => {
  const page = await fetchText("https://example.com");
  assert.match(page.text, /Example Domain/i);
}, { timeout: 25000 });
test("web search parses public results without depending on a live search provider", async () => {
  const originalFetch = global.fetch;
  global.fetch = async (url) => {
    assert.match(String(url), /^https:\/\/html\.duckduckgo\.com\/html\//);
    return new Response(
      '<a class="result__a" href="https://example.com/article">Example result</a><div class="result__snippet">A deterministic test snippet</div>',
      { status: 200, headers: { "content-type": "text/html" } }
    );
  };
  try {
    const result = await webSearch("OpenAI");
    assert.equal(result.query, "OpenAI");
    assert.equal(result.results.length, 1);
    assert.equal(result.results[0].title, "Example result");
    assert.equal(result.results[0].url, "https://example.com/article");
  } finally {
    global.fetch = originalFetch;
  }
});
