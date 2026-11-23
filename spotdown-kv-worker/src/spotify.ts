import type { Env } from './index';

const REFRESH_MARGIN_MS = 300_000; // app-side buffer is the same; never hand out a nearly-dead token
const KV_TTL_S = 3300;             // Spotify tokens live 3600s

/** A client-credentials Spotify token, cached in KV; null when Spotify refuses. */
export async function spotifyToken(env: Env): Promise<{ token: string; exp: number } | null> {
    let hit = await env.KV.get<{ token: string; exp: number }>('spotify', 'json');
    if (!hit || hit.exp - Date.now() < REFRESH_MARGIN_MS) {
        const res = await fetch('https://accounts.spotify.com/api/token', {
            method: 'POST',
            headers: {
                Authorization: 'Basic ' + btoa(`${env.SPOTIFY_CLIENT_ID}:${env.SPOTIFY_CLIENT_SECRET}`),
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: 'grant_type=client_credentials',
        });
        if (!res.ok) return null;
        const { access_token, expires_in } = await res.json<{ access_token: string; expires_in: number }>();
        hit = { token: access_token, exp: Date.now() + expires_in * 1000 };
        await env.KV.put('spotify', JSON.stringify(hit), { expirationTtl: KV_TTL_S });
    }
    return hit;
}
