import 'package:drift/drift.dart';

class JournalEntries extends Table {
  TextColumn get id => text()();
  TextColumn get entryDate => text()(); // YYYY-MM-DD
  TextColumn get content => text()();
  TextColumn get tags => text().nullable()(); // Comma-separated or JSON list
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column> get primaryKey => {id};
}
