import {CodeChallengeMethod, OAuth2Client} from "google-auth-library";
import crypto from "node:crypto";
import type {Config} from "./config.js";

export const SCOPE = "https://www.googleapis.com/auth/photoslibrary.appendonly";
export interface GoogleProvider { authorizationUrl(state:string,challenge:string):string; exchange(code:string,verifier:string):Promise<string>; access(refresh:string):Promise<{accessToken:string;expiresAt:string;scope:string}>; revoke(refresh:string):Promise<void>; }
export class RealGoogleProvider implements GoogleProvider {
  private client: OAuth2Client;
  constructor(private config:Config){this.client=new OAuth2Client(config.googleClientId,config.googleClientSecret,config.googleRedirectUri);}
  private configured(){if(!this.config.googleClientId||!this.config.googleClientSecret)throw new Error("Google OAuth is not configured");}
  authorizationUrl(state:string,challenge:string){this.configured();return this.client.generateAuthUrl({access_type:"offline",scope:[SCOPE],include_granted_scopes:true,prompt:"consent",state,code_challenge:challenge,code_challenge_method:CodeChallengeMethod.S256});}
  async exchange(code:string,verifier:string){this.configured();const {tokens}=await this.client.getToken({code,codeVerifier:verifier});if(!tokens.refresh_token)throw new Error("Google did not return a refresh token");return tokens.refresh_token;}
  async access(refresh:string){this.client.setCredentials({refresh_token:refresh});const r=await this.client.getAccessToken();if(!r.token)throw new Error("Unable to obtain access token");return {accessToken:r.token,expiresAt:new Date(Date.now()+55*60_000).toISOString(),scope:SCOPE};}
  async revoke(refresh:string){try{await this.client.revokeToken(refresh);}catch{/* deletion remains authoritative */}}
}
export const pkceChallenge=(verifier:string)=>crypto.createHash("sha256").update(verifier).digest("base64url");
