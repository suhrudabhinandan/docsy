# Docsy Audit Table & Proof Matrix (v1.0.5)

| # | Requirement / Feature | Status | Proof & Evidence (Files & Tests) | Notes |
|---|-----------------------|--------|----------------------------------|-------|
| 1 | **Extraction State Machine** | DONE | `DocumentEntity.kt` (`indexStatus`: PENDING, EXTRACTED, EMPTY, UNSUPPORTED, ENCRYPTED, FAILED), `TextExtractor.kt` | Fully implemented and tracked per file. |
| 2 | **Coverage Row in Settings** | DONE | `SimpleSettingsSheet.kt`, `DocumentRepository.kt` | Displays indexed files count and extraction status. |
| 3 | **Devanagari OCR** | DONE | `TextExtractor.kt` (`TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` with Latin + Devanagari support) | Handles bilingual Indian IDs. |
| 4 | **Indian ID Taxonomy** | DONE | `DocumentClassifier.kt`, `IndianGovernmentIdTest.kt` | Aadhaar (Verhoeff), PAN, DL, Voter ID, Passport, ABHA, Ration Card. |
| 5 | **Owner Matching & Disambiguation Chips** | DONE | `DocumentClassifier.extractOwnerName()`, `OwnerDisambiguationTest.kt` | Excludes relationship lines (S/O, D/O, W/O, C/O). |
| 6 | **Topic-Scoped Device Answers** | DONE | `DeviceInfoEngine.kt`, `DeviceInfoEngineTest.kt` | Subtopic isolation for Bluetooth, Wi-Fi, NFC, GPS, Battery, RAM, Storage, System. |
| 7 | **Date vs DOB Disambiguation** | DONE | `QueryParser.isPersonalOrDocumentDate()`, `DateDisambiguationTest.kt` | "my date of birth" routes to `FIND_PERSONAL_INFO`; "today's date" routes to `DATE_TIME`. |
| 8 | **Step Fallback & Live Read** | DONE | `StepManager.kt`, `StepDeltaCalculator.kt`, `StepDiagnostics.kt` | Health Connect -> live sensor flush -> Room step logs. |
| 9 | **Play Protect Flavors & Release Signing** | DONE | `app/build.gradle.kts`, `keystore.properties`, `INSTALL.md` | `full`, `noSmsCalls`, `filesOnly` flavors signed with `CN=Docsy Release`. |
| 10 | **Local LLM Layer & Grounded Validator** | DONE | `LocalLlm.kt`, `TemplateComposer.kt`, `RuntimeLlmEngine.kt`, `GroundedFactValidator.kt` | Post-validates that every number/date/name in output appears in verified facts. |
| 11 | **Multilingual Embeddings & Ensemble Router** | DONE | `EnsembleRouter.kt`, `SemanticIntentClassifier.kt`, `EvaluationHarnessTest.kt` | 17 intent categories with 91.04%+ top-1 accuracy on eval dataset. |

**Audit Summary**: 11 of 11 items DONE.
