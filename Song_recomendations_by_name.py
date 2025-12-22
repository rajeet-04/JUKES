import requests
import json
import difflib

# ==================================================================================
# PART 1: FIND VIDEO ID (From Song Name)
# ==================================================================================
def get_best_video_match(user_query):
    print(f"\n--- Searching for: '{user_query}' ---")
    url = "https://mp3juice3.ninja/api/yt-data"
    
    headers = {
        'Content-Type': 'application/json',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    
    payload = {"query": user_query}

    try:
        response = requests.post(url, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()

        items = data.get("items", [])
        
        if not items:
            print("No results found.")
            return None

        best_match_item = None
        highest_score = 0.0

        for item in items:
            title = item.get("title", "")
            
            # Similarity check
            similarity_score = difflib.SequenceMatcher(None, user_query.lower(), title.lower()).ratio()
            
            if similarity_score > highest_score:
                highest_score = similarity_score
                best_match_item = item

        if best_match_item:
            print(f"Best Match:     {best_match_item['title']}")
            print(f"Match Score:    {highest_score:.2f}")
            return best_match_item['id']
        else:
            return None

    except requests.exceptions.RequestException as e:
        print(f"Search Network error: {e}")
        return None
    except json.JSONDecodeError:
        print("Failed to decode search response.")
        return None

# ==================================================================================
# PART 2: GET RECOMMENDATIONS (From Video ID)
# ==================================================================================
def fetch_full_radio_queue(target_video_id):
    if not target_video_id:
        print("Error: No Video ID provided.")
        return

    url = "https://music.youtube.com/youtubei/v1/next?prettyPrint=true"
    
    # Context object required by YT Music API
    client_context = {
        "client": {
            "hl": "en-IN",
            "gl": "IN",
            "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36,gzip(gfe)",
            "clientName": "WEB_REMIX",
            "clientVersion": "1.20251203.02.00",
        }
    }

    headers = {
        "Content-Type": "application/json",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }

    # --- Step 1: Get Playlist ID (Radio ID) ---
    print(f"\n[Step 1] Requesting Radio context for Video ID: {target_video_id}...")
    
    payload_step_1 = {
        "videoId": target_video_id,
        "context": client_context
    }

    radio_playlist_id = None

    try:
        response_1 = requests.post(url, json=payload_step_1, headers=headers)
        response_1.raise_for_status()
        data_1 = response_1.json()

        try:
            tabs = data_1['contents']['singleColumnMusicWatchNextResultsRenderer']['tabbedRenderer']['watchNextTabbedResultsRenderer']['tabs']
            queue_content = tabs[0]['tabRenderer']['content']['musicQueueRenderer']['content']['playlistPanelRenderer']['contents']
            
            # Target the first song to get the radio button endpoint
            first_song_renderer = queue_content[0]['playlistPanelVideoRenderer']
            menu_items = first_song_renderer['menu']['menuRenderer']['items']
            radio_playlist_id = menu_items[0]['menuNavigationItemRenderer']['navigationEndpoint']['watchEndpoint']['playlistId']
            
            print(f"[Step 1] Success! Playlist ID: {radio_playlist_id}")

        except (KeyError, IndexError, TypeError):
            print("Error: Could not extract Radio Playlist ID. The song might not have a direct radio option available.")
            return

    except requests.exceptions.RequestException as e:
        print(f"Network Error in Step 1: {e}")
        return

    # --- Step 2: Get Full Track List ---
    print(f"[Step 2] Fetching recommendations...")

    payload_step_2 = {
        "tunerSettingValue": "AUTOMIX_SETTING_NORMAL",
        "videoId": target_video_id,
        "playlistId": radio_playlist_id,
        "isAudioOnly": True,
        "context": client_context
    }

    try:
        response_2 = requests.post(url, json=payload_step_2, headers=headers)
        response_2.raise_for_status()
        data_2 = response_2.json()

        try:
            tabs = data_2['contents']['singleColumnMusicWatchNextResultsRenderer']['tabbedRenderer']['watchNextTabbedResultsRenderer']['tabs']
            full_queue = tabs[0]['tabRenderer']['content']['musicQueueRenderer']['content']['playlistPanelRenderer']['contents']
            
            print(f"\n{'IDX':<4} | {'VIDEO ID':<15} | {'TITLE':<30} | {'ARTIST':<20}")
            print("-" * 80)

            for index, item in enumerate(full_queue):
                if 'playlistPanelVideoRenderer' in item:
                    node = item['playlistPanelVideoRenderer']
                    
                    v_id = node.get('videoId', 'N/A')
                    
                    title = "Unknown"
                    if 'title' in node and 'runs' in node['title']:
                        title = node['title']['runs'][0]['text']
                        
                    artist = "Unknown"
                    if 'longBylineText' in node and 'runs' in node['longBylineText']:
                        artist = node['longBylineText']['runs'][0]['text']

                    print(f"{index:<4} | {v_id:<15} | {title[:28]:<30} | {artist[:18]:<20}")
                
                elif 'continuationItemRenderer' in item:
                    print(f"{index:<4} | {'---':<15} | {'[End of loaded batch]':<30} | {'---':<20}")

        except KeyError as e:
            print(f"Error parsing recommendations: {e}")

    except requests.exceptions.RequestException as e:
        print(f"Network Error in Step 2: {e}")

# ==================================================================================
# MAIN EXECUTION
# ==================================================================================
if __name__ == "__main__":
    song_query = input("Enter a song name to get recommendations: ")
    
    if song_query.strip():
        # 1. Get ID
        video_id = get_best_video_match(song_query)
        
        # 2. Get Recommendations
        if video_id:
            fetch_full_radio_queue(video_id)
        else:
            print("Could not find the song to generate recommendations.")
    else:
        print("Please enter a valid song name.")