<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:7c3aed,50:2563eb,100:06b6d4&height=170&section=header&text=Muimex11&fontSize=58&fontColor=ffffff&fontAlignY=36&desc=Android%20game%20optimizer%20%E2%80%94%20free%2C%20no%20ads%2C%20no%20premium&descSize=20&descAlignY=58&animation=fadeIn" width="100%" />

[![Build](https://github.com/Inkflow-Gab/Muimex11/actions/workflows/build.yml/badge.svg)](https://github.com/Inkflow-Gab/Muimex11/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Inkflow-Gab/Muimex11?label=Release&color=3765D8)](https://github.com/Inkflow-Gab/Muimex11/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Inkflow-Gab/Muimex11/total?label=Downloads&color=brightgreen)](https://github.com/Inkflow-Gab/Muimex11/releases)
[![Stars](https://img.shields.io/github/stars/Inkflow-Gab/Muimex11?color=yellow)](https://github.com/Inkflow-Gab/Muimex11/stargazers)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)

</div>

# About

Muimex11 is an Android game optimizer built on [Shizuku](https://shizuku.rikka.app/). It runs ADB-level commands without root: a floating Game Space dock with a live FPS counter, a Samsung panel that removes the Game Optimizing Service bottleneck, real-time hardware monitoring, and a set of performance/display tweaks.
No ads, no accounts, no paid tiers — every feature is available to everyone.

<div align="center">

<img src="https://readme-typing-svg.demolab.com/?font=Righteous&weight=600&size=22&duration=3000&pause=900&color=7CF5C9&center=true&vCenter=true&width=640&height=60&lines=Floating+Game+Space+Dock+%7C+Live+FPS;Samsung+Boost+Panel;Shizuku+Powered+%7C+No+Root" alt="live animation" />

<img src="https://count.getloli.com/get/@Muimex11-official-readme" alt="visitors" width="200" />

</div>

# Features

## Floating Game Space dock
A draggable orb that snaps to the screen edge and opens a glass panel over any game. Stays dark and readable over bright content.

- **App detection** — name and icon of whatever app is on screen
- **Live FPS counter** — sampled per second, color-coded by threshold
- **Two-column tile grid** with live on/off state:

| Boost | Game Boost |
|---|---|
| kills background apps + trims caches | forces Android Game Mode *Performance* on the detected app |
| **Fixed Performance** | **Thermal Override** |
| locks the CPU against mid-fight downclocking | suspends thermal throttling limits |
| **120Hz Lock** | **Focus Mode** |
| min + peak refresh rate locked at 120 | heads-up banners disabled |
| **No Window Blurs** | **Resolution Cycle** |
| disables blur effects that cost GPU time | 100% → 75% → 50% render scale |
| **HW Monitor** | **Stop Game** |
| toggles the stats overlay | force-stops the detected app |

## Samsung panel (One UI)
- **Max Boost** — thermal override + background clear + cache trim in one tap
- **GOS disable** — removes Samsung's Game Optimizing Service FPS cap, with restore
- **RAM Plus off**, **logging daemons frozen**, **Device Security frozen**, **Samsung bloat frozen**
- **Restore all** — returns every package and setting to default

## Hardware monitor
Floating overlay with CPU temperature and frequency, RAM usage, battery temp and network traffic, updated in real time.

## Performance & system tools
RAM booster · cache cleaner · app freezer · fstrim · fixed-performance modes · Game Mode selector · thermal override · blur disable · fast animations · refresh rate control (up to 240Hz) · resolution + DPI control · private/custom DNS · touch and network tuners · gyro fix · crosshair overlay · auto clicker · gaming DND · APK/OBB installer · Shizuku shell terminal · device info · English and Brazilian Portuguese.

## Self-updating
The app checks this repository's `version.json`, prompts when a newer release is published, and installs it over itself. Updates are verified against the repo's `signature.txt` fingerprint.

# Install

1. Get the APK from [Releases](https://github.com/Inkflow-Gab/Muimex11/releases/latest) (signed by the repo key on GitHub Actions).
2. Install [Shizuku](https://shizuku.rikka.app/) and start it (wireless debugging, root, or one-time USB from a PC).
3. Open Muimex11, grant Shizuku permission, and enable the tools you want.
4. Optional: allow *Display over other apps* for the Game Space dock and monitor.

Muimex11 installs alongside the old Benimaru app; you can remove Benimaru at any time.

# Building and releases

- Every push to `main` runs the [Build workflow](https://github.com/Inkflow-Gab/Muimex11/actions/workflows/build.yml) and produces a signed APK artifact.
- Launcher icons are regenerated at build time from `art/app-icon.jpg` (Pillow; previews attached to every run).
- To publish an update: bump `versionCode`/`versionName` in `app/build.gradle.kts`, commit, then push a tag `v<name>` — the workflow signs the APK, creates the GitHub release, and refreshes `version.json` and `signature.txt` so installed apps pick it up.
- Signing material lives in write-only Actions secrets (`MUIMEX_KEYSTORE_BASE64`, `MUIMEX_KEYSTORE_PASSWORD`, `MUIMEX_KEY_ALIAS`) and is never committed.

<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Inkflow-Gab/Muimex11/output/github-contribution-grid-snake-dark.svg" />
  <img src="https://raw.githubusercontent.com/Inkflow-Gab/Muimex11/output/github-contribution-grid-snake.svg" alt="contribution snake" width="100%" />
</picture>

[![Telegram](https://img.shields.io/badge/Telegram-muimex11-2CA5E0?logo=telegram&logoColor=white)](https://t.me/muimex11)

</div>

# Português (BR)

O Muimex11 é um otimizador de jogos Android gratuito — sem anúncios e sem premium — baseado no Shizuku (sem root).

- **Game Space flutuante**: bolha arrastável sobre qualquer jogo com **nome + ícone do app aberto**, **contagem de FPS ao vivo** e grade de ações em duas colunas: boost de RAM, Game Boost no app em foco, performance fixa, bypass térmico, trava 120Hz, modo foco, sem blurs, ciclo de resolução (100/75/50%), monitor e fechar jogo.
- **Painel Samsung**: Max Boost em um toque, desativar o GOS (com restauração), RAM Plus off, congelar daemons de log, Device Security e bloat da Samsung, além de restaurar tudo.
- **Monitor de hardware** flutuante em tempo real (CPU, RAM, bateria, rede).
- Ferramentas de desempenho, display, rede, APK/OBB e terminal Shizuku — tudo em **PT-BR e inglês**.

Baixe em [Releases](https://github.com/Inkflow-Gab/Muimex11/releases/latest), inicie o Shizuku e autorize no app. O Muimex11 atualiza sozinho quando sai uma versão nova aqui no GitHub.

---
<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:7c3aed,100:06b6d4&height=4&section=footer" width="100%"/>

</div>
