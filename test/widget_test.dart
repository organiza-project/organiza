import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/main.dart';

void main() {
  testWidgets('App shell loads and displays today tab by default', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: OrganizaApp(),
      ),
    );

    expect(find.text("Today's Focus & Habits"), findsOneWidget);
    expect(find.text('Today'), findsOneWidget);
    expect(find.text('Timetable'), findsOneWidget);
    expect(find.text('Journal'), findsOneWidget);
    expect(find.text('Leaderboard'), findsOneWidget);
  });
}
