import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/core/network/assistant_ai_service.dart';

void main() {
  group('AssistantAiService Tool Engine Tests', () {
    late AssistantAiService service;

    setUp(() {
      service = AssistantAiService();
    });

    test('tools definition contains all required assistant tools including add_timetable_event', () {
      final tools = service.getToolsDefinition();
      final toolNames = tools
          .map((t) => (t['function'] as Map<String, dynamic>)['name'] as String)
          .toList();

      expect(toolNames, contains('get_today_schedule'));
      expect(toolNames, contains('add_timetable_event'));
      expect(toolNames, contains('check_habit_status'));
      expect(toolNames, contains('reschedule_item'));
      expect(toolNames, contains('create_recurring_habit'));
      expect(toolNames, contains('remove_schedule_item'));
      expect(toolNames, contains('append_journal'));
    });

    test('add_timetable_event tool schema requires title, startTime, and endTime', () {
      final tools = service.getToolsDefinition();
      final addTool = tools.firstWhere(
        (t) => (t['function'] as Map<String, dynamic>)['name'] == 'add_timetable_event',
      );
      final function = addTool['function'] as Map<String, dynamic>;
      final parameters = function['parameters'] as Map<String, dynamic>;
      final requiredFields = parameters['required'] as List<dynamic>;

      expect(requiredFields, contains('title'));
      expect(requiredFields, contains('startTime'));
      expect(requiredFields, contains('endTime'));
      
      final properties = parameters['properties'] as Map<String, dynamic>;
      expect(properties.containsKey('specificDate'), isTrue);
      expect(properties.containsKey('dayOfWeek'), isTrue);
      expect(properties.containsKey('notes'), isTrue);
    });

    test('chat history maintains conversation turns and clears properly', () {
      expect(service.chatHistory.isEmpty, isTrue);
      service.clearHistory();
      expect(service.chatHistory.isEmpty, isTrue);
    });
  });
}
