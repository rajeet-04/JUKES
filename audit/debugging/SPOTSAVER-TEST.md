# Spotsaver Kotlin live integration test — beta, 2026-10-04

Test: app/src/androidTest/java/com/example/juke/network/SpotsaverLiveTest.kt

Two fresh Ktor/OkHttp clients on the Android emulator, without cookie storage, browser state, JavaScript, credentials or Cloudflare tokens. JSON POST /api/get-id/ for Jasmine by Talha Anjum, Umair, followed by JSON POST /api/download/ with the returned video ID. Each generated downloadUrl was opened and only 4096 bytes were sampled; no audio was played or saved. Test asserts an MP3 signature and successful statuses; HTML challenges/non-JSON responses fail clearly. Reports exclude signed URLs and tokens.

| Run | Lookup | Download URL | Media first bytes | Total network stages |
| --- | ---: | ---: | ---: | ---: |
| 1 | 1172 ms | 989 ms | 1597 ms | 3758 ms |
| 2 | 945 ms | 1188 ms | 1600 ms | 3733 ms |

All stages HTTP 200. Media content type audio/mpeg. Both MP3 signatures valid. No recognized Cloudflare challenge found in API responses. These results demonstrate current access from this emulator/network; they cannot guarantee future access or all songs. Only initial audio bytes are verified, not whole-file integrity or seek/range support.

Opt-in run (after installing the debug app and Android test APK):

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class com.example.juke.network.SpotsaverLiveTest \
  -e spotsaverLive true \
  com.example.juke.test/androidx.test.runner.AndroidJUnitRunner
```

Build test APK with `bash ./gradlew :app:assembleDebugAndroidTest`. The live test is skipped unless spotsaverLive=true; normal device tests do not call the service. Export safe results with:

```sh
adb -s emulator-5554 exec-out run-as com.example.juke \
  cat files/spotsaver-live-results.json
```

Live result: OK (1 test containing two cold sessions), 7.545 s. Default device regression suite remains passing with the live test skipped. The original probe added no app playback provider integration. See SPOTSAVER-INTEGRATION.md for the subsequent integration and production-client verification.
