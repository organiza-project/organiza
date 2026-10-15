import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import 'tables/app_limits_table.dart';
import 'tables/app_usage_table.dart';
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
  tables: [
    Tasks,
    DailyLogs,
    AppLimits,
    TimetableEntries,
    JournalEntries,
    AppUsageTable,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? executor]) : super(executor ?? _openConnection());

  @override
  int get schemaVersion => 5;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) => m.createAll(),
        onUpgrade: (m, from, to) async {
          if (from < 2) {
            await m.createTable(timetableEntries);
            await m.createTable(journalEntries);
          }
          if (from < 3) {
            await m.addColumn(tasks, tasks.linkedPackageName);
          }
          if (from < 4) {
            await m.addColumn(dailyLogs, dailyLogs.stars);
          }
          if (from < 5) {
            await m.createTable(appUsageTable);
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
      final starsToAward = isNowCompleted
          ? (existingLog.stars > 0 ? existingLog.stars : 5)
          : existingLog.stars;

      await (update(dailyLogs)..where((tbl) => tbl.id.equals(existingLog.id)))
          .write(
        DailyLogsCompanion(
          durationLoggedSeconds: Value(newDuration),
          isCompleted: Value(isNowCompleted),
          xpAwarded: Value(xpToAward),
          stars: Value(starsToAward),
          updatedAt: Value(now),
        ),
      );
    } else {
      final isNowCompleted =
          targetSeconds > 0 && addedSeconds >= targetSeconds;
      final xpToAward = isNowCompleted ? taskXp : 0;
      final starsToAward = isNowCompleted ? 5 : 0;
      final uuid = const Uuid().v4();

      await into(dailyLogs).insert(
        DailyLogsCompanion(
          id: Value(uuid),
          taskId: Value(taskId),
          logDate: Value(todayDate),
          durationLoggedSeconds: Value(addedSeconds),
          isCompleted: Value(isNowCompleted),
          xpAwarded: Value(xpToAward),
          stars: Value(starsToAward),
          updatedAt: Value(now),
        ),
      );
    }
  }

  Future<void> setLogDuration(
    String taskId,
    String todayDate,
    int durationSeconds,
    int targetSeconds,
    int taskXp,
  ) async {
    final existingQuery = select(dailyLogs)
      ..where((tbl) =>
          tbl.taskId.equals(taskId) & tbl.logDate.equals(todayDate));
    final existingLog = await existingQuery.getSingleOrNull();

    final now = DateTime.now();
    final isNowCompleted =
        targetSeconds > 0 && durationSeconds >= targetSeconds;
    final xpToAward = isNowCompleted ? taskXp : 0;
    final starsToAward = isNowCompleted ? 5 : 0;

    if (existingLog != null) {
      await (update(dailyLogs)..where((tbl) => tbl.id.equals(existingLog.id)))
          .write(
        DailyLogsCompanion(
          durationLoggedSeconds: Value(durationSeconds),
          isCompleted: Value(isNowCompleted),
          xpAwarded: Value(xpToAward),
          stars: Value(starsToAward),
          updatedAt: Value(now),
        ),
      );
    } else {
      final uuid = const Uuid().v4();
      await into(dailyLogs).insert(
        DailyLogsCompanion(
          id: Value(uuid),
          taskId: Value(taskId),
          logDate: Value(todayDate),
          durationLoggedSeconds: Value(durationSeconds),
          isCompleted: Value(isNowCompleted),
          xpAwarded: Value(xpToAward),
          stars: Value(starsToAward),
          updatedAt: Value(now),
        ),
      );
    }
  }

  Future<int> updateTask(String taskId, TasksCompanion companion) {
    return (update(tasks)..where((tbl) => tbl.id.equals(taskId))).write(companion);
  }

  Future<int> softDeleteTask(String taskId) {
    return (update(tasks)..where((tbl) => tbl.id.equals(taskId))).write(
      TasksCompanion(
        isDeleted: const Value(true),
        updatedAt: Value(DateTime.now()),
      ),
    );
  }

  Stream<int> watchTotalStars() {
    return select(dailyLogs).watch().map((rows) {
      return rows.fold<int>(0, (sum, row) => sum + row.stars);
    });
  }

  Stream<int> watchTotalXp() {
    return select(dailyLogs).watch().map((rows) {
      return rows.fold<int>(0, (sum, row) => sum + row.xpAwarded);
    });
  }

  Stream<int> watchCurrentStreak() {
    return select(dailyLogs).watch().map((rows) {
      final completedDates = rows
          .where((r) => r.isCompleted || r.durationLoggedSeconds > 0)
          .map((r) => r.logDate)
          .toSet();
      if (completedDates.isEmpty) return 0;
      int streak = 0;
      var check = DateTime.now();
      final todayStr =
          '${check.year}-${check.month.toString().padLeft(2, '0')}-${check.day.toString().padLeft(2, '0')}';
      if (!completedDates.contains(todayStr)) {
        check = check.subtract(const Duration(days: 1));
      }
      while (true) {
        final dateStr =
            '${check.year}-${check.month.toString().padLeft(2, '0')}-${check.day.toString().padLeft(2, '0')}';
        if (completedDates.contains(dateStr)) {
          streak++;
          check = check.subtract(const Duration(days: 1));
        } else {
          break;
        }
      }
      return streak;
    });
  }

  Stream<List<AppUsageTableData>> watchTodayUsage(String date) {
    return (select(appUsageTable)
          ..where((tbl) => tbl.usageDate.equals(date))
          ..orderBy([(tbl) => OrderingTerm.desc(tbl.totalSeconds)]))
        .watch();
  }

  Future<void> incrementUsage(
    String packageName,
    String appName,
    String date,
    int addedSeconds,
  ) async {
    final compositeId = '${date}_$packageName';
    final existing = await (select(appUsageTable)
          ..where((tbl) => tbl.id.equals(compositeId)))
        .getSingleOrNull();

    if (existing != null) {
      await (update(appUsageTable)..where((tbl) => tbl.id.equals(compositeId)))
          .write(
        AppUsageTableCompanion(
          totalSeconds: Value(existing.totalSeconds + addedSeconds),
          lastActiveTime: Value(DateTime.now()),
        ),
      );
    } else {
      await into(appUsageTable).insert(
        AppUsageTableCompanion(
          id: Value(compositeId),
          packageName: Value(packageName),
          appName: Value(appName),
          usageDate: Value(date),
          totalSeconds: Value(addedSeconds),
          lastActiveTime: Value(DateTime.now()),
        ),
      );
    }
  }

  Future<void> setAppUsage(
    String packageName,
    String appName,
    String date,
    int totalSeconds,
  ) async {
    final compositeId = '${date}_$packageName';
    await into(appUsageTable).insertOnConflictUpdate(
      AppUsageTableCompanion(
        id: Value(compositeId),
        packageName: Value(packageName),
        appName: Value(appName),
        usageDate: Value(date),
        totalSeconds: Value(totalSeconds),
        lastActiveTime: Value(DateTime.now()),
      ),
    );
  }

  Future<Map<String, int>> getTodayUsageMap(String date) async {
    final rows = await (select(appUsageTable)
          ..where((tbl) => tbl.usageDate.equals(date)))
        .get();
    return {for (final r in rows) r.packageName: r.totalSeconds};
  }

  Stream<List<AppLimit>> watchAppLimits() {
    return select(appLimits).watch();
  }

  Future<int> updateAppLimit(AppLimitsCompanion limit) {
    return into(appLimits).insertOnConflictUpdate(limit);
  }

  Future<int> deleteAppLimit(String id) {
    return (delete(appLimits)..where((tbl) => tbl.id.equals(id))).go();
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

  Stream<List<TimetableEntry>> watchTimetableForDate(
      String date, int dayOfWeek) {
    return (select(timetableEntries)
          ..where((tbl) =>
              tbl.isCanceled.equals(false) &
              (tbl.specificDate.equals(date) |
                  (tbl.specificDate.isNull() & tbl.dayOfWeek.equals(dayOfWeek))))
          ..orderBy([(tbl) => OrderingTerm.asc(tbl.startTime)]))
        .watch();
  }

  Future<int> deleteTimetableEntry(String id) {
    return (delete(timetableEntries)..where((tbl) => tbl.id.equals(id))).go();
  }

  Future<int> rescheduleTimetableById(
      String id, String newStart, String newEnd) {
    return (update(timetableEntries)..where((tbl) => tbl.id.equals(id))).write(
      TimetableEntriesCompanion(
        startTime: Value(newStart),
        endTime: Value(newEnd),
      ),
    );
  }

  Future<int> toggleCancelTimetableEntry(String id, bool isCanceled) {
    return (update(timetableEntries)..where((tbl) => tbl.id.equals(id))).write(
      TimetableEntriesCompanion(
        isCanceled: Value(isCanceled),
      ),
    );
  }

  Future<int> insertJournalEntry(JournalEntriesCompanion entry) {
    return into(journalEntries).insert(entry);
  }

  Stream<List<JournalEntry>> watchAllJournalEntries() {
    return (select(journalEntries)
          ..orderBy([(tbl) => OrderingTerm.desc(tbl.createdAt)]))
        .watch();
  }

  Future<int> deleteJournalEntry(String id) {
    return (delete(journalEntries)..where((tbl) => tbl.id.equals(id))).go();
  }

  Future<int> updateJournalEntry(String id, String content, String? tags) {
    return (update(journalEntries)..where((tbl) => tbl.id.equals(id))).write(
      JournalEntriesCompanion(
        content: Value(content),
        tags: Value(tags),
      ),
    );
  }

  Future<TaskWithDailyLog?> findHabitByTitle(
      String query, String todayDate) async {
    final pattern = '%${query.trim().toLowerCase()}%';
    final joined = await (select(tasks).join([
      leftOuterJoin(
        dailyLogs,
        dailyLogs.taskId.equalsExp(tasks.id) &
            dailyLogs.logDate.equals(todayDate),
      ),
    ])..where(tasks.title.lower().like(pattern) & tasks.isDeleted.equals(false)))
        .get();

    if (joined.isEmpty) return null;
    final row = joined.first;
    return TaskWithDailyLog(
      task: row.readTable(tasks),
      log: row.readTableOrNull(dailyLogs),
    );
  }

  Future<List<String>> getSimilarItemTitles(String query) async {
    final t = await (select(timetableEntries)
          ..where((tbl) => tbl.isCanceled.equals(false)))
        .get();
    final k = await (select(tasks)..where((tbl) => tbl.isDeleted.equals(false)))
        .get();
    return [...t.map((e) => e.title), ...k.map((e) => e.title)];
  }
}

LazyDatabase _openConnection() {
  return LazyDatabase(() async {
    final dbFolder = await getApplicationSupportDirectory();
    final file = File(p.join(dbFolder.path, 'app.sqlite'));
    return NativeDatabase.createInBackground(file);
  });
}
