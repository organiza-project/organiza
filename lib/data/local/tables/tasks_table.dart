import 'package:drift/drift.dart';

class Tasks extends Table {
  TextColumn get id => text()();
  TextColumn get title => text().withLength(min: 1, max: 255)();
  TextColumn get description => text().nullable()();
  TextColumn get cadenceUnit => text()(); // 'day', 'week', 'month'
  IntColumn get targetFrequency => integer()(); // e.g., 5 times a week
  IntColumn get targetDurationSeconds => integer().nullable()(); // e.g., 1800 for 30 mins
  TextColumn get bestTimeStart => text().nullable()(); // HH:mm format e.g. "07:00"
  TextColumn get bestTimeEnd => text().nullable()(); // "08:00"
  IntColumn get xpValue => integer().withDefault(const Constant(50))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
  BoolColumn get isDeleted => boolean().withDefault(const Constant(false))();
  TextColumn get syncStatus => text().withDefault(const Constant('pending'))(); // 'synced', 'pending'
  TextColumn get linkedPackageName => text().nullable()(); // e.g. "com.amazon.kindle"

  @override
  Set<Column> get primaryKey => {id};
}
