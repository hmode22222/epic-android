# Epic Terminal

A small Linux terminal emulator for Android. It runs a real shell
(`/system/bin/sh`, Android's mksh) inside a pseudo-terminal, so you get an
interactive Linux shell directly on your device.

Built with Kotlin + Gradle, and powered by the
[Termux terminal-view/terminal-emulator](https://github.com/termux/termux-app)
libraries.

## Features

- Interactive shell over a real PTY (`/system/bin/sh`)
- Full VT100/ANSI color emulation with scrollback
- On-screen extra keys bar: `ESC`, `TAB`, `CTRL`, `ALT`, arrow keys, `|`, `&`
  - `CTRL`/`ALT` are toggle keys — tap them once, then type
- Hardware keyboard support (arrow keys, tab, ctrl/alt combos)
- Long-press text selection and clipboard copy/paste
- Terminal resizes automatically with the soft keyboard

## Build

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow (`.github/workflows/android.yml`) builds the APK on
every push to `main` and uploads it as a build artifact. You can also trigger
it manually from the Actions tab.

## License

This project is licensed under the GPL-3.0 (see `LICENSE`).

It uses the Termux terminal libraries
([termux-app](https://github.com/termux/termux-app)), which are also
GPL-3.0 licensed and are distributed unmodified as Maven artifacts.
