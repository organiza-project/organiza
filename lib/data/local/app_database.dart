import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import 'tables/app_limits_table.dart';
import 'tables/daily_logs_table.dart';
import 'tables/tasks_table.dart';

part 'app_database.g.dart';

class TaskWithDailyLog {
  final Task task;
  final DailyLog? log;

  const TaskWithDailyLog({
    required this.task,
    this.log,
  });
}

@DriftDatabase(tables: [Tasks, DailyLogs, AppLimits])
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? executor]) : super(executor ?? _openConnection());

  @override
  int get schemaVersion => 1;

  Stream<List<TaskWithDailyLog>> watchTodayTasksWithLogs(String todayDate) {
    final query = select(tasks).join([
      leftOuterJoin(
        dailyLogs,
        dailyLogs.taskId.equalsExp(tasks.id) & dailyLogs.logDate.equals(todayDate),
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
      ..where((tbl) => tbl.taskId.equals(taskId) & tbl.logDate.equals(todayDate));
    final existingLog = await existingQuery.getSingleOrNull();

    final now = DateTime.now();

    if (existingLog != null) {
      final newDuration = existingLog.durationLoggedSeconds + addedSeconds;
      final isNowCompleted =
          (targetSeconds > 0 && newDuration >= targetSeconds) || existingLog.isCompleted;
      final xpToAward = isNowCompleted
          ? (existingLog.xpAwarded > 0 ? existingLog.xpAwarded : taskXp)
          : existingLog.xpAwarded;

      await (update(dailyLogs)..where((tbl) => tbl.id.equals(existingLog.id))).write(
        DailyLogsCompanion(
          durationLoggedSeconds: Value(newDuration),
          isCompleted: Value(isNowCompleted),
          xpAwarded: Value(xpToAward),
          updatedAt: Value(now),
        ),
      );
    } else {
      final isNowCompleted = targetSeconds > 0 && addedSeconds >= targetSeconds;
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
}

LazyDatabase _openConnection() {
  return LazyDatabase(() async {
    final dbFolder = await getApplicationSupportDirectory();
    final file = File(p.join(dbFolder.path, 'app.sqlite'));
    return NativeDatabase.createInBackground(file);
  });
}
