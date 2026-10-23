import 'dart:ffi';
import 'package:flutter_test/flutter_test.dart';
import 'package:drift/native.dart';
import 'package:drift/drift.dart';
import 'package:sqlite3/open.dart';
import 'package:organiza/data/local/app_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  open.overrideFor(OperatingSystem.linux, () {
    try {
      return DynamicLibrary.open('libsqlite3.so.0');
    } catch (_) {
      return DynamicLibrary.open('/lib/x86_64-linux-gnu/libsqlite3.so.0');
    }
  });

  driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;

  group('Drift Schema Migration Tests (v1 to v5)', () {
    late AppDatabase db;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
    });

    tearDown(() async {
      await db.close();
    });

    test('fresh database initializes at current schema version 5', () async {
      expect(db.schemaVersion, equals(5));
      final tables = db.allTables.map((t) => t.actualTableName).toList();
      expect(tables, contains('tasks'));
      expect(tables, contains('daily_logs'));
      expect(tables, contains('app_limits'));
      expect(tables, contains('timetable_entries'));
      expect(tables, contains('journal_entries'));
      expect(tables, contains('app_usage_table'));
    });

    test('stepwise migration from v1 through v5 preserves and extends schema', () async {
      // Simulate raw migrations with a custom executor
      final testDb = AppDatabase(NativeDatabase.memory());
      final migrator = testDb.createMigrator();

      // Drop any tables created by default onCreate so we start with clean v1
      await testDb.customStatement('DROP TABLE IF EXISTS app_usage_table;');
      await testDb.customStatement('DROP TABLE IF EXISTS journal_entries;');
      await testDb.customStatement('DROP TABLE IF EXISTS timetable_entries;');
      await testDb.customStatement('DROP TABLE IF EXISTS daily_logs;');
      await testDb.customStatement('DROP TABLE IF EXISTS tasks;');
      await testDb.customStatement('DROP TABLE IF EXISTS app_limits;');

      // Create true v1 tables via DDL (before v2 tables, before v3 linked_package_name, before v4 stars, before v5 app_usage)
      await testDb.customStatement('''
        CREATE TABLE tasks (
          id TEXT NOT NULL PRIMARY KEY,
          title TEXT NOT NULL,
          description TEXT,
          cadence_unit TEXT NOT NULL DEFAULT 'day',
          target_frequency INTEGER NOT NULL DEFAULT 1,
          target_duration_seconds INTEGER,
          best_time_start TEXT,
          best_time_end TEXT,
          xp_value INTEGER NOT NULL DEFAULT 50,
          is_deleted INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL,
          sync_status TEXT NOT NULL DEFAULT 'synced'
        );
      ''');

      await testDb.customStatement('''
        CREATE TABLE daily_logs (
          id TEXT NOT NULL PRIMARY KEY,
          task_id TEXT NOT NULL,
          log_date TEXT NOT NULL,
          duration_logged_seconds INTEGER NOT NULL DEFAULT 0,
          is_completed INTEGER NOT NULL DEFAULT 0,
          xp_awarded INTEGER NOT NULL DEFAULT 0,
          notes TEXT,
          updated_at INTEGER NOT NULL
        );
      ''');

      await testDb.customStatement('''
        CREATE TABLE app_limits (
          id TEXT NOT NULL PRIMARY KEY,
          package_name TEXT NOT NULL,
          app_name TEXT NOT NULL,
          daily_cap_minutes INTEGER NOT NULL DEFAULT 30,
          curfew_start TEXT,
          curfew_end TEXT,
          block_reels_shorts INTEGER NOT NULL DEFAULT 0,
          is_essay_locked INTEGER NOT NULL DEFAULT 0
        );
      ''');

      // Populate v1 fixture data
      await testDb.customStatement('''
        INSERT INTO tasks (id, title, cadence_unit, target_frequency, xp_value, created_at, updated_at)
        VALUES ('task-v1', 'Study Spartan Tactics', 'day', 1, 100, 1729000000, 1729000000);
      ''');

      // Migrate from 1 to 5
      await testDb.migration.onUpgrade(migrator, 1, 5);

      // Verify v2 tables exist
      await testDb.into(testDb.timetableEntries).insert(
            TimetableEntriesCompanion(
              id: const Value('tt-1'),
              title: const Value('Morning Lecture'),
              dayOfWeek: const Value(1),
              startTime: const Value('09:00'),
              endTime: const Value('10:00'),
            ),
          );

      await testDb.into(testDb.journalEntries).insert(
            JournalEntriesCompanion(
              id: const Value('j-1'),
              entryDate: const Value('2026-10-23'),
              content: const Value('Migration test journal entry'),
              createdAt: Value(DateTime.now()),
            ),
          );

      // Verify v3 linked_package_name and v4 stars exist
      await testDb.into(testDb.tasks).insert(
            TasksCompanion(
              id: const Value('task-v3'),
              title: const Value('Read Books'),
              cadenceUnit: const Value('day'),
              targetFrequency: const Value(1),
              xpValue: const Value(50),
              linkedPackageName: const Value('com.amazon.kindle'),
              createdAt: Value(DateTime.now()),
              updatedAt: Value(DateTime.now()),
            ),
          );

      await testDb.into(testDb.dailyLogs).insert(
            DailyLogsCompanion(
              id: const Value('log-1'),
              taskId: const Value('task-v1'),
              logDate: const Value('2026-10-23'),
              durationLoggedSeconds: const Value(1800),
              isCompleted: const Value(true),
              xpAwarded: const Value(100),
              stars: const Value(5),
              updatedAt: Value(DateTime.now()),
            ),
          );

      // Verify v5 app_usage_table exists and works
      await testDb.recordDailyUsageSnapshot(
        'com.amazon.kindle',
        'Kindle',
        '2026-10-23',
        1800,
      );

      final usageMap = await testDb.getTodayUsageMap('2026-10-23');
      expect(usageMap['com.amazon.kindle'], equals(1800));

      // Assert the original v1 row survived untouched
      final originalTask = await (testDb.select(testDb.tasks)
            ..where((t) => t.id.equals('task-v1')))
          .getSingle();
      expect(originalTask.title, equals('Study Spartan Tactics'));

      await testDb.close();
    });

    test('unsupported downgrade attempt throws StateError', () async {
      final testDb = AppDatabase(NativeDatabase.memory());
      final migrator = testDb.createMigrator();

      expect(
        () => testDb.migration.onUpgrade(migrator, 6, 5),
        throwsA(isA<StateError>()),
      );

      await testDb.close();
    });
  });
}
