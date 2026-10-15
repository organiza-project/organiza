import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/core/network/supabase_service.dart';

void main() {
  group('SupabaseService Offline & Leaderboard Tests', () {
    late SupabaseService supabaseService;

    setUp(() {
      supabaseService = SupabaseService();
    });

    test('fetchLeaderboard returns empty list when client is unconfigured', () async {
      final leaderboard = await supabaseService.fetchLeaderboard();

      expect(leaderboard, isEmpty);
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

    test('completeHabitWithRpc returns error gracefully when client is unconfigured', () async {
      final result = await supabaseService.completeHabitWithRpc('habit_123');
      expect(result['success'], isFalse);
      expect(result['error'], contains('not connected'));
    });

    test('purchaseCosmeticWithRpc returns error gracefully when client is unconfigured', () async {
      final result = await supabaseService.purchaseCosmeticWithRpc('frame_123');
      expect(result['success'], isFalse);
      expect(result['error'], contains('not connected'));
    });

    test('fetchPendingFriendRequests returns empty list when unconfigured', () async {
      final requests = await supabaseService.fetchPendingFriendRequests();
      expect(requests, isEmpty);
    });

    test('respondToFriendRequest returns false gracefully when unconfigured', () async {
      final result = await supabaseService.respondToFriendRequest('req_123', true);
      expect(result, isFalse);
    });

    test('createChallenge returns false gracefully when unconfigured', () async {
      final result = await supabaseService.createChallenge(
        opponentId: 'u2',
        durationDays: 7,
        wagerStars: 25,
      );
      expect(result, isFalse);
    });

    test('fetchChallenges returns empty list when unconfigured', () async {
      final challenges = await supabaseService.fetchChallenges();
      expect(challenges, isEmpty);
    });

    test('acceptChallenge returns false gracefully when unconfigured', () async {
      final result = await supabaseService.acceptChallenge('ch_123');
      expect(result, isFalse);
    });

    test('declineChallenge returns false gracefully when unconfigured', () async {
      final result = await supabaseService.declineChallenge('ch_123');
      expect(result, isFalse);
    });
  });
}
