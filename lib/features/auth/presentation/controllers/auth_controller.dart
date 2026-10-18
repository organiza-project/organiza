import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../../../core/network/supabase_service.dart';

enum AuthStatus {
  initial,
  loading,
  authenticated,
  unauthenticated,
  error,
}

class OrganizaAuthState {
  final AuthStatus status;
  final User? user;
  final String? username;
  final String? errorMessage;
  final bool isGuest;

  const OrganizaAuthState({
    required this.status,
    this.user,
    this.username,
    this.errorMessage,
    this.isGuest = false,
  });

  OrganizaAuthState copyWith({
    AuthStatus? status,
    User? user,
    String? username,
    String? errorMessage,
    bool? isGuest,
  }) {
    return OrganizaAuthState(
      status: status ?? this.status,
      user: user ?? this.user,
      username: username ?? this.username,
      errorMessage: errorMessage,
      isGuest: isGuest ?? this.isGuest,
    );
  }
}

final authControllerProvider =
    StateNotifierProvider<AuthController, OrganizaAuthState>((ref) {
  final supabase = ref.watch(supabaseServiceProvider);
  return AuthController(supabase);
});

class AuthController extends StateNotifier<OrganizaAuthState> {
  final SupabaseService _supabase;
  static const String _guestKey = 'organiza_guest_mode';
  static const String _cachedUsernameKey = 'organiza_cached_username';

  AuthController(this._supabase)
      : super(const OrganizaAuthState(status: AuthStatus.initial)) {
    checkCurrentSession();
    _listenToAuthChanges();
  }

  void _listenToAuthChanges() {
    _supabase.authStateChanges?.listen((data) {
      final session = data.session;
      if (session != null) {
        state = state.copyWith(
          status: AuthStatus.authenticated,
          user: session.user,
          isGuest: false,
        );
      } else if (!state.isGuest) {
        state = state.copyWith(
          status: AuthStatus.unauthenticated,
          user: null,
        );
      }
    });
  }

  Future<void> checkCurrentSession() async {
    final prefs = await SharedPreferences.getInstance();
    final isGuest = prefs.getBool(_guestKey) ?? false;
    final cachedUsername = prefs.getString(_cachedUsernameKey);

    final currentUser = _supabase.currentUser;
    if (currentUser != null) {
      state = OrganizaAuthState(
        status: AuthStatus.authenticated,
        user: currentUser,
        username: currentUser.userMetadata?['username'] as String? ?? cachedUsername,
        isGuest: false,
      );
    } else if (isGuest) {
      state = OrganizaAuthState(
        status: AuthStatus.authenticated,
        username: cachedUsername ?? 'Spartan Initiate',
        isGuest: true,
      );
    } else {
      state = const OrganizaAuthState(status: AuthStatus.unauthenticated);
    }
  }

  Future<bool> login({
    required String email,
    required String password,
  }) async {
    state = state.copyWith(status: AuthStatus.loading, errorMessage: null);

    try {
      final response = await _supabase.signInWithEmail(
        email: email,
        password: password,
      );

      if (response != null && response.user != null) {
        final prefs = await SharedPreferences.getInstance();
        await prefs.setBool(_guestKey, false);
        final username = response.user!.userMetadata?['username'] as String?;
        if (username != null) {
          await prefs.setString(_cachedUsernameKey, username);
        }

        state = OrganizaAuthState(
          status: AuthStatus.authenticated,
          user: response.user,
          username: username,
          isGuest: false,
        );
        return true;
      } else {
        // Fallback for offline demo mode
        final prefs = await SharedPreferences.getInstance();
        await prefs.setBool(_guestKey, true);
        await prefs.setString(_cachedUsernameKey, email.split('@').first);

        state = OrganizaAuthState(
          status: AuthStatus.authenticated,
          username: email.split('@').first,
          isGuest: true,
        );
        return true;
      }
    } on AuthException catch (e) {
      debugPrint(
          'Organiza Auth: Sign-in AuthException: ${e.message} (code: ${e.statusCode})');
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: e.message,
      );
      return false;
    } catch (e, st) {
      debugPrint('Organiza Auth: Sign-in unexpected error: $e\n$st');
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: 'Login failed: $e',
      );
      return false;
    }
  }

  Future<bool> signUp({
    required String email,
    required String password,
    required String username,
  }) async {
    state = state.copyWith(status: AuthStatus.loading, errorMessage: null);

    try {
      final cleanHandle = username.trim().replaceAll('@', '');
      final isAvailable = await _supabase.isUsernameAvailable(cleanHandle);
      if (!isAvailable) {
        state = state.copyWith(
          status: AuthStatus.error,
          errorMessage: 'Handle @$cleanHandle is already claimed. Choose another.',
        );
        return false;
      }

      final response = await _supabase.signUpWithEmail(
        email: email,
        password: password,
        username: cleanHandle,
      );

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_cachedUsernameKey, cleanHandle);
      await prefs.setBool(_guestKey, false);

      state = OrganizaAuthState(
        status: AuthStatus.authenticated,
        user: response?.user,
        username: cleanHandle,
        isGuest: response == null,
      );
      return true;
    } on AuthException catch (e) {
      debugPrint(
          'Organiza Auth: Sign-up AuthException: ${e.message} (code: ${e.statusCode})');
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: e.message,
      );
      return false;
    } catch (e, st) {
      debugPrint('Organiza Auth: Sign-up unexpected error: $e\n$st');
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: 'Sign up failed: $e',
      );
      return false;
    }
  }

  Future<void> continueAsGuest() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_guestKey, true);
    await prefs.setString(_cachedUsernameKey, 'Spartan Guest');

    state = const OrganizaAuthState(
      status: AuthStatus.authenticated,
      username: 'Spartan Guest',
      isGuest: true,
    );
  }

  Future<bool> refreshVerificationStatus() async {
    final refreshedUser = await _supabase.refreshSession();
    if (refreshedUser != null) {
      final isConfirmed = refreshedUser.emailConfirmedAt != null;
      state = state.copyWith(user: refreshedUser);
      return isConfirmed;
    }
    return false;
  }

  Future<bool> verifyOtp({required String email, required String token}) async {
    final verified = await _supabase.verifyOtp(email: email, token: token);
    if (verified) {
      await refreshVerificationStatus();
      return true;
    }
    return false;
  }

  Future<void> resendConfirmationEmail(String email) async {
    await _supabase.resendVerificationEmail(email);
  }

  Future<void> logout() async {
    await _supabase.signOut();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_guestKey);
    await prefs.remove(_cachedUsernameKey);

    state = const OrganizaAuthState(status: AuthStatus.unauthenticated);
  }
}
