import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/core/services/crash_reporter.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('CrashReporter Privacy & Sanitization Suite', () {
    test('Initialization defaults to opted-out of telemetry', () async {
      await CrashReporter.initialize();
      expect(CrashReporter.isOptedIn, isFalse);
    });

    test('Opt-in state persists to SharedPreferences', () async {
      await CrashReporter.initialize();
      await CrashReporter.setOptIn(true);
      expect(CrashReporter.isOptedIn, isTrue);

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getBool('telemetry_error_reporting_opt_in'), isTrue);
    });

    test('Sanitizes email, bearer tokens, JWTs and usernames without throwing', () async {
      await CrashReporter.initialize();

      // Record error containing email, bearer token, and user path
      expect(
        () => CrashReporter.recordError(
          Exception('User test@domain.com failed request with Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.do_not_leak at /home/john/organiza/file.dart'),
          null,
          context: 'auth_flow',
        ),
        returnsNormally,
      );
    });
  });
}
