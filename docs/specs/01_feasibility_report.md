# CinePilot Feasibility Report

## 1. Verified JioHome & Jio Hybrid STB Integration Mechanisms

### Verified Mechanisms (from APK analysis):
- **Firebase Cloud Messaging (FCM)**: `com.jio.home.notification.FireBaseMessagingService` (verified)
- **Notification Messaging**: `NotificationMsgReceiver` with `com.jio.home.permission.JIO_CUSTOM_BROADCAST_NOTIFICATION_MSG_RECEIVER` permission (verified)
- **Bluetooth LE**: Required feature (`android.hardware.bluetooth_le`) with Bluetooth permissions (verified)
- **Web Deep Links**: HTTPS deep links to `jiohomeliving.page.link/jiohome` and `sit-api.jiohome.jio.com/jiohome` (verified)
- **App Launching**: `com.jio.btremoteapp.action.OPEN_BT_REMOTE` intent (verified)
- **Bluetooth Remote App**: `com.jio.btremoteapp` (explicitly declared)

### Unknown/Unverified Mechanisms:
- **Direct STB Control**: No AIDL interfaces, ContentProviders, or explicit STB control APIs found
- **Content Search API**: No documented endpoints for movie/series lookup found in manifest
- **Subscription Management**: No UI or API for Jio subscription preferences found
- **Local Device Discovery**: No mDNS, SSDP, or other network discovery mechanisms in manifest
- **Hardware Abstraction Layer**: No clear abstraction for STB control in app code

### Supported Alternatives:
1. **Official Jio API**: Not publicly documented (would require partnership)
2. **Custom ROM/Root**: Not feasible without user consent and device modification
3. **Third-Party STB APIs**: Not verified in APK (no evidence of external providers)
4. **Hybrid Approach**: Use app's existing deep links + Bluetooth LE for basic control

## 2. OOAD Diagrams (Planned)

### Use Case Diagram
- User speaks: "Show me Jio movies"
- App processes voice → Gemini intent → movie search → STB control or app launch

### Class Diagram
- `VoiceProcessor` (uses Gemini API)
- `MovieRepository` (manages movie metadata)
- `STBController` (abstract base for device control)
- `JioSTBController` (concrete implementation)
- `StreamingService` (abstract base for streaming services)
- `JioStreamingService` (concrete implementation)
- `UIController` (manages UI state)

### Sequence Diagram (Core Flow)
1. User speaks voice command
2. VoiceProcessor sends audio to Gemini API
3. Gemini returns intent (e.g., "play_movie", "search_movie")
4. MovieRepository queries available titles
5. STBController attempts to send control command
6. If STB control fails: fallback to app launch or app-to-app intent

### Activity Diagram
- Start → Voice Input → Gemini Processing → Intent Parsing → Movie Search → STB Control → Success/Failure → UI Feedback

### Component Diagram
- UI Layer (Compose)
- Voice Layer (Speech-to-Text + Intent Processing)
- Data Layer (Movie Repository)
- Device Control Layer (STBController abstraction)
- Integration Layer (Firebase, Google APIs)

### Deployment Diagram
- User Device (Android phone/tablet)
- STB (Jio Hybrid STB)
- Cloud (Gemini API, Firebase)
- Network Layer (HTTPS, Bluetooth LE)

## 3. Architecture Design

### Modular Architecture
```
CinePilot App
├── UI Layer (Jetpack Compose)
│   ├── MainActivity
│   ├── MovieSearchScreen
│   ├── MovieDetailScreen
│   └── STBControlPanel
│
├── Voice Layer
│   ├── VoiceProcessor (Speech-to-Text + Intent Classification)
│   └── GeminiIntentService (API client)
│
├── Data Layer
│   ├── MovieRepository (local cache + network)
│   ├── SubscriptionManager
│   └── STBDeviceRegistry
│
├── Device Control Layer
│   ├── STBController (interface)
│   ├── JioSTBController (implementation)
│   └── StreamingService (interface)
│
└── Integration Layer
    ├── Google Gemini API
    ├── Firebase Cloud Messaging
    └── Bluetooth LE (for device control)
```

## 4. Integration Spike Plan

### Minimal Proof of Concept (PoC)
- **Goal**: Determine if CinePilot can discover and control Jio Hybrid STB
- **Approach**: 
  1. Use existing `OPEN_BT_REMOTE` intent to launch Jio remote app
  2. Test Bluetooth LE discovery via `android.bluetooth` APIs
  3. Attempt to send STB control commands via known Bluetooth characteristics
  4. Verify if Jio STB responds to standard Bluetooth commands

### Test Cases
1. Can app launch Jio remote control app via intent?
2. Can app discover nearby Jio STB via Bluetooth?
3. Can app send "power on" command via Bluetooth LE?
4. Can app receive STB status updates?

## 5. Gemini Integration Plan

### Configuration Requirements
- **API Key**: Stored in `gradle.properties` (not in APK)
- **Billing**: Gemini API (Pro) vs. Gemini API (Standard) - need to verify cost
- **Security**: API key restricted to app package name + SHA-256 certificate
- **Usage Limits**: Monitor for token consumption in voice processing

### Key Dependencies
- `com.google.ai:gemini-api` (Gemini API client)
- `com.google.android:flexbox` (UI layout)
- `com.google.android.material` (Material components)

## 6. Incremental Implementation Plan

### Phase 1: Foundation (2 weeks)
- Set up project with Compose UI scaffolding
- Implement VoiceProcessor with offline speech recognition
- Create MovieRepository with placeholder data

### Phase 2: Integration Spike (1 week)
- Build PoC for STB control using Bluetooth LE
- Test with physical Jio Hybrid STB

### Phase 3: Core Features (3 weeks)
- Implement movie search with Gemini intent parsing
- Add subscription preference management
- Implement streaming service selection

### Phase 4: Playback & Control (2 weeks)
- Implement video player with ExoPlayer
- Add STB control via Bluetooth LE
- Implement fallback to app launch if STB control fails

## 7. Acceptance Criteria

### Must-Have:
- ✅ Application builds and runs on Android 10+
- ✅ Voice input works with offline speech recognition
- ✅ Gemini API integration with secure key handling
- ✅ Movie search returns valid results from public API
- ✅ At least one verified STB control operation demonstrated
- ✅ Unit tests for core components
- ✅ Device-level integration tests for critical paths

### Not Acceptable:
- ❌ Speculative integrations without evidence
- ❌ Fabricated APIs or endpoints
- ❌ Redistribution of JioHome APK
- ❌ Implementation without user consent for hardware access
- ❌ Ignoring permission requirements

---

I'll now create the Gradle configuration and initial project files to begin implementation.