import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../controllers/auth_controller.dart';
import '../widgets/auth_brand_hero.dart';

class VerifyEmailScreen extends ConsumerStatefulWidget {
  final String email;

  const VerifyEmailScreen({
    super.key,
    required this.email,
  });

  @override
  ConsumerState<VerifyEmailScreen> createState() => _VerifyEmailScreenState();
}

class _VerifyEmailScreenState extends ConsumerState<VerifyEmailScreen> {
  final List<TextEditingController> _otpControllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _focusNodes = List.generate(6, (_) => FocusNode());

  bool _isChecking = false;
  bool _isResending = false;
  bool _isVerified = false;

  int _resendCountdown = 42;
  Timer? _countdownTimer;

  @override
  void initState() {
    super.initState();
    _startCountdown();
  }

  void _startCountdown() {
    _countdownTimer?.cancel();
    setState(() => _resendCountdown = 42);
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_resendCountdown > 0) {
        if (mounted) setState(() => _resendCountdown--);
      } else {
        timer.cancel();
      }
    });
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    for (final c in _otpControllers) {
      c.dispose();
    }
    for (final f in _focusNodes) {
      f.dispose();
    }
    super.dispose();
  }

  String get _currentOtp => _otpControllers.map((c) => c.text).join();

  Future<void> _verifyOtp() async {
    final token = _currentOtp.trim();
    if (token.length < 6) {
      // Check verification via session if code isn't fully typed
      _checkVerificationStatus();
      return;
    }

    setState(() => _isChecking = true);
    final verified = await ref.read(authControllerProvider.notifier).verifyOtp(
          email: widget.email,
          token: token,
        );

    if (!mounted) return;
    setState(() => _isChecking = false);

    if (verified) {
      setState(() => _isVerified = true);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Invalid code or expired. Check email and try again.'),
          backgroundColor: AppColors.errorRed,
        ),
      );
    }
  }

  Future<void> _checkVerificationStatus() async {
    setState(() => _isChecking = true);
    final verified = await ref
        .read(authControllerProvider.notifier)
        .refreshVerificationStatus();

    if (!mounted) return;
    setState(() => _isChecking = false);

    if (verified) {
      setState(() => _isVerified = true);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Email not confirmed yet. Please verify your link or enter the 6-digit OTP code.'),
          backgroundColor: AppColors.errorRed,
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _resendCode() async {
    if (_resendCountdown > 0) return;

    setState(() => _isResending = true);
    await ref
        .read(authControllerProvider.notifier)
        .resendConfirmationEmail(widget.email);

    if (!mounted) return;
    setState(() => _isResending = false);
    _startCountdown();

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('New confirmation code sent to ${widget.email}! ✉️'),
        backgroundColor: AppColors.successGreen,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  Future<void> _backToLogin() async {
    await ref.read(authControllerProvider.notifier).logout();
    if (mounted) {
      Navigator.of(context).popUntil((route) => route.isFirst);
    }
  }

  void _enterOrganiza() {
    Navigator.of(context).popUntil((route) => route.isFirst);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: _isVerified ? _buildVerifiedView() : _buildPendingView(),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPendingView() {
    final formatSeconds = _resendCountdown.toString().padLeft(2, '0');

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const OrganizaLogoGlyph(size: 60),
        const SizedBox(height: 28),

        // Glowing Mail Badge
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            color: AppColors.surface,
            shape: BoxShape.circle,
            border: Border.all(
              color: AppColors.primaryAccent.withAlpha(120),
              width: 2,
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.primaryAccent.withAlpha(50),
                blurRadius: 28,
                spreadRadius: 4,
              ),
            ],
          ),
          child: const Center(
            child: Icon(
              Icons.mail_outline_rounded,
              size: 38,
              color: AppColors.primaryAccent,
            ),
          ),
        ),
        const SizedBox(height: 24),

        // Title & Description
        Text(
          'Check your inbox',
          style: AppTypography.h1.copyWith(fontSize: 26),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          'A six-digit verification code was sent to\n${widget.email}',
          style: AppTypography.bodySmall.copyWith(
            color: AppColors.textSecondary,
            fontSize: 13,
            height: 1.4,
          ),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 28),

        // 6-Digit OTP Boxes
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: List.generate(6, (index) {
            return SizedBox(
              width: 50,
              height: 56,
              child: TextField(
                controller: _otpControllers[index],
                focusNode: _focusNodes[index],
                keyboardType: TextInputType.number,
                textAlign: TextAlign.center,
                maxLength: 1,
                style: AppTypography.accentMono.copyWith(
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: AppColors.primaryAccent,
                ),
                decoration: InputDecoration(
                  counterText: '',
                  filled: true,
                  fillColor: AppColors.surface,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: AppColors.border),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(
                      color: AppColors.primaryAccent,
                      width: 2,
                    ),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: AppColors.border),
                  ),
                ),
                onChanged: (val) {
                  if (val.isNotEmpty && index < 5) {
                    _focusNodes[index + 1].requestFocus();
                  } else if (val.isEmpty && index > 0) {
                    _focusNodes[index - 1].requestFocus();
                  }
                  if (_currentOtp.length == 6) {
                    _verifyOtp();
                  }
                },
              ),
            );
          }),
        ),
        const SizedBox(height: 24),

        // Resend countdown
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              _resendCountdown > 0
                  ? 'Resend code in 0:$formatSeconds'
                  : 'Didn\'t get a code?',
              style: AppTypography.bodySmall.copyWith(
                color: AppColors.textSecondary,
                fontSize: 12,
              ),
            ),
            const SizedBox(width: 8),
            GestureDetector(
              onTap: _resendCountdown == 0 && !_isResending ? _resendCode : null,
              child: Icon(
                Icons.refresh_rounded,
                size: 16,
                color: _resendCountdown == 0
                    ? AppColors.primaryAccent
                    : AppColors.textSecondary.withAlpha(100),
              ),
            ),
          ],
        ),
        const SizedBox(height: 28),

        // Verify Email Cyan Button
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: _isChecking ? null : _verifyOtp,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.primaryAccent,
              foregroundColor: AppColors.background,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              elevation: 0,
            ),
            child: _isChecking
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppColors.background,
                    ),
                  )
                : Text(
                    'Verify email',
                    style: AppTypography.bodyMedium.copyWith(
                      color: AppColors.background,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
          ),
        ),
        const SizedBox(height: 16),

        // Use a different email / Back to login
        TextButton(
          onPressed: _backToLogin,
          child: Text(
            'Use a different email',
            style: AppTypography.bodySmall.copyWith(
              color: AppColors.textSecondary,
              fontSize: 12,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildVerifiedView() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const OrganizaLogoGlyph(size: 60),
        const SizedBox(height: 28),

        // Green Rosette / Shield Badge
        Container(
          width: 86,
          height: 86,
          decoration: BoxDecoration(
            color: AppColors.surface,
            shape: BoxShape.circle,
            border: Border.all(
              color: AppColors.secondary,
              width: 2.5,
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.secondary.withAlpha(60),
                blurRadius: 32,
                spreadRadius: 4,
              ),
            ],
          ),
          child: const Center(
            child: Icon(
              Icons.check_circle_rounded,
              size: 46,
              color: AppColors.secondary,
            ),
          ),
        ),
        const SizedBox(height: 24),

        // Title & Description
        Text(
          'Email verified',
          style: AppTypography.h1.copyWith(fontSize: 26),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          'Email verified to verify your success.',
          style: AppTypography.bodySmall.copyWith(
            color: AppColors.textSecondary,
            fontSize: 13,
          ),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 28),

        // Checklist cards: "Account secured" and "Ready to sync"
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
              const Icon(Icons.shield_rounded,
                  color: AppColors.secondary, size: 20),
              const SizedBox(width: 12),
              Expanded(
                child: Text('Account secured',
                    style: AppTypography.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
              ),
              const Icon(Icons.check_circle_rounded,
                  color: AppColors.secondary, size: 18),
            ],
          ),
        ),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
              const Icon(Icons.sync_rounded,
                  color: AppColors.primaryAccent, size: 20),
              const SizedBox(width: 12),
              Expanded(
                child: Text('Ready to sync',
                    style: AppTypography.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
              ),
              const Icon(Icons.check_circle_rounded,
                  color: AppColors.secondary, size: 18),
            ],
          ),
        ),
        const SizedBox(height: 32),

        // Enter Organiza Button
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton(
            onPressed: _enterOrganiza,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.secondary,
              foregroundColor: AppColors.background,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              elevation: 0,
            ),
            child: Text(
              'Enter Organiza',
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.background,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        TextButton(
          onPressed: _enterOrganiza,
          child: Text(
            'Take a quick tour',
            style: AppTypography.bodySmall.copyWith(
              color: AppColors.textSecondary,
              fontSize: 12,
            ),
          ),
        ),
      ],
    );
  }
}
