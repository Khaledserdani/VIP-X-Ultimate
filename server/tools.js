const dns = require("node:dns/promises");
const net = require("node:net");

const TOOL_REGISTRY = [
  { id: "web_search", name: "بحث ويب", description: "بحث عام مباشر على الويب" },
  { id: "web_fetch", name: "قراءة صفحة", description: "قراءة محتوى صفحة HTTPS عامة" },
  { id: "calculator", name: "آلة حاسبة", description: "حساب تعبير رياضي آمن" }
];

function publicIp(ip) {
  const raw = String(ip);
  const value = (raw.startsWith("[") ? raw.slice(1, -1) : raw).toLowerCase();
  if (net.isIP(value) === 4) {
    const p = value.split(".").map(Number);
    const a = p[0], b = p[1], c = p[2];
    const reserved =
      a === 0 || a === 10 || a === 127 || a >= 224 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && (b === 0 || b === 2 || b === 168)) ||
      (a === 192 && b === 88 && c === 99) ||
      (a === 198 && (b === 18 || b === 19 || (b === 51 && c === 100))) ||
      (a === 203 && b === 0 && c === 113);
    return !reserved;
  }
  if (net.isIP(value) !== 6 || value.includes("%")) return false;
  // IPv4-mapped IPv6 addresses must inherit the embedded IPv4 restrictions.
  if (value.startsWith("::ffff:")) {
    const tail = value.slice(7);
    if (net.isIP(tail) === 4) return publicIp(tail);
    const groups = tail.split(":");
    if (groups.length === 2 && groups.every(x => /^[0-9a-f]{1,4}$/.test(x))) {
      const n = (parseInt(groups[0], 16) * 65536 + parseInt(groups[1], 16)) >>> 0;
      return publicIp([(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255].join("."));
    }
    return false;
  }
  // Only global-unicast 2000::/3 addresses are eligible; exclude documentation and transition ranges.
  return /^[23]/.test(value) &&
    !value.startsWith("2001:db8:") &&
    !value.startsWith("2001:0:") &&
    !value.startsWith("2002:");
}
async function safeUrl(raw) {
  const u = new URL(String(raw));
  const host = u.hostname.startsWith("[") ? u.hostname.slice(1, -1) : u.hostname;
  if (u.protocol !== "https:" || u.username || u.password ||
      host === "localhost" || host.endsWith(".local")) {
    throw new Error("يسمح فقط بروابط HTTPS العامة");
  }
  if (net.isIP(host)) {
    if (!publicIp(host)) throw new Error("عنوان محلي أو خاص غير مسموح");
  } else {
    const ips = await dns.lookup(host, { all: true, verbatim: true });
    if (!ips.length || ips.some(x => !publicIp(x.address))) throw new Error("النطاق ليس عاماً وآمناً");
  }
  return u;
}
function textOf(html) {
  return html.replace(/<(script|style|noscript)[^>]*>[\s\S]*?<\/\1>/gi," ")
    .replace(/<(br|\/p|\/div|\/li|\/h[1-6])[^>]*>/gi,"\n")
    .replace(/<[^>]+>/g," ").replace(/&nbsp;/g," ").replace(/&amp;/g,"&")
    .replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/[ \t]+/g," ")
    .replace(/\n\s+/g,"\n").trim().slice(0,20000);
}
async function fetchText(raw) {
  let u=await safeUrl(raw);
  for(let n=0;n<4;n++){
    const r=await fetch(u,{redirect:"manual",headers:{"User-Agent":"VIPX/1.0","Accept":"text/html,text/plain,application/json"},signal:AbortSignal.timeout(15000)});
    if([301,302,303,307,308].includes(r.status)){const loc=r.headers.get("location");if(!loc)throw new Error("تحويل غير صالح");u=await safeUrl(new URL(loc,u).toString());continue;}
    if(!r.ok)throw new Error("HTTP "+r.status);
    const type=r.headers.get("content-type")||"";
    if(!/(text\/html|text\/plain|application\/json)/i.test(type))throw new Error("نوع المحتوى غير مدعوم");
    const html=(await r.text()).slice(0,1000000);
    return {url:u.toString(),text:type.includes("html")?textOf(html):html.slice(0,20000)};
  }
  throw new Error("عدد التحويلات كبير");
}
async function webSearch(query) {
  const q=String(query||"").trim().slice(0,250);if(!q)throw new Error("عبارة البحث فارغة");
  const u="https://html.duckduckgo.com/html/?q="+encodeURIComponent(q);
  const r=await fetch(u,{headers:{"User-Agent":"Mozilla/5.0 VIPX/1.0","Accept":"text/html"},signal:AbortSignal.timeout(15000)});
  if(!r.ok)throw new Error("فشل البحث HTTP "+r.status);
  const html=await r.text(), out=[];
  const re=/<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;let m;
  while((m=re.exec(html))&&out.length<8){
    let url=m[1].replace(/&amp;/g,"&");const hit=url.match(/[?&]uddg=([^&]+)/);if(hit)url=decodeURIComponent(hit[1]);
    if(!/^https:\/\//i.test(url))continue;
    const sn=html.slice(re.lastIndex,re.lastIndex+1200).match(/class="result__snippet"[^>]*>([\s\S]*?)<\/(?:a|td|div)>/i);
    out.push({title:textOf(m[2]),url,snippet:sn?textOf(sn[1]):""});
  }
  return {query:q,results:out};
}
function calculate(expr) {
  const s=String(expr||"").trim();if(!s||s.length>100||!/^[0-9+\-*/%^().\s]+$/.test(s))throw new Error("تعبير غير مسموح");
  const ts=s.match(/\d*\.?\d+|[()+\-*/%^]/g)||[];if(ts.join("").replace(/\./g,"")!==s.replace(/[\s.]/g,""))throw new Error("صيغة غير صحيحة");
  const out=[],ops=[],p={"u-":4,"^":3,"*":2,"/":2,"%":2,"+":1,"-":1};let need=true;
  for(const t of ts){
    if(/^\d/.test(t)){if(!need)throw new Error("عامل مفقود");out.push(Number(t));need=false;}
    else if(t==="("){if(!need)throw new Error("صيغة غير صحيحة");ops.push(t);need=true;}
    else if(t===")"){if(need)throw new Error("قوس غير صحيح");while(ops.length&&ops.at(-1)!=="(")out.push(ops.pop());if(ops.pop()!=="(")throw new Error("أقواس غير متوازنة");need=false;}
    else{const op=t==="-"&&need?"u-":t;if(need&&op!=="u-")throw new Error("عامل غير صحيح");while(ops.length&&ops.at(-1)!=="("&&(p[ops.at(-1)]>p[op]||(p[ops.at(-1)]===p[op]&&op!=="^"&&op!=="u-")))out.push(ops.pop());ops.push(op);need=true;}
  }
  if(need)throw new Error("تعبير ناقص");while(ops.length){const x=ops.pop();if(x==="(")throw new Error("أقواس غير متوازنة");out.push(x);}
  const st=[];for(const x of out){if(typeof x==="number")st.push(x);else if(x==="u-"){if(!st.length)throw new Error("صيغة غير صحيحة");st.push(-st.pop());}else{if(st.length<2)throw new Error("صيغة غير صحيحة");const b=st.pop(),a=st.pop();st.push(x==="+"?a+b:x==="-"?a-b:x==="*"?a*b:x==="/" ?a/b:x==="%"?a%b:Math.pow(a,b));}}
  if(st.length!==1||!Number.isFinite(st[0]))throw new Error("نتيجة غير صالحة");return {expression:s,result:st[0]};
}
async function executeToolCall(call) {
  const a=call.arguments||{};
  if(call.name==="web_search")return {tool:call.name,output:await webSearch(a.query)};
  if(call.name==="web_fetch")return {tool:call.name,output:await fetchText(a.url)};
  if(call.name==="calculator")return {tool:call.name,output:calculate(a.expression)};
  throw new Error("أداة غير مسموحة");
}
async function executeMissionTools(goal,askAI) {
  const prompt=await askAI([{role:"system",content:"أخرج JSON فقط ككائن يحوي مصفوفة calls. كل عنصر في المصفوفة يحوي name من web_search أو web_fetch أو calculator و arguments ككائن مناسب للأداة. اختر فقط الأدوات اللازمة وبحد أقصى 3. web_search يحتاج query، web_fetch يحتاج url HTTPS عام، calculator يحتاج expression. لا تنفذ معاملات أو إرسال رسائل أو حذفاً أو شراء. إذا لا تحتاج أداة أعد calls فارغة."},{role:"user",content:String(goal).slice(0,3000)}]);
  let plan;try{plan=JSON.parse(prompt.replace(/^\`\`\`(?:json)?\s*/i,"").replace(/\s*\`\`\`$/i,""));}catch{throw new Error("تعذر قراءة خطة الأدوات");}
  const results=[];for(const c of (Array.isArray(plan.calls)?plan.calls.slice(0,3):[])){try{results.push(await executeToolCall(c));}catch(e){results.push({tool:c.name,error:e.message});}}
  return {tools:TOOL_REGISTRY,results};
}
module.exports={TOOL_REGISTRY,executeMissionTools,executeToolCall,calculate,webSearch,fetchText,safeUrl};
