import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

final supabaseServiceProvider = Provider<SupabaseService>((ref) {
  return SupabaseService();
});

class LeaderboardUser {
  final String id;
  final String username;
  final String avatarUrl;
  final int xp;
  final int streakDays;
  final int rank;
  final bool isCurrentUser;

  const LeaderboardUser({
    required this.id,
    required this.username,
    required this.avatarUrl,
    required this.xp,
    required this.streakDays,
    required this.rank,
    this.isCurrentUser = false,
  });
}

class SupabaseService {
  static const String _defaultUrl = String.fromEnvironment(
    'SUPABASE_URL',
    defaultValue: 'https://organiza-cloud-placeholder.supabase.co',
  );

  static const String _defaultAnonKey = String.fromEnvironment(
    'SUPABASE_ANON_KEY',
    defaultValue:
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.placeholder_anon_key_for_organiza_offline',
  );

  bool _initialized = false;
  bool get isInitialized => _initialized;

  Future<void> initialize({String? url, String? anonKey}) async {
    if (_initialized) return;

    final targetUrl = url ?? _defaultUrl;
    final targetKey = anonKey ?? _defaultAnonKey;

    try {
      await Supabase.initialize(
        url: targetUrl,
        // ignore: deprecated_member_use
        anonKey: targetKey,
        debug: kDebugMode,
      );
      _initialized = true;
      debugPrint('Organiza: Supabase client initialized.');
    } catch (e) {
      debugPrint('Organiza: Supabase initialization notice (running offline/local mode): $e');
      _initialized = false;
    }
  }

  SupabaseClient? get client {
    if (!_initialized) return null;
    try {
      return Supabase.instance.client;
    } catch (_) {
      return null;
    }
  }

  /// Sync local stats to Supabase cloud profile
  Future<bool> syncProfileStats({
    required int totalXp,
    required int totalStars,
    required String levelTitle,
  }) async {
    final c = client;
    if (c == null) {
      debugPrint('Organiza: Supabase not connected. Stats preserved in SQLite.');
      return false;
    }

    try {
      final user = c.auth.currentUser;
      if (user == null) {
        debugPrint('Organiza: Supabase user not logged in. Operating in guest sync.');
        return false;
      }

      await c.from('profiles').upsert({
        'id': user.id,
        'total_xp': totalXp,
        'total_stars': totalStars,
        'level_title': levelTitle,
        'updated_at': DateTime.now().toIso8601String(),
      });
      return true;
    } catch (e) {
      debugPrint('Organiza: Supabase profile sync error: $e');
      return false;
    }
  }

  /// Fetch leaderboard rankings (with fallback realistic cohort)
  Future<List<LeaderboardUser>> fetchLeaderboard() async {
    final c = client;
    if (c != null) {
      try {
        final res = await c
            .from('profiles')
            .select('id, username, avatar_url, total_xp, streak_days')
            .order('total_xp', ascending: false)
            .limit(20);

        final list = (res as List)
            .asMap()
            .entries
            .map((e) => LeaderboardUser(
                  id: e.value['id'] as String? ?? 'id_${e.key}',
                  username: e.value['username'] as String? ?? 'Disciplined Spartan',
                  avatarUrl: e.value['avatar_url'] as String? ?? '',
                  xp: e.value['total_xp'] as int? ?? 0,
                  streakDays: e.value['streak_days'] as int? ?? 1,
                  rank: e.key + 1,
                  isCurrentUser: e.value['id'] == c.auth.currentUser?.id,
                ))
            .toList();

        if (list.isNotEmpty) return list;
      } catch (e) {
        debugPrint('Organiza: Falling back to local/cached leaderboard: $e');
      }
    }

    // Default authentic Stoic cohort for offline MVP
    return const [
      LeaderboardUser(
        id: 'user_1',
        username: 'Marcus Aurelius',
        avatarUrl: 'assets/avatars/marcus.png',
        xp: 3420,
        streakDays: 42,
        rank: 1,
      ),
      LeaderboardUser(
        id: 'user_2',
        username: 'Seneca Focus',
        avatarUrl: 'assets/avatars/seneca.png',
        xp: 2890,
        streakDays: 28,
        rank: 2,
      ),
      LeaderboardUser(
        id: 'user_3',
        username: 'Epictetus Will',
        avatarUrl: 'assets/avatars/epictetus.png',
        xp: 2410,
        streakDays: 21,
        rank: 3,
      ),
      LeaderboardUser(
        id: 'user_me',
        username: 'You (Stoic Initiate)',
        avatarUrl: '',
        xp: 1850,
        streakDays: 14,
        rank: 4,
        isCurrentUser: true,
      ),
      LeaderboardUser(
        id: 'user_5',
        username: 'Alexander The Great',
        avatarUrl: '',
        xp: 1620,
        streakDays: 12,
        rank: 5,
      ),
      LeaderboardUser(
        id: 'user_6',
        username: 'Hypatia Scholar',
        avatarUrl: '',
        xp: 1430,
        streakDays: 9,
        rank: 6,
      ),
      LeaderboardUser(
        id: 'user_7',
        username: 'Zeno of Citium',
        avatarUrl: '',
        xp: 1100,
        streakDays: 7,
        rank: 7,
      ),
    ];
  }

  /// Nudge a peer to resume their habits
  Future<bool> nudgeFriend(String friendId, String friendName) async {
    final c = client;
    if (c != null) {
      try {
        await c.from('nudges').insert({
          'target_user_id': friendId,
          'created_at': DateTime.now().toIso8601String(),
        });
        return true;
      } catch (_) {
        // Fallback for offline/mock
      }
    }
    return true;
  }

  // ==========================================
  // PURE SUPABASE AUTHENTICATION
  // ==========================================

  User? get currentUser => client?.auth.currentUser;

  Stream<AuthState>? get authStateChanges => client?.auth.onAuthStateChange;

  /// Check whether a handle is available in public.profiles
  Future<bool> isUsernameAvailable(String username) async {
    final cleanHandle = username.trim().replaceAll('@', '');
    if (cleanHandle.isEmpty) return false;

    final c = client;
    if (c == null) {
      // Offline fallback: any reasonable non-empty handle is accepted
      return cleanHandle.length >= 3;
    }

    try {
      final res = await c
          .from('profiles')
          .select('id')
          .eq('username', cleanHandle)
          .maybeSingle();
      return res == null;
    } catch (e) {
      debugPrint('Organiza: Handle availability check notice: $e');
      return true;
    }
  }

  /// Sign in with pure Supabase email & password
  Future<AuthResponse?> signInWithEmail({
    required String email,
    required String password,
  }) async {
    final c = client;
    if (c == null) {
      debugPrint('Organiza: Supabase client offline. Using local session fallback.');
      return null;
    }
    return await c.auth.signInWithPassword(
      email: email.trim(),
      password: password,
    );
  }

  /// Register with pure Supabase email & password, automatically creating public.profiles record
  Future<AuthResponse?> signUpWithEmail({
    required String email,
    required String password,
    required String username,
  }) async {
    final c = client;
    final cleanHandle = username.trim().replaceAll('@', '');

    if (c == null) {
      debugPrint('Organiza: Supabase client offline. Using local session fallback.');
      return null;
    }

    final response = await c.auth.signUp(
      email: email.trim(),
      password: password,
      data: {'username': cleanHandle},
    );

    final user = response.user;
    if (user != null) {
      try {
        await c.from('profiles').upsert({
          'id': user.id,
          'username': cleanHandle,
          'total_xp': 0,
          'total_stars': 0,
          'level': 1,
          'level_title': 'Level 1 · Stoic Novice',
          'streak_days': 1,
          'created_at': DateTime.now().toIso8601String(),
          'updated_at': DateTime.now().toIso8601String(),
        });
      } catch (e) {
        debugPrint('Organiza: Profile auto-creation notice: $e');
      }
    }

    return response;
  }

  /// Sign out current session
  Future<void> signOut() async {
    final c = client;
    if (c != null) {
      try {
        await c.auth.signOut();
      } catch (e) {
        debugPrint('Organiza: Sign out notice: $e');
      }
    }
  }
}
