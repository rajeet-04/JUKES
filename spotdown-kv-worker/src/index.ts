export interface Env {
    KV: KVNamespace;
    SPOTIFY_CLIENT_ID: string;
    SPOTIFY_CLIENT_SECRET: string;
    APP_SECRET: string;
}

const REFRESH_MARGIN_MS = 300_000; // app-side buffer is the same; never hand out a nearly-dead token
const KV_TTL_S = 3300;             // Spotify tokens live 3600s

// ponytail: concurrent cold requests may each refresh once; harmless, add a Durable Object if it ever matters
export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        if (new URL(request.url).pathname !== '/spotify-token') return new Response('Not Found', { status: 404 });
        if (request.headers.get('X-App-Secret') !== env.APP_SECRET) return Response.json({ error: 'Unauthorized' }, { status: 401 });

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
            if (!res.ok) return Response.json({ error: 'spotify ' + res.status }, { status: 502 });
            const { access_token, expires_in } = await res.json<{ access_token: string; expires_in: number }>();
            hit = { token: access_token, exp: Date.now() + expires_in * 1000 };
            await env.KV.put('spotify', JSON.stringify(hit), { expirationTtl: KV_TTL_S });
        }
        return Response.json({ access_token: hit.token, expires_in: Math.floor((hit.exp - Date.now()) / 1000) });
    },
};
