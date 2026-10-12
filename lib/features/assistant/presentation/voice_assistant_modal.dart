import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/assistant_ai_service.dart';
import '../../../../core/network/groq_transcription_service.dart';
import '../../../../core/services/voice_recording_service.dart';
import '../../../../data/local/database_provider.dart';
import '../domain/assistant_models.dart';

enum AssistantUiState {
  idle,
  listening,
  transcribing,
  thinking,
  executed,
  error,
}

class VoiceAssistantModal extends ConsumerStatefulWidget {
  const VoiceAssistantModal({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const VoiceAssistantModal(),
    );
  }

  @override
  ConsumerState<VoiceAssistantModal> createState() =>
      _VoiceAssistantModalState();
}

class _VoiceAssistantModalState extends ConsumerState<VoiceAssistantModal>
    with SingleTickerProviderStateMixin {
  final VoiceRecordingService _recordingService = VoiceRecordingService();
  final GroqTranscriptionService _groqService = GroqTranscriptionService();
  final AssistantAiService _aiService = AssistantAiService();
  final TextEditingController _textController = TextEditingController();

  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  AssistantUiState _state = AssistantUiState.idle;
  String _userTranscript = '';
  AssistantExecutionResult? _executionResult;
  String? _errorMessage;
  bool _isTextMode = false;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 1.0, end: 1.25).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _recordingService.dispose();
    _textController.dispose();
    super.dispose();
  }

  Future<void> _startListening() async {
    try {
      final hasPerm = await _recordingService.hasPermission();
      if (!hasPerm) {
        setState(() {
          _state = AssistantUiState.error;
          _errorMessage = 'Microphone permission denied.';
        });
        return;
      }

      await _recordingService.startRecording();
      setState(() {
        _state = AssistantUiState.listening;
        _errorMessage = null;
        _userTranscript = '';
        _executionResult = null;
      });
    } catch (e) {
      setState(() {
        _state = AssistantUiState.error;
        _errorMessage = 'Could not start recording: $e';
      });
    }
  }

  Future<void> _stopListeningAndProcess() async {
    try {
      setState(() {
        _state = AssistantUiState.transcribing;
      });

      final audioPath = await _recordingService.stopRecording();
      if (audioPath == null) {
        setState(() {
          _state = AssistantUiState.error;
          _errorMessage = 'No audio captured.';
        });
        return;
      }

      // 1. Transcribe with Groq Whisper
      final transcript = await _groqService.transcribeAudio(audioPath);
      if (transcript.isEmpty) {
        setState(() {
          _state = AssistantUiState.error;
          _errorMessage = 'Could not understand audio. Please try again.';
        });
        return;
      }

      setState(() {
        _userTranscript = transcript;
        _state = AssistantUiState.thinking;
      });

      // 2. Process with Groq Llama 3.3 Tool-calling
      await _executeAiAssistant(transcript);
    } catch (e) {
      setState(() {
        _state = AssistantUiState.error;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _submitTextPrompt() async {
    final text = _textController.text.trim();
    if (text.isEmpty) return;

    _textController.clear();
    FocusScope.of(context).unfocus();

    setState(() {
      _userTranscript = text;
      _state = AssistantUiState.thinking;
      _errorMessage = null;
    });

    try {
      await _executeAiAssistant(text);
    } catch (e) {
      setState(() {
        _state = AssistantUiState.error;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _executeAiAssistant(String prompt) async {
    final db = ref.read(appDatabaseProvider);
    final result = await _aiService.processCommand(
      transcript: prompt,
      db: db,
    );

    setState(() {
      _executionResult = result;
      _state = AssistantUiState.executed;
    });
  }

  void _showApiKeyDialog() {
    final groqKeyController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: AppColors.border),
        ),
        title: Text('Groq API Key (Free)', style: AppTypography.h2),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'A single Groq key powers both Whisper audio transcription and Llama 3.3 function calling 100% free.',
              style: AppTypography.bodySmall,
            ),
            const SizedBox(height: 12),
            Text('Groq API Key', style: AppTypography.bodySmall),
            const SizedBox(height: 6),
            TextField(
              controller: groqKeyController,
              obscureText: true,
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                hintText: 'gsk_...',
                hintStyle: AppTypography.bodySmall,
                filled: true,
                fillColor: AppColors.background,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('Cancel', style: AppTypography.bodyMedium),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.primaryAccent,
              foregroundColor: AppColors.background,
            ),
            onPressed: () async {
              if (groqKeyController.text.trim().isNotEmpty) {
                await _aiService.saveApiKey(groqKeyController.text.trim());
              }
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: Text(
              'Save Key',
              style: AppTypography.bodyMedium.copyWith(
                fontWeight: FontWeight.bold,
                color: AppColors.background,
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Container(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: bottomInset + 24,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        border: Border(
          top: BorderSide(color: AppColors.border, width: 1),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag Handle
          Container(
            width: 44,
            height: 4,
            decoration: BoxDecoration(
              color: AppColors.border,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(height: 16),

          // Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.auto_awesome_rounded,
                    color: AppColors.primaryAccent,
                    size: 22,
                  ),
                  const SizedBox(width: 8),
                  Text('Organiza Assistant', style: AppTypography.h2),
                ],
              ),
              Row(
                children: [
                  IconButton(
                    onPressed: () => setState(() => _isTextMode = !_isTextMode),
                    icon: Icon(
                      _isTextMode ? Icons.mic_rounded : Icons.keyboard_rounded,
                      color: AppColors.textSecondary,
                    ),
                    tooltip: _isTextMode ? 'Voice Mode' : 'Text Mode',
                  ),
                  IconButton(
                    onPressed: _showApiKeyDialog,
                    icon: const Icon(
                      Icons.key_rounded,
                      color: AppColors.textSecondary,
                      size: 20,
                    ),
                    tooltip: 'Configure API Keys',
                  ),
                  IconButton(
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(
                      Icons.close_rounded,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Body Content depending on state
          _buildStateContent(),

          const SizedBox(height: 20),

          // Bottom Action / Input
          if (_isTextMode)
            _buildTextInputField()
          else
            _buildMicSection(),
        ],
      ),
    );
  }

  Widget _buildStateContent() {
    switch (_state) {
      case AssistantUiState.idle:
        return Column(
          children: [
            Text(
              'Tap the microphone or type to speak to Organiza.',
              textAlign: TextAlign.center,
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '"Reschedule Maths to 5 PM", "Create habit Read daily", or "Save reflection to journal"',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall.copyWith(
                fontStyle: FontStyle.italic,
              ),
            ),
          ],
        );

      case AssistantUiState.listening:
        return Column(
          children: [
            Text(
              'Listening...',
              style: AppTypography.h2.copyWith(
                color: AppColors.primaryAccent,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Speak your schedule change, habit, or reflection.',
              style: AppTypography.bodySmall,
            ),
          ],
        );

      case AssistantUiState.transcribing:
        return Column(
          children: [
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: AppColors.primaryAccent,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Transcribing audio with Groq Whisper...',
              style: AppTypography.bodyMedium,
            ),
          ],
        );

      case AssistantUiState.thinking:
        return Column(
          children: [
            if (_userTranscript.isNotEmpty) ...[
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                margin: const EdgeInsets.only(bottom: 12),
                decoration: BoxDecoration(
                  color: AppColors.surfaceElevated,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppColors.border),
                ),
                child: Text(
                  '"$_userTranscript"',
                  style: AppTypography.bodyMedium.copyWith(
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ),
            ],
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: AppColors.primaryAccent,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Reasoning & executing tools with Groq (Llama 3.3)...',
              style: AppTypography.bodyMedium,
            ),
          ],
        );

      case AssistantUiState.executed:
        final res = _executionResult!;
        return Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.background,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (res.userTranscript.isNotEmpty) ...[
                Text(
                  '"${res.userTranscript}"',
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.textSecondary,
                    fontStyle: FontStyle.italic,
                  ),
                ),
                const SizedBox(height: 10),
              ],
              if (res.executedActionName != null) ...[
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: AppColors.successGreen.withAlpha(35),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: AppColors.successGreen.withAlpha(120),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.check_circle_outline_rounded,
                        color: AppColors.successGreen,
                        size: 16,
                      ),
                      const SizedBox(width: 6),
                      Text(
                        '[Executed] ${res.executedActionName!}',
                        style: AppTypography.bodySmall.copyWith(
                          color: AppColors.successGreen,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
              ],
              Text(
                res.spokenConfirmation,
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
            ],
          ),
        );

      case AssistantUiState.error:
        return Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.errorRed.withAlpha(30),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: AppColors.errorRed.withAlpha(80)),
          ),
          child: Column(
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.error_outline_rounded,
                    color: AppColors.errorRed,
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _errorMessage ?? 'An error occurred.',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.errorRed,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              OutlinedButton.icon(
                onPressed: () => setState(() => _state = AssistantUiState.idle),
                icon: const Icon(Icons.refresh_rounded, size: 16),
                label: const Text('Try Again'),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AppColors.border),
                  foregroundColor: AppColors.textPrimary,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
            ],
          ),
        );
    }
  }

  Widget _buildMicSection() {
    final isListening = _state == AssistantUiState.listening;

    return Center(
      child: GestureDetector(
        onTap: () {
          if (isListening) {
            _stopListeningAndProcess();
          } else if (_state == AssistantUiState.idle ||
              _state == AssistantUiState.executed ||
              _state == AssistantUiState.error) {
            _startListening();
          }
        },
        child: AnimatedBuilder(
          animation: _pulseAnimation,
          builder: (context, child) {
            final scale = isListening ? _pulseAnimation.value : 1.0;
            return Stack(
              alignment: Alignment.center,
              children: [
                if (isListening) ...[
                  Transform.scale(
                    scale: scale * 1.2,
                    child: Container(
                      width: 80,
                      height: 80,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.primaryAccent.withAlpha(40),
                      ),
                    ),
                  ),
                ],
                Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isListening
                        ? AppColors.primaryAccent
                        : AppColors.surfaceElevated,
                    border: Border.all(
                      color: AppColors.primaryAccent,
                      width: 2,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primaryAccent.withAlpha(50),
                        blurRadius: 16,
                        spreadRadius: 2,
                      ),
                    ],
                  ),
                  child: Icon(
                    isListening ? Icons.stop_rounded : Icons.mic_rounded,
                    color: isListening
                        ? AppColors.background
                        : AppColors.primaryAccent,
                    size: 34,
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildTextInputField() {
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: _textController,
            style: AppTypography.bodyMedium,
            onSubmitted: (_) => _submitTextPrompt(),
            decoration: InputDecoration(
              hintText: 'Type schedule instructions or thoughts...',
              hintStyle: AppTypography.bodySmall,
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
                borderSide: const BorderSide(color: AppColors.primaryAccent),
              ),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 14,
                vertical: 12,
              ),
            ),
          ),
        ),
        const SizedBox(width: 10),
        IconButton.filled(
          onPressed: _submitTextPrompt,
          style: IconButton.styleFrom(
            backgroundColor: AppColors.primaryAccent,
            foregroundColor: AppColors.background,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            padding: const EdgeInsets.all(12),
          ),
          icon: const Icon(Icons.send_rounded, size: 20),
        ),
      ],
    );
  }
}
