import 'package:drift/drift.dart';

class AppUsageTable extends Table {
  TextColumn get id => text()(); // composite key: '$usageDate\_$packageName'
  TextColumn get packageName => text()();
  TextColumn get appName => text()();
  TextColumn get usageDate => text()(); // YYYY-MM-DD
  IntColumn get totalSeconds => integer().withDefault(const Constant(0))();
  DateTimeColumn get lastActiveTime => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}
