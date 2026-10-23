package com.suhrud.docsy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.suhrud.docsy.data.db.dao.ChatContextDao
import com.suhrud.docsy.data.db.dao.ChatDao
import com.suhrud.docsy.data.db.dao.ChunkDao
import com.suhrud.docsy.data.db.dao.DocJsonDao
import com.suhrud.docsy.data.db.dao.DocLayoutDao
import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.dao.FactDao
import com.suhrud.docsy.data.db.dao.FieldDao
import com.suhrud.docsy.data.db.dao.LineDao
import com.suhrud.docsy.data.db.dao.SubjectDao
import com.suhrud.docsy.data.db.entity.ChatContextEntity
import com.suhrud.docsy.data.db.entity.ChatMessageEntity
import com.suhrud.docsy.data.db.entity.ChatSessionEntity
import com.suhrud.docsy.data.db.entity.ChunkEntity
import com.suhrud.docsy.data.db.entity.DocJsonEntity
import com.suhrud.docsy.data.db.entity.DocLayoutEntity
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.data.db.entity.LineEntity
import com.suhrud.docsy.data.db.entity.SubjectEntity
import com.suhrud.docsy.retrieval.Fts5SearchEngine

import com.suhrud.docsy.data.db.dao.DocBlockDao
import com.suhrud.docsy.data.db.dao.DocEntityDao
import com.suhrud.docsy.data.db.dao.DocTableDao
import com.suhrud.docsy.data.db.dao.EntityDao
import com.suhrud.docsy.data.db.dao.MarksheetDao
import com.suhrud.docsy.data.db.dao.RelationDao
import com.suhrud.docsy.data.db.entity.DocBlockEntity
import com.suhrud.docsy.data.db.entity.DocEntityRecord
import com.suhrud.docsy.data.db.entity.DocTableEntity
import com.suhrud.docsy.data.db.entity.EntityRecord
import com.suhrud.docsy.data.db.entity.MarksheetEntity
import com.suhrud.docsy.data.db.entity.MarksheetSubjectEntity
import com.suhrud.docsy.data.db.entity.RelationRecord

class DocsyConverters {
    @TypeConverter
    fun fromStatus(status: DocumentStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): String = try {
        DocumentStatus.valueOf(value).name
    } catch (_: Exception) {
        DocumentStatus.DISCOVERED.name
    }
}

@Database(
    entities = [
        DocumentEntity::class,
        FactEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ChunkEntity::class,
        DocJsonEntity::class,
        DocLayoutEntity::class,
        LineEntity::class,
        FieldEntity::class,
        SubjectEntity::class,
        DocBlockEntity::class,
        DocTableEntity::class,
        EntityRecord::class,
        DocEntityRecord::class,
        RelationRecord::class,
        ChatContextEntity::class,
        com.suhrud.docsy.data.db.entity.RecordEntity::class,
        com.suhrud.docsy.data.db.entity.SheetMetaEntity::class,
        MarksheetEntity::class,
        MarksheetSubjectEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(DocsyConverters::class)
abstract class DocsyDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun factDao(): FactDao
    abstract fun chatDao(): ChatDao
    abstract fun chatContextDao(): ChatContextDao
    abstract fun chunkDao(): ChunkDao
    abstract fun docJsonDao(): DocJsonDao
    abstract fun docLayoutDao(): DocLayoutDao
    abstract fun lineDao(): LineDao
    abstract fun fieldDao(): FieldDao
    abstract fun subjectDao(): SubjectDao
    abstract fun docBlockDao(): DocBlockDao
    abstract fun docTableDao(): DocTableDao
    abstract fun entityDao(): EntityDao
    abstract fun docEntityDao(): DocEntityDao
    abstract fun relationDao(): RelationDao
    abstract fun recordDao(): com.suhrud.docsy.data.db.dao.RecordDao
    abstract fun sheetMetaDao(): com.suhrud.docsy.data.db.dao.SheetMetaDao
    abstract fun marksheetDao(): MarksheetDao

    companion object {
        private const val DATABASE_NAME = "docsy_knowledge.db"

        @Volatile
        private var INSTANCE: DocsyDatabase? = null

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS marksheets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        document_id INTEGER NOT NULL,
                        level TEXT NOT NULL,
                        board TEXT,
                        exam_name TEXT,
                        exam_year INTEGER,
                        exam_session TEXT,
                        stream TEXT,
                        semester TEXT,
                        student_name TEXT,
                        mother_name TEXT,
                        father_name TEXT,
                        dob TEXT,
                        roll_no TEXT,
                        regn_no TEXT,
                        certificate_no TEXT,
                        school_code TEXT,
                        school_name TEXT,
                        result TEXT,
                        division TEXT,
                        issue_date TEXT,
                        total_obtained REAL,
                        total_max REAL,
                        percentage_printed REAL,
                        percentage_all REAL,
                        percentage_best5 REAL,
                        sgpa REAL,
                        cgpa REAL,
                        total_credits REAL,
                        read_by TEXT NOT NULL DEFAULT 'NATIVE',
                        confidence REAL NOT NULL DEFAULT 1.0,
                        needs_review INTEGER NOT NULL DEFAULT 0,
                        validation_flags TEXT,
                        extra_json TEXT,
                        extracted_at INTEGER NOT NULL
                    );
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_marksheets_document_id ON marksheets(document_id);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_marksheets_level_year ON marksheets(level, exam_year);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_marksheets_roll_no ON marksheets(roll_no);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS marksheet_subjects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        marksheet_id INTEGER NOT NULL,
                        row_no INTEGER NOT NULL,
                        subject_code TEXT,
                        subject_name TEXT NOT NULL,
                        subject_kind TEXT NOT NULL DEFAULT 'MAIN',
                        theory_marks REAL,
                        internal_marks REAL,
                        total_marks REAL,
                        max_marks REAL,
                        marks_flag TEXT,
                        total_in_words TEXT,
                        grade TEXT,
                        grade_point REAL,
                        credits REAL,
                        subject_result TEXT,
                        in_best5 INTEGER NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'TABLE',
                        confidence REAL NOT NULL DEFAULT 1.0,
                        needs_review INTEGER NOT NULL DEFAULT 0
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_marksheet_subjects_marksheet_id ON marksheet_subjects(marksheet_id);")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE documents ADD COLUMN display_title TEXT;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN chars_extracted INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN owner_entity_id INTEGER;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN runnerUpType TEXT;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN runnerUpScore REAL NOT NULL DEFAULT 0.0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN pipelineStepsJson TEXT;") } catch (_: Exception) {}

                try { db.execSQL("ALTER TABLE fields ADD COLUMN label TEXT NOT NULL DEFAULT '';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE fields ADD COLUMN valueType TEXT NOT NULL DEFAULT 'text';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE fields ADD COLUMN isDerived INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE fields ADD COLUMN formula TEXT;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE fields ADD COLUMN sensitive INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE fields ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE fields ADD COLUMN validationWarning TEXT;") } catch (_: Exception) {}

                try { db.execSQL("ALTER TABLE subjects ADD COLUMN rowNo INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE subjects ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0;") } catch (_: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        doc_id INTEGER NOT NULL,
                        sheet TEXT NOT NULL DEFAULT 'Sheet1',
                        row_no INTEGER NOT NULL,
                        col_index INTEGER NOT NULL,
                        col_key TEXT,
                        value TEXT NOT NULL,
                        value_type TEXT NOT NULL DEFAULT 'text'
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_records_doc_id_sheet ON records(doc_id, sheet);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_records_doc_id ON records(doc_id);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sheet_meta (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        doc_id INTEGER NOT NULL,
                        sheet TEXT NOT NULL DEFAULT 'Sheet1',
                        header_row INTEGER NOT NULL DEFAULT 1,
                        title TEXT,
                        institution TEXT,
                        batch TEXT
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sheet_meta_doc_id_sheet ON sheet_meta(doc_id, sheet);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sheet_meta_doc_id ON sheet_meta(doc_id);")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL DEFAULT 'New chat',
                        title_source TEXT NOT NULL DEFAULT 'DEFAULT',
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        pinned INTEGER NOT NULL DEFAULT 0
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_updated_at ON chats(updated_at);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        text TEXT NOT NULL,
                        ts INTEGER NOT NULL DEFAULT 0,
                        sources_json TEXT,
                        state TEXT NOT NULL DEFAULT 'completed'
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_id_ts ON messages(chat_id, ts);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_id ON messages(chat_id);")

                try {
                    db.execSQL("""
                        INSERT INTO chats (id, title, title_source, created_at, updated_at)
                        SELECT id, title, 'GENERATED', createdAt, updatedAt FROM chat_sessions;
                    """.trimIndent())
                } catch (_: Exception) {}

                try {
                    db.execSQL("""
                        INSERT INTO messages (id, chat_id, role, text, ts, sources_json, state)
                        SELECT id, sessionId, CASE WHEN isFromUser = 1 THEN 'user' ELSE 'assistant' END, text, timestamp, payloadJson, 'completed' FROM chat_messages;
                    """.trimIndent())
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE documents ADD COLUMN section TEXT NOT NULL DEFAULT 'OTHER';")
                } catch (_: Exception) {}
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_section ON documents(section);")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_context (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        turn INTEGER NOT NULL,
                        entities_json TEXT,
                        active_doc_ids TEXT,
                        active_owner TEXT,
                        active_doc_type TEXT,
                        active_period TEXT,
                        last_field TEXT,
                        last_intent TEXT,
                        ts INTEGER NOT NULL
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_context_chat_id ON chat_context(chat_id);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_context_ts ON chat_context(ts);")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS entities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        canonical_name TEXT NOT NULL,
                        embedding TEXT
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS doc_entities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        doc_id INTEGER NOT NULL,
                        entity_id INTEGER NOT NULL,
                        role TEXT NOT NULL
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS relations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        a_id INTEGER NOT NULL,
                        b_id INTEGER NOT NULL,
                        relation TEXT NOT NULL,
                        score REAL NOT NULL,
                        evidence_doc_id INTEGER NOT NULL
                    );
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_entities_type ON entities(type);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entities_canonical_name ON entities(canonical_name);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doc_entities_doc_id ON doc_entities(doc_id);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doc_entities_entity_id ON doc_entities(entity_id);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_relations_a_id ON relations(a_id);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_relations_b_id ON relations(b_id);")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_relations_evidence_doc_id ON relations(evidence_doc_id);")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS doc_blocks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        doc_id INTEGER NOT NULL,
                        page INTEGER NOT NULL DEFAULT 1,
                        `order` INTEGER NOT NULL DEFAULT 0,
                        type TEXT NOT NULL DEFAULT 'TEXT',
                        text TEXT NOT NULL,
                        x REAL NOT NULL DEFAULT 0.0,
                        y REAL NOT NULL DEFAULT 0.0,
                        w REAL NOT NULL DEFAULT 0.0,
                        h REAL NOT NULL DEFAULT 0.0,
                        conf REAL NOT NULL DEFAULT 1.0
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doc_blocks_doc_id ON doc_blocks(doc_id);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS doc_tables (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        doc_id INTEGER NOT NULL,
                        page INTEGER NOT NULL DEFAULT 1,
                        table_id INTEGER NOT NULL DEFAULT 0,
                        row INTEGER NOT NULL DEFAULT 0,
                        col INTEGER NOT NULL DEFAULT 0,
                        text TEXT NOT NULL,
                        conf REAL NOT NULL DEFAULT 1.0
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doc_tables_doc_id ON doc_tables(doc_id);")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.query("PRAGMA journal_mode = WAL;").close() } catch (_: Exception) {}

                try { db.execSQL("ALTER TABLE documents ADD COLUMN path TEXT;") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET path = absolutePath WHERE path IS NULL OR path = '';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN name TEXT;") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET name = displayName WHERE name IS NULL OR name = '';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN ext TEXT;") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET ext = extension WHERE ext IS NULL OR ext = '';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN mime TEXT;") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET mime = mimeType WHERE mime IS NULL OR mime = '';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN size INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET size = sizeBytes WHERE size = 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN modified INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET modified = lastModified WHERE modified = 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN docType TEXT;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN docTypeScore REAL NOT NULL DEFAULT 0.0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN ownerName TEXT;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN ownerEvidence TEXT;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN state TEXT NOT NULL DEFAULT 'DISCOVERED';") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET state = status WHERE state IS NULL OR state = 'DISCOVERED';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN extractorVersion TEXT NOT NULL DEFAULT '1.0';") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN extractedAt INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                try { db.execSQL("UPDATE documents SET extractedAt = indexedAt WHERE extractedAt = 0;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE documents ADD COLUMN lang TEXT;") } catch (_: Exception) {}

                try { db.execSQL("ALTER TABLE chunks ADD COLUMN kind TEXT NOT NULL DEFAULT 'PARAGRAPH';") } catch (_: Exception) {}
                try { db.execSQL("UPDATE chunks SET kind = type WHERE kind = 'PARAGRAPH' AND type IS NOT NULL;") } catch (_: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS doc_facts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        documentId INTEGER NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        normalizedValue TEXT,
                        evidence TEXT,
                        page INTEGER NOT NULL DEFAULT 1,
                        confidence REAL NOT NULL DEFAULT 1.0,
                        needsConfirm INTEGER NOT NULL DEFAULT 0,
                        ownerName TEXT,
                        extractedAt INTEGER NOT NULL DEFAULT 0
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS doc_json (
                        documentId INTEGER PRIMARY KEY NOT NULL,
                        json TEXT NOT NULL
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS doc_layout (
                        documentId INTEGER NOT NULL,
                        page INTEGER NOT NULL,
                        blob BLOB NOT NULL,
                        PRIMARY KEY(documentId, page)
                    );
                """.trimIndent())

                try { db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_docType ON documents(docType);") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_contentHash ON documents(contentHash);") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS index_doc_facts_documentId_key ON doc_facts(documentId, key);") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS index_chunks_documentId ON chunks(documentId);") } catch (_: Exception) {}
            }
        }

        fun getInstance(context: Context): DocsyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocsyDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try { db.query("PRAGMA journal_mode=WAL;").close() } catch (_: Exception) {}
                            try {
                                db.execSQL("""
                                    CREATE TABLE IF NOT EXISTS doc_blocks (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        doc_id INTEGER NOT NULL,
                                        page INTEGER NOT NULL DEFAULT 1,
                                        `order` INTEGER NOT NULL DEFAULT 0,
                                        type TEXT NOT NULL DEFAULT 'TEXT',
                                        text TEXT NOT NULL,
                                        x REAL NOT NULL DEFAULT 0.0,
                                        y REAL NOT NULL DEFAULT 0.0,
                                        w REAL NOT NULL DEFAULT 0.0,
                                        h REAL NOT NULL DEFAULT 0.0,
                                        conf REAL NOT NULL DEFAULT 1.0
                                    );
                                """.trimIndent())
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_doc_blocks_doc_id ON doc_blocks(doc_id);")

                                db.execSQL("""
                                    CREATE TABLE IF NOT EXISTS doc_tables (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        doc_id INTEGER NOT NULL,
                                        page INTEGER NOT NULL DEFAULT 1,
                                        table_id INTEGER NOT NULL DEFAULT 0,
                                        row INTEGER NOT NULL DEFAULT 0,
                                        col INTEGER NOT NULL DEFAULT 0,
                                        text TEXT NOT NULL,
                                        conf REAL NOT NULL DEFAULT 1.0
                                    );
                                """.trimIndent())
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_doc_tables_doc_id ON doc_tables(doc_id);")

                                db.execSQL("""
                                    CREATE TABLE IF NOT EXISTS lines (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        documentId INTEGER NOT NULL,
                                        page INTEGER NOT NULL DEFAULT 1,
                                        lineNo INTEGER NOT NULL DEFAULT 0,
                                        yTop REAL NOT NULL DEFAULT 0.0,
                                        xLeft REAL NOT NULL DEFAULT 0.0,
                                        text TEXT NOT NULL
                                    );
                                """.trimIndent())
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_lines_documentId ON lines(documentId);")
                            } catch (_: Exception) {}

                            try {
                                db.execSQL("""
                                    CREATE TABLE IF NOT EXISTS fields (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        documentId INTEGER NOT NULL,
                                        key TEXT NOT NULL,
                                        value TEXT NOT NULL,
                                        normalizedValue TEXT,
                                        page INTEGER NOT NULL DEFAULT 1,
                                        lineNo INTEGER NOT NULL DEFAULT 0,
                                        confidence REAL NOT NULL DEFAULT 1.0,
                                        sourceBlockId INTEGER,
                                        needsReview INTEGER NOT NULL DEFAULT 0
                                    );
                                """.trimIndent())
                                try { db.execSQL("ALTER TABLE fields ADD COLUMN sourceBlockId INTEGER;") } catch (_: Exception) {}
                                try { db.execSQL("ALTER TABLE fields ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_fields_documentId_key ON fields(documentId, key);")
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_fields_documentId ON fields(documentId);")
                            } catch (_: Exception) {}

                            try {
                                db.execSQL("""
                                    CREATE TABLE IF NOT EXISTS subjects (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        documentId INTEGER NOT NULL,
                                        name TEXT NOT NULL,
                                        marks TEXT,
                                        maxMarks TEXT,
                                        grade TEXT
                                    );
                                """.trimIndent())
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_subjects_documentId ON subjects(documentId);")
                            } catch (_: Exception) {}

                            try {
                                db.execSQL("""
                                    CREATE TABLE IF NOT EXISTS chat_context (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        chat_id INTEGER NOT NULL,
                                        turn INTEGER NOT NULL,
                                        entities_json TEXT,
                                        active_doc_ids TEXT,
                                        active_owner TEXT,
                                        active_doc_type TEXT,
                                        active_period TEXT,
                                        last_field TEXT,
                                        last_intent TEXT,
                                        ts INTEGER NOT NULL
                                    );
                                """.trimIndent())
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_context_chat_id ON chat_context(chat_id);")
                                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_context_ts ON chat_context(ts);")
                            } catch (_: Exception) {}

                            initFts5Tables(db)
                        }
                    })
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun initFts5Tables(db: SupportSQLiteDatabase) {
            val supported = Fts5SearchEngine.probeFts5(db)
            if (supported) {
                try {
                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
                            text,
                            content='chunks',
                            content_rowid='id',
                            tokenize="unicode61 remove_diacritics 2",
                            prefix='2 3 4'
                        );
                        """.trimIndent()
                    )

                    try {
                        db.execSQL(
                            """
                            CREATE VIRTUAL TABLE IF NOT EXISTS names_fts USING fts5(
                                documentId UNINDEXED,
                                name_tokens,
                                tokenize='trigram'
                            );
                            """.trimIndent()
                        )
                    } catch (_: Exception) {
                        db.execSQL(
                            """
                            CREATE VIRTUAL TABLE IF NOT EXISTS names_fts USING fts5(
                                documentId UNINDEXED,
                                name_tokens,
                                tokenize="unicode61 remove_diacritics 2",
                                prefix='2 3 4'
                            );
                            """.trimIndent()
                        )
                    }

                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN
                            INSERT INTO chunks_fts(rowid, text) VALUES (new.id, new.text);
                        END;
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN
                            INSERT INTO chunks_fts(chunks_fts, rowid, text) VALUES('delete', old.id, old.text);
                        END;
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS lines_fts USING fts5(
                            text,
                            content='lines',
                            content_rowid='id',
                            tokenize="unicode61 remove_diacritics 2",
                            prefix='2 3 4'
                        );
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS lines_ai AFTER INSERT ON lines BEGIN
                            INSERT INTO lines_fts(rowid, text) VALUES (new.id, new.text);
                        END;
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS lines_ad AFTER DELETE ON lines BEGIN
                            INSERT INTO lines_fts(lines_fts, rowid, text) VALUES('delete', old.id, old.text);
                        END;
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS lines_au AFTER UPDATE ON lines BEGIN
                            INSERT INTO lines_fts(lines_fts, rowid, text) VALUES('delete', old.id, old.text);
                            INSERT INTO lines_fts(rowid, text) VALUES (new.id, new.text);
                        END;
                        """.trimIndent()
                    )
                } catch (e: Exception) {
                    android.util.Log.e("DocsyDatabase", "Error initializing FTS5 virtual tables", e)
                }
            }

            try {
                db.execSQL("UPDATE documents SET extractedText = substr(extractedText, 1, 2048) WHERE length(extractedText) > 2048;")
                db.execSQL("UPDATE documents SET summary = substr(summary, 1, 2048) WHERE length(summary) > 2048;")
            } catch (_: Exception) {}
        }
    }
}
