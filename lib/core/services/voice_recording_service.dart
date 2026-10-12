import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

class VoiceRecordingService {
  final AudioRecorder _recorder = AudioRecorder();
  String? _currentFilePath;

  Future<bool> hasPermission() async {
    return await _recorder.hasPermission();
  }

  Future<void> startRecording() async {
    final hasPerm = await hasPermission();
    if (!hasPerm) {
      throw Exception('Microphone permission not granted');
    }

    final tempDir = await getTemporaryDirectory();
    _currentFilePath = p.join(
      tempDir.path,
      'organiza_mic_input_${DateTime.now().millisecondsSinceEpoch}.m4a',
    );

    await _recorder.start(
      const RecordConfig(
        encoder: AudioEncoder.aacLc,
        bitRate: 128000,
        sampleRate: 44100,
      ),
      path: _currentFilePath!,
    );
  }

  Future<String?> stopRecording() async {
    final path = await _recorder.stop();
    return path ?? _currentFilePath;
  }

  Future<void> cancelRecording() async {
    await _recorder.cancel();
    if (_currentFilePath != null) {
      final file = File(_currentFilePath!);
      if (await file.exists()) {
        try {
          await file.delete();
        } catch (_) {}
      }
      _currentFilePath = null;
    }
  }

  void dispose() {
    _recorder.dispose();
  }
}
