import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import '../../data/local/app_database.dart';
import '../../features/assistant/domain/assistant_models.dart';

class AssistantAiService {
  static const String _prefsKey = 'groq_api_key';
  static const String _endpoint =
      'https://api.groq.com/openai/v1/chat/completions';
  static const String _model = 'llama-3.3-70b-versatile';

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

  List<Map<String, dynamic>> _getToolsDefinition() {
    return [
      {
        'type': 'function',
        'function': {
          'name': 'get_today_schedule',
          'description': 'Inspect tasks and schedule items for today.',
          'parameters': {
            'type': 'object',
            'properties': {},
            'required': [],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'reschedule_item',
          'description':
              'Reschedule a class, lecture, or habit to a new time window.',
          'parameters': {
            'type': 'object',
            'properties': {
              'titleQuery': {
                'type': 'string',
                'description':
                    'The name or subject of the item to reschedule, e.g. Maths, Gym'
              },
              'newStartTime': {
                'type': 'string',
                'description': 'Start time in HH:mm 24-hour format, e.g. 17:00'
              },
              'newEndTime': {
                'type': 'string',
                'description': 'End time in HH:mm 24-hour format, e.g. 18:30'
              },
            },
            'required': ['titleQuery', 'newStartTime', 'newEndTime'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'create_recurring_habit',
          'description': 'Create a new recurring habit or routine task.',
          'parameters': {
            'type': 'object',
            'properties': {
              'title': {
                'type': 'string',
                'description': 'Name of the habit or task'
              },
              'cadenceUnit': {
                'type': 'string',
                'enum': ['day', 'week'],
                'description': 'Cadence unit: day or week'
              },
              'frequency': {
                'type': 'integer',
                'description':
                    'Target frequency (e.g. 1 for daily, 3 for 3x a week)'
              },
              'durationMinutes': {
                'type': 'integer',
                'description': 'Goal duration in minutes (e.g. 30)'
              },
              'bestTimeStart': {
                'type': 'string',
                'description': 'Optional start time in HH:mm format, e.g. 07:00'
              },
            },
            'required': ['title', 'cadenceUnit', 'frequency'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'remove_schedule_item',
          'description':
              'Cancel or remove a timetable lecture/event or delete a habit.',
          'parameters': {
            'type': 'object',
            'properties': {
              'titleQuery': {
                'type': 'string',
                'description':
                    'The name or subject of the item to cancel or remove'
              },
            },
            'required': ['titleQuery'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'append_journal',
          'description':
              'Save a voice thought, reflection, or journal entry for today.',
          'parameters': {
            'type': 'object',
            'properties': {
              'content': {
                'type': 'string',
                'description': 'The journal entry content or reflection'
              },
              'tags': {
                'type': 'array',
                'items': {'type': 'string'},
                'description':
                    'Optional list of tags, e.g. ["reflection", "gratitude"]'
              },
            },
            'required': ['content'],
          },
        },
      },
    ];
  }

  Future<AssistantExecutionResult> processCommand({
    required String transcript,
    required AppDatabase db,
  }) async {
    final apiKey = await getApiKey();
    if (apiKey == null || apiKey.isEmpty) {
      throw Exception(
        'Groq API key not found. Please configure GROQ_API_KEY.',
      );
    }

    final now = DateTime.now();
    final todayDate =
        '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    final dayOfWeek = now.weekday; // 1 = Mon ... 7 = Sun
    const weekdays = [
      'Monday',
      'Tuesday',
      'Wednesday',
      'Thursday',
      'Friday',
      'Saturday',
      'Sunday'
    ];
    final dayName = weekdays[dayOfWeek - 1];

    final systemPrompt = '''
You are Organiza AI, a proactive, high-discipline voice and text assistant.
Today's Date: $todayDate ($dayName).
Current Time: ${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}.

Rules:
1. When the user asks to inspect their schedule, reschedule, add habits, remove events, or log journal thoughts, call the relevant function tool.
2. Provide concise, clear 1-2 sentence spoken confirmations.
3. Be encouraging and direct.
''';

    final messages = <Map<String, dynamic>>[
      {'role': 'system', 'content': systemPrompt},
      {'role': 'user', 'content': transcript},
    ];

    final tools = _getToolsDefinition();

    // 1. Initial Request to Groq Llama 3.3
    final initialPayload = {
      'model': _model,
      'messages': messages,
      'tools': tools,
      'tool_choice': 'auto',
    };

    final firstResponse = await http.post(
      Uri.parse(_endpoint),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $apiKey',
      },
      body: jsonEncode(initialPayload),
    );

    if (firstResponse.statusCode < 200 || firstResponse.statusCode >= 300) {
      throw Exception(
        'Groq Assistant Error (${firstResponse.statusCode}): ${firstResponse.body}',
      );
    }

    final firstData = jsonDecode(utf8.decode(firstResponse.bodyBytes))
        as Map<String, dynamic>;
    final choices = firstData['choices'] as List<dynamic>?;
    if (choices == null || choices.isEmpty) {
      throw Exception('No response choices from Groq.');
    }

    final firstMessage = choices[0]['message'] as Map<String, dynamic>;
    final toolCalls = firstMessage['tool_calls'] as List<dynamic>?;

    if (toolCalls == null || toolCalls.isEmpty) {
      // Direct text response without tools
      final textContent = firstMessage['content'] as String? ?? 'Done.';
      return AssistantExecutionResult(
        userTranscript: transcript,
        spokenConfirmation: textContent.trim(),
      );
    }

    // 2. Execute Tools on SQLite DB
    String? executedActionName;
    Map<String, dynamic>? executedDetails;

    // Append assistant's message with tool calls
    messages.add(firstMessage);

    for (final rawCall in toolCalls) {
      final call = rawCall as Map<String, dynamic>;
      final function = call['function'] as Map<String, dynamic>;
      final fnName = function['name'] as String;
      final argsString = function['arguments'] as String? ?? '{}';
      final args = jsonDecode(argsString) as Map<String, dynamic>;
      final callId = call['id'] as String? ?? const Uuid().v4();

      dynamic toolResult;

      switch (fnName) {
        case 'get_today_schedule':
          final schedule = await db.getTodaySchedule(todayDate, dayOfWeek);
          final timetableSummary = schedule.timetableEntries.map((e) => {
                'title': e.title,
                'start': e.startTime,
                'end': e.endTime,
                'notes': e.locationOrNotes,
              }).toList();
          final tasksSummary = schedule.tasks.map((t) => {
                'title': t.task.title,
                'logged': t.log?.durationLoggedSeconds ?? 0,
                'target': t.task.targetDurationSeconds,
                'completed': t.log?.isCompleted ?? false,
              }).toList();
          toolResult = {
            'timetable': timetableSummary,
            'tasks': tasksSummary,
          };
          executedActionName = 'Inspected Schedule';
          executedDetails = {
            'events': timetableSummary.length,
            'tasks': tasksSummary.length,
          };
          break;

        case 'reschedule_item':
          final titleQuery = args['titleQuery'] as String? ?? '';
          final newStart = args['newStartTime'] as String? ?? '';
          final newEnd = args['newEndTime'] as String? ?? '';
          final updatedCount = await db.rescheduleItem(
            titleQuery,
            newStart,
            newEnd,
            todayDate,
          );
          toolResult = {
            'status': updatedCount > 0 ? 'success' : 'item_not_found',
            'matchedCount': updatedCount,
            'title': titleQuery,
            'newWindow': '$newStart - $newEnd',
          };
          executedActionName = 'Rescheduled $titleQuery';
          executedDetails = {
            'title': titleQuery,
            'newStart': newStart,
            'newEnd': newEnd,
            'success': updatedCount > 0,
          };
          break;

        case 'create_recurring_habit':
          final title = args['title'] as String? ?? 'Untitled Habit';
          final cadenceUnit = args['cadenceUnit'] as String? ?? 'day';
          final frequency = (args['frequency'] as num?)?.toInt() ?? 1;
          final durationMins = (args['durationMinutes'] as num?)?.toInt();
          final bestTimeStart = args['bestTimeStart'] as String?;

          final taskId = const Uuid().v4();
          final rightNow = DateTime.now();

          await db.insertTask(
            TasksCompanion(
              id: Value(taskId),
              title: Value(title),
              cadenceUnit: Value(cadenceUnit),
              targetFrequency: Value(frequency),
              targetDurationSeconds: Value(
                  durationMins != null ? durationMins * 60 : 1800),
              bestTimeStart: Value(bestTimeStart),
              xpValue: const Value(50),
              createdAt: Value(rightNow),
              updatedAt: Value(rightNow),
              isDeleted: const Value(false),
              syncStatus: const Value('pending'),
            ),
          );

          toolResult = {
            'status': 'created',
            'taskId': taskId,
            'title': title,
          };
          executedActionName = 'Created Habit: $title';
          executedDetails = {
            'title': title,
            'cadence': '${frequency}x / $cadenceUnit',
            'duration': '${durationMins ?? 30}m',
          };
          break;

        case 'remove_schedule_item':
          final titleQuery = args['titleQuery'] as String? ?? '';
          final canceledCount =
              await db.cancelScheduleItem(titleQuery, todayDate);
          toolResult = {
            'status': canceledCount > 0 ? 'canceled' : 'not_found',
            'title': titleQuery,
          };
          executedActionName = 'Removed $titleQuery';
          executedDetails = {
            'title': titleQuery,
            'success': canceledCount > 0,
          };
          break;

        case 'append_journal':
          final content = args['content'] as String? ?? '';
          final rawTags = args['tags'] as List<dynamic>?;
          final tags = rawTags?.map((e) => e.toString()).join(',');

          final journalId = const Uuid().v4();
          await db.insertJournalEntry(
            JournalEntriesCompanion(
              id: Value(journalId),
              entryDate: Value(todayDate),
              content: Value(content),
              tags: Value(tags),
              createdAt: Value(DateTime.now()),
            ),
          );

          toolResult = {
            'status': 'saved',
            'journalId': journalId,
          };
          executedActionName = 'Saved to Journal';
          executedDetails = {
            'content': content.length > 50
                ? '${content.substring(0, 50)}...'
                : content,
          };
          break;

        default:
          toolResult = {'error': 'Unknown tool: $fnName'};
      }

      messages.add({
        'role': 'tool',
        'tool_call_id': callId,
        'content': jsonEncode(toolResult),
      });
    }

    // 3. Second Request to Groq to obtain spoken confirmation
    final followUpPayload = {
      'model': _model,
      'messages': messages,
    };

    final followUpResponse = await http.post(
      Uri.parse(_endpoint),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $apiKey',
      },
      body: jsonEncode(followUpPayload),
    );

    if (followUpResponse.statusCode < 200 ||
        followUpResponse.statusCode >= 300) {
      throw Exception(
        'Groq Confirmation Error (${followUpResponse.statusCode}): ${followUpResponse.body}',
      );
    }

    final followUpData = jsonDecode(utf8.decode(followUpResponse.bodyBytes))
        as Map<String, dynamic>;
    final followUpChoices = followUpData['choices'] as List<dynamic>?;
    final finalSpoken =
        followUpChoices?[0]['message']['content'] as String? ??
            'I have updated your schedule.';

    return AssistantExecutionResult(
      userTranscript: transcript,
      spokenConfirmation: finalSpoken.trim(),
      executedActionName: executedActionName,
      executedDetails: executedDetails,
    );
  }
}
