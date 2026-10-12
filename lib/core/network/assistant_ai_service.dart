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
  static const String _defaultModel = 'qwen/qwen3.8-27b';
  static const String _fallbackModel = 'openai/gpt-oss-120b';

  final List<Map<String, dynamic>> _chatHistory = [];

  List<Map<String, dynamic>> get chatHistory => List.unmodifiable(_chatHistory);

  void clearHistory() {
    _chatHistory.clear();
  }

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
          'name': 'check_habit_status',
          'description':
              'Check if a specific task or habit was completed or logged today, and return duration logged vs target.',
          'parameters': {
            'type': 'object',
            'properties': {
              'task_title_query': {
                'type': 'string',
                'description':
                    'The name or keyword of the habit/task to check, e.g. Reading, Gym, Coding'
              },
            },
            'required': ['task_title_query'],
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

    // Fetch real-time context from SQLite
    final currentSchedule = await db.getTodaySchedule(todayDate, dayOfWeek);
    final timetableContext = currentSchedule.timetableEntries.isEmpty
        ? 'No timetable entries scheduled for today.'
        : currentSchedule.timetableEntries
            .map((e) =>
                '- ${e.startTime} to ${e.endTime}: ${e.title}${e.locationOrNotes != null ? " (${e.locationOrNotes})" : ""}')
            .join('\n');

    final habitContext = currentSchedule.tasks.isEmpty
        ? 'No active habits or tasks.'
        : currentSchedule.tasks.map((t) {
            final loggedMins =
                ((t.log?.durationLoggedSeconds ?? 0) / 60).round();
            final targetMins =
                ((t.task.targetDurationSeconds ?? 0) / 60).round();
            final isDone = t.log?.isCompleted ?? false;
            return '- ${t.task.title}: ${loggedMins}m / ${targetMins}m (${isDone ? "Completed" : "Incomplete"})';
          }).join('\n');

    final systemPrompt = '''
CRITICAL LANGUAGE RULE: You must operate exclusively in English. All parsed intents, tool arguments, and conversational responses must be in English only. Do not interpret or reply in Arabic or any other language, even if phonetic artifacts are present.

You are Organiza AI, a proactive, high-discipline voice and text assistant.
Today's Date: $todayDate ($dayName).
Current Time: ${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}.

Real-time Schedule Context for Today:
Timetable Events:
$timetableContext

Habits & Tasks:
$habitContext

Rules:
1. When the user asks to inspect their schedule, check habit progress, reschedule, add habits, remove events, or log reflections, call the appropriate function tool.
2. If the user asks whether a habit was completed or how much time they logged, call `check_habit_status` or reference the context above.
3. If rescheduling an item that is not found, suggest similar existing items.
4. Provide concise, clear 1-2 sentence spoken confirmations.
5. Be encouraging, concise, and direct.
''';

    final messages = <Map<String, dynamic>>[
      {'role': 'system', 'content': systemPrompt},
      ..._chatHistory,
      {'role': 'user', 'content': transcript},
    ];

    final tools = _getToolsDefinition();

    // 1. Initial Request to Groq
    final initialPayload = {
      'model': _defaultModel,
      'messages': messages,
      'tools': tools,
      'tool_choice': 'auto',
    };

    http.Response firstResponse = await http.post(
      Uri.parse(_endpoint),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $apiKey',
      },
      body: jsonEncode(initialPayload),
    );

    if (firstResponse.statusCode < 200 || firstResponse.statusCode >= 300) {
      // Fallback attempt
      final fallbackPayload = Map<String, dynamic>.from(initialPayload)
        ..['model'] = _fallbackModel;
      final fallbackResponse = await http.post(
        Uri.parse(_endpoint),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode(fallbackPayload),
      );
      if (fallbackResponse.statusCode >= 200 &&
          fallbackResponse.statusCode < 300) {
        firstResponse = fallbackResponse;
      } else {
        throw Exception(
          'Groq Assistant Error (${firstResponse.statusCode}): ${firstResponse.body}',
        );
      }
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
      final textContent = (firstMessage['content'] as String?)?.trim() ?? 'Done.';
      _recordConversationTurn(transcript, textContent);
      return AssistantExecutionResult(
        userTranscript: transcript,
        spokenConfirmation: textContent,
      );
    }

    // 2. Execute Tools on SQLite DB
    String? executedActionName;
    Map<String, dynamic>? executedDetails;

    // Append assistant's message with tool calls to prompt messages
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

        case 'check_habit_status':
          final query = (args['task_title_query'] ??
                  args['taskTitleQuery'] ??
                  args['title'] ??
                  '') as String;
          final habit = await db.findHabitByTitle(query, todayDate);
          if (habit != null) {
            final loggedSeconds = habit.log?.durationLoggedSeconds ?? 0;
            final targetSeconds = habit.task.targetDurationSeconds ?? 0;
            final isCompleted = habit.log?.isCompleted ?? false;
            final loggedMins = (loggedSeconds / 60).round();
            final targetMins = (targetSeconds / 60).round();

            toolResult = {
              'found': true,
              'title': habit.task.title,
              'loggedMinutes': loggedMins,
              'targetMinutes': targetMins,
              'isCompleted': isCompleted,
              'cadence':
                  '${habit.task.targetFrequency}x / ${habit.task.cadenceUnit}',
            };
            executedActionName = 'Checked Habit: ${habit.task.title}';
            executedDetails = {
              'title': habit.task.title,
              'logged': '${loggedMins}m / ${targetMins}m',
              'completed': isCompleted,
            };
          } else {
            final similar = await db.getSimilarItemTitles(query);
            toolResult = {
              'found': false,
              'query': query,
              'message': 'No habit found matching "$query".',
              'suggestions': similar.take(3).toList(),
            };
            executedActionName = 'Habit Not Found';
            executedDetails = {
              'query': query,
              'found': false,
            };
          }
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
          if (updatedCount > 0) {
            toolResult = {
              'status': 'success',
              'matchedCount': updatedCount,
              'title': titleQuery,
              'newWindow': '$newStart - $newEnd',
            };
            executedActionName = 'Rescheduled $titleQuery';
            executedDetails = {
              'title': titleQuery,
              'newStart': newStart,
              'newEnd': newEnd,
              'success': true,
            };
          } else {
            final similar = await db.getSimilarItemTitles(titleQuery);
            toolResult = {
              'status': 'item_not_found',
              'title': titleQuery,
              'suggestions': similar.take(3).toList(),
              'message':
                  'No scheduled item matching "$titleQuery" was found. Did you mean: ${similar.take(3).join(", ")}?',
            };
            executedActionName = 'Reschedule Failed';
            executedDetails = {
              'title': titleQuery,
              'success': false,
              'suggestions': similar.take(3).toList(),
            };
          }
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
      'model': _defaultModel,
      'messages': messages,
    };

    http.Response followUpResponse = await http.post(
      Uri.parse(_endpoint),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $apiKey',
      },
      body: jsonEncode(followUpPayload),
    );

    if (followUpResponse.statusCode < 200 ||
        followUpResponse.statusCode >= 300) {
      final fallbackFollowUp = Map<String, dynamic>.from(followUpPayload)
        ..['model'] = _fallbackModel;
      final fallbackResponse = await http.post(
        Uri.parse(_endpoint),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode(fallbackFollowUp),
      );
      if (fallbackResponse.statusCode >= 200 &&
          fallbackResponse.statusCode < 300) {
        followUpResponse = fallbackResponse;
      } else {
        throw Exception(
          'Groq Confirmation Error (${followUpResponse.statusCode}): ${followUpResponse.body}',
        );
      }
    }

    final followUpData = jsonDecode(utf8.decode(followUpResponse.bodyBytes))
        as Map<String, dynamic>;
    final followUpChoices = followUpData['choices'] as List<dynamic>?;
    final finalSpoken =
        (followUpChoices?[0]['message']['content'] as String?)?.trim() ??
            'I have updated your schedule.';

    _recordConversationTurn(transcript, finalSpoken);

    return AssistantExecutionResult(
      userTranscript: transcript,
      spokenConfirmation: finalSpoken,
      executedActionName: executedActionName,
      executedDetails: executedDetails,
    );
  }

  void _recordConversationTurn(String userText, String assistantText) {
    _chatHistory.add({'role': 'user', 'content': userText});
    _chatHistory.add({'role': 'assistant', 'content': assistantText});
    if (_chatHistory.length > 20) {
      _chatHistory.removeRange(0, _chatHistory.length - 20);
    }
  }
}
