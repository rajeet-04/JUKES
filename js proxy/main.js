const http = require('http');

const server = http.createServer(async (req, res) => {
  if (req.method !== 'GET') {
    res.writeHead(405, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Method not allowed' }));
    return;
  }

  const url = new URL(req.url, `http://${req.headers.host}`);
  const artist = url.searchParams.get('artist');
  const song = url.searchParams.get('song');
  const album = url.searchParams.get('album');

  if (!artist || !song) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Missing artist or song parameter' }));
    return;
  }

  const params = new URLSearchParams({
    track_name: song,
    artist_name: artist,
  });

  if (album) {
    params.set('album_name', album);
  }

  const apiUrl = `https://lrclib.net/api/search?${params.toString()}`;

  try {
    const response = await fetch(apiUrl);
    const data = await response.json();

    res.writeHead(response.status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(data));
  } catch (error) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Failed to fetch from lrclib.net' }));
  }
});

server.listen(8000, () => {
  console.log('Server running on http://localhost:8000');
});