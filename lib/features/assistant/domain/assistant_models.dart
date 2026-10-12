class AssistantExecutionResult {
  final String userTranscript;
  final String spokenConfirmation;
  final String? executedActionName;
  final Map<String, dynamic>? executedDetails;

  const AssistantExecutionResult({
    required this.userTranscript,
    required this.spokenConfirmation,
    this.executedActionName,
    this.executedDetails,
  });

  @override
  String toString() {
    return 'AssistantExecutionResult(transcript: $userTranscript, confirmation: $spokenConfirmation, action: $executedActionName, details: $executedDetails)';
  }
}
