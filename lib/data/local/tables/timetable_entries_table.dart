import 'package:drift/drift.dart';

class TimetableEntries extends Table {
  TextColumn get id => text()();
  TextColumn get title => text()(); // e.g. "Maths Lecture", "Team Sync"
  IntColumn get dayOfWeek => integer()(); // 1 = Monday .. 7 = Sunday (0 for date-specific)
  TextColumn get specificDate => text().nullable()(); // YYYY-MM-DD
  TextColumn get startTime => text()(); // HH:mm format, e.g. "15:00"
  TextColumn get endTime => text()(); // HH:mm format, e.g. "17:00"
  TextColumn get locationOrNotes => text().nullable()();
  BoolColumn get isCanceled => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}
