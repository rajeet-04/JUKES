# Search surface

## Scope

Native Android, Operate mode. Phone listeners enter a query, identify a song or collection, then play, queue, or open it. This is an ordinary extension of the incumbent theme, not an approved global visual-system change. Sources: `SearchScreen.kt`, `SearchComponents.kt`, and `ui/components/SearchResultItem.kt` under `app/src/main/java/com/example/juke/ui/`.

## Direction contract

**THESIS:** Make query entry and result selection a direct path to playback; replace multiple entry affordances and carousel browsing with one field and vertical results.

**OWN-WORLD:** Inherit neutral MaterialTheme surfaces, semantic foreground and accent roles, Figtree Material typography, JukeIcons, and native Material controls. Existing app navigation and mini-player remain the surrounding chrome.

**STORY:** Idle shows recent queries; typing shows suggestions and an explicit submit row; submitting shows category previews. See all or a filter opens the full category. Song rows play immediately or expose Play now / Play next.

**FIRST VIEWPORT:** A full-width search field below the status inset; submitted results place one horizontal All / Songs / Artists / Albums / Playlists row immediately below. Content scrolls vertically beneath it. IME Search is the primary submit action.

**FORM:** User-approved native list structure within the incumbent Operate world. No selection ranking or seed key was emitted; none is invented here.

**FINISH:** unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

For this scoped extension, the FINISH design-system obligation is preservation: no global DESIGN.md or `.impeccable/design.json` exists in the checkout, and neither is created. PRODUCT.md retains historical product context. Finish review is recorded separately; this brief does not imply a verdict.

## Shipped behavior

- Search field: single line, IME Search, clear button, and selection of all existing text when the Search tab focus trigger fires again. Clear retains focus. Input respects the status inset; the screen applies IME padding.
- Typing: explicit `Search for “query”` row followed by suggestions. Idle: recent queries with individual removal and Clear all, or the empty prompt.
- Every submission path resets the selected category to All: IME Search, explicit submit row, suggestion, and recent query.
- Submitted ordinary queries: one horizontally scrolling Material FilterChip row. Playlist URL import follows its existing separate path.
- All: deduplicated previews of up to two local tracks, three remote songs, and two artists, albums, and playlists each. See all appears when the corresponding raw result count exceeds the preview limit; it selects that category.
- Category: the complete deduplicated vertical list; Songs includes local and remote matches. Category or query changes reset the result list to its top. Upward scrolling dismisses the keyboard.
- Local and remote songs: tappable row plus overflow Play now / Play next; existing swipe-to-add-next paths remain. Remote downloading replaces overflow with progress and disables row playback.
- Artists, albums, and playlists: vertical Material ListItem rows with artwork and metadata; callbacks retain existing detail navigation. Import and queue operations remain delegated to existing ViewModels.

## Visual application

Use existing theme roles rather than introducing Search palette primitives. The search field uses `surfaceContainer`; secondary identity uses `onSurfaceVariant` or existing foreground opacity. Material default FilterChip state colors remain inherited, including the visible tinted selected fill.

Use the existing Figtree roles: section titleMedium, result/input/suggestion bodyLarge, result subtitle bodyMedium, local metadata bodySmall, and filter labelLarge. This brief introduces no type tokens or new global scale.

The field has 16dp horizontal and 8dp vertical outer padding, 16dp corners, and a transparent unfocused border. Filter content has 16dp horizontal padding with 8dp gaps. Section headers have a 48dp minimum height. Artwork is 48dp, with circular artists and 8dp rounded track/collection thumbnails. Lists reserve caller-provided bottom space plus 24dp for the existing player/navigation overlay.

**The Single Entry Rule:** Search has one visible query field; tab re-tap selects its existing query.

**The Preview to List Rule:** All remains bounded; filters and See all open complete vertical categories.

**The Inherited Theme Rule:** Search uses the app's Material roles, type scale, and native controls; its route strategy does not become a global layout rule.

## Evidence and unresolved drift

Source extraction includes `ui/theme/Theme.kt` and `ui/theme/Type.kt`. Parent reports `assembleDebug` and `testDebugUnitTest` passed. Physical-phone captures at 1080×2400: `../review/search-phone-{idle,suggestions,results,albums,light,large-text}.png`. Results and large-text captures were inspected for documentation; the latter uses font scale 1.3. Parent reports the phone restored to font scale 1.0 and dark mode.

Not canonized or repaired: PRODUCT.md's historical glass commitment differs from current neutral theme foundations; existing glass mini-player/navigation/import/error containers and selected-chip tint remain inherited implementation details, not a new Search-wide visual doctrine. Phone screenshots alone do not establish tablet behavior, complete accessibility compliance, playback success, or a finished review verdict.
