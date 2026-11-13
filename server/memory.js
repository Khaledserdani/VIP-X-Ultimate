const { Pool } = require("pg");
const pool = process.env.DATABASE_URL ? new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.PGSSLMODE === "disable" ? false : { rejectUnauthorized: false },
  max: 5, idleTimeoutMillis: 30000, connectionTimeoutMillis: 10000
}) : null;
let schemaPromise;
async function ensureSchema() {
  if (!pool) throw new Error("قاعدة الذاكرة الدائمة غير مهيأة");
  if (!schemaPromise) schemaPromise = pool.query(`
    CREATE TABLE IF NOT EXISTS agent_memory_versions (
      id BIGSERIAL PRIMARY KEY, agent_id TEXT NOT NULL, content TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'candidate' CHECK(status IN ('candidate','verified','superseded')),
      metadata JSONB NOT NULL DEFAULT '{}'::jsonb, verification JSONB,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), verified_at TIMESTAMPTZ
    );
    CREATE INDEX IF NOT EXISTS agent_memory_versions_lookup ON agent_memory_versions(agent_id,created_at DESC);
    CREATE TABLE IF NOT EXISTS agent_memory_heads (
      agent_id TEXT PRIMARY KEY, last_verified_version BIGINT REFERENCES agent_memory_versions(id),
      latest_candidate_version BIGINT REFERENCES agent_memory_versions(id), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `).catch(e=>{schemaPromise=null;throw e;});
  await schemaPromise;
}
async function getAgentMemory(agentId) {
  await ensureSchema();
  const q=await pool.query(`
    SELECT h.last_verified_version,h.latest_candidate_version,
    v.content verified_content,v.created_at verified_at,c.content candidate_content,c.created_at candidate_at
    FROM agent_memory_heads h
    LEFT JOIN agent_memory_versions v ON v.id=h.last_verified_version
    LEFT JOIN agent_memory_versions c ON c.id=h.latest_candidate_version WHERE h.agent_id=$1
  `,[agentId]);
  const r=q.rows[0];
  return {agentId,lastVerified:r?.last_verified_version?{versionId:Number(r.last_verified_version),content:r.verified_content,at:r.verified_at}:null,
    latestCandidate:r?.latest_candidate_version?{versionId:Number(r.latest_candidate_version),content:r.candidate_content,at:r.candidate_at}:null};
}
async function saveCandidate(agentId,content,metadata={}) {
  await ensureSchema(); const c=await pool.connect();
  try { await c.query("BEGIN");
    const s=await c.query("INSERT INTO agent_memory_versions(agent_id,content,status,metadata) VALUES($1,$2,'candidate',$3) RETURNING id",[agentId,content,JSON.stringify(metadata)]);
    await c.query(`INSERT INTO agent_memory_heads(agent_id,latest_candidate_version) VALUES($1,$2)
      ON CONFLICT(agent_id) DO UPDATE SET latest_candidate_version=EXCLUDED.latest_candidate_version,updated_at=NOW()`,[agentId,s.rows[0].id]);
    await c.query("COMMIT"); return {versionId:Number(s.rows[0].id),status:"candidate"};
  } catch(e){await c.query("ROLLBACK");throw e;} finally{c.release();}
}
async function promoteVersion(agentId,versionId,verification) {
  await ensureSchema(); if(!Number.isSafeInteger(versionId)||versionId<=0) throw new Error("رقم الإصدار غير صالح");
  const c=await pool.connect();
  try { await c.query("BEGIN");
    const f=await c.query("SELECT id FROM agent_memory_versions WHERE id=$1 AND agent_id=$2 AND status='candidate' FOR UPDATE",[versionId,agentId]);
    if(!f.rowCount) throw new Error("الإصدار غير موجود أو ليس مرشحاً");
    await c.query("UPDATE agent_memory_versions SET status='superseded' WHERE agent_id=$1 AND status='verified'",[agentId]);
    await c.query("UPDATE agent_memory_versions SET status='verified',verification=$2,verified_at=NOW() WHERE id=$1",[versionId,JSON.stringify(verification)]);
    await c.query(`INSERT INTO agent_memory_heads(agent_id,last_verified_version,latest_candidate_version) VALUES($1,$2,NULL)
      ON CONFLICT(agent_id) DO UPDATE SET last_verified_version=EXCLUDED.last_verified_version,updated_at=NOW()`,[agentId,versionId]);
    await c.query("COMMIT"); return {agentId,lastVerifiedVersion:versionId,status:"verified"};
  } catch(e){await c.query("ROLLBACK");throw e;} finally{c.release();}
}
async function rollbackToLastVerified(agentId) {
  await ensureSchema(); const c=await pool.connect();
  try { await c.query("BEGIN");
    const h=await c.query("SELECT last_verified_version FROM agent_memory_heads WHERE agent_id=$1 FOR UPDATE",[agentId]);
    if(!h.rowCount||!h.rows[0].last_verified_version){await c.query("ROLLBACK");return {agentId,restored:false,reason:"لا توجد نتيجة مثبتة سابقة"};}
    const id=Number(h.rows[0].last_verified_version);
    await c.query("UPDATE agent_memory_heads SET latest_candidate_version=NULL,updated_at=NOW() WHERE agent_id=$1",[agentId]);
    await c.query("COMMIT"); return {agentId,restored:true,lastVerifiedVersion:id};
  } catch(e){await c.query("ROLLBACK");throw e;} finally{c.release();}
}
module.exports={getAgentMemory,saveCandidate,promoteVersion,rollbackToLastVerified};
