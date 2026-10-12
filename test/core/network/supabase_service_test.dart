import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/core/network/supabase_service.dart';

void main() {
  group('SupabaseService Offline & Leaderboard Tests', () {
    late SupabaseService supabaseService;

    setUp(() {
      supabaseService = SupabaseService();
    });

    test('fetchLeaderboard returns fallback cohort when client is unconfigured', () async {
      final leaderboard = await supabaseService.fetchLeaderboard();

      expect(leaderboard, isNotEmpty);
      expect(leaderboard.length, greaterThanOrEqualTo(5));

      // 1st place should be Marcus Aurelius
      expect(leaderboard.first.username, 'Marcus Aurelius');
      expect(leaderboard.first.rank, 1);
      expect(leaderboard.first.xp, greaterThan(3000));

      // Contains current user entry
      final currentUser = leaderboard.firstWhere((u) => u.isCurrentUser);
      expect(currentUser.username, contains('You'));
      expect(currentUser.rank, 4);
    });

    test('nudgeFriend succeeds gracefully offline', () async {
      final result = await supabaseService.nudgeFriend('user_1', 'Marcus');
      expect(result, isTrue);
    });

    test('syncProfileStats returns false gracefully when client is unconfigured', () async {
      final result = await supabaseService.syncProfileStats(
        totalXp: 1850,
        totalStars: 65,
        levelTitle: 'Level 14 - Focus Adept',
      );
      expect(result, isFalse);
    });
  });
}
