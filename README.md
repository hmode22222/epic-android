# Epic Terminal

A small Linux terminal emulator for Android. It bundles a static
[BusyBox](https://www.busybox.net/) binary and runs its `ash` shell inside a
real pseudo-terminal, giving you a self-contained Linux environment directly on
your device.

Built with Kotlin + Gradle, and powered by the
[Termux terminal-view/terminal-emulator](https://github.com/termux/termux-app)
libraries, which are vendored in this repository (see `termux/`) so the APK
needs no external runtime dependencies and no JitPack artifacts.

## Features

- Bundled static BusyBox v1.29.3 for `arm64-v8a`, `armeabi-v7a`, `x86` and
  `x86_64` — the binary is extracted from assets on first run
- BusyBox `ash` interactive shell over a real PTY (`/dev/ptmx`)
- Full VT100/ANSI color emulation with scrollback
- A small app-private Linux filesystem (`filesDir`): `home/`, `bin/`
  (BusyBox applets), `etc/`, `tmp/`
- On-screen extra keys bar: `ESC`, `TAB`, `CTRL`, `ALT`, arrow keys, `|`, `&`
  - `CTRL`/`ALT` are toggle keys — tap them once, then type
- Hardware keyboard support (arrow keys, tab, ctrl/alt combos)
- Long-press text selection and clipboard copy/paste
- Terminal resizes automatically with the soft keyboard
- Startup errors are shown on screen instead of silently crashing

## Build

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The native `libtermux.so` (PTY handling) is compiled with the NDK
(`termux/terminal-emulator/src/main/jni`); the first CI run will download the
required NDK automatically.

A GitHub Actions workflow (`.github/workflows/android.yml`) builds the APK on
every push to `main` and uploads it as a build artifact. You can also trigger
it manually from the Actions tab.

## License

This project is licensed under the GPL-3.0 (see `LICENSE`).

The vendored Termux terminal libraries under `termux/`
([termux-app](https://github.com/termux/termux-app), tag `v0.118.0`) are
GPL-3.0 licensed.

BusyBox is licensed under GPL-2.0 (see
https://www.busybox.net/license.html); the static binaries are built by the
[android-busybox-ndk](https://github.com/osm0sis/android-busybox-ndk) project.
