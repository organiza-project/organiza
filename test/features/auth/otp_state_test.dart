import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:organiza/features/auth/presentation/controllers/auth_controller.dart';
import 'package:organiza/core/network/supabase_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('OTP Verification State Machine Tests', () {
    late SupabaseService mockSupabase;
    late AuthController authController;

    setUp(() {
      SharedPreferences.setMockInitialValues({});
      mockSupabase = SupabaseService();
      authController = AuthController(mockSupabase);
    });

    tearDown(() {
      authController.dispose();
    });

    test('initial auth state is unauthenticated or guest after session check', () async {
      await authController.checkCurrentSession();
      expect(authController.state.status, equals(AuthStatus.unauthenticated));
      expect(authController.state.isGuest, isFalse);
    });

    test('guest mode transition succeeds', () async {
      await authController.continueAsGuest();
      expect(authController.state.isGuest, isTrue);
      expect(authController.state.status, equals(AuthStatus.authenticated));
      expect(authController.state.username, equals('Spartan Guest'));
    });

    test('verifyOtp returns false gracefully when unconfigured or code is invalid', () async {
      final result = await authController.verifyOtp(
        email: 'spartan@organiza.app',
        token: '123456',
      );
      expect(result, isFalse);
    });

    test('logout resets auth state to unauthenticated and clears guest flags', () async {
      await authController.continueAsGuest();
      expect(authController.state.isGuest, isTrue);

      await authController.logout();
      expect(authController.state.status, equals(AuthStatus.unauthenticated));
      expect(authController.state.isGuest, isFalse);
    });
  });
}
