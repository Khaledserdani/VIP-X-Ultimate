const test = require("node:test");
const assert = require("node:assert/strict");
const { calculate } = require("./tools");

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
