import crypto from "node:crypto";
import {describe,expect,it} from "vitest";
import {loadConfig} from "./config.js";

const key=crypto.randomBytes(32).toString("base64");
const production=(extra:NodeJS.ProcessEnv={})=>({NODE_ENV:"production",PUBLIC_BASE_URL:"https://photo-backup.up.railway.app",GOOGLE_CLIENT_ID:"client-id",GOOGLE_CLIENT_SECRET:"client-secret",TOKEN_ENCRYPTION_KEY_BASE64:key,...extra});

describe("deployment configuration",()=>{
 it("rejects a missing production PUBLIC_BASE_URL",()=>expect(()=>loadConfig(production({PUBLIC_BASE_URL:undefined}))).toThrow(/PUBLIC_BASE_URL is required/));
 it("rejects HTTP and loopback production URLs",()=>{expect(()=>loadConfig(production({PUBLIC_BASE_URL:"http://photo-backup.up.railway.app"}))).toThrow(/HTTPS/);expect(()=>loadConfig(production({PUBLIC_BASE_URL:"https://localhost"}))).toThrow(/loopback/);});
 it("permits and normalizes a local URL outside production",()=>expect(loadConfig({NODE_ENV:"development",PUBLIC_BASE_URL:"http://127.0.0.1:8787/"}).publicBaseUrl).toBe("http://127.0.0.1:8787"));
 it("derives the production callback from PUBLIC_BASE_URL",()=>{const config=loadConfig(production({PUBLIC_BASE_URL:"https://photo-backup.up.railway.app/",GOOGLE_REDIRECT_URI:"https://malicious.invalid/callback"}));expect(config.googleRedirectUri).toBe("https://photo-backup.up.railway.app/oauth/google/callback");});
 it("defaults production storage to the Railway volume path and supports overrides",()=>{expect(loadConfig(production()).databasePath).toBe("/data/photo-backup.sqlite");expect(loadConfig(production({DATABASE_PATH:"/data/custom.sqlite"})).databasePath).toBe("/data/custom.sqlite");});
 it("rejects malformed encryption keys",()=>{for(const value of ["not base64!",Buffer.alloc(16).toString("base64"),`${key}extra`])expect(()=>loadConfig(production({TOKEN_ENCRYPTION_KEY_BASE64:value}))).toThrow(/TOKEN_ENCRYPTION_KEY_BASE64/);});
 it("uses Railway PORT and one trusted proxy hop",()=>{const config=loadConfig(production({PORT:"4567"}));expect(config.port).toBe(4567);expect(config.trustProxy).toBe(1);});
});
