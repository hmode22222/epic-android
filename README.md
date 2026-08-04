# Epic Terminal

A small Linux terminal emulator for Android. It bundles a static
[BusyBox](https://www.busybox.net/) binary and a static
[PRoot](https://proot-me.github.io/) user-space chroot, together with an
[Alpine Linux](https://alpinelinux.org/) minirootfs. The terminal runs a real
Linux shell (`/bin/sh` inside Alpine) over a real pseudo-terminal — with fake
root and a working `apk` package manager — without requiring root.

Built with Kotlin + Gradle, and powered by the
[Termux terminal-view/terminal-emulator](https://github.com/termux/termux-app)
libraries, which are vendored in this repository (see `termux/`) so the APK
needs no external runtime dependencies and no JitPack artifacts.

## Features

- Real Linux environment: Alpine Linux minirootfs (v3.20.9) run under PRoot
  v5.3.0 with fake root (`-0`) — you are `root` inside the guest
- `apk` package manager works: `apk add`, `apk update`, ...
- PTY provided by the vendored Termux terminal libraries (`/dev/ptmx`)
- Bundled statically, per ABI:
  - BusyBox v1.29.3 for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
  - PRoot for `arm64-v8a`, `armeabi-v7a`, `x86_64` (no static x86 build —
    those devices fall back to plain BusyBox ash)
  - Alpine minirootfs for `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Everything is extracted to app-private storage on first run (one time)
- Full VT100/ANSI color emulation with scrollback
- On-screen extra keys bar: `ESC`, `TAB`, `CTRL`, `ALT`, arrow keys, `|`, `&`
  - `CTRL`/`ALT` are toggle keys — tap them once, then type
- Hardware keyboard support (arrow keys, tab, ctrl/alt combos)
- Long-press text selection and clipboard copy/paste
- Terminal resizes automatically with the soft keyboard
- Startup errors and early shell exits are shown on screen instead of silently
  crashing; diagnostics are also written to `filesDir/startup.log` and
  `filesDir/crash.log`

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

PRoot (v5.3.0 static binaries) is licensed under GPL-2.0 (see
https://github.com/proot-me/proot).

Alpine Linux minirootfs is licensed under GPL-2.0 (see
https://alpinelinux.org/).
