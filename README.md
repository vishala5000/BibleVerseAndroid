# Bible Verse Video Generator — Android

Native Kotlin Android app. The Python `app.py` and `requirements.txt` supplied by the user are not modified.

## Runtime file selection
The app asks for exactly two files at runtime:
- `en_US-ryan-high.onnx`
- `en_US-ryan-high.onnx.json`

The other generator files are read directly from `app/src/main/assets/`:
- `quotes.txt`
- `font.ttf`
- `bg.mp3`

Put your original `font.ttf` and `bg.mp3` in the assets folder before pushing to GitHub. Replace the sample `quotes.txt` with your real quotes file if needed.

## Build
Push the project to GitHub and run **Actions → Build APK**. The APK is uploaded as an Actions artifact.

The workflow packages a native Android Piper CLI and uses FFmpegKit for MP4 creation. The Piper Android binary is sourced from the Android/Termux build project; the workflow does not use Python at runtime.

## Important
The FFmpeg dependency is the GPL build because the original generator specifies libx264/H.264 encoding. This has GPL licensing implications for redistribution.

The app targets arm64-v8a devices (the common Android 64-bit ABI).
