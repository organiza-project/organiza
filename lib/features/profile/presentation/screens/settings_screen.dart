import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../core/config/supabase_config.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/constants/legal_docs.dart';
import '../../../../core/network/supabase_service.dart';
import '../../../auth/presentation/widgets/custom_server_dialog.dart';
import '../controllers/profile_controller.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  static Future<void> show(BuildContext context) {
    return Navigator.of(context).push(
      MaterialPageRoute(builder: (context) => const SettingsScreen()),
    );
  }

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late final TextEditingController _displayNameController;
  late final TextEditingController _avatarUrlController;
  late final TextEditingController _bannerUrlController;
  late final TextEditingController _groqApiKeyController;

  bool _isSavingProfile = false;
  bool _isTestingGroq = false;
  String? _groqTestStatus;

  @override
  void initState() {
    super.initState();
    final profile = ref.read(profileControllerProvider);
    _displayNameController = TextEditingController(text: profile.username);
    _avatarUrlController = TextEditingController();
    _bannerUrlController = TextEditingController();
    _groqApiKeyController = TextEditingController();
    _loadSavedSettings();
  }

  Future<void> _loadSavedSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _avatarUrlController.text = prefs.getString('custom_avatar_url') ?? '';
      _bannerUrlController.text = prefs.getString('custom_banner_url') ?? '';
      _groqApiKeyController.text = prefs.getString('groq_api_key') ?? '';
      final customDisplayName = prefs.getString('custom_display_name');
      if (customDisplayName != null && customDisplayName.isNotEmpty) {
        _displayNameController.text = customDisplayName;
      }
    });
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _avatarUrlController.dispose();
    _bannerUrlController.dispose();
    _groqApiKeyController.dispose();
    super.dispose();
  }

  Future<void> _saveProfileChanges() async {
    setState(() => _isSavingProfile = true);
    final prefs = await SharedPreferences.getInstance();
    final newDisplayName = _displayNameController.text.trim();
    final newAvatarUrl = _avatarUrlController.text.trim();
    final newBannerUrl = _bannerUrlController.text.trim();

    await prefs.setString('custom_display_name', newDisplayName);
    await prefs.setString('custom_avatar_url', newAvatarUrl);
    await prefs.setString('custom_banner_url', newBannerUrl);

    final supabase = ref.read(supabaseServiceProvider);
    if (supabase.isInitialized && supabase.currentUser != null) {
      await supabase.client?.from('profiles').update({
        'display_name': newDisplayName,
        if (newAvatarUrl.isNotEmpty) 'avatar_url': newAvatarUrl,
        if (newBannerUrl.isNotEmpty) 'banner_url': newBannerUrl,
      }).eq('id', supabase.currentUser!.id);
    }

    if (mounted) {
      setState(() => _isSavingProfile = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Profile preferences saved successfully!'),
          backgroundColor: AppColors.successGreen,
        ),
      );
    }
  }

  Future<void> _saveGroqApiKey() async {
    final key = _groqApiKeyController.text.trim();
    final prefs = await SharedPreferences.getInstance();
    if (key.isEmpty) {
      await prefs.remove('groq_api_key');
    } else {
      await prefs.setString('groq_api_key', key);
    }

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Groq API Key saved to local storage.'),
          backgroundColor: AppColors.primaryAccent,
        ),
      );
    }
  }

  Future<void> _testGroqConnection() async {
    final key = _groqApiKeyController.text.trim();
    if (key.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter a Groq API Key first.'),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    setState(() {
      _isTestingGroq = true;
      _groqTestStatus = null;
    });

    try {
      final response = await http.get(
        Uri.parse('https://api.groq.com/openai/v1/models'),
        headers: {
          'Authorization': 'Bearer $key',
          'Content-Type': 'application/json',
        },
      ).timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final count = (data['data'] as List?)?.length ?? 0;
        setState(() {
          _groqTestStatus = 'Connected! ($count models available)';
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Groq API Connected! ($count models ready) ⚡'),
              backgroundColor: AppColors.successGreen,
            ),
          );
        }
      } else {
        setState(() {
          _groqTestStatus = 'Authentication failed (HTTP ${response.statusCode})';
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Groq test failed: HTTP ${response.statusCode}'),
              backgroundColor: AppColors.errorRed,
            ),
          );
        }
      }
    } catch (e) {
      setState(() {
        _groqTestStatus = 'Connection error: $e';
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Groq connection error: $e'),
            backgroundColor: AppColors.errorRed,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isTestingGroq = false);
      }
    }
  }

  void _showLegalDialog(String title, String content) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.border),
        ),
        title: Row(
          children: [
            const Icon(Icons.gavel_rounded, color: AppColors.primaryAccent, size: 20),
            const SizedBox(width: 8),
            Text(title, style: AppTypography.h2),
          ],
        ),
        content: SizedBox(
          width: 540,
          child: SingleChildScrollView(
            child: Text(
              content,
              style: AppTypography.bodySmall.copyWith(
                height: 1.6,
                color: AppColors.textPrimary,
              ),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: Text(
              'Close',
              style: AppTypography.bodyMedium.copyWith(color: AppColors.primaryAccent),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final profile = ref.watch(profileControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        title: Text('Settings & Configuration', style: AppTypography.h1),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        children: [
          // Section 1: Profile Customization
          Text('PROFILE CUSTOMIZATION', style: AppTypography.bodySmall),
          const SizedBox(height: 10),
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
                // Immutable Username Badge
                Row(
                  children: [
                    Text('Username: ', style: AppTypography.bodyMedium),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceElevated,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: AppColors.border),
                      ),
                      child: Text(
                        '@${profile.username}',
                        style: AppTypography.accentMono.copyWith(
                          fontSize: 12,
                          color: AppColors.primaryAccent,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    const Icon(Icons.lock_rounded, size: 14, color: AppColors.textSecondary),
                  ],
                ),
                const SizedBox(height: 16),

                // Editable Display Name
                Text('Display Name', style: AppTypography.bodySmall),
                const SizedBox(height: 6),
                TextField(
                  controller: _displayNameController,
                  style: AppTypography.bodyMedium,
                  decoration: InputDecoration(
                    hintText: 'Enter public display name',
                    hintStyle: AppTypography.bodySmall,
                    filled: true,
                    fillColor: AppColors.background,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    prefixIcon: const Icon(Icons.badge_outlined,
                        color: AppColors.textSecondary, size: 20),
                  ),
                ),
                const SizedBox(height: 16),

                // Custom Avatar URL
                Text('Custom Avatar URL', style: AppTypography.bodySmall),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _avatarUrlController,
                        style: AppTypography.bodyMedium,
                        onChanged: (_) => setState(() {}),
                        decoration: InputDecoration(
                          hintText: 'https://example.com/avatar.png',
                          hintStyle: AppTypography.bodySmall,
                          filled: true,
                          fillColor: AppColors.background,
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: AppColors.border),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(color: AppColors.border),
                          ),
                          prefixIcon: const Icon(Icons.image_outlined,
                              color: AppColors.textSecondary, size: 20),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    // Live Avatar Preview
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.surfaceElevated,
                        border: Border.all(color: AppColors.primaryAccent),
                        image: _avatarUrlController.text.trim().isNotEmpty
                            ? DecorationImage(
                                image: NetworkImage(_avatarUrlController.text.trim()),
                                fit: BoxFit.cover,
                              )
                            : null,
                      ),
                      child: _avatarUrlController.text.trim().isEmpty
                          ? const Icon(Icons.person_rounded,
                              color: AppColors.textSecondary, size: 24)
                          : null,
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Custom Banner URL
                Text('Custom Banner URL', style: AppTypography.bodySmall),
                const SizedBox(height: 6),
                TextField(
                  controller: _bannerUrlController,
                  style: AppTypography.bodyMedium,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    hintText: 'https://example.com/banner.png',
                    hintStyle: AppTypography.bodySmall,
                    filled: true,
                    fillColor: AppColors.background,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    prefixIcon: const Icon(Icons.panorama_outlined,
                        color: AppColors.textSecondary, size: 20),
                  ),
                ),
                if (_bannerUrlController.text.trim().isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Container(
                    height: 60,
                    width: double.infinity,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: AppColors.border),
                      image: DecorationImage(
                        image: NetworkImage(_bannerUrlController.text.trim()),
                        fit: BoxFit.cover,
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 18),

                // Save Profile Button
                SizedBox(
                  width: double.infinity,
                  height: 44,
                  child: ElevatedButton.icon(
                    onPressed: _isSavingProfile ? null : _saveProfileChanges,
                    icon: _isSavingProfile
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: AppColors.background),
                          )
                        : const Icon(Icons.save_rounded, size: 18),
                    label: Text(
                      _isSavingProfile ? 'Saving...' : 'Save Profile Details',
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.background,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primaryAccent,
                      foregroundColor: AppColors.background,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // Section 2: AI & Groq Engine
          Text('AI ASSISTANT & GROQ ENGINE', style: AppTypography.bodySmall),
          const SizedBox(height: 10),
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
                Text(
                  'Provide your personal Groq API key for ultra-fast, local-first timetable scheduling and task breakdowns.',
                  style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary),
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: _groqApiKeyController,
                  obscureText: true,
                  style: AppTypography.accentMono.copyWith(fontSize: 13),
                  decoration: InputDecoration(
                    hintText: 'gsk_...',
                    hintStyle: AppTypography.bodySmall,
                    filled: true,
                    fillColor: AppColors.background,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    prefixIcon: const Icon(Icons.vpn_key_rounded,
                        color: AppColors.primaryAccent, size: 20),
                  ),
                ),
                if (_groqTestStatus != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    _groqTestStatus!,
                    style: AppTypography.bodySmall.copyWith(
                      color: _groqTestStatus!.contains('Connected')
                          ? AppColors.successGreen
                          : AppColors.errorRed,
                      fontSize: 12,
                    ),
                  ),
                ],
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _saveGroqApiKey,
                        icon: const Icon(Icons.check_rounded, size: 16),
                        label: const Text('Save Key'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.textPrimary,
                          side: const BorderSide(color: AppColors.border),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: _isTestingGroq ? null : _testGroqConnection,
                        icon: _isTestingGroq
                            ? const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2, color: AppColors.background),
                              )
                            : const Icon(Icons.network_check_rounded, size: 16),
                        label: Text(_isTestingGroq ? 'Testing...' : 'Test Connection'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.background,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // Section 3: Server & Backend Connection
          Text('BACKEND & SELF-HOSTING', style: AppTypography.bodySmall),
          const SizedBox(height: 10),
          Container(
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.border),
            ),
            child: ListTile(
              leading: const Icon(Icons.dns_rounded, color: AppColors.primaryAccent),
              title: Text('Server Connection', style: AppTypography.bodyMedium),
              subtitle: FutureBuilder<String>(
                future: SharedPreferences.getInstance().then(
                  (p) => SupabaseConfig.hasCustomServer(p)
                      ? (SupabaseConfig.getSavedCustomUrl(p) ?? 'Custom Server')
                      : 'Default Production Cloud',
                ),
                builder: (context, snapshot) => Text(
                  snapshot.data ?? 'Default Production Cloud',
                  style: AppTypography.bodySmall.copyWith(fontSize: 11),
                ),
              ),
              trailing: const Icon(Icons.chevron_right_rounded,
                  color: AppColors.textSecondary),
              onTap: () async {
                await CustomServerDialog.show(context);
                if (mounted) setState(() {});
              },
            ),
          ),
          const SizedBox(height: 24),

          // Section 4: Open Source & Legal
          Text('OPEN SOURCE & LEGAL', style: AppTypography.bodySmall),
          const SizedBox(height: 10),
          Container(
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.border),
            ),
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.shield_outlined,
                      color: AppColors.secondary),
                  title: Text('Privacy Policy', style: AppTypography.bodyMedium),
                  subtitle: Text('Local-first, unprivileged tracking declaration',
                      style: AppTypography.bodySmall.copyWith(fontSize: 11)),
                  trailing: const Icon(Icons.chevron_right_rounded,
                      color: AppColors.textSecondary),
                  onTap: () => _showLegalDialog(
                      'Privacy Policy', LegalDocs.privacyPolicy),
                ),
                const Divider(color: AppColors.border, height: 1),
                ListTile(
                  leading: const Icon(Icons.description_outlined,
                      color: AppColors.primaryAccent),
                  title: Text('Terms of Service', style: AppTypography.bodyMedium),
                  subtitle: Text('MIT License, fair play & account rules',
                      style: AppTypography.bodySmall.copyWith(fontSize: 11)),
                  trailing: const Icon(Icons.chevron_right_rounded,
                      color: AppColors.textSecondary),
                  onTap: () => _showLegalDialog(
                      'Terms of Service', LegalDocs.termsOfService),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Footnote
          Center(
            child: Text(
              'Licensed under MIT License — Open Source Productivity Engine',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall.copyWith(
                fontSize: 11,
                color: AppColors.textSecondary,
              ),
            ),
          ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }
}
