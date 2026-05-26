# 于你 (YuNi) — AI 情感陪伴助手

开源二次元 AI 陪伴应用，支持多角色聊天、群聊、离线语音通话、Live2D 虚拟形象和 AI 生图。

## 功能

### 核心聊天
- **多角色对话** — 支持导入酒馆 (SillyTavern) PNG 角色卡，内置 21 张预设人设卡
- **三种聊天模式** — 普通 / Waifu（拟微信短句，逐条气泡）/ 穿越（沉浸式叙事互动）
- **群聊** — Swap-Card Round-Robin 机制，多角色轮流发言
- **消息分支** — 支持重新生成和分支切换
- **引用回复** — 长按 AI 消息引用回复

### 语音 & TTS
- **电话模式** — 离线 STT（sherpa-onnx Paraformer）→ 自动发送 → VITS TTS 语音回复
- **静默自动发送** — 停止说话 5 秒后自动发送
- **离线 TTS** — sherpa-onnx VITS 引擎，AISHELL-3（174 音色）/ Piper（中文女声）
- **逐句朗读** — 长按 AI 消息 → 朗读此句

### 虚拟形象
- **Live2D 渲染** — Cubism 5 SDK 原生集成，通话时显示动态角色
- **人设市场** — 21 张 PNG 角色卡，2 列网格浏览，一键导入

### 社交化叙事
- **朋友圈** — AI 自动生成 WeChat 风格动态，支持配图（二次元风格）
- **最近经历** — AI 生成角色故事，第三人称叙事
- **每日状态** — 16 种随机状态（💭 胡思乱想中 / 📚 学习中 / 😴 发呆中等）

### AI 生图
- **多 provider** — 硅基流动 / 魔搭社区 / 阿里云百炼 / OpenAI 兼容
- **朋友圈配图** — 自动生成二次元自拍或风景照
- **测试生成** — 输入描述即时验证
- **生成相册** — 收藏、删除管理

### 主动消息
- **定时推送** — AI 主动发消息，12 种随机场景，防重复
- **未读红点** — 通知栏 + 对话列表红点

### 数据
- **加密存储** — SQLCipher 加密数据库
- **记忆系统** — LLM 提取 + 混合检索评分 + 自动清理 + 用户画像

## 技术栈

```
Kotlin / Jetpack Compose / Hilt / Room+SQLCipher / Ktor OkHttp
WorkManager / sherpa-onnx / Live2D Cubism 5 SDK / Coil 3
```

## 构建

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

需要 Android Studio + JDK 17 + Android SDK 35。

## 项目结构

```
app/                    Hilt 入口，单 Activity，Compose NavHost + Workers
feature/
  feature-chat/         聊天 UI、Waifu/穿越模式、电话模式
  feature-voice/        STT/TTS/VoiceProfile/VoiceSettings
  feature-live2d/       Cubism 5 SDK 渲染管线
  feature-persona/      人设宇宙、人设市场、引导创建、朋友圈/经历
  feature-memory/       记忆提取和检索
  feature-settings/     设置、生图配置、相册
  feature-apiconfig/    API Provider 配置
domain/                 数据模型、Repository 接口、UseCase
data/                   Repository 实现、Room Mapper
core/
  core-common/          工具类：CryptoUtil/ImageGenerator/UnreadTracker
  core-database/        Room 实体/DAO/DatabaseFactory
  core-network/         Ktor HTTP/SSE
  core-di/              Hilt 模块
  core-ui/              主题色板 (AnimeColors.kt)
```

## 第三方库

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — 离线 STT/TTS
- [Live2D Cubism SDK for Java](https://www.live2d.com/) — Live2D 渲染
- [OpenAI Images API](https://platform.openai.com/docs/guides/images) — 图片生成
- [ModelScope API](https://modelscope.cn/) — 魔搭社区模型推理
- [SiliconFlow](https://siliconflow.cn/) — FLUX 生图 API

## License

MIT
