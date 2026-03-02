export interface Env {
    KV: KVNamespace;
}

export default {
    async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
        const url = new URL(request.url);

        if (url.pathname === '/api') {
            try {
                // read key "api" from the bound KV namespace
                const apiKey = await env.KV.get('api');

                return new Response(
                    JSON.stringify({ value: apiKey }),
                    {
                        headers: {
                            'Content-Type': 'application/json',
                            'Access-Control-Allow-Origin': '*'
                        }
                    }
                );
            } catch (err: any) {
                return new Response(JSON.stringify({ error: err.message }), { status: 500 });
            }
        }

        return new Response('Not Found', { status: 404 });
    }
};
