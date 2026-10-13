import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../../../assistant/presentation/voice_assistant_modal.dart';
import '../controllers/journal_controller.dart';
import '../widgets/create_journal_modal.dart';

class ReflectionStoryPrompt {
  final String id;
  final String label;
  final IconData icon;
  final Color color;
  final String promptText;
  final String tag;

  const ReflectionStoryPrompt({
    required this.id,
    required this.label,
    required this.icon,
    required this.color,
    required this.promptText,
    required this.tag,
  });
}

class JournalScreen extends ConsumerStatefulWidget {
  const JournalScreen({super.key});

  @override
  ConsumerState<JournalScreen> createState() => _JournalScreenState();
}

class _JournalScreenState extends ConsumerState<JournalScreen> {
  final Set<String> _expandedSections = {'Today', 'This Week'};

  static const List<ReflectionStoryPrompt> _storyPrompts = [
    ReflectionStoryPrompt(
      id: 'quote',
      label: 'Daily Quote',
      icon: Icons.format_quote_rounded,
      color: Colors.amber,
      promptText:
          '# Daily Stoic Quote\n"We suffer more often in imagination than in reality." – Seneca\n\nReflection: ',
      tag: 'quote',
    ),
    ReflectionStoryPrompt(
      id: 'morning',
      label: 'Morning Focus',
      icon: Icons.wb_sunny_rounded,
      color: Color(0xFFF59E0B),
      promptText:
          '# Morning Intentions\n1. What is my single highest priority today?\n2. What obstacle might arise and how will I respond with tranquility?\n\nPlan: ',
      tag: 'morning',
    ),
    ReflectionStoryPrompt(
      id: 'gratitude',
      label: 'Evening Gratitude',
      icon: Icons.nightlight_round,
      color: Color(0xFF8B5CF6),
      promptText:
          '# Evening Examination\n1. What virtue did I practice today?\n2. What could I have handled with greater calm?\n3. Three things I am grateful for: \n',
      tag: 'gratitude',
    ),
    ReflectionStoryPrompt(
      id: 'weekly',
      label: 'Weekly Review',
      icon: Icons.insights_rounded,
      color: Color(0xFF10B981),
      promptText:
          '# Weekly Retrospective\n- Core Habit Completion: \n- Biggest Win: \n- Key Insight for Next Week: \n',
      tag: 'weekly',
    ),
  ];

  String _formatDatePill(DateTime date) {
    const days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
    return '${days[date.weekday - 1]} ${date.day} • NOTE';
  }

  String _formatMonthYear(DateTime date) {
    const months = [
      'January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'
    ];
    return '${months[date.month - 1]} ${date.year}';
  }

  void _openPromptModal(ReflectionStoryPrompt prompt) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => CreateJournalModal(
        existingEntry: JournalEntry(
          id: 'new_prompt_${DateTime.now().millisecondsSinceEpoch}',
          entryDate: DateTime.now().toIso8601String().split('T').first,
          content: prompt.promptText,
          tags: prompt.tag,
          createdAt: DateTime.now(),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final journalAsync = ref.watch(journalEntriesStreamProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Story Reflections', style: AppTypography.h1),
            const SizedBox(height: 2),
            Text(
              'Chronological Stoic Archive',
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Voice Dictate Reflection',
            icon: const Icon(Icons.mic_rounded, color: AppColors.primaryAccent),
            onPressed: () => VoiceAssistantModal.show(context),
          ),
          IconButton(
            onPressed: () => CreateJournalModal.show(context),
            icon: const Icon(
              Icons.add_circle_outline_rounded,
              color: AppColors.primaryAccent,
              size: 26,
            ),
            tooltip: 'New Reflection',
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Top Header: Horizontal Circular Story Avatars
            _buildStoryAvatarsHeader(),
            const SizedBox(height: 12),

            // Accordion Grouped Sections
            Expanded(
              child: journalAsync.when(
                loading: () => const Center(
                  child: CircularProgressIndicator(color: AppColors.primaryAccent),
                ),
                error: (err, _) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text(
                      'Error loading reflections: $err',
                      style: AppTypography.bodyMedium.copyWith(color: AppColors.errorRed),
                    ),
                  ),
                ),
                data: (entries) {
                  if (entries.isEmpty) {
                    return _buildEmptyState(context);
                  }
                  return _buildGroupedAccordionList(entries);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Horizontal Circular Story Avatars
  Widget _buildStoryAvatarsHeader() {
    return Container(
      height: 104,
      padding: const EdgeInsets.symmetric(vertical: 8),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(
          bottom: BorderSide(color: AppColors.border, width: 0.8),
        ),
      ),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        itemCount: _storyPrompts.length,
        itemBuilder: (context, index) {
          final prompt = _storyPrompts[index];

          return GestureDetector(
            onTap: () => _openPromptModal(prompt),
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 8),
              child: Column(
                children: [
                  // Circular Avatar with Gradient Ring
                  Container(
                    width: 58,
                    height: 58,
                    padding: const EdgeInsets.all(2.5),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        colors: [
                          prompt.color,
                          prompt.color.withAlpha(120),
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                    ),
                    child: Container(
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.background,
                      ),
                      child: Center(
                        child: Icon(
                          prompt.icon,
                          color: prompt.color,
                          size: 26,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    prompt.label,
                    style: AppTypography.bodySmall.copyWith(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  // Accordion Sections (Day, This Week, By Month)
  Widget _buildGroupedAccordionList(List<JournalEntry> entries) {
    final now = DateTime.now();
    final todayEntries = <JournalEntry>[];
    final thisWeekEntries = <JournalEntry>[];
    final monthlyGroups = <String, List<JournalEntry>>{};

    for (final e in entries) {
      final diff = now.difference(e.createdAt);
      if (diff.inDays == 0 && now.day == e.createdAt.day) {
        todayEntries.add(e);
      } else if (diff.inDays <= 7) {
        thisWeekEntries.add(e);
      } else {
        final monthKey = _formatMonthYear(e.createdAt);
        monthlyGroups.putIfAbsent(monthKey, () => []).add(e);
      }
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 90),
      children: [
        if (todayEntries.isNotEmpty)
          _buildAccordionSection(
            title: 'Today & Yesterday',
            entries: todayEntries,
            count: todayEntries.length,
          ),
        if (thisWeekEntries.isNotEmpty)
          _buildAccordionSection(
            title: 'This Week',
            entries: thisWeekEntries,
            count: thisWeekEntries.length,
          ),
        ...monthlyGroups.entries.map((group) {
          return _buildAccordionSection(
            title: group.key,
            entries: group.value,
            count: group.value.length,
          );
        }),
      ],
    );
  }

  Widget _buildAccordionSection({
    required String title,
    required List<JournalEntry> entries,
    required int count,
  }) {
    final isExpanded = _expandedSections.contains(title);

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          // Section Title Header
          InkWell(
            onTap: () {
              setState(() {
                if (isExpanded) {
                  _expandedSections.remove(title);
                } else {
                  _expandedSections.add(title);
                }
              });
            },
            borderRadius: BorderRadius.circular(16),
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: AppColors.primaryAccent,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text(
                    title,
                    style: AppTypography.h2.copyWith(fontSize: 16),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceElevated,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      '$count',
                      style: AppTypography.accentMono.copyWith(fontSize: 11),
                    ),
                  ),
                  const Spacer(),
                  Icon(
                    isExpanded
                        ? Icons.keyboard_arrow_up_rounded
                        : Icons.keyboard_arrow_down_rounded,
                    color: AppColors.textSecondary,
                  ),
                ],
              ),
            ),
          ),

          // Entries List
          if (isExpanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
              child: Column(
                children: entries.map((entry) {
                  return _buildReflectionCard(entry);
                }).toList(),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildReflectionCard(JournalEntry entry) {
    final tags = (entry.tags != null && entry.tags!.isNotEmpty)
        ? entry.tags!.split(',').map((t) => t.trim()).toList()
        : <String>[];

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Date Stamp Pill & Mood Badge
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.surfaceElevated,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  _formatDatePill(entry.createdAt),
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: AppColors.primaryAccent,
                  ),
                ),
              ),
              // Mood Badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.amber.withAlpha(25),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: Colors.amber.withAlpha(70)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.mood_rounded, size: 14, color: Colors.amber),
                    const SizedBox(width: 4),
                    Text(
                      'Focused',
                      style: AppTypography.bodySmall.copyWith(
                        fontSize: 10,
                        color: Colors.amber,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Image Attachment Preview Container (Mock Placeholder)
          if (entry.content.toLowerCase().contains('quote') ||
              entry.content.toLowerCase().contains('nature')) ...[
            Container(
              height: 110,
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 10),
              decoration: BoxDecoration(
                color: AppColors.surfaceElevated,
                borderRadius: BorderRadius.circular(8),
                gradient: const LinearGradient(
                  colors: [Color(0xFF1E293B), Color(0xFF0F172A)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: Center(
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.image_outlined,
                        color: AppColors.textSecondary, size: 20),
                    const SizedBox(width: 8),
                    Text(
                      'Stoic Attachment Preview',
                      style: AppTypography.bodySmall.copyWith(fontSize: 11),
                    ),
                  ],
                ),
              ),
            ),
          ],

          // Expandable text snippet
          Text(
            entry.content,
            style: AppTypography.bodyMedium.copyWith(
              height: 1.45,
            ),
            maxLines: 4,
            overflow: TextOverflow.ellipsis,
          ),

          if (tags.isNotEmpty) ...[
            const SizedBox(height: 10),
            Wrap(
              spacing: 6,
              children: tags.map((tag) {
                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceElevated,
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    '#$tag',
                    style: AppTypography.bodySmall.copyWith(
                      fontSize: 10,
                      color: AppColors.textSecondary,
                    ),
                  ),
                );
              }).toList(),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.surface,
                shape: BoxShape.circle,
                border: Border.all(color: AppColors.border),
              ),
              child: const Icon(
                Icons.book_rounded,
                size: 40,
                color: AppColors.primaryAccent,
              ),
            ),
            const SizedBox(height: 18),
            Text('No Journal Entries', style: AppTypography.h2),
            const SizedBox(height: 8),
            Text(
              'Select a story avatar above or tap New Reflection to record your thoughts.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () => CreateJournalModal.show(context),
              icon: const Icon(Icons.add_rounded, size: 18),
              label: Text(
                'Create First Entry',
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.background,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryAccent,
                foregroundColor: AppColors.background,
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
