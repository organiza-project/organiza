import 'dart:ffi';
import 'package:flutter_test/flutter_test.dart';
import 'package:drift/native.dart';
import 'package:drift/drift.dart';
import 'package:sqlite3/open.dart';
import 'package:organiza/data/local/app_database.dart';
import 'package:organiza/features/gamification/domain/cosmetics_catalog.dart';

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

  group('Gamification & Reward Reconciliation Tests', () {
    late AppDatabase db;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
    });

    tearDown(() async {
      await db.close();
    });

    test('offline habit completion sets progress and idempotently prevents multiple XP awards', () async {
      const taskId = 'task-reward-1';
      const today = '2026-10-23';

      await db.insertTask(
        TasksCompanion(
          id: const Value(taskId),
          title: const Value('Daily Cold Shower'),
          cadenceUnit: const Value('day'),
          targetFrequency: const Value(1),
          targetDurationSeconds: const Value(300),
          xpValue: const Value(75),
          createdAt: Value(DateTime.now()),
          updatedAt: Value(DateTime.now()),
        ),
      );

      // First completion logs progress and awards XP
      await db.logProgress(taskId, today, 300, 300, 75);

      var log = await (db.select(db.dailyLogs)
            ..where((l) => l.taskId.equals(taskId) & l.logDate.equals(today)))
          .getSingle();

      expect(log.isCompleted, isTrue);
      expect(log.durationLoggedSeconds, equals(300));
      expect(log.xpAwarded, equals(75));

      // Second identical completion on the same day updates duration without duplicating XP
      await db.logProgress(taskId, today, 100, 300, 75);

      log = await (db.select(db.dailyLogs)
            ..where((l) => l.taskId.equals(taskId) & l.logDate.equals(today)))
          .getSingle();

      expect(log.durationLoggedSeconds, equals(400));
      // XP remains capped for that day's completion
      expect(log.xpAwarded, equals(75));
    });

    test('cosmetics catalog items have valid identifiers and cost constraints', () {
      final frames = CosmeticsCatalog.avatarFrames;
      expect(frames.isNotEmpty, isTrue);

      for (final frame in frames) {
        expect(frame.id.isNotEmpty, isTrue);
        expect(frame.costStars, greaterThanOrEqualTo(0));
        expect(frame.requiredLevel, greaterThanOrEqualTo(1));
        expect(frame.type, equals(CosmeticType.avatarFrame));
      }
    });
  });
}
