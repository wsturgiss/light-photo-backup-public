import Database from "better-sqlite3";

export type Session = { id:string; code_hash:string; status:string; expires_at:number; attempts:number; state_hash:string|null; verifier:string|null; device_id:string|null; credential_once:string|null };
export type OAuthStart = { token_hash:string; session_id:string; oauth_state:string; expires_at:number; consumed_at:number|null };
export class Store {
  readonly db: Database.Database;
  constructor(path: string) {
    this.db = new Database(path === ":memory:" ? path : path);
    this.db.pragma("foreign_keys = ON");
    this.db.exec(`CREATE TABLE IF NOT EXISTS pairing_sessions(id TEXT PRIMARY KEY, code_hash TEXT UNIQUE NOT NULL, status TEXT NOT NULL, expires_at INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, state_hash TEXT, verifier TEXT, device_id TEXT, credential_once TEXT);
      CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, credential_hash TEXT UNIQUE NOT NULL, encrypted_refresh_token TEXT NOT NULL, expires_at INTEGER NOT NULL, revoked_at INTEGER);
      CREATE TABLE IF NOT EXISTS oauth_start_tokens(token_hash TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES pairing_sessions(id) ON DELETE CASCADE, oauth_state TEXT NOT NULL, expires_at INTEGER NOT NULL, consumed_at INTEGER);
      CREATE INDEX IF NOT EXISTS idx_pairing_code ON pairing_sessions(code_hash);`);
    const startColumns=(this.db.pragma("table_info(oauth_start_tokens)") as Array<{name:string}>).map((column)=>column.name);
    if(!startColumns.includes("oauth_state"))this.db.exec("ALTER TABLE oauth_start_tokens ADD COLUMN oauth_state TEXT NOT NULL DEFAULT ''");
  }
  createSession(row: Session) { this.db.prepare("INSERT INTO pairing_sessions VALUES(@id,@code_hash,@status,@expires_at,@attempts,@state_hash,@verifier,@device_id,@credential_once)").run(row); }
  session(id: string) { return this.db.prepare("SELECT * FROM pairing_sessions WHERE id=?").get(id) as Session|undefined; }
  byCode(hash: string) { return this.db.prepare("SELECT * FROM pairing_sessions WHERE code_hash=?").get(hash) as Session|undefined; }
  setOAuth(id:string,stateHash:string,verifier:string) { this.db.prepare("UPDATE pairing_sessions SET state_hash=?,verifier=? WHERE id=?").run(stateHash,verifier,id); }
  createOAuthStart(tokenHash:string,sessionId:string,oauthState:string,expiresAt:number) { this.db.prepare("INSERT INTO oauth_start_tokens(token_hash,session_id,oauth_state,expires_at,consumed_at) VALUES(?,?,?,?,NULL)").run(tokenHash,sessionId,oauthState,expiresAt); }
  consumeOAuthStart(tokenHash:string,now=Date.now()):{session:Session;oauthState:string}|undefined { const tx=this.db.transaction(()=>{const row=this.db.prepare("SELECT p.*,t.oauth_state AS oauth_start_state FROM oauth_start_tokens t JOIN pairing_sessions p ON p.id=t.session_id WHERE t.token_hash=? AND t.consumed_at IS NULL AND t.expires_at>? AND p.expires_at>? AND p.status='pending'").get(tokenHash,now,now) as (Session&{oauth_start_state:string})|undefined;if(!row)return undefined;const changed=this.db.prepare("UPDATE oauth_start_tokens SET consumed_at=? WHERE token_hash=? AND consumed_at IS NULL").run(now,tokenHash);if(changed.changes!==1)return undefined;const {oauth_start_state,...session}=row;return {session,oauthState:oauth_start_state};});return tx(); }
  oauthStart(tokenHash:string) { return this.db.prepare("SELECT * FROM oauth_start_tokens WHERE token_hash=?").get(tokenHash) as OAuthStart|undefined; }
  oauthStartCount() { return (this.db.prepare("SELECT COUNT(*) AS count FROM oauth_start_tokens").get() as {count:number}).count; }
  incrementAttempts(id:string) { this.db.prepare("UPDATE pairing_sessions SET attempts=attempts+1 WHERE id=?").run(id); }
  connect(id:string,deviceId:string,credential:string,credentialHash:string,refresh:string,expires:number) { const tx=this.db.transaction(()=>{this.db.prepare("INSERT INTO devices VALUES(?,?,?,?,NULL)").run(deviceId,credentialHash,refresh,expires);this.db.prepare("UPDATE pairing_sessions SET status='connected',device_id=?,credential_once=? WHERE id=?").run(deviceId,credential,id);});tx(); }
  consumeCredential(id:string) { const tx=this.db.transaction(()=>{const s=this.session(id);if(!s?.credential_once)return null;this.db.prepare("UPDATE pairing_sessions SET credential_once=NULL WHERE id=?").run(id);return s.credential_once;});return tx(); }
  device(hash:string) { return this.db.prepare("SELECT * FROM devices WHERE credential_hash=? AND revoked_at IS NULL AND expires_at>?").get(hash,Date.now()) as any; }
  disconnect(id:string) { const tx=this.db.transaction(()=>{this.db.prepare("DELETE FROM pairing_sessions WHERE device_id=?").run(id);this.db.prepare("DELETE FROM devices WHERE id=?").run(id);});tx(); }
  close(){this.db.close();}
}
