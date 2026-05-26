# CLAUDE.md

## Build & Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

Multi-module Clean: `app → feature/* → domain + data → core/*`

| Layer | Modules | Role |
|-------|---------|------|
| App | `app` | Hilt entry, single-Activity, Compose NavHost, Workers |
| Feature | `feature-{chat,voice,live2d,memory,persona,apiconfig,settings}` | Screens + ViewModels |
| Domain | `domain` | Models, Repository interfaces, UseCases |
| Data | `data` | Repository impls, Room mappers |
| Core | `core-{common,database,network,di,ui}` | Constants, Room+SQLCipher, HTTP/SSE, Hilt, Theme |

Hilt: `core-di/DatabaseModule` + `RepositoryModule`
Network: Ktor OkHttp + manual SSE parsing（非 Ktor SSE 插件）+ `HttpURLConnection`（Worker 中调用）
DB: Room + SQLCipher, `DatabaseFactory.kt` 中 `DB_SCHEMA_VERSION=17`, 升级自动清库重建

## Critical Rules

- **第三方服务集成**：优先使用官方 SDK 或官方文档中的示例代码，不要自行猜测 REST API 端点和参数格式。DashScope、OpenAI 等服务都有官方 SDK（如 `dashscope-sdk-java`）或文档说明正确的端点 URL、鉴权方式、请求/响应格式。直接逆向工程 HTTP API 极易出现端点错误（本次 TTS 集成反复试了 4 个端点均 400，最终用官方 SDK 一行代码解决）
- Room Flow: `.first()` 用于一次性读取, `.collect{}` 仅用于 launched coroutine 中做持续观察
- Schema 变更: 递增 `DB_SCHEMA_VERSION`（`DatabaseFactory.kt`）
- Feature 模块必须有 `hilt.android` plugin + `ksp(libs.hilt.compiler)` 才能用 `@HiltViewModel`
- 跨模块依赖: feature 模块不能依赖 app 模块，Worker 回调通过 callback/lambda 传递
- 线程: `TextToSpeech.speak()/stop()` 必须在主线程调用；STT 录音在 `Dispatchers.IO`
- 所有页面使用 `Column` 而非内层 `Scaffold`（外层 Scaffold 处理底部导航 insets）
- `windowSoftInputMode=adjustResize`
- **SherpaOnnxTTS 线程安全**: `stateLock` 保护 isSpeaking/isReleased，`nativeLock` 保护 native generate/release 互斥。永远先获取 stateLock 再获取 nativeLock（避免死锁）
- **TTSManager.state**: `@Volatile` 确保 UI 线程读到 IO 线程的写入。`speakLazyLoad/speakCloud/speakLoaded` 都通过 `scope.launch` 异步执行
- **TTS 内存策略**: STT(78MB) 和 TTS(38MB) 不同时加载。`speakWithTts` 先 `onnxSTT.release()` 再加载 TTS
- **网络检测**: `ChatViewModel.speakWithTts` 通过 `ConnectivityManager.getNetworkCapabilities` 判断联网（不依赖 NetworkMonitor，避免跨模块依赖）

## UI Design System（治愈系少女粉）

详见 `DESIGN.md`（YAML tokens + Markdown 规范），关键色值：
- 背景: `#FFF0F3 → #FFF8F9` 竖向渐变
- 强调色: `#FF8DAC`（柔和粉） / `#FF6B9B`（部分地方）
- 文字: 标题 `#2D1B2E` / 正文 `#6B5B6E` / 次要 `#B0A0B0`
- 圆角: 卡片 14-18dp，按钮胶囊形 20dp，头像圆形
- 阴影: 卡片 1dp / 导航 2dp / FAB 4dp
- 底部导航: 半透明白底 `Color(0xF5FFFFFF)`，轻阴影
- Legacy 色板: `AnimeColors.kt`（Pink50~Pink700, ChatBg, topBarGradient）仍被部分页面引用

## 导航路由

**Bottom nav**（3 tab）: 对话 | 人设 | 设置（仅在 HOME/PERSONAS/SETTINGS 显示）

| Route | 页面 | 文件 |
|-------|------|------|
| `HOME` | 对话/动态/经历 三 tab | `HomeScreen.kt` |
| `CHAT` | 聊天详情 | `ChatScreen.kt` |
| `PERSONAS` | 人设宇宙（2 列网格卡片） | `PersonaScreen.kt` |
| `PERSONA_SETTINGS` | 人设详细设置 | `PersonaSettingsScreen.kt` |
| `MOMENTS` | 单个人设的朋友圈 | `MomentsScreen.kt` |
| `EXPERIENCES` | 单个人设的经历 | `ExperiencesScreen.kt` |
| `RENSHE_SHICHANG` | 人设市场（21 张预设卡） | `RensheShichangScreen.kt` |
| `SETTINGS` | 系统设置 | `SettingsScreen.kt` |
| `PROACTIVE_MESSAGES` | 主动消息设置 | `ProactiveMessageSettingsScreen.kt` |
| `LIVE2D_MODELS` | Live2D 模型管理 | `Live2DModelManagerScreen.kt` |
| `VOICE_SETTINGS` | 语音设置 | `VoiceSettingsScreen.kt` |

**导航流**:
- HOME 对话列表 → 点击角色: `SelectPersona(id)` → `chatRoute(personaId)`
- HOME 对话列表 → 点击群聊: `SelectConversation(id)` → `chatRoute(personaId, convId)`
- 聊天页 → 右上角: 群聊→群设置, 单聊→`PERSONA_SETTINGS`
- 人设宇宙 → 右上角🏪 → 人设市场 → 点击导入
- `selectPersona` 过滤时排除 `isGroupChat`

## Key Files

### Chat
| 文件 | 职责 |
|------|------|
| `ChatViewModel.kt` | 核心聊天（~1020 行）— 群聊轮询、Waifu 句分割、消息分支、情绪检测、引用回复、STT/TTS 控制、语音输入自动发送、LLM 记忆提取 |
| `ChatScreen.kt` | 聊天 UI — MessageList、ReplyBar、ChatInputBar、CallModeOverlay（Live2D 角色） |
| `ChatState.kt` | `ChatUiState`（含 replyTarget/callMode/voiceMode/live2DModel）、`ChatIntent`、`MessageUi` |
| `ChatRepositoryImpl.kt` | SSE 流式、`buildApiMessages`（10 层分层 prompt）、记忆 + 用户画像注入 |

### HOME / Moments / Experiences
| 文件 | 职责 |
|------|------|
| `HomeScreen.kt` | HOME 页 — 顶部导航(于你♥)、欢迎卡片(角色插画)、三 tab(对话/动态/经历)、对话列表(头像+关系标签+未读数)、朋友圈时间线聚合、经历卡片聚合 |
| `MomentsScreen.kt` | 单个人设朋友圈 — WeChat 风格时间线 + 设置弹窗(开关/间隔/立即生成) |
| `ExperiencesScreen.kt` | 单个人设经历 — 卡片故事列表 + 设置弹窗 |

### Persona
| 文件 | 职责 |
|------|------|
| `PersonaScreen.kt` | 人设宇宙 — 2 列网格卡片(角色插画背景+渐变遮罩+姓名+关系标签+每日状态+陪伴天数)、分类标签过滤、列表/网格切换 |
| `PersonaSettingsScreen.kt` | 人设设置 — 音色/模型/表情包/世界书/Waifu/Author's Note/朋友圈入口/经历入口 |
| `RensheShichangScreen.kt` | 人设市场 — 读取 assets 中 21 张 PNG，解析 tavern card 元数据，卡片展示，一键导入 |
| `TavernCardParser.kt` | PNG tavern card 解析（v2/v3 spec，tEXt chunk → Base64 → JSON） |

### Voice
| 文件 | 职责 |
|------|------|
| `SherpaOnnxSTT.kt` | 离线 STT — sherpa-onnx Paraformer 78MB 中文模型，AudioRecord 16kHz PCM，流式识别 + 5s 静默自动发送 |
| `SherpaOnnxTTS.kt` | 离线 TTS — sherpa-onnx VITS AISHELL-3 38MB 模型（174 音色），synchronized(stateLock)+synchronized(nativeLock) 保护 native 对象，MODE_STATIC AudioTrack 播放 |
| `TTSManager.kt` | TTS 总调度 — 混合路由：有网+DashScope Key → `speakCloud()` → CosyVoice 云端；否则 → `speakLazyLoad()` → AISHELL-3 离线；失败回退系统 TTS。`@Volatile state` |
| `CloudTTSClient.kt` | DashScope CosyVoice 云端 TTS — POST 文本 → 返回 PCM 16-bit 24kHz 音频 |
| `CloudAudioPlayer.kt` | AudioTrack 播放云端 PCM，try-catch 保护不支持的采样率 |
| `VoicePrefs.kt` | SharedPreferences 持久化：模型选择/sid/pitch/speed/DashScope API Key |
| `STTManager.kt` | 系统 SpeechRecognizer 封装（备用方案） |

### Live2D
| 文件 | 职责 |
|------|------|
| `Live2DModel.kt` | 继承 CubismUserModel，完整 .moc3/.model3.json 加载管线（纹理/物理/pose/表达式/动画） |
| `CubismRendererView.kt` | GLSurfaceView + CubismRenderer，透明背景，融入通话覆盖层 |
| `Live2DComposeView.kt` | Compose AndroidView 包装，情绪驱动表情切换，说话驱动口型 |
| `Live2DManager.kt` | CubismFramework 生命周期管理，默认 Haru 模型回退 |

### Memory
| 文件 | 职责 |
|------|------|
| `MemoryExtractor.kt` | LLM 提取（struct JSON: type/content/importance）+ 正则兜底 |
| `MemoryRepositoryImpl.kt` | 混合检索评分（关键词×recency×importance）、自动清理（MAX=50）、记忆巩固（Jaccard 相似度合并） |

### Workers（后台服务）
| 文件 | 职责 |
|------|------|
| `ProactiveMessageWorker.kt` | 主动消息 — WorkManager 单次链调度，12 场景随机+防重复提示词+历史消息去重，保存后增未读数+通知 |
| `MomentGeneratorWorker.kt` | 朋友圈生成 — 人设驱动的 WeChat 风格文案，传递前 5 条防重复 |
| `ExperienceGeneratorWorker.kt` | 经历生成 — 第三人称叙事，5 类（日常/冒险/奇遇/回忆/成长），传递前 3 篇保连续性 |
| `UnreadTracker.kt` | 未读计数 — SharedPreferences 存储，Worker 递增，ChatViewModel 打开对话清零，HomeScreen 读取 |

## Prompt Architecture（`buildApiMessages`）

10 层分层构建：

```
Layer 1: applySystemPrompt       → 人格+反小说化规则+systemPrompt
Layer 2: applyCharacterCard      → 场景+关系+说话风格
Layer 3: applyExampleChats       → 对话样本（system prompt 中展示）
Layer 4: applyChatGuidance       → 节奏提示+禁止问候+情绪适配
Layer 5: collectWorldBookEntries → before/after 关键词触发注入
Layer 6: applySituationalContext → 群聊/记忆/用户画像/引用回复
Layer 7: applyRecentMessages     → 最近 N*2 条真实对话历史
Layer 8: worldBookAfter + Author's Note
Layer 9: applySystemReminders    → <system_reminder> 时间注入
Layer 10: applyWaifuInstruction  → Waifu 拆句指令
```

关键设计原则：
- **对话感优先**：明确禁止 `*动作描写*` `(心理描写)` `【场景描述】`，强调"像发微信消息"
- 对话样本在 system prompt 中展示，不作为独立消息
- Waifu 模式下 Repository 不保存完整助手消息，由 ViewModel 保存分割后句子
- 用户画像（`UserProfile`）在 Layer 6 注入为紧凑的 "关于用户: ..." 一行

## 数据模型（Models.kt 关键类型）

| 类型 | 说明 |
|------|------|
| `Persona` | 30+ 字段，含 moments/experiences/waifuMode/userProfile/各种设置 |
| `Conversation` | 含 isGroupChat/groupPersonaIds/avatarImageUri |
| `Message` | 含 senderPersonaId/branchParentId/branchIndex |
| `ChatContext` | prompt 构建 DTO，含 conversationMood/replyTarget/replyTargetSenderName |
| `MomentEntry` | 朋友圈条目: id/content/location/mood/createdAt |
| `ExperienceEntry` | 经历条目: id/title/content/category/createdAt |
| `UserProfile` | 用户画像: name/traits/preferences/facts |
| `MemoryEntry` | 含 importance/confidence/decayFactor/accessCount/lastAccessedAt |

## 群聊机制（Swap-Card Round-Robin）

- `startGroupChat`: 创建 `isGroupChat=true` 的对话，`personaId` 设为第一个成员
- 发送消息后，`speakInGroupRoundRobin` 按 `groupPersonaIds` 顺序逐一发言
- 每个发言者用自己 persona 构建独立 `ChatContext`（`isGroupSpeakerTurn=true`）
- 回复注入 `sharedMessages` 供下一人感知上下文
- Waifu 模式兼容群聊：`processWaifuResponse` 接受 `sharedMessages` 参数

## Waifu 模式

- 开启后 AI 回复拆分为逐句气泡（以 `。！？!?…\n` 分割）
- 后处理 strip 动作描写（`*...*`、`（...）`、`(...)`、`【...】`）
- 仅最后一句贴 1 个表情包
- 模拟打字延迟逐条上屏
- `speakWithTts` 逐句朗读（`QUEUE_ADD` 排队，不打断）

## 电话模式

**内存策略（每次只加载一个大模型，峰值 ~80MB）：**
```
进入电话模式 → 只加载 Live2D 静态头像（不加载语音模型）
  ↓ 点麦克风
加载 STT (78MB) → 录音 → 识别 → 静默/结束 → 发送 → 释放 STT
  ↓ AI 回复
加载 TTS (38MB) → 朗读 → 释放 TTS
  ↓ 下一轮
重新加载 STT → ...
```

**TTS 混合路由（CloudTTSClient + TTSManager.speakCloud）：**
- 有网 + DashScope Key 已配置 → CosyVoice 云端 TTS（知甜 `zhitian_emo`，情感自然）
- 无网/未配置 Key → AISHELL-3 离线 VITS
- DashScope Key 独立于聊天 API Key，在 VoiceSettingsScreen 配置，存 VoicePrefs

**STT 静默自动发送：**
- 每次 Partial 结果重置 5 秒计时器
- 计时器触发或 Final 结果 → 自动 `sendMessage()`
- 手动停止且 callMode 中 → 也发送累积文本

**退出：**
- 停止 TTS → 释放 STT → 释放 TTS 模型 → `callMode=false`

## 配色与 UI 约定

- HomeScreen 使用私有颜色常量（软粉渐变 #FFF0F3→#FFF8F9），强调色 #FF8DAC
- 其他页面仍引用 `AnimeColors.kt`（Pink50~Pink700），需逐步统一
- 卡片: 白色底+高圆角(14-18dp)+微弱阴影(1-2dp)
- 底部安全区域: `contentPadding(bottom = 80.dp)` 确保不被导航栏遮挡
