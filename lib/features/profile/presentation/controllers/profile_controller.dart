import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../data/local/database_provider.dart';
import '../../../auth/presentation/controllers/auth_controller.dart';
import '../../../today/presentation/controllers/today_tasks_controller.dart';

class UserProfile {
  final String id;
  final String username;
  final int totalXp;
  final int totalStars;
  final int level;
  final String levelTitle;
  final int currentStreak;
  final String avatarFrame;
  final String bannerUrl;
  final String nameEffect;

  const UserProfile({
    required this.id,
    required this.username,
    required this.totalXp,
    required this.totalStars,
    required this.level,
    required this.levelTitle,
    required this.currentStreak,
    this.avatarFrame = 'frame_stoic_laurel',
    this.bannerUrl = 'banner_deep_space',
    this.nameEffect = 'effect_clean_white',
  });

  static String getTitleForLevel(int level) {
    if (level <= 1) return 'Stoic Initiate';
    if (level <= 3) return 'Focus Novice';
    if (level <= 5) return 'Habit Builder';
    if (level <= 7) return 'Discipline Apprentice';
    if (level <= 9) return 'Iron Will';
    if (level <= 12) return 'Focus Adept';
    if (level <= 15) return 'Spartan Vanguard';
    return 'Emperor of Citium';
  }

  static int calculateLevel(int xp) {
    return (xp ~/ 250) + 1;
  }

  static int nextLevelXp(int level) {
    return level * 250;
  }

  static int currentTierBaseXp(int level) {
    return (level - 1) * 250;
  }

  int get stars => totalStars;
  String get moniker => UserProfile.getTitleForLevel(level);
  int get xpInCurrentLevel => (totalXp - currentTierBaseXp(level)).clamp(0, 999999).toInt();
  int get xpNeededForNextLevel => nextLevelXp(level) - currentTierBaseXp(level);

  UserProfile copyWith({
    String? id,
    String? username,
    int? totalXp,
    int? totalStars,
    int? level,
    String? levelTitle,
    int? currentStreak,
    String? avatarFrame,
    String? bannerUrl,
    String? nameEffect,
  }) {
    return UserProfile(
      id: id ?? this.id,
      username: username ?? this.username,
      totalXp: totalXp ?? this.totalXp,
      totalStars: totalStars ?? this.totalStars,
      level: level ?? this.level,
      levelTitle: levelTitle ?? this.levelTitle,
      currentStreak: currentStreak ?? this.currentStreak,
      avatarFrame: avatarFrame ?? this.avatarFrame,
      bannerUrl: bannerUrl ?? this.bannerUrl,
      nameEffect: nameEffect ?? this.nameEffect,
    );
  }
}

final userStreakStreamProvider = StreamProvider<int>((ref) {
  final db = ref.watch(appDatabaseProvider);
  return db.watchCurrentStreak();
});

final userTotalXpStreamProvider = StreamProvider<int>((ref) {
  final db = ref.watch(appDatabaseProvider);
  return db.watchTotalXp();
});

final userProfileProvider = Provider<UserProfile>((ref) {
  final authState = ref.watch(authControllerProvider);
  final user = authState.user;

  final localXp = ref.watch(userTotalXpStreamProvider).valueOrNull ?? 0;
  final localStars = ref.watch(totalStarsStreamProvider).valueOrNull ?? 0;
  final localStreak = ref.watch(userStreakStreamProvider).valueOrNull ?? 0;

  final username = authState.username ??
      (user?.userMetadata?['username'] as String?) ??
      (authState.isGuest ? 'Spartan Guest' : 'Disciplined Spartan');

  final frame = (user?.userMetadata?['avatar_frame'] as String?) ??
      'frame_stoic_laurel';
  final banner =
      (user?.userMetadata?['banner_url'] as String?) ?? 'banner_deep_space';
  final effect =
      (user?.userMetadata?['name_effect'] as String?) ?? 'effect_clean_white';

  final level = UserProfile.calculateLevel(localXp);
  final levelTitle = 'Level $level · ${UserProfile.getTitleForLevel(level)}';

  return UserProfile(
    id: user?.id ?? 'guest_id',
    username: username,
    totalXp: localXp,
    totalStars: localStars,
    level: level,
    levelTitle: levelTitle,
    currentStreak: localStreak,
    avatarFrame: frame,
    bannerUrl: banner,
    nameEffect: effect,
  );
});

final profileControllerProvider = Provider<UserProfile>((ref) {
  return ref.watch(userProfileProvider);
});
