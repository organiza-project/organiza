import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../../core/services/app_usage_service.dart';
import '../../../../data/local/app_database.dart';
import '../../../../data/local/database_provider.dart';

String formatTodayDate([DateTime? date]) {
  final d = date ?? DateTime.now();
  final year = d.year.toString().padLeft(4, '0');
  final month = d.month.toString().padLeft(2, '0');
  final day = d.day.toString().padLeft(2, '0');
  return '$year-$month-$day';
}

final appUsageServiceProvider = Provider<AppUsageService>((ref) {
  return AppUsageService();
});

final todayDateProvider = StateProvider<String>((ref) {
  return formatTodayDate();
});

final todayTasksStreamProvider = StreamProvider<List<TaskWithDailyLog>>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final date = ref.watch(todayDateProvider);
  return db.watchTodayTasksWithLogs(date);
});

final todayEarnedXpProvider = Provider<int>((ref) {
  final tasksAsync = ref.watch(todayTasksStreamProvider);
  return tasksAsync.maybeWhen(
    data: (items) =>
        items.fold<int>(0, (sum, item) => sum + (item.log?.xpAwarded ?? 0)),
    orElse: () => 0,
  );
});

class TodayTasksController {
  final AppDatabase _db;
  final Ref _ref;
  final AppUsageService _appUsageService;

  TodayTasksController(this._db, this._ref, [AppUsageService? appUsageService])
      : _appUsageService = appUsageService ?? AppUsageService();

  Future<void> addProgress({
    required String taskId,
    required int addedSeconds,
    required int targetSeconds,
    required int taskXp,
  }) async {
    final todayDate = _ref.read(todayDateProvider);
    await _db.logProgress(
      taskId,
      todayDate,
      addedSeconds,
      targetSeconds,
      taskXp,
    );
  }

  Future<void> syncLinkedAppUsage(String taskId, String packageName) async {
    final currentUsageSeconds =
        await _appUsageService.getForegroundSecondsToday(packageName);
    if (currentUsageSeconds <= 0) return;

    final todayDate = _ref.read(todayDateProvider);
    final task = await (_db.select(_db.tasks)
          ..where((t) => t.id.equals(taskId)))
        .getSingleOrNull();
    if (task == null) return;

    final existingLog = await (_db.select(_db.dailyLogs)
          ..where((tbl) =>
              tbl.taskId.equals(taskId) & tbl.logDate.equals(todayDate)))
        .getSingleOrNull();

    final alreadyLogged = existingLog?.durationLoggedSeconds ?? 0;
    if (currentUsageSeconds > alreadyLogged) {
      final delta = currentUsageSeconds - alreadyLogged;
      await addProgress(
        taskId: taskId,
        addedSeconds: delta,
        targetSeconds: task.targetDurationSeconds ?? 0,
        taskXp: task.xpValue,
      );
    }
  }

  Future<void> syncAllLinkedApps() async {
    final todayDate = _ref.read(todayDateProvider);
    final tasksWithLogs = await _db.watchTodayTasksWithLogs(todayDate).first;
    for (final item in tasksWithLogs) {
      final pkg = item.task.linkedPackageName;
      if (pkg != null && pkg.isNotEmpty) {
        await syncLinkedAppUsage(item.task.id, pkg);
      }
    }
  }

  Future<void> createNewTask({
    required String title,
    required String cadenceUnit,
    required int targetFrequency,
    int? targetDurationSeconds,
    String? bestTimeStart,
    String? bestTimeEnd,
    int xpValue = 50,
    String? description,
    String? linkedPackageName,
  }) async {
    final id = const Uuid().v4();
    final now = DateTime.now();
    await _db.insertTask(
      TasksCompanion(
        id: Value(id),
        title: Value(title),
        description: Value(description),
        cadenceUnit: Value(cadenceUnit),
        targetFrequency: Value(targetFrequency),
        targetDurationSeconds: Value(targetDurationSeconds),
        bestTimeStart: Value(bestTimeStart),
        bestTimeEnd: Value(bestTimeEnd),
        xpValue: Value(xpValue),
        linkedPackageName: Value(linkedPackageName),
        createdAt: Value(now),
        updatedAt: Value(now),
        isDeleted: const Value(false),
        syncStatus: const Value('pending'),
      ),
    );
  }
}

final todayTasksControllerProvider = Provider<TodayTasksController>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final usageService = ref.watch(appUsageServiceProvider);
  return TodayTasksController(db, ref, usageService);
});
