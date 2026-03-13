export interface Env {
    KV: KVNamespace;
    APP_SECRET?: string;
}

const CORS_HEADERS: Record<string, string> = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, X-App-Secret',
    'Access-Control-Max-Age': '86400',
};

export default {
    async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
        const url = new URL(request.url);

        // Handle CORS preflight
        if (request.method === 'OPTIONS') {
            return new Response(null, { status: 204, headers: CORS_HEADERS });
        }

        if (url.pathname === '/api') {
            // Validate secret if configured
            if (env.APP_SECRET) {
                const provided = request.headers.get('X-App-Secret');
                if (provided !== env.APP_SECRET) {
                    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
                        status: 401,
                        headers: { 'Content-Type': 'application/json', ...CORS_HEADERS }
                    });
                }
            }

            try {
                // read key "api" from the bound KV namespace
                const apiKey = await env.KV.get('api');

                return new Response(
                    JSON.stringify({ value: apiKey }),
                    {
                        headers: {
                            'Content-Type': 'application/json',
                            ...CORS_HEADERS
                        }
                    }
                );
            } catch (err: any) {
                return new Response(JSON.stringify({ error: err.message }), {
                    status: 500,
                    headers: {
                        'Content-Type': 'application/json',
                        ...CORS_HEADERS
                    }
                });
            }
        }

        return new Response('Not Found', { status: 404 });
    }
};
