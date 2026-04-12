# Spotify API Setup Guide

JUKE now uses the **official Spotify Web API** for searching songs and retrieving metadata. Follow these steps to configure your Spotify credentials.

## Prerequisites

- Spotify account (free or premium)

## Step 1: Create a Spotify App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Log in with your Spotify account
3. Click **"Create app"**
4. Fill in the app details:
   - **App name**: `JUKE Music Player` (or any name you prefer)
   - **App description**: `Android music streaming app`
   - **Redirect URIs**: Leave empty (not needed for Client Credentials flow)
   - **Which API/SDKs are you planning to use?**: Check **Web API**
5. Accept the Terms of Service
6. Click **"Save"**

## Step 2: Get Your Credentials

1. On your app's dashboard page, click **"Settings"**
2. You'll see:
   - **Client ID**: Copy this value
   - **Client Secret**: Click **"View client secret"** and copy this value

⚠️ **IMPORTANT**: Never share your Client Secret publicly or commit it to version control!

## Step 3: Add Credentials to local.properties

1. Open `local.properties` in the project root directory
2. Add these lines (replace with your actual credentials):

```properties
SPOTIFY_CLIENT_ID=your_actual_client_id_here
SPOTIFY_CLIENT_SECRET=your_actual_client_secret_here
```

Example:
```properties
sdk.dir=R\:\\Android_Studio_SDK
SPOTIFY_CLIENT_ID=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
SPOTIFY_CLIENT_SECRET=z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4
```

## Step 4: Build and Run

1. Sync your Gradle files
2. Build the app:
   ```bash
   .\gradlew assembleDebug
   ```
3. The app will now use the official Spotify API!

## How It Works

### OAuth Authentication
- The app uses **Client Credentials Flow** to authenticate with Spotify
- Access tokens are automatically obtained and refreshed
- Tokens are cached and reused until they expire (typically 1 hour)

### Search API
- Search endpoint: `https://api.spotify.com/v1/search`
- Market: `US` (configured for US market recommendations)
- Returns up to 20 tracks per search
- Includes high-quality album artwork (640x640, 300x300, 64x64)

### Download Flow
1. **Search**: Official Spotify API retrieves metadata
2. **Download**: Spotdown API downloads the actual MP3 file
3. **Lyrics**: LRCLib API fetches synced/plain lyrics
4. **Playback**: Media3 ExoPlayer handles local playback

## API Response Structure

The official Spotify API returns:
- Track name and ID
- Artist names
- Album information with artwork URLs
- Duration in milliseconds
- Popularity score
- Explicit content flag
- Preview URL (30-second snippet)
- Spotify URI and external URLs

## Troubleshooting

### "Spotify credentials not configured" error
- Make sure you added both `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` to `local.properties`
- Rebuild the app after adding credentials

### "Failed to authenticate with Spotify" error
- Verify your credentials are correct (no extra spaces)
- Check your internet connection
- Ensure your Spotify app is active in the Developer Dashboard

### No search results
- The app searches in the US market (`market=US`)
- Some tracks may not be available in the US market
- Try different search terms or artist names

## Rate Limits

Spotify API rate limits (Client Credentials):
- **Normal**: Up to 180 requests per minute
- **Extended**: Higher limits available (request from Spotify)

JUKE caches tokens and reuses them, so authentication doesn't count against your search quota.

## Security Best Practices

✅ **DO**:
- Keep `local.properties` in `.gitignore` (already configured)
- Rotate your Client Secret periodically
- Use environment-specific credentials for production

❌ **DON'T**:
- Commit `local.properties` to version control
- Share your Client Secret publicly
- Use the same credentials for production apps (create separate apps)

## Alternative: Environment Variables

If you prefer environment variables over `local.properties`, you can modify `app/build.gradle.kts`:

```kotlin
buildConfigField(
    "String", 
    "SPOTIFY_CLIENT_ID", 
    "\"${System.getenv("SPOTIFY_CLIENT_ID") ?: ""}\""
)
buildConfigField(
    "String", 
    "SPOTIFY_CLIENT_SECRET", 
    "\"${System.getenv("SPOTIFY_CLIENT_SECRET") ?: ""}\""
)
```

Then set the environment variables before building:
```bash
$env:SPOTIFY_CLIENT_ID="your_client_id"
$env:SPOTIFY_CLIENT_SECRET="your_client_secret"
.\gradlew assembleDebug
```

## Additional Resources

- [Spotify Web API Documentation](https://developer.spotify.com/documentation/web-api)
- [Client Credentials Flow Guide](https://developer.spotify.com/documentation/web-api/tutorials/client-credentials-flow)
- [Spotify API Console (for testing)](https://developer.spotify.com/console)
