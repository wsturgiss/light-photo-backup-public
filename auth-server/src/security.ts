import crypto from "node:crypto";

export const randomToken = (bytes = 32) => crypto.randomBytes(bytes).toString("base64url");
export const hashSecret = (value: string) => crypto.createHash("sha256").update(value).digest("hex");
export const pairingCode = () => {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const raw = [...crypto.randomBytes(8)].map((v) => alphabet[v % alphabet.length]).join("");
  return `${raw.slice(0, 4)}-${raw.slice(4)}`;
};

export class TokenCipher {
  constructor(private readonly key: Buffer) {
    if (key.length !== 32) throw new Error("TOKEN_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes");
  }
  encrypt(value: string): string {
    const nonce = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv("aes-256-gcm", this.key, nonce);
    const ciphertext = Buffer.concat([cipher.update(value, "utf8"), cipher.final()]);
    return [nonce, cipher.getAuthTag(), ciphertext].map((b) => b.toString("base64url")).join(".");
  }
  decrypt(value: string): string {
    const parts = value.split(".");
    if (parts.length !== 3) throw new Error("Invalid encrypted token");
    const [nonce, tag, ciphertext] = parts.map((v) => Buffer.from(v!, "base64url"));
    const decipher = crypto.createDecipheriv("aes-256-gcm", this.key, nonce!);
    decipher.setAuthTag(tag!);
    return Buffer.concat([decipher.update(ciphertext!), decipher.final()]).toString("utf8");
  }
}

const sensitive = /(authorization|access[_-]?token|refresh[_-]?token|device[_-]?credential|client[_-]?secret|code)/i;
export function redact(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, sensitive.test(k) ? "[REDACTED]" : redact(v)]));
  return value;
}
