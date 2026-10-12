import 'package:drift/drift.dart';
import 'tasks_table.dart';

class DailyLogs extends Table {
  TextColumn get id => text()();
  TextColumn get taskId => text().references(Tasks, #id)();
  TextColumn get logDate => text()(); // YYYY-MM-DD
  IntColumn get durationLoggedSeconds =>
      integer().withDefault(const Constant(0))();
  BoolColumn get isCompleted =>
      boolean().withDefault(const Constant(false))();
  IntColumn get xpAwarded => integer().withDefault(const Constant(0))();
  IntColumn get stars => integer().withDefault(const Constant(0))();
  TextColumn get notes => text().nullable()();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column> get primaryKey => {id};
}
