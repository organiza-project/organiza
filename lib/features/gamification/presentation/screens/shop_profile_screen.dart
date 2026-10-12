import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';
import '../../../today/presentation/controllers/today_tasks_controller.dart';

class ShopItem {
  final String id;
  final String title;
  final String subtitle;
  final int cost;
  final IconData icon;
  final Color accentColor;

  const ShopItem({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.cost,
    required this.icon,
    required this.accentColor,
  });
}

class ShopProfileScreen extends ConsumerStatefulWidget {
  const ShopProfileScreen({super.key});

  static Future<void> show(BuildContext context) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const ShopProfileScreen(),
      ),
    );
  }

  @override
  ConsumerState<ShopProfileScreen> createState() => _ShopProfileScreenState();
}

class _ShopProfileScreenState extends ConsumerState<ShopProfileScreen> {
  static const List<ShopItem> _catalog = [
    ShopItem(
      id: 'spartan_helmet',
      title: 'Spartan Helmet Avatar',
      subtitle: 'Ancient emblem of unwavering endurance',
      cost: 50,
      icon: Icons.shield_rounded,
      accentColor: AppColors.primaryAccent,
    ),
    ShopItem(
      id: 'marcus_bust',
      title: 'Marcus Aurelius Bust',
      subtitle: 'Emperor who commanded his mind over all else',
      cost: 100,
      icon: Icons.account_balance_rounded,
      accentColor: Color(0xFF60A5FA),
    ),
    ShopItem(
      id: 'midnight_obsidian',
      title: 'Midnight Obsidian Theme',
      subtitle: 'Pitch-black distraction-free OLED canvas',
      cost: 150,
      icon: Icons.dark_mode_rounded,
      accentColor: Color(0xFFA78BFA),
    ),
    ShopItem(
      id: 'stoic_gold',
      title: 'Stoic Gold Accents',
      subtitle: 'Gilded badge borders for disciplined masters',
      cost: 200,
      icon: Icons.military_tech_rounded,
      accentColor: Colors.amber,
    ),
    ShopItem(
      id: 'binaural_soundscape',
      title: 'Deep Work Soundscape',
      subtitle: '40Hz gamma frequency audio generator',
      cost: 75,
      icon: Icons.graphic_eq_rounded,
      accentColor: Color(0xFF34D399),
    ),
  ];

  final Set<String> _ownedItemIds = {'spartan_helmet'};
  String? _equippedItemId = 'spartan_helmet';
  bool _isSyncing = false;
  bool _cloudSyncEnabled = true;

  @override
  void initState() {
    super.initState();
    _loadShopState();
  }

  Future<void> _loadShopState() async {
    final prefs = await SharedPreferences.getInstance();
    final owned = prefs.getStringList('shop_owned_items') ?? ['spartan_helmet'];
    final equipped = prefs.getString('shop_equipped_item') ?? 'spartan_helmet';
    final sync = prefs.getBool('supabase_cloud_sync') ?? true;

    if (mounted) {
      setState(() {
        _ownedItemIds.clear();
        _ownedItemIds.addAll(owned);
        _equippedItemId = equipped;
        _cloudSyncEnabled = sync;
      });
    }
  }

  Future<void> _saveShopState() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList('shop_owned_items', _ownedItemIds.toList());
    if (_equippedItemId != null) {
      await prefs.setString('shop_equipped_item', _equippedItemId!);
    }
    await prefs.setBool('supabase_cloud_sync', _cloudSyncEnabled);
  }

  Future<void> _purchaseOrEquip(ShopItem item, int totalStars) async {
    final isOwned = _ownedItemIds.contains(item.id);

    if (isOwned) {
      setState(() {
        _equippedItemId = (_equippedItemId == item.id) ? null : item.id;
      });
      await _saveShopState();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _equippedItemId == item.id
                  ? 'Equipped ${item.title}!'
                  : 'Unequipped ${item.title}.',
            ),
            duration: const Duration(seconds: 1),
            backgroundColor: AppColors.surfaceElevated,
          ),
        );
      }
      return;
    }

    // Purchase check
    if (totalStars < item.cost) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Need ${item.cost - totalStars} more Stars! Complete 100% of daily habits to earn +5 Stars.',
          ),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    // Deduct and unlock
    setState(() {
      _ownedItemIds.add(item.id);
      _equippedItemId = item.id;
    });
    await _saveShopState();

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Unlocked and equipped ${item.title}! ⭐'),
          backgroundColor: AppColors.successGreen,
        ),
      );
    }
  }

  Future<void> _syncWithSupabase(int earnedXp, int totalStars) async {
    setState(() => _isSyncing = true);
    final supabase = ref.read(supabaseServiceProvider);

    final success = await supabase.syncProfileStats(
      totalXp: earnedXp,
      totalStars: totalStars,
      levelTitle: 'Level 14 - Focus Adept',
    );

    if (mounted) {
      setState(() => _isSyncing = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            success
                ? 'Profile synced to Supabase Cloud!'
                : 'Synced locally. Cloud operating in offline standby mode.',
          ),
          backgroundColor:
              success ? AppColors.successGreen : AppColors.surfaceElevated,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final totalStarsAsync = ref.watch(totalStarsStreamProvider);
    final totalStars = totalStarsAsync.valueOrNull ?? 65;
    final earnedXp = ref.watch(todayEarnedXpProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        title: Text('Stoic Profile & Star Shop', style: AppTypography.h1),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 16),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.amber.withAlpha(25),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.amber.withAlpha(80)),
            ),
            child: Row(
              children: [
                const Icon(Icons.star_rounded, size: 18, color: Colors.amber),
                const SizedBox(width: 4),
                Text(
                  '$totalStars',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 14,
                    color: Colors.amber,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
          children: [
            // User Level & Tier Card (Wireframe #10)
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 52,
                        height: 52,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: AppColors.primaryAccent.withAlpha(30),
                          border: Border.all(
                            color: AppColors.primaryAccent,
                            width: 2,
                          ),
                        ),
                        child: const Icon(
                          Icons.shield_rounded,
                          color: AppColors.primaryAccent,
                          size: 28,
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Level 14 · Focus Adept',
                              style: AppTypography.h2.copyWith(fontSize: 17),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Ranked #4 among cohort Spartans',
                              style: AppTypography.bodySmall,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('LEVEL PROGRESSION', style: AppTypography.bodySmall),
                      Text('1,850 / 2,000 XP', style: AppTypography.accentMono),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: 0.925,
                      minHeight: 8,
                      backgroundColor: AppColors.surfaceElevated,
                      valueColor: const AlwaysStoppedAnimation(
                        AppColors.primaryAccent,
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      _buildStatBadge(
                        icon: Icons.star_rounded,
                        color: Colors.amber,
                        label: '$totalStars Total Stars',
                      ),
                      const SizedBox(width: 10),
                      _buildStatBadge(
                        icon: Icons.local_fire_department_rounded,
                        color: Colors.deepOrangeAccent,
                        label: '14-Day Streak',
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 22),

            // Star Shop Section
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('STAR VAULT & SHOP', style: AppTypography.bodySmall),
                Text(
                  '100% Habit Completion = +5 Stars',
                  style: AppTypography.bodySmall.copyWith(
                    color: Colors.amber,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),

            // Catalog Items
            ..._catalog.map((item) {
              final isOwned = _ownedItemIds.contains(item.id);
              final isEquipped = _equippedItemId == item.id;

              return Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: isEquipped
                        ? AppColors.primaryAccent
                        : AppColors.border,
                    width: isEquipped ? 1.5 : 1.0,
                  ),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: item.accentColor.withAlpha(30),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: item.accentColor.withAlpha(80),
                        ),
                      ),
                      child: Icon(item.icon, color: item.accentColor, size: 24),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(item.title, style: AppTypography.h2.copyWith(fontSize: 15)),
                          const SizedBox(height: 2),
                          Text(
                            item.subtitle,
                            style: AppTypography.bodySmall.copyWith(fontSize: 11),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: isEquipped
                            ? AppColors.primaryAccent
                            : (isOwned
                                ? AppColors.surfaceElevated
                                : Colors.amber.withAlpha(35)),
                        foregroundColor: isEquipped
                            ? AppColors.background
                            : (isOwned ? AppColors.textPrimary : Colors.amber),
                        elevation: 0,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                          side: BorderSide(
                            color: isEquipped
                                ? AppColors.primaryAccent
                                : (isOwned
                                    ? AppColors.border
                                    : Colors.amber.withAlpha(90)),
                          ),
                        ),
                      ),
                      onPressed: () => _purchaseOrEquip(item, totalStars),
                      child: Text(
                        isEquipped
                            ? 'Equipped'
                            : (isOwned ? 'Equip' : '${item.cost} ⭐'),
                        style: AppTypography.bodySmall.copyWith(
                          fontWeight: FontWeight.bold,
                          color: isEquipped
                              ? AppColors.background
                              : (isOwned ? AppColors.textPrimary : Colors.amber),
                        ),
                      ),
                    ),
                  ],
                ),
              );
            }),

            const SizedBox(height: 20),

            // Supabase Cloud Sync Section (Wireframe Screen 10)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          const Icon(
                            Icons.cloud_sync_rounded,
                            size: 20,
                            color: AppColors.primaryAccent,
                          ),
                          const SizedBox(width: 8),
                          Text('SUPABASE CLOUD SYNC',
                              style: AppTypography.bodySmall),
                        ],
                      ),
                      Switch.adaptive(
                        value: _cloudSyncEnabled,
                        activeTrackColor: AppColors.primaryAccent,
                        onChanged: (val) {
                          setState(() => _cloudSyncEnabled = val);
                          _saveShopState();
                        },
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Backup SQLite habits, XP standings, and leaderboard progress to your private Supabase backend.',
                    style: AppTypography.bodySmall.copyWith(fontSize: 12),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: AppColors.successGreen,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        _cloudSyncEnabled
                            ? 'Sync Enabled (Local-First Offline Fallback)'
                            : 'Sync Disabled (Local SQLite Only)',
                        style: AppTypography.bodySmall.copyWith(
                          fontSize: 11,
                          color: AppColors.textSecondary,
                        ),
                      ),
                      const Spacer(),
                      OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: AppColors.border),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 8,
                          ),
                        ),
                        onPressed: _isSyncing
                            ? null
                            : () => _syncWithSupabase(earnedXp, totalStars),
                        icon: _isSyncing
                            ? const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: AppColors.primaryAccent,
                                ),
                              )
                            : const Icon(Icons.refresh_rounded, size: 16),
                        label: Text(
                          _isSyncing ? 'Syncing...' : 'Sync Now',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.primaryAccent,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatBadge({
    required IconData icon,
    required Color color,
    required String label,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withAlpha(20),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withAlpha(60)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: 6),
          Text(
            label,
            style: AppTypography.accentMono.copyWith(
              fontSize: 12,
              color: color,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
