import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/features/journal/presentation/controllers/journal_controller.dart';
import 'package:organiza/features/timetable/presentation/controllers/timetable_controller.dart';
import 'package:organiza/features/today/presentation/controllers/today_tasks_controller.dart';
import 'package:organiza/main.dart';

void main() {
  testWidgets('App shell loads and displays today tab by default',
      (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          todayTasksStreamProvider.overrideWith((ref) => Stream.value([])),
          timetableEntriesStreamProvider
              .overrideWith((ref) => Stream.value([])),
          journalEntriesStreamProvider
              .overrideWith((ref) => Stream.value([])),
        ],
        child: const OrganizaApp(),
      ),
    );

    expect(find.text("Today's Focus & Habits"), findsOneWidget);
    expect(find.text('Today'), findsOneWidget);
    expect(find.text('Timetable'), findsOneWidget);
    expect(find.text('Journal'), findsOneWidget);
    expect(find.text('Leaderboard'), findsOneWidget);
  });
}
