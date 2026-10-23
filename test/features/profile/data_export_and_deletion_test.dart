import 'dart:ffi';
import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/data/local/app_database.dart';
import 'package:sqlite3/open.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // Linux dynamic library override for sqlite3 in tests
  open.overrideFor(OperatingSystem.linux, () => DynamicLibrary.open('libsqlite3.so.0'));
  driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;

  group('Data Export and Account Wipe Suite', () {
    late AppDatabase db;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
    });

    tearDown(() async {
      await db.close();
    });

    test('exportAllLocalData exports complete structure with schema version', () async {
      // Seed a task
      await db.into(db.tasks).insert(
            TasksCompanion.insert(
              id: 'task_export_1',
              title: 'Test Export Task',
              cadenceUnit: 'daily',
              targetFrequency: 1,
            ),
          );

      // Seed a timetable entry
      await db.into(db.timetableEntries).insert(
            TimetableEntriesCompanion.insert(
              id: 'event_export_1',
              title: 'Lecture',
              dayOfWeek: 1,
              startTime: '09:00',
              endTime: '10:00',
            ),
          );

      final exportData = await db.exportAllLocalData();

      expect(exportData['schemaVersion'], equals(5));
      expect(exportData['exportedAt'], isNotNull);
      expect((exportData['tasks'] as List).length, equals(1));
      expect((exportData['timetableEntries'] as List).length, equals(1));
      expect((exportData['tasks'] as List).first['title'], equals('Test Export Task'));
    });

    test('wipeAllLocalData purges all tables cleanly', () async {
      await db.into(db.tasks).insert(
            TasksCompanion.insert(
              id: 'task_wipe_1',
              title: 'Task to be wiped',
              cadenceUnit: 'daily',
              targetFrequency: 1,
            ),
          );

      await db.recordDailyUsageSnapshot(
        'com.test.app',
        'Test App',
        '2026-10-24',
        120,
      );

      final preWipeTasks = await db.select(db.tasks).get();
      expect(preWipeTasks.length, equals(1));

      // Execute wipe
      await db.wipeAllLocalData();

      final postWipeTasks = await db.select(db.tasks).get();
      final postWipeUsage = await db.select(db.appUsageTable).get();

      expect(postWipeTasks, isEmpty);
      expect(postWipeUsage, isEmpty);
    });
  });
}
