import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../config/supabase_config.dart';

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
  final String avatarFrame;
  final String bannerUrl;
  final String nameEffect;

  const LeaderboardUser({
    required this.id,
    required this.username,
    required this.avatarUrl,
    required this.xp,
    required this.streakDays,
    required this.rank,
    this.isCurrentUser = false,
    this.avatarFrame = 'frame_stoic_laurel',
    this.bannerUrl = 'banner_deep_space',
    this.nameEffect = 'effect_clean_white',
  });
}

class SupabaseService {
  bool _initialized = false;
  bool get isInitialized => _initialized;

  Future<void> initialize({String? url, String? anonKey}) async {
    if (_initialized) return;

    final targetUrl = url ?? SupabaseConfig.defaultUrl;
    final targetKey = anonKey ?? SupabaseConfig.defaultAnonKey;

    final isUrlValid = targetUrl.startsWith('https://') &&
        !targetUrl.contains('YOUR_PROJECT_ID');
    if (!isUrlValid || targetKey.isEmpty || targetKey.contains('YOUR_ANON_KEY')) {
      debugPrint('Organiza: Supabase not configured with valid credentials. Running in offline/local mode.');
      _initialized = false;
      return;
    }

    try {
      await Supabase.initialize(
        url: targetUrl,
        // ignore: deprecated_member_use
        anonKey: targetKey,
        authOptions: const FlutterAuthClientOptions(
          authFlowType: AuthFlowType.pkce,
        ),
        debug: kDebugMode,
      );
      _initialized = true;
      debugPrint('Organiza: Supabase client initialized.');
    } catch (e) {
      debugPrint(
          'Organiza: Supabase initialization notice (running offline/local mode): $e');
      _initialized = false;
    }
  }

  Future<bool> reconfigure(
      {required String url, required String anonKey}) async {
    try {
      if (_initialized) {
        await Supabase.instance.dispose();
        _initialized = false;
      }
      await initialize(url: url, anonKey: anonKey);
      return _initialized;
    } catch (e) {
      debugPrint('Organiza: Supabase reconfigure error: $e');
      return false;
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

  /// Server-side RPC for awarding habit completion XP & stars
  Future<Map<String, dynamic>> completeHabitWithRpc(String habitId) async {
    final c = client;
    if (c == null) {
      return {'success': false, 'error': 'Supabase not connected'};
    }
    try {
      final response = await c.rpc(
        'complete_habit_and_award_rewards',
        params: {'p_habit_id': habitId},
      );
      if (response is Map) {
        return Map<String, dynamic>.from(response);
      }
      return {'success': true, 'data': response};
    } catch (e) {
      debugPrint('Organiza: completeHabitWithRpc notice: $e');
      return {'success': false, 'error': e.toString()};
    }
  }

  /// Server-side RPC for purchasing cosmetics via PostgreSQL RLS & stored procedure
  Future<Map<String, dynamic>> purchaseCosmeticWithRpc(
      String cosmeticId) async {
    final c = client;
    if (c == null) {
      return {'success': false, 'error': 'Supabase not connected'};
    }
    try {
      final response = await c.rpc(
        'purchase_cosmetic_item',
        params: {'p_cosmetic_id': cosmeticId},
      );
      if (response is Map) {
        return Map<String, dynamic>.from(response);
      }
      return {'success': true, 'data': response};
    } catch (e) {
      debugPrint('Organiza: purchaseCosmeticWithRpc notice: $e');
      return {'success': false, 'error': e.toString()};
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

  /// Fetch leaderboard rankings directly from profiles
  Future<List<LeaderboardUser>> fetchLeaderboard() async {
    final c = client;
    if (c != null) {
      try {
        final res = await c
            .from('profiles')
            .select(
                'id, username, avatar_url, total_xp, streak_days, avatar_frame, banner_url, name_effect')
            .order('total_xp', ascending: false)
            .limit(50);

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
                  avatarFrame: e.value['avatar_frame'] as String? ?? 'frame_stoic_laurel',
                  bannerUrl: e.value['banner_url'] as String? ?? 'banner_deep_space',
                  nameEffect: e.value['name_effect'] as String? ?? 'effect_clean_white',
                ))
            .toList();

        return list;
      } catch (e) {
        debugPrint('Organiza: Leaderboard query error: $e');
      }
    }

    return const [];
  }

  /// Fetch live friends for current user from public.friendships
  Future<List<Map<String, dynamic>>> fetchFriends() async {
    final c = client;
    final user = currentUser;
    if (c == null || user == null) return [];

    try {
      final res = await c
          .from('friendships')
          .select('id, friend_id, status, profiles:friend_id(id, username, total_xp, streak_days, avatar_frame, name_effect)')
          .eq('user_id', user.id);

      return List<Map<String, dynamic>>.from(res as List);
    } catch (e) {
      debugPrint('Organiza: Fetch friendships notice: $e');
      return [];
    }
  }

  /// Add a friend by exact @username
  Future<bool> addFriendByUsername(String username) async {
    final clean = username.trim().replaceAll('@', '');
    if (clean.isEmpty) return false;

    final c = client;
    final user = currentUser;
    if (c == null || user == null) return false;

    try {
      final target = await c
          .from('profiles')
          .select('id, username')
          .eq('username', clean)
          .maybeSingle();

      if (target == null) return false;
      final friendId = target['id'] as String;

      await c.from('friendships').upsert({
        'user_id': user.id,
        'friend_id': friendId,
        'status': 'accepted',
        'created_at': DateTime.now().toIso8601String(),
      });
      return true;
    } catch (e) {
      debugPrint('Organiza: Add friend notice: $e');
      return false;
    }
  }

  /// Update equipped cosmetics in public.profiles
  Future<bool> updateEquippedCosmetics({
    String? avatarFrame,
    String? bannerUrl,
    String? nameEffect,
  }) async {
    final c = client;
    final user = currentUser;
    if (c == null || user == null) return false;

    final updates = <String, dynamic>{
      'updated_at': DateTime.now().toIso8601String(),
    };
    if (avatarFrame != null) updates['avatar_frame'] = avatarFrame;
    if (bannerUrl != null) updates['banner_url'] = bannerUrl;
    if (nameEffect != null) updates['name_effect'] = nameEffect;

    try {
      await c.from('profiles').update(updates).eq('id', user.id);
      return true;
    } catch (e) {
      debugPrint('Organiza: Update cosmetics notice: $e');
      return false;
    }
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

  /// Resend signup confirmation email
  Future<void> resendVerificationEmail(String email) async {
    final c = client;
    if (c != null) {
      await c.auth.resend(
        type: OtpType.signup,
        email: email.trim(),
      );
    }
  }

  /// Refresh current session to check if email has been verified
  Future<User?> refreshSession() async {
    final c = client;
    if (c != null) {
      final res = await c.auth.refreshSession();
      return res.user;
    }
    return null;
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
