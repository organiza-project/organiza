import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/constants/app_colors.dart';
import 'core/constants/app_typography.dart';
import 'features/assistant/presentation/voice_assistant_modal.dart';
import 'features/blocker/presentation/screens/screen_time_blocker_screen.dart';
import 'features/gamification/presentation/screens/battlepass_shop_screen.dart';
import 'features/journal/presentation/screens/journal_screen.dart';
import 'features/profile/presentation/screens/profile_screen.dart';
import 'features/timetable/presentation/screens/timetable_screen.dart';
import 'features/today/presentation/screens/today_screen.dart';

class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  int _currentIndex = 0;

  static const List<Widget> _screens = [
    TodayScreen(),
    TimetableScreen(),
    ScreenTimeBlockerScreen(), // Detox Hub
    JournalScreen(),
    BattlepassShopScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final isDesktop = constraints.maxWidth >= 900;

        if (isDesktop) {
          return _buildDesktopLayout();
        } else {
          return _buildMobileLayout();
        }
      },
    );
  }

  Widget _buildDesktopLayout() {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Row(
        children: [
          // Slim Sidebar Icon Rail (Mockup #4 & #5)
          Container(
            width: 72,
            decoration: const BoxDecoration(
              color: AppColors.surface,
              border: Border(
                right: BorderSide(color: AppColors.border, width: 1),
              ),
            ),
            child: Column(
              children: [
                const SizedBox(height: 18),
                // Brand Logo "O"
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: AppColors.background,
                    border: Border.all(color: AppColors.primaryAccent, width: 2.5),
                  ),
                  child: Center(
                    child: Text(
                      'O',
                      style: AppTypography.h1.copyWith(
                        color: AppColors.primaryAccent,
                        fontSize: 22,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 28),
                _buildSidebarIcon(0, Icons.check_circle_outline_rounded, Icons.check_circle_rounded, 'Today'),
                _buildSidebarIcon(1, Icons.calendar_view_week_rounded, Icons.calendar_view_week_rounded, 'Timetable'),
                _buildSidebarIcon(2, Icons.hourglass_bottom_rounded, Icons.hourglass_bottom_rounded, 'Detox Hub'),
                _buildSidebarIcon(3, Icons.auto_stories_outlined, Icons.auto_stories_rounded, 'Journal'),
                _buildSidebarIcon(4, Icons.military_tech_outlined, Icons.military_tech_rounded, 'Battlepass'),
                _buildSidebarIcon(5, Icons.person_outline_rounded, Icons.person_rounded, 'Profile'),
                const Spacer(),
                // Voice Assistant in Sidebar
                IconButton(
                  tooltip: 'Voice Assistant',
                  onPressed: () => VoiceAssistantModal.show(context),
                  icon: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.primaryAccent.withAlpha(35),
                      border: Border.all(color: AppColors.primaryAccent),
                    ),
                    child: const Icon(Icons.auto_awesome_rounded,
                        color: AppColors.primaryAccent, size: 20),
                  ),
                ),
                const SizedBox(height: 18),
              ],
            ),
          ),

          // Main View Area
          Expanded(
            child: Column(
              children: [
                // Desktop Header Bar with Search, Notification, and Streak
                _buildDesktopTopBar(),
                Expanded(
                  child: IndexedStack(
                    index: _currentIndex,
                    children: _screens,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSidebarIcon(int index, IconData unselected, IconData selected, String label) {
    final isSelected = _currentIndex == index;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Tooltip(
        message: label,
        child: InkWell(
          onTap: () => setState(() => _currentIndex = index),
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: isSelected ? AppColors.surfaceElevated : Colors.transparent,
              borderRadius: BorderRadius.circular(12),
              border: isSelected
                  ? Border.all(color: AppColors.primaryAccent.withAlpha(120), width: 1.5)
                  : null,
            ),
            child: Icon(
              isSelected ? selected : unselected,
              color: isSelected ? AppColors.primaryAccent : AppColors.textSecondary,
              size: 22,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDesktopTopBar() {
    return Container(
      height: 58,
      padding: const EdgeInsets.symmetric(horizontal: 20),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(
          bottom: BorderSide(color: AppColors.border, width: 1),
        ),
      ),
      child: Row(
        children: [
          // Search Field (Mockup #4 & #5)
          Container(
            width: 320,
            height: 38,
            decoration: BoxDecoration(
              color: AppColors.background,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppColors.border),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                const Icon(Icons.search_rounded, size: 18, color: AppColors.textSecondary),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    decoration: InputDecoration(
                      hintText: 'Search habits, events, entries...',
                      hintStyle: AppTypography.bodySmall.copyWith(fontSize: 12),
                      border: InputBorder.none,
                      isDense: true,
                    ),
                    style: AppTypography.bodySmall,
                  ),
                ),
              ],
            ),
          ),
          const Spacer(),
          // Top Bar Controls: Voice, Notification, Streak, Profile
          IconButton(
            tooltip: 'Notifications',
            onPressed: () {},
            icon: const Icon(Icons.notifications_none_rounded, color: AppColors.textSecondary, size: 20),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.secondary.withAlpha(25),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.secondary.withAlpha(80)),
            ),
            child: Row(
              children: [
                const Icon(Icons.local_fire_department_rounded, size: 16, color: AppColors.secondary),
                const SizedBox(width: 4),
                Text('21d', style: AppTypography.accentMono.copyWith(fontSize: 12, color: AppColors.secondary)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMobileLayout() {
    final navIndex = _currentIndex.clamp(0, 4);

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
      floatingActionButton: Container(
        margin: const EdgeInsets.only(top: 24),
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: AppColors.primaryAccent.withAlpha(90),
              blurRadius: 18,
              spreadRadius: 2,
            ),
          ],
        ),
        child: FloatingActionButton(
          elevation: 4,
          backgroundColor: AppColors.primaryAccent,
          foregroundColor: AppColors.background,
          shape: const CircleBorder(),
          tooltip: 'Voice Assistant',
          onPressed: () => VoiceAssistantModal.show(context),
          child: const Icon(
            Icons.auto_awesome_rounded,
            size: 26,
          ),
        ),
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          border: Border(
            top: BorderSide(
              color: AppColors.border,
              width: 1,
            ),
          ),
        ),
        child: BottomNavigationBar(
          currentIndex: navIndex,
          type: BottomNavigationBarType.fixed,
          backgroundColor: AppColors.surface,
          selectedItemColor: AppColors.primaryAccent,
          unselectedItemColor: AppColors.textSecondary,
          selectedFontSize: 11,
          unselectedFontSize: 11,
          onTap: (index) {
            setState(() {
              _currentIndex = index;
            });
          },
          items: const [
            BottomNavigationBarItem(
              icon: Icon(Icons.check_circle_outline_rounded),
              activeIcon: Icon(Icons.check_circle_rounded),
              label: 'Today',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.table_chart_outlined),
              activeIcon: Icon(Icons.table_chart_rounded),
              label: 'Timetable',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.hourglass_bottom_rounded),
              activeIcon: Icon(Icons.hourglass_full_rounded),
              label: 'Detox',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.auto_stories_outlined),
              activeIcon: Icon(Icons.auto_stories_rounded),
              label: 'Journal',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.person_outline_rounded),
              activeIcon: Icon(Icons.person_rounded),
              label: 'You',
            ),
          ],
        ),
      ),
    );
  }
}
