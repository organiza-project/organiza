import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../../data/local/app_database.dart';
import '../../../../data/local/database_provider.dart';

String formatTodayDate([DateTime? date]) {
  final d = date ?? DateTime.now();
  final year = d.year.toString().padLeft(4, '0');
  final month = d.month.toString().padLeft(2, '0');
  final day = d.day.toString().padLeft(2, '0');
  return '$year-$month-$day';
}

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

  TodayTasksController(this._db, this._ref);

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

  Future<void> createNewTask({
    required String title,
    required String cadenceUnit,
    required int targetFrequency,
    int? targetDurationSeconds,
    String? bestTimeStart,
    String? bestTimeEnd,
    int xpValue = 50,
    String? description,
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
  return TodayTasksController(db, ref);
});
