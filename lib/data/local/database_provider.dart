import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app_database.dart';

final appDatabaseProvider = Provider<AppDatabase>((ref) {
  final db = AppDatabase();
  ref.onDispose(db.close);
  return db;
});

final appLimitsStreamProvider = StreamProvider<List<AppLimit>>((ref) {
  final db = ref.watch(appDatabaseProvider);
  return db.watchAppLimits();
});

final todayAppUsageStreamProvider =
    StreamProvider<List<AppUsageTableData>>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final now = DateTime.now();
  final todayStr =
      '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
  return db.watchTodayUsage(todayStr);
});
