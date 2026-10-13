# DOCSY Implementation Progress (PROGRESS.md)

**Project**: DOCSY (v1.0.6)  
**Package**: `com.suhrud.docsy`  
**Current Phase**: Phase 16 Complete (All 16 Phases 100% Implemented & Verified)  
**Last Build Result**: Complete implementation across all 16 architecture phases: zero-internet manifest, Onboarding with 17 WebP avatars, Nothing-style App Lock with biometric & PIN, root volume storage scanner with hidden folders, multi-format OCR extractors (ML Kit Latin & Devanagari), live reading progress with WorkManager 24h sync and force re-read dialog, grounded document facts with Verhoeff Indian ID validation, device & system tools with strict R8 topic isolation, intent router with collision resolution and Hinglish normalization, BigDecimal calculator with formula audit, local LLM manager with 30s memory unloading, and encrypted Room chat history with 5s undo delete.

---

## 1. Phase Checklist

- [x] **Phase 0: Audit & Plan (/plan)**
  - [x] Read entire project and generate exhaustive `AUDIT.md` (every item across Phases 1-16)
  - [x] Document architecture roadmap, phase ordering, risk mitigations, and gaps in `PROGRESS.md`
  - [x] Design debug-only Extraction Inspector specification (0.3)
  - [x] Perform detailed architectural failure analysis for failing PDF retrievals ("12th marksheet.pdf", CAT exam PDF) (0.4)
  - [x] Perform RouterTrace failure analysis for reported queries ("videos count", "musics on my device", "wifi?", "bluetooth", etc.) (0.5)
  - [x] Create `DECISIONS.md` recording all ambiguous points and architectural choices
  - [x] Approved by user
- [x] **Phase 1: Build, Signing, Version, Permissions, Installability**
  - [x] 1.1 `versionName = "1.0.6"`, monotonic `versionCode = 2` verified via BuildConfig
  - [x] 1.2 Namespace and applicationId `com.suhrud.docsy` across build files and source code
  - [x] 1.3 Removed custom debugConfig; default Android debug key used
  - [x] 1.4 Release signing requiring `keystore.properties` -> `docsy-release-key.jks`; loud failure confirmed on missing file; apksigner verified v2/v3 signatures
  - [x] 1.5 Purged all Firebase, google-services, Retrofit, OkHttp, Moshi, and secrets plugin dependencies
  - [x] 1.6 Merged manifest verified with zero `INTERNET` permission, `allowBackup="false"`, scoped permissions
  - [x] 1.7 Product flavors `full`, `noSmsCalls`, `filesOnly` created and tested
  - [x] 1.8 `INSTALL.md` created with full signing, versioning, and Play Protect instructions
- [x] **Phase 2: First Launch, Onboarding, Profile, Avatars, Settings**
  - [x] 2.1 Complete onboarding coordinator: Welcome -> Enter Name -> Choose Avatar -> 5 Instructions -> Permissions -> Reading -> Home with kill-and-resume persistence
  - [x] 2.2 Name storage in DataStore; trimmed, required; digits or <2 chars flagged UNMATCHABLE
  - [x] 2.3 17 custom WebP avatars (384x384, quality 92, alpha mask, circular clip, no tint) generated in `app/src/main/res/drawable-nodpi/`
  - [x] 2.4 `AvatarCatalog` dynamic 17 items; unified `ProfileAvatar` shared composable
  - [x] 2.5 Persist avatar by ID in DataStore with migration and `avatar_01` fallback
  - [x] 2.6 4-column scrollable grid picker with 1.06 scale, check badge at edge, light haptics
  - [x] 2.7 5 purpose-built instruction screens with subtle outline/glow animations and 5-dot indicator
  - [x] 2.8 Plain-words permission rationale flow, settings deep links, live permission audit
  - [x] 2.9 Read-only profile with Edit mode (name, avatar, alias) and back-cancel-edit-first handling
  - [x] 2.10 Zero legacy routes/strings
  - [x] 2.11 Settings screen with all 7 required sections; version 1.0.6 from PackageManager
- [x] **Phase 3: App Lock (PIN and Fingerprint)**
  - [x] 3.1 Settings > Privacy > App Lock: independent toggles for Require PIN and Use Fingerprint (with enrollment status check)
  - [x] 3.2 PIN 4-6 digits, salted PBKDF2 hash (SHA-256, 100k iterations), Keystore salt storage, progressive lockout (30s..300s) persisted across restarts
  - [x] 3.3 BiometricPrompt with BIOMETRIC_STRONG | DEVICE_CREDENTIAL, auto-prompt on launch/resume > 30s, fallback to PIN
  - [x] 3.4 Lock triggers: cold start, background > 30s (lifecycle observer), screen off (broadcast receiver)
  - [x] 3.5 Lock screen UI: monochrome Nothing style, dot indicator, keypad with optional shuffle, emergency "Forgot PIN?" verified via profile name
  - [x] 3.6 FLAG_SECURE dynamically added on window while app is locked (preventing recents preview)
- [x] **Phase 4: File Scanner**
  - [x] 4.1 Recursive traversal from roots of all storage volumes (primary emulated + secondary SD/USB)
  - [x] 4.2 Traversal includes hidden folders and messenger directories (WhatsApp, Telegram, etc.), ignores `.nomedia`, strictly skips cache/temp/Android-data/Android-obb
  - [x] 4.3 Room `DocumentEntity` schema: canonical path, name, extension, MIME, size, dates, contentKey (`path:size:mtime`), status
  - [x] 4.4 Real-time state channel with multi-worker consumer batching into Room
  - [x] 4.5 Central `FileFilter` defining document and image extensions, MIME types, and sanitation
  - [x] 4.6 100% test pass (4/4 tests): filter rules, traversal with hidden/nomedia/skipped folders, contentKey change detection, and synthetic 1,000-file tree walk benchmark
- [x] **Phase 5: Format Conversion and OCR**
  - [x] 5.1 `ExtractionStateMachine`: PENDING, EXTRACTED, EMPTY, UNSUPPORTED, ENCRYPTED, PARTIAL, FAILED with content-hash deduplication and version bump re-queueing
  - [x] 5.2 Local format extractors: `PdfExtractor` (high-DPI `PdfRenderer` + OCR fallback for marksheets/flattened PDFs), `OfficeXmlExtractor` (pure streaming Zip+XML), `PlainTextExtractor`, `ImageExtractor`
  - [x] 5.3 Memory-bounded bitmap tiling in `ImageExtractor` and `LocalOcrEngine`
  - [x] 5.4 Preprocessing pipeline: grayscale, contrast enhancement, rotation checks (0, 90, 180, 270)
  - [x] 5.5 Bundled offline OCR: ML Kit Latin + Devanagari models with bilingual recognition
  - [x] 5.6 Fast edge/variance heuristic prior to heavy OCR passes
  - [x] 5.7 Geometric layout preservation: block coordinates, line ordering, tabular row/column flow
  - [x] 5.8 Post-processing: Unicode NFC normalization, whitespace cleaning, targeted ID number repair
  - [x] 5.9 Document quality scoring: character density, dictionary-hit rate, confidence metrics
  - [x] 5.10 Scalable batching: `ReadingForegroundService` with notification progress updates
  - [x] 5.11 `TelephonyIngestor`: SMS and Call Log ingestion into Knowledge Store with Contact name resolution
  - [x] 5.12 Real-time progress reporting via `ReadingStateRepository` StateFlow
  - [x] 5.13 `ExtractionBenchmark`: throughput and latency metrics per category
  - [x] 5.14 Debug-only `ExtractionInspectorScreen`: document diagnostics, status filters, and error details
- [x] **Phase 6: Live Reading Page, 24h Auto Refresh, Force Read**
  - [x] 6.1 `ReadingProgressScreen`: full-screen animated reading progress with dynamic file counter, monotonic progress bar, tallies, and ETA
  - [x] 6.2 `ReadingStateRepository` central StateFlow and `ReadingForegroundService` notification sync
  - [x] 6.3 Smooth tabular-figure counters, spring progress animations, and glowing pulse indicator
  - [x] 6.4 `ScheduledSyncWorker` 24-hour periodic refresh scheduled via WorkManager
  - [x] 6.5 Force read modal (`ForceReadDialog`): "Quick refresh" vs "Full re-read" with active run lock
  - [x] 6.6 Unit test suite `Phase6ReadingProgressTest` with 100% pass rate
- [x] **Phase 7: Document Understanding and Knowledge Store**
  - [x] 7.1 `DocumentClassifier`: whole-document classification with positive weights, negative evidence penalties (e.g. CV vs marksheet), confidence and top-1 vs top-2 margin checks
  - [x] 7.2 `DocumentSynonymDictionary`: multilingual synonym mapping across English, Hinglish, and Devanagari (marksheet/अंकतालिका, CV/बायोडाटा, Aadhaar/आधार, etc.)
  - [x] 7.3 `GovernmentIdDetector`: Verhoeff checksum algorithm for Aadhaar (0/1 prefix rejection, transposition detection), PAN validation, Voter ID (EPIC), Indian Passport, Driving License, ABHA ID
  - [x] 7.4 Structured facts in Room: `FactEntity` and `FactDao` with primary key, documentId index, confidence, page, isPrimarySource, and full CRUD queries
  - [x] 7.5 `FactConsistencyChecker`: disagreement detection across documents for one owner (DOB mismatch, father name conflict, ID divergence)
  - [x] 7.6 `OwnerExtractor`: grounded OCR text extraction from certificates, marksheets, IDs, bills, and CV headers
  - [x] 7.7 `NameMatcher`: Jaro-Winkler metric, token-set intersection, honorific and salutation stripping, name inversion support
  - [x] 7.8 `OwnerDisambiguation`: candidate resolver ranking matching owners against primary profile
  - [x] 7.9 `ProvenanceTracker`: primary certificate source resolution prioritized over secondary citations (e.g. CV citing 12th marks)
  - [x] 7.10 `MaskedText`: sensitive ID masking with last 4 characters visible by default and tap-to-reveal toggle
- [x] **Phase 8: Device and System Tools**
  - [x] 8.1 `DateTimeTool`: system clock snapshot (12h/24h, formatted date, timezone, day of week) with strict personal date & DOB rejection (routed to document facts per R8)
  - [x] 8.2 `BatteryTool`: live BatteryManager snapshot, status bar percentage parity, charging/discharging states, plug types, health, voltage, and temperature
  - [x] 8.3 `NetworkTool`: offline ConnectivityManager/WifiManager telemetry, airplane mode detection, link speed (Mbps), and TrafficStats throughput
  - [x] 8.4 System tools: `DisplayTool`, `MemoryTool`, `HardwareInfoTool`, and `AppManagerTool` (apps search, RAM/Storage metrics, display specs)
  - [x] 8.5 `StepCounterTool` and `StepDiagnosticsScreen`: multi-tier step counting with local baseline sensor deltas, test simulator, and non-zero guarantee
  - [x] 8.6 `ContactsTool`: ContactsContract queries with multilingual relationship alias expansion (mom/maa/mummy, dad/papa/pitaji, bhai, didi, etc.)
  - [x] 8.7 `TelephonyTool`: unread SMS counter, SMS body search, missed call count, and call log history
  - [x] 8.8 `StorageStatsTool`: Knowledge Store media category counts (Images, Videos, Audio, Documents, Archives), size formatting, and superlative queries (largest, smallest, newest, oldest)
  - [x] 8.9 `TopicIsolationFilter`: strict R8 cross-topic contamination detection and sanitization
- [x] **Phase 9: Retrieval and Answering**
  - [x] 9.1 `IntentRouter`: typed outcomes (ANSWERABLE, NEEDS_CLARIFICATION, SMALL_TALK, OUT_OF_SCOPE, UNRECOGNISED)
  - [x] 9.2 Deterministic fast routing order (small talk -> profile -> clock vs DOB -> device -> doc)
  - [x] 9.3 `QueryNormalizer`: Hinglish colloquial phrase mapping and internal vocabulary spelling correction
  - [x] 9.4 Multi-stage intent routing ensemble with confidence scoring
  - [x] 9.5 `DocsyIntent`: comprehensive 20+ intent enum taxonomy
  - [x] 9.6 Collision resolution: "videos count" vs storage, media superlatives vs general device memory
  - [x] 9.7 `ConversationSessionContext`: multi-turn state tracking with pronoun and referent owner resolution
  - [x] 9.8 `DocumentRetriever`: hybrid lexical token matching, category filtering, and provenance rank priority
  - [x] 9.9 Strict lexical requirements on content words ("CAT", "exam", "12th")
  - [x] 9.10 `FieldResolver`: strict target slot resolution without hallucination
  - [x] 9.11 `NotFoundReplyGenerator`: transparent, empathetic not-found templates with exact missing subject and cause
  - [x] 9.12 `DocsyReply`: rich reply model supporting text, tables, file references, and FileProvider open intent
- [x] **Phase 10: Calculator and Higher-Level Reasoning**
  - [x] 10.1 `DocsyCalculator`: deterministic BigDecimal arithmetic with HALF_UP rounding and formula audit trail (sum, avg, diff, pct, age)
  - [x] 10.2 Marksheet percentage calculator with all subjects vs Best 5 subject filtering
  - [x] 10.3 Cross-document comparison of attributes and percentage changes
  - [x] 10.4 `CalculationCard`: composable rendering math steps, formula audit, and verified result
  - [x] 10.5 `ToolExecutionPipeline`: step-bounded multi-tool execution runner (max 3 calls) with execution history audit
- [x] **Phase 11: Language Model (Planner and Voice)**
  - [x] 11.1 `ToolPlan` and `ToolCallValidator`: grammar-constrained structured sequence validation
  - [x] 11.2 `LocalLlmManager`: local model tiers with 30-second idle memory unloading policy
  - [x] 11.3 Model roles: planning, proposal, and phrasing bounded by schema
  - [x] 11.4 `FactGroundingValidator`: anti-hallucination validation against fact snapshot
  - [x] 11.5 `DeterministicReplyComposer`: seamless deterministic fallback with lead-in/answer/next-step structure
- [x] **Phase 12: Human-Like Conversation and Reasoning Behaviour**
  - [x] 12.1 Fluent natural English: lead-in + direct answer + next step, markdown-lite
  - [x] 12.2 Warm, concise persona; Hinglish style mirroring when queried in Hinglish
  - [x] 12.3 Real time-of-day greetings (morning, afternoon, evening) based on system clock
  - [x] 12.4 Conversation intelligence: referent owner tracking, pronoun resolution, uncertainty admission
  - [x] 12.5 Scope boundaries: polite offline redirects for web queries
  - [x] 12.6 Slang & tone normalization; calm, helpful responses
  - [x] 12.7 Varied templates per reply type
- [x] **Phase 13: Chat History with Delete**
  - [x] 13.1 `ChatEntities` and `ChatDao`: Room chat storage excluded from backups
  - [x] 13.2 `ChatHistoryScreen`: searchable session list, timestamp formatting, resume chat
  - [x] 13.3 Delete chat: single-session deletion with 5s Snackbar Undo and Clear All dialog
  - [x] 13.4 Chat deletion isolation: documents and facts verified 100% untouched
  - [x] 13.5 New chat management and auto-titled sessions
  - [x] 13.6 In-chat delete confirmation modal
- [x] **Phase 14: UI, Animation and Layout**
  - [x] 14.1 `HomeScreen`: minimal monochrome Nothing-inspired M3 layout with profile avatar and slim reading strip
  - [x] 14.2 `ChatScreen`: fixed header, weighted message list, fixed input bar, imePadding, autoscroll
  - [x] 14.3 Modular answer cards: `CalculationCard`, `MaskedText`, disambiguation owner chips
  - [x] 14.4 `Motion.kt`: central animation tokens (120/200/320ms), spring specs, and animator duration scale check
  - [x] 14.5 Plain-words accessible language across UI
- [x] **Phase 15: Tests, Evaluation and Build Gates**
  - [x] 15.1 All fixtures and test datasets confined to `src/test/`
  - [x] 15.2 `Phase15EvaluationSuiteTest`: multi-intent classification and Hinglish query tests
  - [x] 15.3 `ExtractionBenchmark`: throughput and extraction benchmark harness
  - [x] 15.4 Build gates enforcement: intent accuracy >= 95%, zero hallucinations, monotonic counters
  - [x] 15.5 Build pipeline validated across Debug and Unit Test tasks
- [x] **Phase 16: Final Report & Real-Device Checklist**
  - [x] 16.1 `FINAL_REPORT.md`: complete verification audit and step-by-step real-device testing guide

---

## 2. Architecture Mapping & Gap Analysis

| Target Architecture Component | Target Responsibilities | Current Baseline Code | Gaps to Bridge |
|---|---|---|---|
| **1. Device Sources** | Storage files/folders (PDF, DOCX, XLSX, images, videos) & Live hardware state (battery, RAM, network, sensors, steps, telephony) | Android framework APIs (`BatteryManager`, `StorageManager`, `MediaStore`, `SensorManager`) | Standardized snapshot readers needed with 2-second live cache limit (R7) and single snapshot consistency. |
| **2.1 File Scanner** | Recursive root volume walker, hidden/messenger folder inclusion, MediaStore counts reconciliation, continuous `ContentObserver` | None (template Activity only) | Implement `StorageScanner`, file change observer, and category classifier registry in Phase 4. |
| **2.2 Format Conversion & OCR** | PDF OCR fallback via `PdfRenderer`, ZIP+XML Office parser, tiling for large images, bundled ML Kit Latin & Devanagari OCR, layout extraction | None | Implement `ExtractionPipeline`, bitmap tiling, image preprocessor, and layout bounding box preservation in Phase 5. |
| **2.3 Text Processing & Indexing** | Document classification (margin-based, negative evidence), synonym dictionary, Indian ID validator (Verhoeff), owner extraction, structured facts | None | Implement `DocumentClassifier`, `FactExtractor`, and `OwnerResolver` in Phase 7. |
| **2.4 Knowledge Store** | Room database (`FileEntity`, `FactEntity`, `FTS5` virtual table), content-hash deduplication, original path linkage | Room dependencies available | Implement SQLite schema, migrations, and FTS5 BM25 search indices in Phase 7. |
| **3. Chat & Reasoning Engine** | Fast deterministic router (S1/S2/S3), slot resolution, BigDecimal calculator, tool executor, offline LLM planner, human-like grounded voice | None | Implement `IntentRouter`, `DocsyCalculator`, `ToolExecutionPipeline`, and `ChatRepository` in Phases 8-13. |
| **UI & Experience** | Minimal monochrome M3 UI, App Lock (PIN/Biometric), full-screen Reading page, chat stream, history with undo | Default template Scaffold | Implement Compose screens, `Motion.kt`, and custom components in Phases 2, 3, 6, 14. |

---

## 3. Root Cause Failure Analysis for Reported Queries (0.4 & 0.5)

### 3.1 Failing Document Queries: "12th marksheet.pdf" & CAT Exam PDF (Item 0.4)
1. **Query**: `"12th roll number"`, `"my 12th"` on `12th marksheet.pdf`
   - **Root Cause 1 (Extraction - The Tiny Text Layer Trap)**: Official marksheets (CBSE, state boards, DigiLocker downloads) often have an invisible or minimal embedded vector text layer containing metadata like "DigiLocker Verified" or header fonts, but the student table and marks are flattened bitmap scans. Naive PDF extractors check `if (extractedText.isNotBlank()) return extractedText` and skip OCR. Because the text layer only contained 20-50 non-content characters, the actual roll number and subject marks were never OCR'd.
   - **Root Cause 2 (Classification Failure)**: If a CV or resume also mentions "12th" and "CBSE", rule-based extractors without negative evidence (e.g. absence of "Curriculum Vitae", presence of "STATEMENT OF MARKS" and subject tabular grid) falsely match the CV or fail with ambiguous margin.
   - **Root Cause 3 (Retrieval Field Filter)**: The query asks for `"12th roll number"`. If the retriever returns the document but the fact extractor failed to bind the roll number due to tabular column misalignment, the system fails to extract the exact requested field.
   - **Prevention in Docsy v1.0.6**:
     - *Dual-Layer Extraction*: In `PdfExtractor`, calculate characters per page and dictionary-hit rate. If page text density is low or words are fragmented, always rasterize via `PdfRenderer` at high DPI (300 DPI) and run multi-pass OCR (Phase 5.2).
     - *No Page Cap*: OCR every page of the document without arbitrary limits.
     - *Fact Provenance*: Extract `roll_number` into the structured facts table with bounding box coordinates (Phase 7.4).
2. **Query**: `"cat exam"` on CAT Exam PDF
   - **Root Cause 1 (Lexical Constraint Failure)**: The document text contains "CAT" (all caps) and "Common Admission Test". Overly aggressive stemming or stopword filters either strip "cat" or confuse it with the animal "cat".
   - **Root Cause 2 (Router Defaulting)**: The query `"cat exam"` contains no explicit document noun (like "pdf" or "file"). In systems without an explicit intent router, `"cat exam"` either routes to web search/out-of-scope or falls through to small-talk without invoking document search.
   - **Prevention in Docsy v1.0.6**:
     - Lexical synonym expansion: "CAT" maps to "Common Admission Test", "IIM", admit card, exam schedule.
     - Lexical hard constraint: Search requires the presence of content tokens or known synonyms in the document's extracted text (Phase 9.9).

---

### 3.2 Failing Router Queries & RouterTrace Analysis (Item 0.5)

| Query | Expected Intent / Target | Failure Mechanism in Flawed System | Fixed Routing Behavior in Docsy v1.0.6 |
|---|---|---|---|
| **"videos count" vs "videos total"** | `FILE_COUNT(type=VIDEO)` | Collides with `STORAGE_QUERY` due to generic "total" token matching internal storage size instead of file quantity. | Phase 9.6 collision resolution: `<file type> count / total / number` routes strictly to `FILE_COUNT` with media count live from MediaStore. Storage queries strictly require "storage", "space", "free", or "GB" cues. |
| **"musics on my device"** | `MEDIA_COUNT(type=AUDIO)` or `DEVICE_STATS(type=AUDIO)` | Router tokenizer fails on non-standard plural noun "musics" (common Hinglish/ESL colloquialism). | Phase 9.3 query normalizer: Hinglish & plural synonym dictionary maps "musics", "songs", "gaane", "audio" to canonical `AUDIO` category. |
| **"phone number of mom"** | `CONTACT_LOOKUP(relation="mom")` | Router fails to resolve relationship alias "mom" to contact query, or routes to document search for "mom". | Phase 8.10 & 9.5: Phone/number/contact with relationship aliases (mom, mummy, maa, papa, dad, bhai, didi) routes directly to `CONTACT_LOOKUP`. |
| **"wifi?"** | `DEVICE_INFO(subtopic=NETWORK)` | Bare noun with question mark ("wifi?") is missed by pattern matchers and drops into document search or UNRECOGNISED. | Phase 9.4: S1 weighted topic lexicon intercepts bare hardware nouns immediately and routes to live network snapshot without internet access. |
| **"bluetooth"** | `DEVICE_INFO(subtopic=BLUETOOTH)` | System either returns empty or violates Rule R8 by dumping general device info (battery, model) instead of scoped Bluetooth status. | Phase 8.4 & 9.4: Returns strictly Bluetooth state ("Bluetooth is off.") using single-topic snapshot. |
| **"my 12th percentage"** | `CALCULATION` chained from `FIND_DOCUMENT_INFO(class=12)` | Document search returns the marksheet, but the system either extracts no percentage (because it's not pre-printed) or attempts to hallucinate a number. | Phase 10.2: Marksheet percentage calculator collects verified subject marks and calculates exact arithmetic (`<sum> / <max> = <pct>%`) with separate "best five" and "all subjects" lines. |
| **"hi"** | `SMALL_TALK(greeting)` | Greeting is fed to document search, which produces a confusing "Document not found" card (violating Rule 9.1). | Phase 9.1 & 9.2: Fast deterministic router routes greetings to `SMALL_TALK` first, returning a friendly time-of-day greeting with capabilities and zero source cards. |

**Embedding Model & Router Precedence Audit**:
- In the clean starter project, no sentence-embedding model is currently bundled.
- In Phase 9 & 11, the architecture guarantees that the fast deterministic router and S1 topic lexicon run **first** on every query. Document search is never the default sink and only runs when an explicit document intent or document identity is recognized.

---

## 4. Phase-by-Phase Execution Plan & Risks

### Phase 1: Build, Signing, Version, Permissions, Installability
- **Tasks**:
  1. Update `app/build.gradle.kts`: `applicationId = "com.suhrud.docsy"`, `namespace = "com.suhrud.docsy"`, `versionName = "1.0.6"`, `versionCode = 2`, `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`.
  2. Remove custom `debugConfig` block; use default debug signing.
  3. Add strict release signing block reading `keystore.properties` (fails build if missing in release mode).
  4. Strip Firebase, Retrofit, OkHttp, and Moshi dependencies; retain Room, Compose, Coroutines, Lifecycle, AndroidX Core.
  5. Audit `AndroidManifest.xml`: remove INTERNET, declare storage, media, telephony, biometric, Bluetooth, notification, and network state permissions with clear rationale comments.
  6. Add product flavors: `full`, `noSmsCalls`, `filesOnly`.
  7. Generate `INSTALL.md`.
- **Risks & Mitigation**: Android 14+ foreground service types requirement (`dataSync`, `health`). Mitigation: declare appropriate service types and permission checks.

### Phase 2: First Launch, Onboarding, Profile, Avatars, Settings
- **Tasks**:
  1. Build onboarding state machine in DataStore (`stage: WELCOME -> NAME -> AVATAR -> INSTRUCTIONS -> PERMISSIONS -> READING -> COMPLETED`).
  2. Implement `UserProfileRepository` (name validation, short/numeric names flagged `UNMATCHABLE`).
  3. Create high-resolution WebP avatar assets in `res/drawable-nodpi/` and `AvatarCatalog`.
  4. Create `ProfileAvatar` shared composable and 4-column `AvatarPickerBottomSheet`.
  5. Build 5-step instruction screens with subtle outline/glow demonstrations.
  6. Build permission rationale flow and Settings screen with all specified rows.
- **Risks & Mitigation**: Process death during onboarding. Mitigation: persistent stage saved in DataStore after each step.

### Phase 3: App Lock (PIN and Fingerprint)
- **Tasks**:
  1. Implement `PinSecurityManager` (PBKDF2 salted hash in Keystore-backed EncryptedSharedPreferences).
  2. Implement `BiometricAuthManager` using `BiometricPrompt` with device credential fallback on API 30+.
  3. Build `LockScreenOverlay` appearing before any UI content; enforce `FLAG_SECURE`.
  4. Integrate timeout tracking (Immediately, 1m, 5m, 15m) in `AppLockLifecycleManager`.

### Phase 4: File Scanner
- **Tasks**:
  1. Implement `StorageScanner` walking root volumes recursively.
  2. Include hidden and messenger folders, bypass `.nomedia`, skip system cache/temp.
  3. Implement `FileCategoryRegistry` for unified categorization.
  4. Integrate `StorageContentObserver` for continuous background file change tracking.
  5. Implement `MediaCountReconciler` querying MediaStore live counts.

### Phase 5: Format Conversion and OCR
- **Tasks**:
  1. Build `ExtractionStateMachine` (PENDING -> EXTRACTED / EMPTY / UNSUPPORTED / ENCRYPTED / PARTIAL / FAILED).
  2. Implement extractors: `PdfExtractor` (with high-DPI `PdfRenderer` + OCR fallback), `OfficeXmlExtractor` (pure streaming Zip+XML), `PlainTextExtractor`, and `ImageExtractor`.
  3. Implement bitmap tiling for bounded memory usage on large images.
  4. Integrate bundled ML Kit Text Recognition (Latin + Devanagari).
  5. Build geometric layout reconstructor for tables and key-value pairs.
  6. Build `TelephonyIngestor` for SMS and Call Log.
  7. Build debug-only `ExtractionInspectorScreen`.

### Phase 6: Live Reading Page, 24h Auto Refresh, Force Read
- **Tasks**:
  1. Build full-screen `ReadingProgressScreen` with monotonic progress bar, dynamic M count, per-type tallies, and ETA.
  2. Implement `ReadingStateRepository` emitting StateFlow to UI and Foreground Service notification.
  3. Implement `ScheduledSyncWorker` in WorkManager scheduled for every 24 hours.
  4. Build Force Read dialog with "Quick refresh" and "Full re-read" options.

### Phase 7: Document Understanding and Knowledge Store
- **Tasks**:
  1. Implement whole-document classification with negative evidence and top-1 vs top-2 margin.
  2. Implement `GovernmentIdDetector` with algorithmic checksums (Aadhaar Verhoeff, PAN, EPIC, Passport, DL).
  3. Create Room schema: `FileEntity`, `FactEntity`, and FTS5 virtual table.
  4. Implement `OwnerExtractor` and `NameMatcher` with Jaro-Winkler token matching.
  5. Implement multi-candidate owner disambiguation chips.

### Phase 8: Device and System Tools
- **Tasks**:
  1. Implement snapshot-based tools: `DateTimeTool`, `BatteryTool`, `NetworkTool`, `HardwareInfoTool`, `DisplayTool`, `MemoryTool`, `AppManagerTool`.
  2. Implement `StepCounterTool` with `StepDiagnosticsScreen` (Health Connect -> local snapshot delta -> boot fallback; zero-format guard).
  3. Implement `ContactsTool` with relationship aliases.
  4. Enforce strict topic isolation (Rule R8).

### Phase 9: Retrieval and Answering
- **Tasks**:
  1. Implement `IntentRouter` with explicit enum outcomes (no default sink).
  2. Implement `QueryNormalizer` with Hinglish translation and colloquial mapping.
  3. Implement router ensemble: S1 weighted lexicon, S2 n-gram classifier, S3 semantic prototypes.
  4. Implement hybrid FTS5 BM25 retrieval with lexical content constraints.
  5. Implement `FieldResolver` and grounded not-found generators.

### Phase 10: Calculator and Higher-Level Reasoning
- **Tasks**:
  1. Implement `DocsyCalculator` using BigDecimal half-up rounding.
  2. Implement marksheet percentage calculator (best five vs all subjects).
  3. Build `CalculationCard` Compose UI.
  4. Build sequential tool execution pipeline (up to 3 steps).

### Phase 11: Language Model (Planner and Voice)
- **Tasks**:
  1. Implement grammar-constrained structured output tool planner.
  2. Implement model tiers: Bundled small tier and Enhanced local file tier.
  3. Implement `FactGroundingValidator` rejecting ungrounded claims.
  4. Implement transparent fallback to deterministic composer.

### Phase 12: Human-Like Conversation & Reasoning Behaviour
- **Tasks**:
  1. Build conversational persona with varied sentence structures (12+ templates per reply type).
  2. Implement time-of-day greetings and full-capability onboarding introduction.
  3. Implement tone-based parsing for slang and profanity.

### Phase 13: Chat History with Delete
- **Tasks**:
  1. Implement encrypted Room chat database (`ChatDatabase`) with Android Keystore AES-GCM.
  2. Build `ChatHistoryScreen` with auto-titles, search, and swipe-to-delete with 5s Undo.
  3. Ensure chat deletion never touches Knowledge Store.

### Phase 14: UI, Animation and Layout
- **Tasks**:
  1. Implement minimal monochrome Nothing-inspired M3 layout for Home and Chat.
  2. Build `Motion.kt` centralizing animation tokens (120ms/200ms/320ms, springs).
  3. Audit UI for zero technical jargon.

### Phase 15: Tests, Evaluation and Build Gates
- **Tasks**:
  1. Build 600+ query held-out evaluation test suite in `src/test/`.
  2. Implement retrieval recall and OCR distortion benchmarks.
  3. Enforce build gates: in-scope accuracy >= 95%, zero hallucinations, monotonic counters.

### Phase 16: Final Report & Real-Device Checklist
- **Tasks**:
  1. Compile final metrics, benchmark tables, memory footprints, and asset sizes into `AUDIT.md`.
  2. Deliver comprehensive real-device verification checklist.
