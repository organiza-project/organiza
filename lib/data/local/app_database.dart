import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import 'tables/app_limits_table.dart';
import 'tables/daily_logs_table.dart';
import 'tables/journal_entries_table.dart';
import 'tables/tasks_table.dart';
import 'tables/timetable_entries_table.dart';

part 'app_database.g.dart';

class TaskWithDailyLog {
  final Task task;
  final DailyLog? log;

  const TaskWithDailyLog({
    required this.task,
    this.log,
  });
}

class TodayScheduleResult {
  final List<TimetableEntry> timetableEntries;
  final List<TaskWithDailyLog> tasks;

  const TodayScheduleResult({
    required this.timetableEntries,
    required this.tasks,
  });
}

@DriftDatabase(
  tables: [Tasks, DailyLogs, AppLimits, TimetableEntries, JournalEntries],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? executor]) : super(executor ?? _openConnection());

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) => m.createAll(),
        onUpgrade: (m, from, to) async {
          if (from < 2) {
            await m.createTable(timetableEntries);
            await m.createTable(journalEntries);
          }
        },
      );

  Stream<List<TaskWithDailyLog>> watchTodayTasksWithLogs(String todayDate) {
    final query = select(tasks).join([
      leftOuterJoin(
        dailyLogs,
        dailyLogs.taskId.equalsExp(tasks.id) &
            dailyLogs.logDate.equals(todayDate),
      ),
    ])..where(tasks.isDeleted.equals(false));

    return query.watch().map((rows) {
      return rows.map((row) {
        return TaskWithDailyLog(
          task: row.readTable(tasks),
          log: row.readTableOrNull(dailyLogs),
        );
      }).toList();
    });
  }

  Future<int> insertTask(TasksCompanion task) {
    return into(tasks).insert(task);
  }

  Future<void> logProgress(
    String taskId,
    String todayDate,
    int addedSeconds,
    int targetSeconds,
    int taskXp,
  ) async {
    final existingQuery = select(dailyLogs)
      ..where((tbl) =>
          tbl.taskId.equals(taskId) & tbl.logDate.equals(todayDate));
    final existingLog = await existingQuery.getSingleOrNull();

    final now = DateTime.now();

    if (existingLog != null) {
      final newDuration = existingLog.durationLoggedSeconds + addedSeconds;
      final isNowCompleted = (targetSeconds > 0 &&
              newDuration >= targetSeconds) ||
          existingLog.isCompleted;
      final xpToAward = isNowCompleted
          ? (existingLog.xpAwarded > 0 ? existingLog.xpAwarded : taskXp)
          : existingLog.xpAwarded;

      await (update(dailyLogs)..where((tbl) => tbl.id.equals(existingLog.id)))
          .write(
        DailyLogsCompanion(
          durationLoggedSeconds: Value(newDuration),
          isCompleted: Value(isNowCompleted),
          xpAwarded: Value(xpToAward),
          updatedAt: Value(now),
        ),
      );
    } else {
      final isNowCompleted =
          targetSeconds > 0 && addedSeconds >= targetSeconds;
      final xpToAward = isNowCompleted ? taskXp : 0;
      final uuid = const Uuid().v4();

      await into(dailyLogs).insert(
        DailyLogsCompanion(
          id: Value(uuid),
          taskId: Value(taskId),
          logDate: Value(todayDate),
          durationLoggedSeconds: Value(addedSeconds),
          isCompleted: Value(isNowCompleted),
          xpAwarded: Value(xpToAward),
          updatedAt: Value(now),
        ),
      );
    }
  }

  Stream<List<AppLimit>> watchAppLimits() {
    return select(appLimits).watch();
  }

  Future<int> updateAppLimit(AppLimitsCompanion limit) {
    return into(appLimits).insertOnConflictUpdate(limit);
  }

  // --- Voice Assistant DAO Queries ---

  Future<TodayScheduleResult> getTodaySchedule(
      String todayDate, int dayOfWeek) async {
    final timetableQuery = select(timetableEntries)
      ..where((tbl) =>
          tbl.isCanceled.equals(false) &
          (tbl.specificDate.equals(todayDate) |
              (tbl.specificDate.isNull() & tbl.dayOfWeek.equals(dayOfWeek))))
      ..orderBy([(tbl) => OrderingTerm.asc(tbl.startTime)]);
    final items = await timetableQuery.get();

    final todayTasks = await (select(tasks).join([
      leftOuterJoin(
        dailyLogs,
        dailyLogs.taskId.equalsExp(tasks.id) &
            dailyLogs.logDate.equals(todayDate),
      ),
    ])..where(tasks.isDeleted.equals(false)))
        .get();

    final taskItems = todayTasks.map((row) {
      return TaskWithDailyLog(
        task: row.readTable(tasks),
        log: row.readTableOrNull(dailyLogs),
      );
    }).toList();

    return TodayScheduleResult(timetableEntries: items, tasks: taskItems);
  }

  Future<int> rescheduleItem(
    String titleQuery,
    String newStart,
    String newEnd,
    String todayDate,
  ) async {
    final pattern = '%${titleQuery.trim().toLowerCase()}%';

    // 1. Search matching timetable entries
    final matchedTimetable = await (select(timetableEntries)
          ..where((tbl) =>
              tbl.title.lower().like(pattern) & tbl.isCanceled.equals(false)))
        .get();

    if (matchedTimetable.isNotEmpty) {
      final target = matchedTimetable.first;
      await (update(timetableEntries)
            ..where((tbl) => tbl.id.equals(target.id)))
          .write(
        TimetableEntriesCompanion(
          startTime: Value(newStart),
          endTime: Value(newEnd),
        ),
      );
      return 1;
    }

    // 2. Search matching tasks
    final matchedTasks = await (select(tasks)
          ..where((tbl) =>
              tbl.title.lower().like(pattern) & tbl.isDeleted.equals(false)))
        .get();

    if (matchedTasks.isNotEmpty) {
      final target = matchedTasks.first;
      await (update(tasks)..where((tbl) => tbl.id.equals(target.id))).write(
        TasksCompanion(
          bestTimeStart: Value(newStart),
          bestTimeEnd: Value(newEnd),
          updatedAt: Value(DateTime.now()),
        ),
      );
      return 1;
    }

    return 0;
  }

  Future<int> cancelScheduleItem(String titleQuery, String todayDate) async {
    final pattern = '%${titleQuery.trim().toLowerCase()}%';

    final matchedTimetable = await (select(timetableEntries)
          ..where((tbl) =>
              tbl.title.lower().like(pattern) & tbl.isCanceled.equals(false)))
        .get();

    if (matchedTimetable.isNotEmpty) {
      final target = matchedTimetable.first;
      await (update(timetableEntries)
            ..where((tbl) => tbl.id.equals(target.id)))
          .write(
        const TimetableEntriesCompanion(
          isCanceled: Value(true),
        ),
      );
      return 1;
    }

    final matchedTasks = await (select(tasks)
          ..where((tbl) =>
              tbl.title.lower().like(pattern) & tbl.isDeleted.equals(false)))
        .get();

    if (matchedTasks.isNotEmpty) {
      final target = matchedTasks.first;
      await (update(tasks)..where((tbl) => tbl.id.equals(target.id))).write(
        TasksCompanion(
          isDeleted: const Value(true),
          updatedAt: Value(DateTime.now()),
        ),
      );
      return 1;
    }

    return 0;
  }

  Future<int> insertTimetableEntry(TimetableEntriesCompanion entry) {
    return into(timetableEntries).insert(entry);
  }

  Future<int> insertJournalEntry(JournalEntriesCompanion entry) {
    return into(journalEntries).insert(entry);
  }
}

LazyDatabase _openConnection() {
  return LazyDatabase(() async {
    final dbFolder = await getApplicationSupportDirectory();
    final file = File(p.join(dbFolder.path, 'app.sqlite'));
    return NativeDatabase.createInBackground(file);
  });
}
