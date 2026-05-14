# CLAUDE.md

## Build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

Multi-module Clean: `app → feature/* → domain + data → core/*`

| Layer | Modules | Role |
|-------|---------|------|
| App | `app` | Hilt entry, single-Activity, Compose NavHost |
| Feature | `feature-{chat,voice,live2d,memory,persona,apiconfig,settings}` | Screens + ViewModels |
| Domain | `domain` | Models, Repository interfaces, UseCases |
| Data | `data` | Repository impls, Room mappers |
| Core | `core-{common,database,network,di,ui}` | Constants, Room, HTTP/SSE, Hilt modules, Theme |

Hilt: `core-di/DatabaseModule` + `RepositoryModule`
Network: Ktor OkHttp + manual SSE parsing (no Ktor SSE plugin)
DB: Room + SQLCipher, `DatabaseFactory.kt` with `DB_SCHEMA_VERSION` auto-clean

## Critical Rules

- Room Flow: `.first()` for one-shot reads, `.collect{}` ONLY in launched coroutines for live observation
- Schema change: bump `DB_SCHEMA_VERSION` in DatabaseFactory.kt
- All pages use `Column` not `Scaffold` (outer Scaffold handles bottom nav insets)
- `windowSoftInputMode=adjustNothing` — Compose uses `imePadding()`
- Feature modules MUST have `hilt.android` plugin + `ksp(libs.hilt.compiler)` for `@HiltViewModel`

## Key Files

`AppNavigation.kt` — routes, bottom nav (3 tabs: 对话/人设/设置)
`ChatViewModel.kt` — core chat logic, persona/provider selection, branching
`ChatRepositoryImpl.kt` — SSE stream, API messages, system prompt + world book injection
`SSEClient.kt` — manual SSE parsing (choices[] array)
`HttpClientFactory.kt` — OkHttp engine + TLS
`DatabaseFactory.kt` — Room + SQLCipher + auto-recovery (DB_SCHEMA_VERSION=9)
`PersonaScreen.kt` — character card editor + PNG tavern card import
`PersonaSettingsScreen.kt` — per-persona settings (voice/model/stickers/world book/author's note)
`ConversationListScreen.kt` — main page chat list + group chat creation
`TavernCardParser.kt` — PNG chunk parser for v2/v3 character cards
`WorldBookScreen.kt` — per-persona world book entry management
`AnimeColors.kt` — centralized pink anime palette
`gradle/libs.versions.toml` — version catalog
