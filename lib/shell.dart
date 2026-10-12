import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/constants/app_colors.dart';
import 'features/assistant/presentation/voice_assistant_modal.dart';
import 'features/journal/presentation/screens/journal_screen.dart';
import 'features/leaderboard/presentation/screens/leaderboard_screen.dart';
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
    JournalScreen(),
    LeaderboardScreen(),
  ];

  @override
  Widget build(BuildContext context) {
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
          currentIndex: _currentIndex,
          onTap: (index) {
            setState(() {
              _currentIndex = index;
            });
          },
          items: const [
            BottomNavigationBarItem(
              icon: Icon(Icons.check_circle_outline_rounded),
              label: 'Today',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.calendar_view_week_rounded),
              label: 'Timetable',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.book_rounded),
              label: 'Journal',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.emoji_events_rounded),
              label: 'Leaderboard',
            ),
          ],
        ),
      ),
    );
  }
}
