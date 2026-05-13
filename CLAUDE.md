# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clean
./gradlew clean assembleDebug

# Release
./gradlew assembleRelease
```

SDK path in `local.properties`: `C:\Users\切记内服\Android\Sdk`

## Architecture

Multi-module Clean Architecture, Android 8.0+ (API 26–35), Kotlin + Jetpack Compose.

```
app  ──→  feature/*  ──→  domain  +  data  ──→  core/*
```

| Layer | Modules | Responsibility |
|-------|---------|---------------|
| App | `app` | Hilt entry point, single-Activity, Compose NavHost |
| Feature | `feature-chat`, `feature-voice`, `feature-live2d`, `feature-memory`, `feature-persona`, `feature-apiconfig`, `feature-settings` | Screens + ViewModels per feature |
| Domain | `domain` | Models, Repository interfaces, UseCases. Zero Android deps |
| Data | `data` | Repository implementations, Room mappers, API calls |
| Core | `core-common`, `core-database`, `core-network`, `core-di`, `core-ui` | Constants, Room DAOs/entities, HTTP/SSE, Hilt modules, Theme |

### DI (Hilt)

- `core-di/DatabaseModule` — Room DB + SQLCipher + all DAOs
- `core-di/RepositoryModule` — Repository impls + UseCases + SSEClient
- All feature modules must have `hilt.android` plugin + `ksp(libs.hilt.compiler)` for `@HiltViewModel`

### Database

Room with **SQLCipher** encryption. Passphrase from Android Keystore in `DatabaseFactory`.
`DatabaseFactory` has auto-recovery: if DB open fails, deletes corrupted files and recreates.

### Network

`HttpClientFactory` → Ktor `OkHttp` engine (NOT CIO) with TLS 1.2/1.3.
`SSEClient` handles streaming chat with manual SSE parsing (not Ktor SSE plugin).

### Navigation

Single `NavHost` in `AppNavigation.kt`. Screens communicate via `savedStateHandle` (e.g., persona selection → chat).

## Critical Constraints

- **`sendMessage()` flow must NOT use `.collect {}` on Room Flows** — Room flows are infinite, `.collect {}` blocks the coroutine forever. Use `.first()` for one-shot reads.
- **ABI filter** in `app/build.gradle.kts`: `arm64-v8a, armeabi-v7a, x86_64` (SQLCipher needs native libs).
- **`windowSoftInputMode="adjustNothing"`** — Compose handles IME via `Modifier.imePadding()`.
- **`CryptoUtil.getOrCreateKey()` returns Keystore key** whose `getEncoded()` is null. `DatabaseModule` falls back to fixed passphrase — encryption is cosmetic until this is fixed.
- **Schema changes** require `fallbackToDestructiveMigration()` in `DatabaseFactory` — no proper migrations yet.

## Key Files

| File | Role |
|------|------|
| `app/build.gradle.kts` | ABI filter, ProGuard, all module deps |
| `app/.../navigation/AppNavigation.kt` | All routes, NavHost |
| `core/core-di/.../DatabaseModule.kt` | Room + SQLCipher setup |
| `core/core-di/.../RepositoryModule.kt` | Repository + UseCase bindings |
| `core/core-database/.../DatabaseFactory.kt` | DB creation with auto-recovery |
| `core/core-network/.../HttpClientFactory.kt` | Ktor OkHttp engine + TLS |
| `core/core-network/.../SSEClient.kt` | Manual SSE parsing |
| `feature/feature-chat/.../ChatViewModel.kt` | Core chat logic, message flow |
| `data/.../ChatRepositoryImpl.kt` | SSE stream, message persistence |
| `gradle/libs.versions.toml` | Version catalog |

## Development State

- AI 对话（SSE 流式）：DeepSeek 已验证可用
- 人设系统：支持自定义 + userDisplayName
- 记忆管理：keyword search fallback（无向量搜索）
- Live2D：状态管理器骨架，无 Cubism SDK
- 语音：依赖 Android SpeechRecognizer（需 Google 服务或系统语音）
- 合规页面：内容待完善
