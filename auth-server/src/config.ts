import "dotenv/config";
import crypto from "node:crypto";

export type Config = ReturnType<typeof loadConfig>;

function decodeEncryptionKey(value:string):Buffer {
  const normalized=value.trim();
  if(!/^[A-Za-z0-9+/]+={0,2}$/.test(normalized))throw new Error("TOKEN_ENCRYPTION_KEY_BASE64 must be valid base64");
  const key=Buffer.from(normalized,"base64");
  const canonical=key.toString("base64").replace(/=+$/,"");
  if(key.length!==32||canonical!==normalized.replace(/=+$/,""))throw new Error("TOKEN_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes");
  return key;
}

function normalizePublicBaseUrl(value:string,production:boolean):string {
  let url:URL;
  try{url=new URL(value);}catch{throw new Error("PUBLIC_BASE_URL must be a valid absolute URL");}
  const loopback=url.hostname==="localhost"||url.hostname==="127.0.0.1"||url.hostname==="::1"||url.hostname==="0.0.0.0";
  if(production&&url.protocol!=="https:")throw new Error("Production PUBLIC_BASE_URL must use HTTPS");
  if(production&&loopback)throw new Error("Production PUBLIC_BASE_URL must not use localhost or a loopback address");
  if(url.username||url.password||url.search||url.hash||url.pathname!=="/")throw new Error("PUBLIC_BASE_URL must contain only scheme, host, and optional port");
  return url.origin;
}

function positiveInteger(value:string|undefined,fallback:number,name:string):number {const parsed=Number(value??fallback);if(!Number.isInteger(parsed)||parsed<=0)throw new Error(`${name} must be a positive integer`);return parsed;}

function pairingTtl(value:string|undefined,production:boolean):number {
  const parsed=Number(value??600);
  if(!Number.isInteger(parsed)||(production&&parsed<=0))throw new Error("PAIRING_TTL_SECONDS must be a positive integer in production");
  return parsed;
}

export function loadConfig(env:NodeJS.ProcessEnv=process.env) {
  const production=env.NODE_ENV==="production";
  if(production&&!env.PUBLIC_BASE_URL)throw new Error("PUBLIC_BASE_URL is required in production");
  if(production&&!env.GOOGLE_CLIENT_ID)throw new Error("GOOGLE_CLIENT_ID is required in production");
  if(production&&!env.GOOGLE_CLIENT_SECRET)throw new Error("GOOGLE_CLIENT_SECRET is required in production");
  const publicBaseUrl=normalizePublicBaseUrl(env.PUBLIC_BASE_URL??"http://127.0.0.1:8787",production);
  let key:Buffer;
  if(env.TOKEN_ENCRYPTION_KEY_BASE64)key=decodeEncryptionKey(env.TOKEN_ENCRYPTION_KEY_BASE64);
  else if(production)throw new Error("TOKEN_ENCRYPTION_KEY_BASE64 is required in production");
  else key=crypto.createHash("sha256").update("development-only-ephemeral-key").digest();
  const port=positiveInteger(env.PORT,8787,"PORT");if(port>65535)throw new Error("PORT must be at most 65535");
  const callback=`${publicBaseUrl}/oauth/google/callback`;
  return {
    production,port,publicBaseUrl,
    googleClientId:env.GOOGLE_CLIENT_ID??"",googleClientSecret:env.GOOGLE_CLIENT_SECRET??"",
    googleRedirectUri:production?callback:(env.GOOGLE_REDIRECT_URI??callback),key,
    databasePath:env.DATABASE_PATH??(production?"/data/photo-backup.sqlite":"./data/photo-backup.sqlite"),
    pairingTtlSeconds:pairingTtl(env.PAIRING_TTL_SECONDS,production),
    pollSeconds:positiveInteger(env.PAIRING_POLL_INTERVAL_SECONDS,5,"PAIRING_POLL_INTERVAL_SECONDS"),
    deviceTtlDays:positiveInteger(env.DEVICE_TOKEN_TTL_DAYS,365,"DEVICE_TOKEN_TTL_DAYS"),
    trustProxy:env.TRUST_PROXY==="true"||production?1:false
  };
}
