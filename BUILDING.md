# Build Android APK

The repository includes a GitHub Actions workflow at `.github/workflows/android-build.yml`.

A push to `main` starts a debug APK build. When the workflow succeeds, download the `Audacity-claude-debug-apk` artifact from the workflow run's Artifacts section.

Local build requirements:
- JDK 17
- Android SDK platform 34
- Android build-tools 34.0.0
- Gradle 8.7

Build command:

```sh
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
