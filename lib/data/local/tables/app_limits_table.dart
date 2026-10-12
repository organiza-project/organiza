import 'package:drift/drift.dart';

class AppLimits extends Table {
  TextColumn get id => text()();
  TextColumn get packageName => text()(); // e.g. "com.instagram.android"
  TextColumn get appName => text()(); // "Instagram"
  IntColumn get dailyCapMinutes => integer()(); // e.g., 60
  TextColumn get curfewStart => text().nullable()(); // e.g., "22:00"
  TextColumn get curfewEnd => text().nullable()(); // e.g., "07:00"
  BoolColumn get blockReelsShorts => boolean().withDefault(const Constant(true))();
  BoolColumn get isEssayLocked => boolean().withDefault(const Constant(true))();

  @override
  Set<Column> get primaryKey => {id};
}
