import fs from "node:fs";
import path from "node:path";
import pino from "pino";
import {loadConfig} from "./config.js";
import {createApp} from "./app.js";
import {RealGoogleProvider} from "./google.js";
import {Store} from "./store.js";

const config=loadConfig();
fs.mkdirSync(path.dirname(config.databasePath),{recursive:true});
const log=pino({redact:["req.headers.authorization","*.accessToken","*.refreshToken","*.deviceCredential","*.googleClientSecret","*.key"]});
const store=new Store(config.databasePath);
const server=createApp(config,store,new RealGoogleProvider(config)).listen(config.port,"0.0.0.0",()=>log.info({port:config.port,host:"0.0.0.0"},"Photo Backup auth server listening"));
let shuttingDown=false;
function shutdown(signal:string){if(shuttingDown)return;shuttingDown=true;log.info({signal},"Photo Backup auth server shutting down");server.close((error)=>{try{store.close();}finally{if(error){log.error({error},"HTTP server shutdown failed");process.exitCode=1;}process.exit();}});setTimeout(()=>{log.error("Graceful shutdown timed out");process.exit(1);},10_000).unref();}
process.on("SIGTERM",()=>shutdown("SIGTERM"));
process.on("SIGINT",()=>shutdown("SIGINT"));
