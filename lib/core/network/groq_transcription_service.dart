import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class GroqTranscriptionService {
  static const String _prefsKey = 'groq_api_key';
  static const String _endpoint =
      'https://api.groq.com/openai/v1/audio/transcriptions';

  Future<String?> getApiKey() async {
    const envKey = String.fromEnvironment('GROQ_API_KEY');
    if (envKey.isNotEmpty) return envKey;

    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_prefsKey);
  }

  Future<void> saveApiKey(String key) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefsKey, key.trim());
  }

  Future<String> transcribeAudio(String audioFilePath) async {
    final apiKey = await getApiKey();
    if (apiKey == null || apiKey.isEmpty) {
      throw Exception(
        'Groq API key not found. Please configure GROQ_API_KEY.',
      );
    }

    final file = File(audioFilePath);
    if (!await file.exists()) {
      throw Exception('Audio file does not exist at path: $audioFilePath');
    }

    final request = http.MultipartRequest('POST', Uri.parse(_endpoint))
      ..headers['Authorization'] = 'Bearer $apiKey'
      ..fields['model'] = 'whisper-large-v3'
      ..fields['language'] = 'en'
      ..fields['prompt'] =
          'Organiza productivity assistant commands in English: habits, tasks, timetable, schedule, journal.'
      ..fields['response_format'] = 'json'
      ..files.add(await http.MultipartFile.fromPath('file', audioFilePath));

    final streamedResponse = await request.send();
    final response = await http.Response.fromStream(streamedResponse);

    if (response.statusCode >= 200 && response.statusCode < 300) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      final text = data['text'] as String?;
      return (text ?? '').trim();
    } else {
      throw Exception(
        'Groq Whisper error (${response.statusCode}): ${response.body}',
      );
    }
  }
}
