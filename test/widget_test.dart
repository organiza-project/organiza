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

import 'package:organiza/data/local/database_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:organiza/core/services/app_usage_service.dart';

class MockAuthController extends AuthController {
  MockAuthController(super.supabase);

  @override
  Future<void> checkCurrentSession() async {}

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

class MockAppUsageService extends AppUsageService {
  @override
  Future<Map<String, int>> getAggregatedUsageToday() async => {};

  @override
  Future<int> getForegroundSecondsToday(String packageName) async => 0;

  @override
  Future<List<AppInfoOption>> getInstalledAppOptions() async => [];
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

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
          appLimitsStreamProvider
              .overrideWith((ref) => Stream.value([])),
          appUsageServiceProvider.overrideWithValue(MockAppUsageService()),
        ],
        child: const OrganizaApp(),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.byType(AppShell), findsOneWidget);
    expect(find.text("Today's Focus & Habits"), findsOneWidget);
    expect(find.text('Today'), findsWidgets);
    expect(find.text('Timetable'), findsWidgets);
    expect(find.text('Detox'), findsWidgets);
    expect(find.text('Journal'), findsWidgets);
    expect(find.text('You'), findsWidgets);
  });
}
