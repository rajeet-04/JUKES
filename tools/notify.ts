#!/usr/bin/env bun
/**
 * Send a push announcement to JUKE installs through Firebase Cloud Messaging (see app/.../push/Push.kt).
 *
 *   bun tools/notify.ts "JUKE is on your Mac" "Get the desktop app" --link https://github.com/rajeet-04/JUKES/releases
 *   bun tools/notify.ts "Update ready" "2.8.1 fixes sync" --version 2.8.0-beta     # only installs on that version
 *   bun tools/notify.ts "Test" "hello" --dry-run                                   # print, don't send
 *
 * Needs a Firebase service-account key (Firebase console > Project settings > Service accounts > Generate new
 * private key). Keep it outside the repo: FCM_SERVICE_ACCOUNT=/path/key.json, default ~/.config/jukes/fcm-service-account.json.
 * Free: FCM has no charge or quota for topic messages.
 */
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';

const args = process.argv.slice(2);
const flag = (name: string) => { const i = args.indexOf(name); if (i < 0) return undefined; const v = args[i + 1]; args.splice(i, 2); return v; };
const dryRun = args.includes('--dry-run'); if (dryRun) args.splice(args.indexOf('--dry-run'), 1);
const link = flag('--link');
const version = flag('--version');
const topic = flag('--topic') ?? (version ? 'v' + version.replace(/[^A-Za-z0-9._~%-]/g, '-') : 'all');
const [title, body = ''] = args;
if (!title) {
    console.error('usage: bun tools/notify.ts "<title>" "<body>" [--link <url>] [--version <versionName> | --topic <topic>] [--dry-run]');
    process.exit(2);
}
if (link && !/^https?:\/\//.test(link)) { console.error('--link must be an http(s) URL'); process.exit(2); }

const message = { message: { topic, data: { title, body, ...(link ? { link } : {}) }, android: { priority: 'high', ttl: '604800s' } } };
if (dryRun) { console.log(JSON.stringify(message, null, 2)); process.exit(0); }

const keyPath = process.env.FCM_SERVICE_ACCOUNT ?? `${homedir()}/.config/jukes/fcm-service-account.json`;
let key: { project_id: string; client_email: string; private_key: string; token_uri?: string };
try { key = JSON.parse(readFileSync(keyPath, 'utf8')); } catch { console.error(`No service-account key at ${keyPath} (set FCM_SERVICE_ACCOUNT)`); process.exit(1); }

/** OAuth access token for FCM, from a JWT signed with the service account's key (RS256). */
async function accessToken(): Promise<string> {
    const b64url = (b: ArrayBuffer | string) => Buffer.from(b as any).toString('base64url');
    const now = Math.floor(Date.now() / 1000);
    const tokenUri = key.token_uri ?? 'https://oauth2.googleapis.com/token';
    const unsigned = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' })) + '.' + b64url(JSON.stringify({
        iss: key.client_email, scope: 'https://www.googleapis.com/auth/firebase.messaging', aud: tokenUri, iat: now, exp: now + 600,
    }));
    const der = Buffer.from(key.private_key.replace(/-----[^-]+-----|\s/g, ''), 'base64');
    const signer = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
    const jwt = unsigned + '.' + b64url(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', signer, new TextEncoder().encode(unsigned)));
    const res = await fetch(tokenUri, {
        method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt }),
    });
    const out = await res.json() as { access_token?: string; error_description?: string };
    if (!out.access_token) throw new Error(`token: ${res.status} ${out.error_description ?? JSON.stringify(out)}`);
    return out.access_token;
}

const res = await fetch(`https://fcm.googleapis.com/v1/projects/${key.project_id}/messages:send`, {
    method: 'POST', headers: { Authorization: `Bearer ${await accessToken()}`, 'Content-Type': 'application/json' }, body: JSON.stringify(message),
});
const text = await res.text();
if (!res.ok) { console.error(`FCM ${res.status}: ${text}`); process.exit(1); }
console.log(`Sent to topic "${topic}": ${JSON.parse(text).name}`);
