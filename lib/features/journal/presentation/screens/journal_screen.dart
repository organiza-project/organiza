import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../controllers/journal_controller.dart';
import '../widgets/create_journal_modal.dart';

class JournalScreen extends ConsumerWidget {
  const JournalScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journalAsync = ref.watch(journalEntriesStreamProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Text('Daily Journal', style: AppTypography.h1),
        actions: [
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
        child: journalAsync.when(
          loading: () => const Center(
            child: CircularProgressIndicator(
              color: AppColors.primaryAccent,
            ),
          ),
          error: (err, _) => Center(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text(
                'Error loading journal entries: $err',
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.errorRed,
                ),
              ),
            ),
          ),
          data: (entries) {
            if (entries.isEmpty) {
              return _buildEmptyState(context);
            }

            return ListView.builder(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 90),
              itemCount: entries.length,
              itemBuilder: (context, index) {
                final entry = entries[index];
                return _JournalEntryCard(entry: entry);
              },
            );
          },
        ),
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
              'Capture your thoughts, celebrate small wins, or speak reflections aloud via voice assistant.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () => CreateJournalModal.show(context),
              icon: const Icon(Icons.edit_note_rounded, size: 20),
              label: Text(
                'Write First Reflection',
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.background,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryAccent,
                foregroundColor: AppColors.background,
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
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

class _JournalEntryCard extends ConsumerStatefulWidget {
  final JournalEntry entry;

  const _JournalEntryCard({required this.entry});

  @override
  ConsumerState<_JournalEntryCard> createState() => _JournalEntryCardState();
}

class _JournalEntryCardState extends ConsumerState<_JournalEntryCard> {
  bool _isExpanded = false;

  String _formatDateTime(DateTime dt) {
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec'
    ];
    final month = months[dt.month - 1];
    final hour = dt.hour.toString().padLeft(2, '0');
    final minute = dt.minute.toString().padLeft(2, '0');
    return '$month ${dt.day}, ${dt.year} • $hour:$minute';
  }

  @override
  Widget build(BuildContext context) {
    final entry = widget.entry;
    final tagList = entry.tags != null && entry.tags!.isNotEmpty
        ? entry.tags!.split(',').where((t) => t.trim().isNotEmpty).toList()
        : <String>[];

    final isLong = entry.content.length > 220;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top Row: Date & Actions Menu
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.schedule_rounded,
                      size: 14,
                      color: AppColors.primaryAccent,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      _formatDateTime(entry.createdAt),
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.primaryAccent,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
                PopupMenuButton<String>(
                  color: AppColors.surfaceElevated,
                  icon: const Icon(
                    Icons.more_horiz_rounded,
                    color: AppColors.textSecondary,
                  ),
                  onSelected: (val) async {
                    if (val == 'edit') {
                      CreateJournalModal.show(context, existingEntry: entry);
                    } else if (val == 'delete') {
                      await ref
                          .read(journalControllerProvider)
                          .deleteEntry(entry.id);
                    }
                  },
                  itemBuilder: (context) => [
                    const PopupMenuItem(
                      value: 'edit',
                      child: Row(
                        children: [
                          Icon(Icons.edit_rounded,
                              size: 16, color: AppColors.primaryAccent),
                          SizedBox(width: 8),
                          Text('Edit Entry'),
                        ],
                      ),
                    ),
                    const PopupMenuItem(
                      value: 'delete',
                      child: Row(
                        children: [
                          Icon(Icons.delete_outline_rounded,
                              size: 16, color: AppColors.errorRed),
                          SizedBox(width: 8),
                          Text('Delete Entry',
                              style: TextStyle(color: AppColors.errorRed)),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 10),

            // Entry Content
            GestureDetector(
              onTap: isLong
                  ? () => setState(() => _isExpanded = !_isExpanded)
                  : null,
              child: Text(
                entry.content,
                style: AppTypography.bodyMedium.copyWith(
                  height: 1.5,
                ),
                maxLines: _isExpanded ? null : (isLong ? 4 : null),
                overflow: _isExpanded || !isLong
                    ? TextOverflow.visible
                    : TextOverflow.ellipsis,
              ),
            ),

            if (isLong) ...[
              const SizedBox(height: 4),
              GestureDetector(
                onTap: () => setState(() => _isExpanded = !_isExpanded),
                child: Text(
                  _isExpanded ? 'Show less' : 'Read more...',
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.primaryAccent,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],

            // Tags Pill Chips
            if (tagList.isNotEmpty) ...[
              const SizedBox(height: 12),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: tagList.map((tag) {
                  return Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceElevated,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(
                        color: AppColors.border,
                        width: 0.8,
                      ),
                    ),
                    child: Text(
                      '#$tag',
                      style: AppTypography.bodySmall.copyWith(
                        fontSize: 11,
                        color: AppColors.textSecondary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
