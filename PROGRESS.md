# Progress Report - Layout Model & Table Extraction

## Status
- **Status:** Completed & Verified
- **Database Migration:** Created Room version 6 migration adding `doc_blocks` and `doc_tables` tables.
- **Preprocessing:** Added deskew, denoise, and upscale for small text with no dimension downsampling cap in `ImagePreprocessor.kt`.
- **Layout Model Analyzer:** Implemented `LayoutModelAnalyzer` for top-left to bottom-right reading order, key-value pair detection ("Key: Value"), and row/column clustering for tables.
- **Pipeline Integration:** `FileScanner` persists `doc_blocks` and `doc_tables` inside transactions and keeps `lines` and `lines_fts` populated from ordered text.
- **Debug UI:** Integrated `layout_debug_view` tabbed dialog in `ExtractionInspectorScreen.kt`.
- **Test:** Created `SyntheticTableLayoutTest.kt` in test sources.

## Next Steps
1. Perform device verification of layout block extraction on physical test files.
