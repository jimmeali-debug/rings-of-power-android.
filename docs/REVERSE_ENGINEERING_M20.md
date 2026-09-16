# Reverse-engineering milestone 20: Android document-tree pack importer

The native audio manager can now activate remaster packs selected through
Android's Storage Access Framework without requesting broad storage access.

## Document-tree source

`SafAudioAssetSource` resolves the pack's safe relative paths by querying child
documents under the selected tree URI. It rejects traversal, non-directory
intermediate components, ambiguous duplicate names, missing documents, null
provider cursors, and null input streams. Resolved document URIs are cached
without translating provider paths into filesystem paths.

## Atomic activation

`SafAudioOverrideController.activate`:

1. persists read permission for the selected tree;
2. reads `audio-overrides.json` with a 1 MiB limit;
3. parses the strict version-1 manifest;
4. verifies every encoded asset through the document provider;
5. atomically activates the fully verified pack;
6. releases the prior tree permission only after replacement succeeds.

If any step fails, a newly selected tree permission is released and the
previously active pack remains unchanged. Retrying the currently active tree
also preserves its existing permission on failure. Deactivation returns
playback to original assets and releases the active permission.

The importer performs blocking document and hashing work and must be called
from an Android background dispatcher or executor.

## Build verification

`native-remaster/test-android-storage.sh` runs a deterministic fake document
provider covering permission and rollback behavior.
`native-remaster/compile-android-storage.sh` then compiles the portable runtime
and production SAF adapter against Android API 35. The native-remaster GitHub
Actions workflow runs both tests and the real Android SDK compilation.
