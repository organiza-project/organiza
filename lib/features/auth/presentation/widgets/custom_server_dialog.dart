import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../core/config/supabase_config.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';

class CustomServerDialog extends ConsumerStatefulWidget {
  const CustomServerDialog({super.key});

  static Future<void> show(BuildContext context) {
    return showDialog<void>(
      context: context,
      barrierDismissible: true,
      builder: (context) => const CustomServerDialog(),
    );
  }

  @override
  ConsumerState<CustomServerDialog> createState() => _CustomServerDialogState();
}

class _CustomServerDialogState extends ConsumerState<CustomServerDialog> {
  final _formKey = GlobalKey<FormState>();
  final _urlController = TextEditingController();
  final _keyController = TextEditingController();
  bool _isLoading = true;
  bool _isSaving = false;
  bool _isCustom = false;

  @override
  void initState() {
    super.initState();
    _loadCurrentConfig();
  }

  @override
  void dispose() {
    _urlController.dispose();
    _keyController.dispose();
    super.dispose();
  }

  Future<void> _loadCurrentConfig() async {
    final prefs = await SharedPreferences.getInstance();
    final hasCustom = SupabaseConfig.hasCustomServer(prefs);
    final savedUrl = SupabaseConfig.getSavedCustomUrl(prefs) ?? '';
    final savedKey = SupabaseConfig.getSavedCustomAnonKey(prefs) ?? '';

    if (mounted) {
      setState(() {
        _isCustom = hasCustom;
        _urlController.text = hasCustom ? savedUrl : '';
        _keyController.text = hasCustom ? savedKey : '';
        _isLoading = false;
      });
    }
  }

  Future<void> _saveAndReconnect() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSaving = true);
    final url = _urlController.text.trim();
    final key = _keyController.text.trim();

    try {
      final prefs = await SharedPreferences.getInstance();
      await SupabaseConfig.saveCustomServer(prefs, url, key);

      final supabase = ref.read(supabaseServiceProvider);
      final connected = await supabase.reconfigure(url: url, anonKey: key);

      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              connected
                  ? 'Reconnected to custom Supabase server!'
                  : 'Saved configuration. Server initialized.',
            ),
            backgroundColor: AppColors.successGreen,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to connect: $e'),
            backgroundColor: AppColors.errorRed,
          ),
        );
      }
    }
  }

  Future<void> _resetToDefault() async {
    setState(() => _isSaving = true);
    try {
      final prefs = await SharedPreferences.getInstance();
      await SupabaseConfig.resetToDefault(prefs);

      final supabase = ref.read(supabaseServiceProvider);
      await supabase.reconfigure(
        url: SupabaseConfig.defaultUrl,
        anonKey: SupabaseConfig.defaultAnonKey,
      );

      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Reset to default production Supabase cloud.'),
            backgroundColor: AppColors.primaryAccent,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to reset: $e'),
            backgroundColor: AppColors.errorRed,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: AppColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: const BorderSide(color: AppColors.border, width: 1.5),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: _isLoading
              ? const SizedBox(
                  height: 180,
                  child: Center(
                    child: CircularProgressIndicator(
                      color: AppColors.primaryAccent,
                    ),
                  ),
                )
              : Form(
                  key: _formKey,
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Header
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.primaryAccent.withAlpha(30),
                                border: Border.all(
                                    color: AppColors.primaryAccent.withAlpha(120)),
                              ),
                              child: const Icon(
                                Icons.dns_rounded,
                                color: AppColors.primaryAccent,
                                size: 22,
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Custom Server Setup',
                                    style: AppTypography.h2.copyWith(fontSize: 18),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    'Self-Hosted or Private Supabase Backend',
                                    style: AppTypography.bodySmall.copyWith(
                                      color: AppColors.textSecondary,
                                      fontSize: 11,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              onPressed: () => Navigator.of(context).pop(),
                              icon: const Icon(
                                Icons.close_rounded,
                                color: AppColors.textSecondary,
                                size: 20,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 18),

                        // Status Badge
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 12, vertical: 8),
                          decoration: BoxDecoration(
                            color: _isCustom
                                ? AppColors.secondary.withAlpha(25)
                                : AppColors.surfaceElevated,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                              color: _isCustom
                                  ? AppColors.secondary.withAlpha(100)
                                  : AppColors.border,
                            ),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                _isCustom
                                    ? Icons.check_circle_rounded
                                    : Icons.cloud_outlined,
                                size: 16,
                                color: _isCustom
                                    ? AppColors.secondary
                                    : AppColors.textSecondary,
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  _isCustom
                                      ? 'Custom server override active'
                                      : 'Using default Organiza cloud',
                                  style: AppTypography.bodySmall.copyWith(
                                    fontSize: 12,
                                    color: _isCustom
                                        ? AppColors.secondary
                                        : AppColors.textSecondary,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 20),

                        // Project URL Input
                        Text('SUPABASE PROJECT URL', style: AppTypography.bodySmall),
                        const SizedBox(height: 8),
                        TextFormField(
                          controller: _urlController,
                          style: AppTypography.bodyMedium,
                          keyboardType: TextInputType.url,
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Please enter a Supabase project URL';
                            }
                            final trimmed = value.trim();
                            if (!trimmed.startsWith('http://') &&
                                !trimmed.startsWith('https://')) {
                              return 'URL must start with http:// or https://';
                            }
                            return null;
                          },
                          decoration: InputDecoration(
                            hintText: 'https://xyzcompany.supabase.co',
                            hintStyle: AppTypography.bodySmall.copyWith(
                              color: AppColors.textSecondary.withAlpha(120),
                            ),
                            prefixIcon: const Icon(Icons.link_rounded,
                                color: AppColors.textSecondary, size: 18),
                            filled: true,
                            fillColor: AppColors.background,
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(color: AppColors.border),
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(color: AppColors.border),
                            ),
                            focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(
                                  color: AppColors.primaryAccent),
                            ),
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 14, vertical: 14),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // Anon Public Key Input
                        Text('SUPABASE PUBLIC ANON KEY',
                            style: AppTypography.bodySmall),
                        const SizedBox(height: 8),
                        TextFormField(
                          controller: _keyController,
                          style: AppTypography.bodyMedium,
                          maxLines: 2,
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Please enter the public anon key';
                            }
                            return null;
                          },
                          decoration: InputDecoration(
                            hintText: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
                            hintStyle: AppTypography.bodySmall.copyWith(
                              color: AppColors.textSecondary.withAlpha(120),
                            ),
                            prefixIcon: const Icon(Icons.key_rounded,
                                color: AppColors.textSecondary, size: 18),
                            filled: true,
                            fillColor: AppColors.background,
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(color: AppColors.border),
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(color: AppColors.border),
                            ),
                            focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: const BorderSide(
                                  color: AppColors.primaryAccent),
                            ),
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 14, vertical: 14),
                          ),
                        ),
                        const SizedBox(height: 24),

                        // Action Buttons
                        Row(
                          children: [
                            if (_isCustom) ...[
                              OutlinedButton(
                                onPressed: _isSaving ? null : _resetToDefault,
                                style: OutlinedButton.styleFrom(
                                  side: const BorderSide(
                                      color: AppColors.errorRed),
                                  foregroundColor: AppColors.errorRed,
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: 14, vertical: 12),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                                child: const Text('Reset Default'),
                              ),
                              const SizedBox(width: 10),
                            ],
                            Expanded(
                              child: ElevatedButton.icon(
                                onPressed: _isSaving ? null : _saveAndReconnect,
                                icon: _isSaving
                                    ? const SizedBox(
                                        width: 18,
                                        height: 18,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                          color: AppColors.background,
                                        ),
                                      )
                                    : const Icon(Icons.sync_rounded, size: 18),
                                label: Text(
                                  _isSaving ? 'Connecting...' : 'Save & Reconnect',
                                  style: AppTypography.bodyMedium.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.background,
                                  ),
                                ),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: AppColors.primaryAccent,
                                  foregroundColor: AppColors.background,
                                  padding:
                                      const EdgeInsets.symmetric(vertical: 13),
                                  elevation: 2,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
        ),
      ),
    );
  }
}
