import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/features/profile/presentation/controllers/profile_controller.dart';
import 'package:organiza/core/services/app_usage_service.dart';

void main() {
  group('UserProfile Calculation & Mapping Tests', () {
    test('calculateLevel maps XP properly', () {
      expect(UserProfile.calculateLevel(0), 1);
      expect(UserProfile.calculateLevel(249), 1);
      expect(UserProfile.calculateLevel(250), 2);
      expect(UserProfile.calculateLevel(499), 2);
      expect(UserProfile.calculateLevel(500), 3);
    });

    test('getTitleForLevel returns appropriate Stoic titles', () {
      expect(UserProfile.getTitleForLevel(1), 'Stoic Initiate');
      expect(UserProfile.getTitleForLevel(3), 'Focus Novice');
      expect(UserProfile.getTitleForLevel(5), 'Habit Builder');
      expect(UserProfile.getTitleForLevel(7), 'Discipline Apprentice');
      expect(UserProfile.getTitleForLevel(9), 'Iron Will');
      expect(UserProfile.getTitleForLevel(12), 'Focus Adept');
      expect(UserProfile.getTitleForLevel(15), 'Spartan Vanguard');
      expect(UserProfile.getTitleForLevel(20), 'Emperor of Citium');
    });

    test('UserProfile getters calculate relative XP correctly', () {
      const profile = UserProfile(
        id: 'usr_1',
        username: 'Marcus',
        totalXp: 350,
        totalStars: 42,
        level: 2,
        levelTitle: 'Level 2 · Focus Novice',
        currentStreak: 5,
      );

      expect(profile.stars, 42);
      expect(profile.moniker, 'Focus Novice');
      // Level 2 base is 250, next is 500
      expect(profile.xpInCurrentLevel, 100);
      expect(profile.xpNeededForNextLevel, 250);
    });
  });

  group('AppUsageService Utility Tests', () {
    test('getCleanAppName formats known packages and Linux binary names', () {
      final service = AppUsageService();
      addTearDown(() => service.dispose());

      expect(service.getCleanAppName('com.amazon.kindle'), 'Amazon Kindle');
      expect(service.getCleanAppName('com.duolingo'), 'Duolingo');
      expect(service.getCleanAppName('notion.id'), 'Notion');
      expect(service.getCleanAppName('code'), 'VS Code');
      expect(service.getCleanAppName('discord'), 'Discord');
      expect(service.getCleanAppName('spotify'), 'Spotify');
      expect(service.getCleanAppName('custom-unknown-app'), 'Custom-unknown-app');
    });
  });
}
