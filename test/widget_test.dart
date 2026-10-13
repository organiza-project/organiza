import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/core/network/supabase_service.dart';
import 'package:organiza/features/auth/presentation/controllers/auth_controller.dart';
import 'package:organiza/features/auth/presentation/screens/login_screen.dart';
import 'package:organiza/features/journal/presentation/controllers/journal_controller.dart';
import 'package:organiza/features/timetable/presentation/controllers/timetable_controller.dart';
import 'package:organiza/features/today/presentation/controllers/today_tasks_controller.dart';
import 'package:organiza/main.dart';
import 'package:organiza/shell.dart';

class MockAuthController extends AuthController {
  MockAuthController(super.supabase);

  void setAuthenticated() {
    state = const OrganizaAuthState(
      status: AuthStatus.authenticated,
      username: 'Spartan Initiate',
      isGuest: true,
    );
  }

  void setUnauthenticated() {
    state = const OrganizaAuthState(
      status: AuthStatus.unauthenticated,
    );
  }
}

void main() {
  testWidgets('App displays LoginScreen when unauthenticated by default',
      (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((ref) {
            final mock = MockAuthController(ref.watch(supabaseServiceProvider));
            mock.setUnauthenticated();
            return mock;
          }),
        ],
        child: const OrganizaApp(),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text('ORGANIZA'), findsOneWidget);
    expect(find.text('Sign In'), findsOneWidget);
  });

  testWidgets('App shell loads and displays today tab when authenticated',
      (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((ref) {
            final mock = MockAuthController(ref.watch(supabaseServiceProvider));
            mock.setAuthenticated();
            return mock;
          }),
          todayTasksStreamProvider.overrideWith((ref) => Stream.value([])),
          timetableEntriesStreamProvider
              .overrideWith((ref) => Stream.value([])),
          journalEntriesStreamProvider
              .overrideWith((ref) => Stream.value([])),
        ],
        child: const OrganizaApp(),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.byType(AppShell), findsOneWidget);
    expect(find.text("Today's Focus & Habits"), findsOneWidget);
    expect(find.text('Today'), findsOneWidget);
    expect(find.text('Timetable'), findsOneWidget);
    expect(find.text('Journal'), findsOneWidget);
    expect(find.text('Battlepass'), findsOneWidget);
    expect(find.text('You'), findsOneWidget);
  });
}
