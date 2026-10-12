import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../../data/local/app_database.dart';
import '../../../../data/local/database_provider.dart';

final journalEntriesStreamProvider = StreamProvider<List<JournalEntry>>((ref) {
  final db = ref.watch(appDatabaseProvider);
  return db.watchAllJournalEntries();
});

class JournalController {
  final AppDatabase _db;

  JournalController(this._db);

  Future<void> addEntry({
    required String content,
    required DateTime dateTime,
    List<String>? tags,
  }) async {
    final id = const Uuid().v4();
    final entryDate =
        '${dateTime.year.toString().padLeft(4, '0')}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')}';
    final tagsString = tags != null && tags.isNotEmpty ? tags.join(',') : null;

    await _db.insertJournalEntry(
      JournalEntriesCompanion(
        id: Value(id),
        entryDate: Value(entryDate),
        content: Value(content),
        tags: Value(tagsString),
        createdAt: Value(dateTime),
      ),
    );
  }

  Future<void> updateEntry({
    required String id,
    required String content,
    List<String>? tags,
  }) async {
    final tagsString = tags != null && tags.isNotEmpty ? tags.join(',') : null;
    await _db.updateJournalEntry(id, content, tagsString);
  }

  Future<void> deleteEntry(String id) async {
    await _db.deleteJournalEntry(id);
  }
}

final journalControllerProvider = Provider<JournalController>((ref) {
  final db = ref.watch(appDatabaseProvider);
  return JournalController(db);
});
