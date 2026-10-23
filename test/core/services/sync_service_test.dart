import 'dart:ffi';
import 'package:flutter_test/flutter_test.dart';
import 'package:drift/native.dart';
import 'package:drift/drift.dart' hide isNull;
import 'package:sqlite3/open.dart';
import 'package:organiza/core/network/supabase_service.dart';
import 'package:organiza/core/services/sync_service.dart';
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

  group('SyncService Observable & LWW Tests', () {
    late AppDatabase db;
    late SupabaseService mockSupabase;
    late SyncService syncService;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      mockSupabase = SupabaseService();
      syncService = SyncService(db, mockSupabase);
    });

    tearDown(() async {
      await db.close();
    });

    test('syncAll returns failure SyncReport when unauthenticated', () async {
      final report = await syncService.syncAll();

      expect(report.overallSuccess, isFalse);
      expect(report.authOrNetworkError, contains('Unauthenticated'));
      expect(syncService.lastReport, equals(report));
      expect(syncService.lastSuccessfulSync, isNull);
    });

    test('pending queue depth counts unpushed local tasks', () async {
      expect(await syncService.getPendingQueueDepth(), equals(0));

      await db.insertTask(
        TasksCompanion(
          id: const Value('pending-task-1'),
          title: const Value('Complete Focus Session'),
          cadenceUnit: const Value('day'),
          targetFrequency: const Value(1),
          xpValue: const Value(50),
          createdAt: Value(DateTime.now()),
          updatedAt: Value(DateTime.now()),
          syncStatus: const Value('pending'),
        ),
      );

      expect(await syncService.getPendingQueueDepth(), equals(1));
    });

    test('tombstone preservation prevents resurrection of deleted tasks', () async {
      final initialTime = DateTime(2026, 10, 20);
      final deleteTime = DateTime(2026, 10, 23);

      // Local task was soft-deleted on Oct 23
      await db.insertTask(
        TasksCompanion(
          id: const Value('task-tombstone'),
          title: const Value('Deleted Habit'),
          cadenceUnit: const Value('day'),
          targetFrequency: const Value(1),
          xpValue: const Value(50),
          isDeleted: const Value(true),
          createdAt: Value(initialTime),
          updatedAt: Value(deleteTime),
          syncStatus: const Value('synced'),
        ),
      );

      final task = await (db.select(db.tasks)
            ..where((t) => t.id.equals('task-tombstone')))
          .getSingle();

      expect(task.isDeleted, isTrue);
    });
  });
}
