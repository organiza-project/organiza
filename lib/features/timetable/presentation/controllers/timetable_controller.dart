import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../../data/local/app_database.dart';
import '../../../../data/local/database_provider.dart';

final selectedTimetableDateProvider = StateProvider<DateTime>((ref) {
  return DateTime.now();
});

String formatDate(DateTime date) {
  return '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
}

final timetableEntriesStreamProvider =
    StreamProvider<List<TimetableEntry>>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final selectedDate = ref.watch(selectedTimetableDateProvider);
  final dateString = formatDate(selectedDate);
  return db.watchTimetableForDate(dateString, selectedDate.weekday);
});

class TimetableController {
  final AppDatabase _db;

  TimetableController(this._db);

  Future<void> addEntry({
    required String title,
    required String startTime,
    required String endTime,
    int? dayOfWeek,
    String? specificDate,
    String? locationOrNotes,
  }) async {
    final id = const Uuid().v4();
    await _db.insertTimetableEntry(
      TimetableEntriesCompanion(
        id: Value(id),
        title: Value(title),
        dayOfWeek: Value(dayOfWeek ?? 0),
        specificDate: Value(specificDate),
        startTime: Value(startTime),
        endTime: Value(endTime),
        locationOrNotes: Value(locationOrNotes),
        isCanceled: const Value(false),
      ),
    );
  }

  Future<void> rescheduleEntry({
    required String id,
    required String newStart,
    required String newEnd,
  }) async {
    await _db.rescheduleTimetableById(id, newStart, newEnd);
  }

  Future<void> deleteEntry(String id) async {
    await _db.deleteTimetableEntry(id);
  }

  Future<void> cancelEntry(String id) async {
    await _db.toggleCancelTimetableEntry(id, true);
  }
}

final timetableControllerProvider = Provider<TimetableController>((ref) {
  final db = ref.watch(appDatabaseProvider);
  return TimetableController(db);
});
