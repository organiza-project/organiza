// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $TasksTable extends Tasks with TableInfo<$TasksTable, Task> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TasksTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 255,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _cadenceUnitMeta = const VerificationMeta(
    'cadenceUnit',
  );
  @override
  late final GeneratedColumn<String> cadenceUnit = GeneratedColumn<String>(
    'cadence_unit',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _targetFrequencyMeta = const VerificationMeta(
    'targetFrequency',
  );
  @override
  late final GeneratedColumn<int> targetFrequency = GeneratedColumn<int>(
    'target_frequency',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _targetDurationSecondsMeta =
      const VerificationMeta('targetDurationSeconds');
  @override
  late final GeneratedColumn<int> targetDurationSeconds = GeneratedColumn<int>(
    'target_duration_seconds',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _bestTimeStartMeta = const VerificationMeta(
    'bestTimeStart',
  );
  @override
  late final GeneratedColumn<String> bestTimeStart = GeneratedColumn<String>(
    'best_time_start',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _bestTimeEndMeta = const VerificationMeta(
    'bestTimeEnd',
  );
  @override
  late final GeneratedColumn<String> bestTimeEnd = GeneratedColumn<String>(
    'best_time_end',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _xpValueMeta = const VerificationMeta(
    'xpValue',
  );
  @override
  late final GeneratedColumn<int> xpValue = GeneratedColumn<int>(
    'xp_value',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(50),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _isDeletedMeta = const VerificationMeta(
    'isDeleted',
  );
  @override
  late final GeneratedColumn<bool> isDeleted = GeneratedColumn<bool>(
    'is_deleted',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_deleted" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _syncStatusMeta = const VerificationMeta(
    'syncStatus',
  );
  @override
  late final GeneratedColumn<String> syncStatus = GeneratedColumn<String>(
    'sync_status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('pending'),
  );
  static const VerificationMeta _linkedPackageNameMeta = const VerificationMeta(
    'linkedPackageName',
  );
  @override
  late final GeneratedColumn<String> linkedPackageName =
      GeneratedColumn<String>(
        'linked_package_name',
        aliasedName,
        true,
        type: DriftSqlType.string,
        requiredDuringInsert: false,
      );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    description,
    cadenceUnit,
    targetFrequency,
    targetDurationSeconds,
    bestTimeStart,
    bestTimeEnd,
    xpValue,
    createdAt,
    updatedAt,
    isDeleted,
    syncStatus,
    linkedPackageName,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'tasks';
  @override
  VerificationContext validateIntegrity(
    Insertable<Task> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('cadence_unit')) {
      context.handle(
        _cadenceUnitMeta,
        cadenceUnit.isAcceptableOrUnknown(
          data['cadence_unit']!,
          _cadenceUnitMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_cadenceUnitMeta);
    }
    if (data.containsKey('target_frequency')) {
      context.handle(
        _targetFrequencyMeta,
        targetFrequency.isAcceptableOrUnknown(
          data['target_frequency']!,
          _targetFrequencyMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_targetFrequencyMeta);
    }
    if (data.containsKey('target_duration_seconds')) {
      context.handle(
        _targetDurationSecondsMeta,
        targetDurationSeconds.isAcceptableOrUnknown(
          data['target_duration_seconds']!,
          _targetDurationSecondsMeta,
        ),
      );
    }
    if (data.containsKey('best_time_start')) {
      context.handle(
        _bestTimeStartMeta,
        bestTimeStart.isAcceptableOrUnknown(
          data['best_time_start']!,
          _bestTimeStartMeta,
        ),
      );
    }
    if (data.containsKey('best_time_end')) {
      context.handle(
        _bestTimeEndMeta,
        bestTimeEnd.isAcceptableOrUnknown(
          data['best_time_end']!,
          _bestTimeEndMeta,
        ),
      );
    }
    if (data.containsKey('xp_value')) {
      context.handle(
        _xpValueMeta,
        xpValue.isAcceptableOrUnknown(data['xp_value']!, _xpValueMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    if (data.containsKey('is_deleted')) {
      context.handle(
        _isDeletedMeta,
        isDeleted.isAcceptableOrUnknown(data['is_deleted']!, _isDeletedMeta),
      );
    }
    if (data.containsKey('sync_status')) {
      context.handle(
        _syncStatusMeta,
        syncStatus.isAcceptableOrUnknown(data['sync_status']!, _syncStatusMeta),
      );
    }
    if (data.containsKey('linked_package_name')) {
      context.handle(
        _linkedPackageNameMeta,
        linkedPackageName.isAcceptableOrUnknown(
          data['linked_package_name']!,
          _linkedPackageNameMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Task map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Task(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      ),
      cadenceUnit: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}cadence_unit'],
      )!,
      targetFrequency: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}target_frequency'],
      )!,
      targetDurationSeconds: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}target_duration_seconds'],
      ),
      bestTimeStart: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}best_time_start'],
      ),
      bestTimeEnd: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}best_time_end'],
      ),
      xpValue: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}xp_value'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      isDeleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_deleted'],
      )!,
      syncStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}sync_status'],
      )!,
      linkedPackageName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}linked_package_name'],
      ),
    );
  }

  @override
  $TasksTable createAlias(String alias) {
    return $TasksTable(attachedDatabase, alias);
  }
}

class Task extends DataClass implements Insertable<Task> {
  final String id;
  final String title;
  final String? description;
  final String cadenceUnit;
  final int targetFrequency;
  final int? targetDurationSeconds;
  final String? bestTimeStart;
  final String? bestTimeEnd;
  final int xpValue;
  final DateTime createdAt;
  final DateTime updatedAt;
  final bool isDeleted;
  final String syncStatus;
  final String? linkedPackageName;
  const Task({
    required this.id,
    required this.title,
    this.description,
    required this.cadenceUnit,
    required this.targetFrequency,
    this.targetDurationSeconds,
    this.bestTimeStart,
    this.bestTimeEnd,
    required this.xpValue,
    required this.createdAt,
    required this.updatedAt,
    required this.isDeleted,
    required this.syncStatus,
    this.linkedPackageName,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['title'] = Variable<String>(title);
    if (!nullToAbsent || description != null) {
      map['description'] = Variable<String>(description);
    }
    map['cadence_unit'] = Variable<String>(cadenceUnit);
    map['target_frequency'] = Variable<int>(targetFrequency);
    if (!nullToAbsent || targetDurationSeconds != null) {
      map['target_duration_seconds'] = Variable<int>(targetDurationSeconds);
    }
    if (!nullToAbsent || bestTimeStart != null) {
      map['best_time_start'] = Variable<String>(bestTimeStart);
    }
    if (!nullToAbsent || bestTimeEnd != null) {
      map['best_time_end'] = Variable<String>(bestTimeEnd);
    }
    map['xp_value'] = Variable<int>(xpValue);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['is_deleted'] = Variable<bool>(isDeleted);
    map['sync_status'] = Variable<String>(syncStatus);
    if (!nullToAbsent || linkedPackageName != null) {
      map['linked_package_name'] = Variable<String>(linkedPackageName);
    }
    return map;
  }

  TasksCompanion toCompanion(bool nullToAbsent) {
    return TasksCompanion(
      id: Value(id),
      title: Value(title),
      description: description == null && nullToAbsent
          ? const Value.absent()
          : Value(description),
      cadenceUnit: Value(cadenceUnit),
      targetFrequency: Value(targetFrequency),
      targetDurationSeconds: targetDurationSeconds == null && nullToAbsent
          ? const Value.absent()
          : Value(targetDurationSeconds),
      bestTimeStart: bestTimeStart == null && nullToAbsent
          ? const Value.absent()
          : Value(bestTimeStart),
      bestTimeEnd: bestTimeEnd == null && nullToAbsent
          ? const Value.absent()
          : Value(bestTimeEnd),
      xpValue: Value(xpValue),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      isDeleted: Value(isDeleted),
      syncStatus: Value(syncStatus),
      linkedPackageName: linkedPackageName == null && nullToAbsent
          ? const Value.absent()
          : Value(linkedPackageName),
    );
  }

  factory Task.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Task(
      id: serializer.fromJson<String>(json['id']),
      title: serializer.fromJson<String>(json['title']),
      description: serializer.fromJson<String?>(json['description']),
      cadenceUnit: serializer.fromJson<String>(json['cadenceUnit']),
      targetFrequency: serializer.fromJson<int>(json['targetFrequency']),
      targetDurationSeconds: serializer.fromJson<int?>(
        json['targetDurationSeconds'],
      ),
      bestTimeStart: serializer.fromJson<String?>(json['bestTimeStart']),
      bestTimeEnd: serializer.fromJson<String?>(json['bestTimeEnd']),
      xpValue: serializer.fromJson<int>(json['xpValue']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      isDeleted: serializer.fromJson<bool>(json['isDeleted']),
      syncStatus: serializer.fromJson<String>(json['syncStatus']),
      linkedPackageName: serializer.fromJson<String?>(
        json['linkedPackageName'],
      ),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'title': serializer.toJson<String>(title),
      'description': serializer.toJson<String?>(description),
      'cadenceUnit': serializer.toJson<String>(cadenceUnit),
      'targetFrequency': serializer.toJson<int>(targetFrequency),
      'targetDurationSeconds': serializer.toJson<int?>(targetDurationSeconds),
      'bestTimeStart': serializer.toJson<String?>(bestTimeStart),
      'bestTimeEnd': serializer.toJson<String?>(bestTimeEnd),
      'xpValue': serializer.toJson<int>(xpValue),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'isDeleted': serializer.toJson<bool>(isDeleted),
      'syncStatus': serializer.toJson<String>(syncStatus),
      'linkedPackageName': serializer.toJson<String?>(linkedPackageName),
    };
  }

  Task copyWith({
    String? id,
    String? title,
    Value<String?> description = const Value.absent(),
    String? cadenceUnit,
    int? targetFrequency,
    Value<int?> targetDurationSeconds = const Value.absent(),
    Value<String?> bestTimeStart = const Value.absent(),
    Value<String?> bestTimeEnd = const Value.absent(),
    int? xpValue,
    DateTime? createdAt,
    DateTime? updatedAt,
    bool? isDeleted,
    String? syncStatus,
    Value<String?> linkedPackageName = const Value.absent(),
  }) => Task(
    id: id ?? this.id,
    title: title ?? this.title,
    description: description.present ? description.value : this.description,
    cadenceUnit: cadenceUnit ?? this.cadenceUnit,
    targetFrequency: targetFrequency ?? this.targetFrequency,
    targetDurationSeconds: targetDurationSeconds.present
        ? targetDurationSeconds.value
        : this.targetDurationSeconds,
    bestTimeStart: bestTimeStart.present
        ? bestTimeStart.value
        : this.bestTimeStart,
    bestTimeEnd: bestTimeEnd.present ? bestTimeEnd.value : this.bestTimeEnd,
    xpValue: xpValue ?? this.xpValue,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    isDeleted: isDeleted ?? this.isDeleted,
    syncStatus: syncStatus ?? this.syncStatus,
    linkedPackageName: linkedPackageName.present
        ? linkedPackageName.value
        : this.linkedPackageName,
  );
  Task copyWithCompanion(TasksCompanion data) {
    return Task(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      description: data.description.present
          ? data.description.value
          : this.description,
      cadenceUnit: data.cadenceUnit.present
          ? data.cadenceUnit.value
          : this.cadenceUnit,
      targetFrequency: data.targetFrequency.present
          ? data.targetFrequency.value
          : this.targetFrequency,
      targetDurationSeconds: data.targetDurationSeconds.present
          ? data.targetDurationSeconds.value
          : this.targetDurationSeconds,
      bestTimeStart: data.bestTimeStart.present
          ? data.bestTimeStart.value
          : this.bestTimeStart,
      bestTimeEnd: data.bestTimeEnd.present
          ? data.bestTimeEnd.value
          : this.bestTimeEnd,
      xpValue: data.xpValue.present ? data.xpValue.value : this.xpValue,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      isDeleted: data.isDeleted.present ? data.isDeleted.value : this.isDeleted,
      syncStatus: data.syncStatus.present
          ? data.syncStatus.value
          : this.syncStatus,
      linkedPackageName: data.linkedPackageName.present
          ? data.linkedPackageName.value
          : this.linkedPackageName,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Task(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('cadenceUnit: $cadenceUnit, ')
          ..write('targetFrequency: $targetFrequency, ')
          ..write('targetDurationSeconds: $targetDurationSeconds, ')
          ..write('bestTimeStart: $bestTimeStart, ')
          ..write('bestTimeEnd: $bestTimeEnd, ')
          ..write('xpValue: $xpValue, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isDeleted: $isDeleted, ')
          ..write('syncStatus: $syncStatus, ')
          ..write('linkedPackageName: $linkedPackageName')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    description,
    cadenceUnit,
    targetFrequency,
    targetDurationSeconds,
    bestTimeStart,
    bestTimeEnd,
    xpValue,
    createdAt,
    updatedAt,
    isDeleted,
    syncStatus,
    linkedPackageName,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Task &&
          other.id == this.id &&
          other.title == this.title &&
          other.description == this.description &&
          other.cadenceUnit == this.cadenceUnit &&
          other.targetFrequency == this.targetFrequency &&
          other.targetDurationSeconds == this.targetDurationSeconds &&
          other.bestTimeStart == this.bestTimeStart &&
          other.bestTimeEnd == this.bestTimeEnd &&
          other.xpValue == this.xpValue &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.isDeleted == this.isDeleted &&
          other.syncStatus == this.syncStatus &&
          other.linkedPackageName == this.linkedPackageName);
}

class TasksCompanion extends UpdateCompanion<Task> {
  final Value<String> id;
  final Value<String> title;
  final Value<String?> description;
  final Value<String> cadenceUnit;
  final Value<int> targetFrequency;
  final Value<int?> targetDurationSeconds;
  final Value<String?> bestTimeStart;
  final Value<String?> bestTimeEnd;
  final Value<int> xpValue;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<bool> isDeleted;
  final Value<String> syncStatus;
  final Value<String?> linkedPackageName;
  final Value<int> rowid;
  const TasksCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.description = const Value.absent(),
    this.cadenceUnit = const Value.absent(),
    this.targetFrequency = const Value.absent(),
    this.targetDurationSeconds = const Value.absent(),
    this.bestTimeStart = const Value.absent(),
    this.bestTimeEnd = const Value.absent(),
    this.xpValue = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.isDeleted = const Value.absent(),
    this.syncStatus = const Value.absent(),
    this.linkedPackageName = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TasksCompanion.insert({
    required String id,
    required String title,
    this.description = const Value.absent(),
    required String cadenceUnit,
    required int targetFrequency,
    this.targetDurationSeconds = const Value.absent(),
    this.bestTimeStart = const Value.absent(),
    this.bestTimeEnd = const Value.absent(),
    this.xpValue = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.isDeleted = const Value.absent(),
    this.syncStatus = const Value.absent(),
    this.linkedPackageName = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       title = Value(title),
       cadenceUnit = Value(cadenceUnit),
       targetFrequency = Value(targetFrequency);
  static Insertable<Task> custom({
    Expression<String>? id,
    Expression<String>? title,
    Expression<String>? description,
    Expression<String>? cadenceUnit,
    Expression<int>? targetFrequency,
    Expression<int>? targetDurationSeconds,
    Expression<String>? bestTimeStart,
    Expression<String>? bestTimeEnd,
    Expression<int>? xpValue,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<bool>? isDeleted,
    Expression<String>? syncStatus,
    Expression<String>? linkedPackageName,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (description != null) 'description': description,
      if (cadenceUnit != null) 'cadence_unit': cadenceUnit,
      if (targetFrequency != null) 'target_frequency': targetFrequency,
      if (targetDurationSeconds != null)
        'target_duration_seconds': targetDurationSeconds,
      if (bestTimeStart != null) 'best_time_start': bestTimeStart,
      if (bestTimeEnd != null) 'best_time_end': bestTimeEnd,
      if (xpValue != null) 'xp_value': xpValue,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (isDeleted != null) 'is_deleted': isDeleted,
      if (syncStatus != null) 'sync_status': syncStatus,
      if (linkedPackageName != null) 'linked_package_name': linkedPackageName,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TasksCompanion copyWith({
    Value<String>? id,
    Value<String>? title,
    Value<String?>? description,
    Value<String>? cadenceUnit,
    Value<int>? targetFrequency,
    Value<int?>? targetDurationSeconds,
    Value<String?>? bestTimeStart,
    Value<String?>? bestTimeEnd,
    Value<int>? xpValue,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<bool>? isDeleted,
    Value<String>? syncStatus,
    Value<String?>? linkedPackageName,
    Value<int>? rowid,
  }) {
    return TasksCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      cadenceUnit: cadenceUnit ?? this.cadenceUnit,
      targetFrequency: targetFrequency ?? this.targetFrequency,
      targetDurationSeconds:
          targetDurationSeconds ?? this.targetDurationSeconds,
      bestTimeStart: bestTimeStart ?? this.bestTimeStart,
      bestTimeEnd: bestTimeEnd ?? this.bestTimeEnd,
      xpValue: xpValue ?? this.xpValue,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      isDeleted: isDeleted ?? this.isDeleted,
      syncStatus: syncStatus ?? this.syncStatus,
      linkedPackageName: linkedPackageName ?? this.linkedPackageName,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (cadenceUnit.present) {
      map['cadence_unit'] = Variable<String>(cadenceUnit.value);
    }
    if (targetFrequency.present) {
      map['target_frequency'] = Variable<int>(targetFrequency.value);
    }
    if (targetDurationSeconds.present) {
      map['target_duration_seconds'] = Variable<int>(
        targetDurationSeconds.value,
      );
    }
    if (bestTimeStart.present) {
      map['best_time_start'] = Variable<String>(bestTimeStart.value);
    }
    if (bestTimeEnd.present) {
      map['best_time_end'] = Variable<String>(bestTimeEnd.value);
    }
    if (xpValue.present) {
      map['xp_value'] = Variable<int>(xpValue.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (isDeleted.present) {
      map['is_deleted'] = Variable<bool>(isDeleted.value);
    }
    if (syncStatus.present) {
      map['sync_status'] = Variable<String>(syncStatus.value);
    }
    if (linkedPackageName.present) {
      map['linked_package_name'] = Variable<String>(linkedPackageName.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TasksCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('description: $description, ')
          ..write('cadenceUnit: $cadenceUnit, ')
          ..write('targetFrequency: $targetFrequency, ')
          ..write('targetDurationSeconds: $targetDurationSeconds, ')
          ..write('bestTimeStart: $bestTimeStart, ')
          ..write('bestTimeEnd: $bestTimeEnd, ')
          ..write('xpValue: $xpValue, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isDeleted: $isDeleted, ')
          ..write('syncStatus: $syncStatus, ')
          ..write('linkedPackageName: $linkedPackageName, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $DailyLogsTable extends DailyLogs
    with TableInfo<$DailyLogsTable, DailyLog> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DailyLogsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _taskIdMeta = const VerificationMeta('taskId');
  @override
  late final GeneratedColumn<String> taskId = GeneratedColumn<String>(
    'task_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES tasks (id)',
    ),
  );
  static const VerificationMeta _logDateMeta = const VerificationMeta(
    'logDate',
  );
  @override
  late final GeneratedColumn<String> logDate = GeneratedColumn<String>(
    'log_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _durationLoggedSecondsMeta =
      const VerificationMeta('durationLoggedSeconds');
  @override
  late final GeneratedColumn<int> durationLoggedSeconds = GeneratedColumn<int>(
    'duration_logged_seconds',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _xpAwardedMeta = const VerificationMeta(
    'xpAwarded',
  );
  @override
  late final GeneratedColumn<int> xpAwarded = GeneratedColumn<int>(
    'xp_awarded',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _notesMeta = const VerificationMeta('notes');
  @override
  late final GeneratedColumn<String> notes = GeneratedColumn<String>(
    'notes',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    taskId,
    logDate,
    durationLoggedSeconds,
    isCompleted,
    xpAwarded,
    notes,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'daily_logs';
  @override
  VerificationContext validateIntegrity(
    Insertable<DailyLog> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('task_id')) {
      context.handle(
        _taskIdMeta,
        taskId.isAcceptableOrUnknown(data['task_id']!, _taskIdMeta),
      );
    } else if (isInserting) {
      context.missing(_taskIdMeta);
    }
    if (data.containsKey('log_date')) {
      context.handle(
        _logDateMeta,
        logDate.isAcceptableOrUnknown(data['log_date']!, _logDateMeta),
      );
    } else if (isInserting) {
      context.missing(_logDateMeta);
    }
    if (data.containsKey('duration_logged_seconds')) {
      context.handle(
        _durationLoggedSecondsMeta,
        durationLoggedSeconds.isAcceptableOrUnknown(
          data['duration_logged_seconds']!,
          _durationLoggedSecondsMeta,
        ),
      );
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('xp_awarded')) {
      context.handle(
        _xpAwardedMeta,
        xpAwarded.isAcceptableOrUnknown(data['xp_awarded']!, _xpAwardedMeta),
      );
    }
    if (data.containsKey('notes')) {
      context.handle(
        _notesMeta,
        notes.isAcceptableOrUnknown(data['notes']!, _notesMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  DailyLog map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return DailyLog(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      taskId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}task_id'],
      )!,
      logDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}log_date'],
      )!,
      durationLoggedSeconds: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}duration_logged_seconds'],
      )!,
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      )!,
      xpAwarded: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}xp_awarded'],
      )!,
      notes: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}notes'],
      ),
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $DailyLogsTable createAlias(String alias) {
    return $DailyLogsTable(attachedDatabase, alias);
  }
}

class DailyLog extends DataClass implements Insertable<DailyLog> {
  final String id;
  final String taskId;
  final String logDate;
  final int durationLoggedSeconds;
  final bool isCompleted;
  final int xpAwarded;
  final String? notes;
  final DateTime updatedAt;
  const DailyLog({
    required this.id,
    required this.taskId,
    required this.logDate,
    required this.durationLoggedSeconds,
    required this.isCompleted,
    required this.xpAwarded,
    this.notes,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['task_id'] = Variable<String>(taskId);
    map['log_date'] = Variable<String>(logDate);
    map['duration_logged_seconds'] = Variable<int>(durationLoggedSeconds);
    map['is_completed'] = Variable<bool>(isCompleted);
    map['xp_awarded'] = Variable<int>(xpAwarded);
    if (!nullToAbsent || notes != null) {
      map['notes'] = Variable<String>(notes);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  DailyLogsCompanion toCompanion(bool nullToAbsent) {
    return DailyLogsCompanion(
      id: Value(id),
      taskId: Value(taskId),
      logDate: Value(logDate),
      durationLoggedSeconds: Value(durationLoggedSeconds),
      isCompleted: Value(isCompleted),
      xpAwarded: Value(xpAwarded),
      notes: notes == null && nullToAbsent
          ? const Value.absent()
          : Value(notes),
      updatedAt: Value(updatedAt),
    );
  }

  factory DailyLog.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return DailyLog(
      id: serializer.fromJson<String>(json['id']),
      taskId: serializer.fromJson<String>(json['taskId']),
      logDate: serializer.fromJson<String>(json['logDate']),
      durationLoggedSeconds: serializer.fromJson<int>(
        json['durationLoggedSeconds'],
      ),
      isCompleted: serializer.fromJson<bool>(json['isCompleted']),
      xpAwarded: serializer.fromJson<int>(json['xpAwarded']),
      notes: serializer.fromJson<String?>(json['notes']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'taskId': serializer.toJson<String>(taskId),
      'logDate': serializer.toJson<String>(logDate),
      'durationLoggedSeconds': serializer.toJson<int>(durationLoggedSeconds),
      'isCompleted': serializer.toJson<bool>(isCompleted),
      'xpAwarded': serializer.toJson<int>(xpAwarded),
      'notes': serializer.toJson<String?>(notes),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  DailyLog copyWith({
    String? id,
    String? taskId,
    String? logDate,
    int? durationLoggedSeconds,
    bool? isCompleted,
    int? xpAwarded,
    Value<String?> notes = const Value.absent(),
    DateTime? updatedAt,
  }) => DailyLog(
    id: id ?? this.id,
    taskId: taskId ?? this.taskId,
    logDate: logDate ?? this.logDate,
    durationLoggedSeconds: durationLoggedSeconds ?? this.durationLoggedSeconds,
    isCompleted: isCompleted ?? this.isCompleted,
    xpAwarded: xpAwarded ?? this.xpAwarded,
    notes: notes.present ? notes.value : this.notes,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  DailyLog copyWithCompanion(DailyLogsCompanion data) {
    return DailyLog(
      id: data.id.present ? data.id.value : this.id,
      taskId: data.taskId.present ? data.taskId.value : this.taskId,
      logDate: data.logDate.present ? data.logDate.value : this.logDate,
      durationLoggedSeconds: data.durationLoggedSeconds.present
          ? data.durationLoggedSeconds.value
          : this.durationLoggedSeconds,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      xpAwarded: data.xpAwarded.present ? data.xpAwarded.value : this.xpAwarded,
      notes: data.notes.present ? data.notes.value : this.notes,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('DailyLog(')
          ..write('id: $id, ')
          ..write('taskId: $taskId, ')
          ..write('logDate: $logDate, ')
          ..write('durationLoggedSeconds: $durationLoggedSeconds, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('xpAwarded: $xpAwarded, ')
          ..write('notes: $notes, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    taskId,
    logDate,
    durationLoggedSeconds,
    isCompleted,
    xpAwarded,
    notes,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is DailyLog &&
          other.id == this.id &&
          other.taskId == this.taskId &&
          other.logDate == this.logDate &&
          other.durationLoggedSeconds == this.durationLoggedSeconds &&
          other.isCompleted == this.isCompleted &&
          other.xpAwarded == this.xpAwarded &&
          other.notes == this.notes &&
          other.updatedAt == this.updatedAt);
}

class DailyLogsCompanion extends UpdateCompanion<DailyLog> {
  final Value<String> id;
  final Value<String> taskId;
  final Value<String> logDate;
  final Value<int> durationLoggedSeconds;
  final Value<bool> isCompleted;
  final Value<int> xpAwarded;
  final Value<String?> notes;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const DailyLogsCompanion({
    this.id = const Value.absent(),
    this.taskId = const Value.absent(),
    this.logDate = const Value.absent(),
    this.durationLoggedSeconds = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.xpAwarded = const Value.absent(),
    this.notes = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  DailyLogsCompanion.insert({
    required String id,
    required String taskId,
    required String logDate,
    this.durationLoggedSeconds = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.xpAwarded = const Value.absent(),
    this.notes = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       taskId = Value(taskId),
       logDate = Value(logDate);
  static Insertable<DailyLog> custom({
    Expression<String>? id,
    Expression<String>? taskId,
    Expression<String>? logDate,
    Expression<int>? durationLoggedSeconds,
    Expression<bool>? isCompleted,
    Expression<int>? xpAwarded,
    Expression<String>? notes,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (taskId != null) 'task_id': taskId,
      if (logDate != null) 'log_date': logDate,
      if (durationLoggedSeconds != null)
        'duration_logged_seconds': durationLoggedSeconds,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (xpAwarded != null) 'xp_awarded': xpAwarded,
      if (notes != null) 'notes': notes,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  DailyLogsCompanion copyWith({
    Value<String>? id,
    Value<String>? taskId,
    Value<String>? logDate,
    Value<int>? durationLoggedSeconds,
    Value<bool>? isCompleted,
    Value<int>? xpAwarded,
    Value<String?>? notes,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return DailyLogsCompanion(
      id: id ?? this.id,
      taskId: taskId ?? this.taskId,
      logDate: logDate ?? this.logDate,
      durationLoggedSeconds:
          durationLoggedSeconds ?? this.durationLoggedSeconds,
      isCompleted: isCompleted ?? this.isCompleted,
      xpAwarded: xpAwarded ?? this.xpAwarded,
      notes: notes ?? this.notes,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (taskId.present) {
      map['task_id'] = Variable<String>(taskId.value);
    }
    if (logDate.present) {
      map['log_date'] = Variable<String>(logDate.value);
    }
    if (durationLoggedSeconds.present) {
      map['duration_logged_seconds'] = Variable<int>(
        durationLoggedSeconds.value,
      );
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (xpAwarded.present) {
      map['xp_awarded'] = Variable<int>(xpAwarded.value);
    }
    if (notes.present) {
      map['notes'] = Variable<String>(notes.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DailyLogsCompanion(')
          ..write('id: $id, ')
          ..write('taskId: $taskId, ')
          ..write('logDate: $logDate, ')
          ..write('durationLoggedSeconds: $durationLoggedSeconds, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('xpAwarded: $xpAwarded, ')
          ..write('notes: $notes, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $AppLimitsTable extends AppLimits
    with TableInfo<$AppLimitsTable, AppLimit> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AppLimitsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _packageNameMeta = const VerificationMeta(
    'packageName',
  );
  @override
  late final GeneratedColumn<String> packageName = GeneratedColumn<String>(
    'package_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _appNameMeta = const VerificationMeta(
    'appName',
  );
  @override
  late final GeneratedColumn<String> appName = GeneratedColumn<String>(
    'app_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _dailyCapMinutesMeta = const VerificationMeta(
    'dailyCapMinutes',
  );
  @override
  late final GeneratedColumn<int> dailyCapMinutes = GeneratedColumn<int>(
    'daily_cap_minutes',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _curfewStartMeta = const VerificationMeta(
    'curfewStart',
  );
  @override
  late final GeneratedColumn<String> curfewStart = GeneratedColumn<String>(
    'curfew_start',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _curfewEndMeta = const VerificationMeta(
    'curfewEnd',
  );
  @override
  late final GeneratedColumn<String> curfewEnd = GeneratedColumn<String>(
    'curfew_end',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _blockReelsShortsMeta = const VerificationMeta(
    'blockReelsShorts',
  );
  @override
  late final GeneratedColumn<bool> blockReelsShorts = GeneratedColumn<bool>(
    'block_reels_shorts',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("block_reels_shorts" IN (0, 1))',
    ),
    defaultValue: const Constant(true),
  );
  static const VerificationMeta _isEssayLockedMeta = const VerificationMeta(
    'isEssayLocked',
  );
  @override
  late final GeneratedColumn<bool> isEssayLocked = GeneratedColumn<bool>(
    'is_essay_locked',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_essay_locked" IN (0, 1))',
    ),
    defaultValue: const Constant(true),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    packageName,
    appName,
    dailyCapMinutes,
    curfewStart,
    curfewEnd,
    blockReelsShorts,
    isEssayLocked,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'app_limits';
  @override
  VerificationContext validateIntegrity(
    Insertable<AppLimit> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('package_name')) {
      context.handle(
        _packageNameMeta,
        packageName.isAcceptableOrUnknown(
          data['package_name']!,
          _packageNameMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_packageNameMeta);
    }
    if (data.containsKey('app_name')) {
      context.handle(
        _appNameMeta,
        appName.isAcceptableOrUnknown(data['app_name']!, _appNameMeta),
      );
    } else if (isInserting) {
      context.missing(_appNameMeta);
    }
    if (data.containsKey('daily_cap_minutes')) {
      context.handle(
        _dailyCapMinutesMeta,
        dailyCapMinutes.isAcceptableOrUnknown(
          data['daily_cap_minutes']!,
          _dailyCapMinutesMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_dailyCapMinutesMeta);
    }
    if (data.containsKey('curfew_start')) {
      context.handle(
        _curfewStartMeta,
        curfewStart.isAcceptableOrUnknown(
          data['curfew_start']!,
          _curfewStartMeta,
        ),
      );
    }
    if (data.containsKey('curfew_end')) {
      context.handle(
        _curfewEndMeta,
        curfewEnd.isAcceptableOrUnknown(data['curfew_end']!, _curfewEndMeta),
      );
    }
    if (data.containsKey('block_reels_shorts')) {
      context.handle(
        _blockReelsShortsMeta,
        blockReelsShorts.isAcceptableOrUnknown(
          data['block_reels_shorts']!,
          _blockReelsShortsMeta,
        ),
      );
    }
    if (data.containsKey('is_essay_locked')) {
      context.handle(
        _isEssayLockedMeta,
        isEssayLocked.isAcceptableOrUnknown(
          data['is_essay_locked']!,
          _isEssayLockedMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  AppLimit map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return AppLimit(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      packageName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}package_name'],
      )!,
      appName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}app_name'],
      )!,
      dailyCapMinutes: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}daily_cap_minutes'],
      )!,
      curfewStart: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}curfew_start'],
      ),
      curfewEnd: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}curfew_end'],
      ),
      blockReelsShorts: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}block_reels_shorts'],
      )!,
      isEssayLocked: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_essay_locked'],
      )!,
    );
  }

  @override
  $AppLimitsTable createAlias(String alias) {
    return $AppLimitsTable(attachedDatabase, alias);
  }
}

class AppLimit extends DataClass implements Insertable<AppLimit> {
  final String id;
  final String packageName;
  final String appName;
  final int dailyCapMinutes;
  final String? curfewStart;
  final String? curfewEnd;
  final bool blockReelsShorts;
  final bool isEssayLocked;
  const AppLimit({
    required this.id,
    required this.packageName,
    required this.appName,
    required this.dailyCapMinutes,
    this.curfewStart,
    this.curfewEnd,
    required this.blockReelsShorts,
    required this.isEssayLocked,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['package_name'] = Variable<String>(packageName);
    map['app_name'] = Variable<String>(appName);
    map['daily_cap_minutes'] = Variable<int>(dailyCapMinutes);
    if (!nullToAbsent || curfewStart != null) {
      map['curfew_start'] = Variable<String>(curfewStart);
    }
    if (!nullToAbsent || curfewEnd != null) {
      map['curfew_end'] = Variable<String>(curfewEnd);
    }
    map['block_reels_shorts'] = Variable<bool>(blockReelsShorts);
    map['is_essay_locked'] = Variable<bool>(isEssayLocked);
    return map;
  }

  AppLimitsCompanion toCompanion(bool nullToAbsent) {
    return AppLimitsCompanion(
      id: Value(id),
      packageName: Value(packageName),
      appName: Value(appName),
      dailyCapMinutes: Value(dailyCapMinutes),
      curfewStart: curfewStart == null && nullToAbsent
          ? const Value.absent()
          : Value(curfewStart),
      curfewEnd: curfewEnd == null && nullToAbsent
          ? const Value.absent()
          : Value(curfewEnd),
      blockReelsShorts: Value(blockReelsShorts),
      isEssayLocked: Value(isEssayLocked),
    );
  }

  factory AppLimit.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return AppLimit(
      id: serializer.fromJson<String>(json['id']),
      packageName: serializer.fromJson<String>(json['packageName']),
      appName: serializer.fromJson<String>(json['appName']),
      dailyCapMinutes: serializer.fromJson<int>(json['dailyCapMinutes']),
      curfewStart: serializer.fromJson<String?>(json['curfewStart']),
      curfewEnd: serializer.fromJson<String?>(json['curfewEnd']),
      blockReelsShorts: serializer.fromJson<bool>(json['blockReelsShorts']),
      isEssayLocked: serializer.fromJson<bool>(json['isEssayLocked']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'packageName': serializer.toJson<String>(packageName),
      'appName': serializer.toJson<String>(appName),
      'dailyCapMinutes': serializer.toJson<int>(dailyCapMinutes),
      'curfewStart': serializer.toJson<String?>(curfewStart),
      'curfewEnd': serializer.toJson<String?>(curfewEnd),
      'blockReelsShorts': serializer.toJson<bool>(blockReelsShorts),
      'isEssayLocked': serializer.toJson<bool>(isEssayLocked),
    };
  }

  AppLimit copyWith({
    String? id,
    String? packageName,
    String? appName,
    int? dailyCapMinutes,
    Value<String?> curfewStart = const Value.absent(),
    Value<String?> curfewEnd = const Value.absent(),
    bool? blockReelsShorts,
    bool? isEssayLocked,
  }) => AppLimit(
    id: id ?? this.id,
    packageName: packageName ?? this.packageName,
    appName: appName ?? this.appName,
    dailyCapMinutes: dailyCapMinutes ?? this.dailyCapMinutes,
    curfewStart: curfewStart.present ? curfewStart.value : this.curfewStart,
    curfewEnd: curfewEnd.present ? curfewEnd.value : this.curfewEnd,
    blockReelsShorts: blockReelsShorts ?? this.blockReelsShorts,
    isEssayLocked: isEssayLocked ?? this.isEssayLocked,
  );
  AppLimit copyWithCompanion(AppLimitsCompanion data) {
    return AppLimit(
      id: data.id.present ? data.id.value : this.id,
      packageName: data.packageName.present
          ? data.packageName.value
          : this.packageName,
      appName: data.appName.present ? data.appName.value : this.appName,
      dailyCapMinutes: data.dailyCapMinutes.present
          ? data.dailyCapMinutes.value
          : this.dailyCapMinutes,
      curfewStart: data.curfewStart.present
          ? data.curfewStart.value
          : this.curfewStart,
      curfewEnd: data.curfewEnd.present ? data.curfewEnd.value : this.curfewEnd,
      blockReelsShorts: data.blockReelsShorts.present
          ? data.blockReelsShorts.value
          : this.blockReelsShorts,
      isEssayLocked: data.isEssayLocked.present
          ? data.isEssayLocked.value
          : this.isEssayLocked,
    );
  }

  @override
  String toString() {
    return (StringBuffer('AppLimit(')
          ..write('id: $id, ')
          ..write('packageName: $packageName, ')
          ..write('appName: $appName, ')
          ..write('dailyCapMinutes: $dailyCapMinutes, ')
          ..write('curfewStart: $curfewStart, ')
          ..write('curfewEnd: $curfewEnd, ')
          ..write('blockReelsShorts: $blockReelsShorts, ')
          ..write('isEssayLocked: $isEssayLocked')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    packageName,
    appName,
    dailyCapMinutes,
    curfewStart,
    curfewEnd,
    blockReelsShorts,
    isEssayLocked,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is AppLimit &&
          other.id == this.id &&
          other.packageName == this.packageName &&
          other.appName == this.appName &&
          other.dailyCapMinutes == this.dailyCapMinutes &&
          other.curfewStart == this.curfewStart &&
          other.curfewEnd == this.curfewEnd &&
          other.blockReelsShorts == this.blockReelsShorts &&
          other.isEssayLocked == this.isEssayLocked);
}

class AppLimitsCompanion extends UpdateCompanion<AppLimit> {
  final Value<String> id;
  final Value<String> packageName;
  final Value<String> appName;
  final Value<int> dailyCapMinutes;
  final Value<String?> curfewStart;
  final Value<String?> curfewEnd;
  final Value<bool> blockReelsShorts;
  final Value<bool> isEssayLocked;
  final Value<int> rowid;
  const AppLimitsCompanion({
    this.id = const Value.absent(),
    this.packageName = const Value.absent(),
    this.appName = const Value.absent(),
    this.dailyCapMinutes = const Value.absent(),
    this.curfewStart = const Value.absent(),
    this.curfewEnd = const Value.absent(),
    this.blockReelsShorts = const Value.absent(),
    this.isEssayLocked = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  AppLimitsCompanion.insert({
    required String id,
    required String packageName,
    required String appName,
    required int dailyCapMinutes,
    this.curfewStart = const Value.absent(),
    this.curfewEnd = const Value.absent(),
    this.blockReelsShorts = const Value.absent(),
    this.isEssayLocked = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       packageName = Value(packageName),
       appName = Value(appName),
       dailyCapMinutes = Value(dailyCapMinutes);
  static Insertable<AppLimit> custom({
    Expression<String>? id,
    Expression<String>? packageName,
    Expression<String>? appName,
    Expression<int>? dailyCapMinutes,
    Expression<String>? curfewStart,
    Expression<String>? curfewEnd,
    Expression<bool>? blockReelsShorts,
    Expression<bool>? isEssayLocked,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (packageName != null) 'package_name': packageName,
      if (appName != null) 'app_name': appName,
      if (dailyCapMinutes != null) 'daily_cap_minutes': dailyCapMinutes,
      if (curfewStart != null) 'curfew_start': curfewStart,
      if (curfewEnd != null) 'curfew_end': curfewEnd,
      if (blockReelsShorts != null) 'block_reels_shorts': blockReelsShorts,
      if (isEssayLocked != null) 'is_essay_locked': isEssayLocked,
      if (rowid != null) 'rowid': rowid,
    });
  }

  AppLimitsCompanion copyWith({
    Value<String>? id,
    Value<String>? packageName,
    Value<String>? appName,
    Value<int>? dailyCapMinutes,
    Value<String?>? curfewStart,
    Value<String?>? curfewEnd,
    Value<bool>? blockReelsShorts,
    Value<bool>? isEssayLocked,
    Value<int>? rowid,
  }) {
    return AppLimitsCompanion(
      id: id ?? this.id,
      packageName: packageName ?? this.packageName,
      appName: appName ?? this.appName,
      dailyCapMinutes: dailyCapMinutes ?? this.dailyCapMinutes,
      curfewStart: curfewStart ?? this.curfewStart,
      curfewEnd: curfewEnd ?? this.curfewEnd,
      blockReelsShorts: blockReelsShorts ?? this.blockReelsShorts,
      isEssayLocked: isEssayLocked ?? this.isEssayLocked,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (packageName.present) {
      map['package_name'] = Variable<String>(packageName.value);
    }
    if (appName.present) {
      map['app_name'] = Variable<String>(appName.value);
    }
    if (dailyCapMinutes.present) {
      map['daily_cap_minutes'] = Variable<int>(dailyCapMinutes.value);
    }
    if (curfewStart.present) {
      map['curfew_start'] = Variable<String>(curfewStart.value);
    }
    if (curfewEnd.present) {
      map['curfew_end'] = Variable<String>(curfewEnd.value);
    }
    if (blockReelsShorts.present) {
      map['block_reels_shorts'] = Variable<bool>(blockReelsShorts.value);
    }
    if (isEssayLocked.present) {
      map['is_essay_locked'] = Variable<bool>(isEssayLocked.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AppLimitsCompanion(')
          ..write('id: $id, ')
          ..write('packageName: $packageName, ')
          ..write('appName: $appName, ')
          ..write('dailyCapMinutes: $dailyCapMinutes, ')
          ..write('curfewStart: $curfewStart, ')
          ..write('curfewEnd: $curfewEnd, ')
          ..write('blockReelsShorts: $blockReelsShorts, ')
          ..write('isEssayLocked: $isEssayLocked, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $TimetableEntriesTable extends TimetableEntries
    with TableInfo<$TimetableEntriesTable, TimetableEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TimetableEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _dayOfWeekMeta = const VerificationMeta(
    'dayOfWeek',
  );
  @override
  late final GeneratedColumn<int> dayOfWeek = GeneratedColumn<int>(
    'day_of_week',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _specificDateMeta = const VerificationMeta(
    'specificDate',
  );
  @override
  late final GeneratedColumn<String> specificDate = GeneratedColumn<String>(
    'specific_date',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _startTimeMeta = const VerificationMeta(
    'startTime',
  );
  @override
  late final GeneratedColumn<String> startTime = GeneratedColumn<String>(
    'start_time',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _endTimeMeta = const VerificationMeta(
    'endTime',
  );
  @override
  late final GeneratedColumn<String> endTime = GeneratedColumn<String>(
    'end_time',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _locationOrNotesMeta = const VerificationMeta(
    'locationOrNotes',
  );
  @override
  late final GeneratedColumn<String> locationOrNotes = GeneratedColumn<String>(
    'location_or_notes',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isCanceledMeta = const VerificationMeta(
    'isCanceled',
  );
  @override
  late final GeneratedColumn<bool> isCanceled = GeneratedColumn<bool>(
    'is_canceled',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_canceled" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    title,
    dayOfWeek,
    specificDate,
    startTime,
    endTime,
    locationOrNotes,
    isCanceled,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'timetable_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<TimetableEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('day_of_week')) {
      context.handle(
        _dayOfWeekMeta,
        dayOfWeek.isAcceptableOrUnknown(data['day_of_week']!, _dayOfWeekMeta),
      );
    } else if (isInserting) {
      context.missing(_dayOfWeekMeta);
    }
    if (data.containsKey('specific_date')) {
      context.handle(
        _specificDateMeta,
        specificDate.isAcceptableOrUnknown(
          data['specific_date']!,
          _specificDateMeta,
        ),
      );
    }
    if (data.containsKey('start_time')) {
      context.handle(
        _startTimeMeta,
        startTime.isAcceptableOrUnknown(data['start_time']!, _startTimeMeta),
      );
    } else if (isInserting) {
      context.missing(_startTimeMeta);
    }
    if (data.containsKey('end_time')) {
      context.handle(
        _endTimeMeta,
        endTime.isAcceptableOrUnknown(data['end_time']!, _endTimeMeta),
      );
    } else if (isInserting) {
      context.missing(_endTimeMeta);
    }
    if (data.containsKey('location_or_notes')) {
      context.handle(
        _locationOrNotesMeta,
        locationOrNotes.isAcceptableOrUnknown(
          data['location_or_notes']!,
          _locationOrNotesMeta,
        ),
      );
    }
    if (data.containsKey('is_canceled')) {
      context.handle(
        _isCanceledMeta,
        isCanceled.isAcceptableOrUnknown(data['is_canceled']!, _isCanceledMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TimetableEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TimetableEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      dayOfWeek: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}day_of_week'],
      )!,
      specificDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}specific_date'],
      ),
      startTime: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}start_time'],
      )!,
      endTime: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}end_time'],
      )!,
      locationOrNotes: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}location_or_notes'],
      ),
      isCanceled: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_canceled'],
      )!,
    );
  }

  @override
  $TimetableEntriesTable createAlias(String alias) {
    return $TimetableEntriesTable(attachedDatabase, alias);
  }
}

class TimetableEntry extends DataClass implements Insertable<TimetableEntry> {
  final String id;
  final String title;
  final int dayOfWeek;
  final String? specificDate;
  final String startTime;
  final String endTime;
  final String? locationOrNotes;
  final bool isCanceled;
  const TimetableEntry({
    required this.id,
    required this.title,
    required this.dayOfWeek,
    this.specificDate,
    required this.startTime,
    required this.endTime,
    this.locationOrNotes,
    required this.isCanceled,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['title'] = Variable<String>(title);
    map['day_of_week'] = Variable<int>(dayOfWeek);
    if (!nullToAbsent || specificDate != null) {
      map['specific_date'] = Variable<String>(specificDate);
    }
    map['start_time'] = Variable<String>(startTime);
    map['end_time'] = Variable<String>(endTime);
    if (!nullToAbsent || locationOrNotes != null) {
      map['location_or_notes'] = Variable<String>(locationOrNotes);
    }
    map['is_canceled'] = Variable<bool>(isCanceled);
    return map;
  }

  TimetableEntriesCompanion toCompanion(bool nullToAbsent) {
    return TimetableEntriesCompanion(
      id: Value(id),
      title: Value(title),
      dayOfWeek: Value(dayOfWeek),
      specificDate: specificDate == null && nullToAbsent
          ? const Value.absent()
          : Value(specificDate),
      startTime: Value(startTime),
      endTime: Value(endTime),
      locationOrNotes: locationOrNotes == null && nullToAbsent
          ? const Value.absent()
          : Value(locationOrNotes),
      isCanceled: Value(isCanceled),
    );
  }

  factory TimetableEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TimetableEntry(
      id: serializer.fromJson<String>(json['id']),
      title: serializer.fromJson<String>(json['title']),
      dayOfWeek: serializer.fromJson<int>(json['dayOfWeek']),
      specificDate: serializer.fromJson<String?>(json['specificDate']),
      startTime: serializer.fromJson<String>(json['startTime']),
      endTime: serializer.fromJson<String>(json['endTime']),
      locationOrNotes: serializer.fromJson<String?>(json['locationOrNotes']),
      isCanceled: serializer.fromJson<bool>(json['isCanceled']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'title': serializer.toJson<String>(title),
      'dayOfWeek': serializer.toJson<int>(dayOfWeek),
      'specificDate': serializer.toJson<String?>(specificDate),
      'startTime': serializer.toJson<String>(startTime),
      'endTime': serializer.toJson<String>(endTime),
      'locationOrNotes': serializer.toJson<String?>(locationOrNotes),
      'isCanceled': serializer.toJson<bool>(isCanceled),
    };
  }

  TimetableEntry copyWith({
    String? id,
    String? title,
    int? dayOfWeek,
    Value<String?> specificDate = const Value.absent(),
    String? startTime,
    String? endTime,
    Value<String?> locationOrNotes = const Value.absent(),
    bool? isCanceled,
  }) => TimetableEntry(
    id: id ?? this.id,
    title: title ?? this.title,
    dayOfWeek: dayOfWeek ?? this.dayOfWeek,
    specificDate: specificDate.present ? specificDate.value : this.specificDate,
    startTime: startTime ?? this.startTime,
    endTime: endTime ?? this.endTime,
    locationOrNotes: locationOrNotes.present
        ? locationOrNotes.value
        : this.locationOrNotes,
    isCanceled: isCanceled ?? this.isCanceled,
  );
  TimetableEntry copyWithCompanion(TimetableEntriesCompanion data) {
    return TimetableEntry(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      dayOfWeek: data.dayOfWeek.present ? data.dayOfWeek.value : this.dayOfWeek,
      specificDate: data.specificDate.present
          ? data.specificDate.value
          : this.specificDate,
      startTime: data.startTime.present ? data.startTime.value : this.startTime,
      endTime: data.endTime.present ? data.endTime.value : this.endTime,
      locationOrNotes: data.locationOrNotes.present
          ? data.locationOrNotes.value
          : this.locationOrNotes,
      isCanceled: data.isCanceled.present
          ? data.isCanceled.value
          : this.isCanceled,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TimetableEntry(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('dayOfWeek: $dayOfWeek, ')
          ..write('specificDate: $specificDate, ')
          ..write('startTime: $startTime, ')
          ..write('endTime: $endTime, ')
          ..write('locationOrNotes: $locationOrNotes, ')
          ..write('isCanceled: $isCanceled')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    title,
    dayOfWeek,
    specificDate,
    startTime,
    endTime,
    locationOrNotes,
    isCanceled,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TimetableEntry &&
          other.id == this.id &&
          other.title == this.title &&
          other.dayOfWeek == this.dayOfWeek &&
          other.specificDate == this.specificDate &&
          other.startTime == this.startTime &&
          other.endTime == this.endTime &&
          other.locationOrNotes == this.locationOrNotes &&
          other.isCanceled == this.isCanceled);
}

class TimetableEntriesCompanion extends UpdateCompanion<TimetableEntry> {
  final Value<String> id;
  final Value<String> title;
  final Value<int> dayOfWeek;
  final Value<String?> specificDate;
  final Value<String> startTime;
  final Value<String> endTime;
  final Value<String?> locationOrNotes;
  final Value<bool> isCanceled;
  final Value<int> rowid;
  const TimetableEntriesCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.dayOfWeek = const Value.absent(),
    this.specificDate = const Value.absent(),
    this.startTime = const Value.absent(),
    this.endTime = const Value.absent(),
    this.locationOrNotes = const Value.absent(),
    this.isCanceled = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TimetableEntriesCompanion.insert({
    required String id,
    required String title,
    required int dayOfWeek,
    this.specificDate = const Value.absent(),
    required String startTime,
    required String endTime,
    this.locationOrNotes = const Value.absent(),
    this.isCanceled = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       title = Value(title),
       dayOfWeek = Value(dayOfWeek),
       startTime = Value(startTime),
       endTime = Value(endTime);
  static Insertable<TimetableEntry> custom({
    Expression<String>? id,
    Expression<String>? title,
    Expression<int>? dayOfWeek,
    Expression<String>? specificDate,
    Expression<String>? startTime,
    Expression<String>? endTime,
    Expression<String>? locationOrNotes,
    Expression<bool>? isCanceled,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (dayOfWeek != null) 'day_of_week': dayOfWeek,
      if (specificDate != null) 'specific_date': specificDate,
      if (startTime != null) 'start_time': startTime,
      if (endTime != null) 'end_time': endTime,
      if (locationOrNotes != null) 'location_or_notes': locationOrNotes,
      if (isCanceled != null) 'is_canceled': isCanceled,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TimetableEntriesCompanion copyWith({
    Value<String>? id,
    Value<String>? title,
    Value<int>? dayOfWeek,
    Value<String?>? specificDate,
    Value<String>? startTime,
    Value<String>? endTime,
    Value<String?>? locationOrNotes,
    Value<bool>? isCanceled,
    Value<int>? rowid,
  }) {
    return TimetableEntriesCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      dayOfWeek: dayOfWeek ?? this.dayOfWeek,
      specificDate: specificDate ?? this.specificDate,
      startTime: startTime ?? this.startTime,
      endTime: endTime ?? this.endTime,
      locationOrNotes: locationOrNotes ?? this.locationOrNotes,
      isCanceled: isCanceled ?? this.isCanceled,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (dayOfWeek.present) {
      map['day_of_week'] = Variable<int>(dayOfWeek.value);
    }
    if (specificDate.present) {
      map['specific_date'] = Variable<String>(specificDate.value);
    }
    if (startTime.present) {
      map['start_time'] = Variable<String>(startTime.value);
    }
    if (endTime.present) {
      map['end_time'] = Variable<String>(endTime.value);
    }
    if (locationOrNotes.present) {
      map['location_or_notes'] = Variable<String>(locationOrNotes.value);
    }
    if (isCanceled.present) {
      map['is_canceled'] = Variable<bool>(isCanceled.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TimetableEntriesCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('dayOfWeek: $dayOfWeek, ')
          ..write('specificDate: $specificDate, ')
          ..write('startTime: $startTime, ')
          ..write('endTime: $endTime, ')
          ..write('locationOrNotes: $locationOrNotes, ')
          ..write('isCanceled: $isCanceled, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $JournalEntriesTable extends JournalEntries
    with TableInfo<$JournalEntriesTable, JournalEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $JournalEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _entryDateMeta = const VerificationMeta(
    'entryDate',
  );
  @override
  late final GeneratedColumn<String> entryDate = GeneratedColumn<String>(
    'entry_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _contentMeta = const VerificationMeta(
    'content',
  );
  @override
  late final GeneratedColumn<String> content = GeneratedColumn<String>(
    'content',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tagsMeta = const VerificationMeta('tags');
  @override
  late final GeneratedColumn<String> tags = GeneratedColumn<String>(
    'tags',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    entryDate,
    content,
    tags,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'journal_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<JournalEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('entry_date')) {
      context.handle(
        _entryDateMeta,
        entryDate.isAcceptableOrUnknown(data['entry_date']!, _entryDateMeta),
      );
    } else if (isInserting) {
      context.missing(_entryDateMeta);
    }
    if (data.containsKey('content')) {
      context.handle(
        _contentMeta,
        content.isAcceptableOrUnknown(data['content']!, _contentMeta),
      );
    } else if (isInserting) {
      context.missing(_contentMeta);
    }
    if (data.containsKey('tags')) {
      context.handle(
        _tagsMeta,
        tags.isAcceptableOrUnknown(data['tags']!, _tagsMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  JournalEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return JournalEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      entryDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}entry_date'],
      )!,
      content: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content'],
      )!,
      tags: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tags'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $JournalEntriesTable createAlias(String alias) {
    return $JournalEntriesTable(attachedDatabase, alias);
  }
}

class JournalEntry extends DataClass implements Insertable<JournalEntry> {
  final String id;
  final String entryDate;
  final String content;
  final String? tags;
  final DateTime createdAt;
  const JournalEntry({
    required this.id,
    required this.entryDate,
    required this.content,
    this.tags,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['entry_date'] = Variable<String>(entryDate);
    map['content'] = Variable<String>(content);
    if (!nullToAbsent || tags != null) {
      map['tags'] = Variable<String>(tags);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  JournalEntriesCompanion toCompanion(bool nullToAbsent) {
    return JournalEntriesCompanion(
      id: Value(id),
      entryDate: Value(entryDate),
      content: Value(content),
      tags: tags == null && nullToAbsent ? const Value.absent() : Value(tags),
      createdAt: Value(createdAt),
    );
  }

  factory JournalEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return JournalEntry(
      id: serializer.fromJson<String>(json['id']),
      entryDate: serializer.fromJson<String>(json['entryDate']),
      content: serializer.fromJson<String>(json['content']),
      tags: serializer.fromJson<String?>(json['tags']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'entryDate': serializer.toJson<String>(entryDate),
      'content': serializer.toJson<String>(content),
      'tags': serializer.toJson<String?>(tags),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  JournalEntry copyWith({
    String? id,
    String? entryDate,
    String? content,
    Value<String?> tags = const Value.absent(),
    DateTime? createdAt,
  }) => JournalEntry(
    id: id ?? this.id,
    entryDate: entryDate ?? this.entryDate,
    content: content ?? this.content,
    tags: tags.present ? tags.value : this.tags,
    createdAt: createdAt ?? this.createdAt,
  );
  JournalEntry copyWithCompanion(JournalEntriesCompanion data) {
    return JournalEntry(
      id: data.id.present ? data.id.value : this.id,
      entryDate: data.entryDate.present ? data.entryDate.value : this.entryDate,
      content: data.content.present ? data.content.value : this.content,
      tags: data.tags.present ? data.tags.value : this.tags,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('JournalEntry(')
          ..write('id: $id, ')
          ..write('entryDate: $entryDate, ')
          ..write('content: $content, ')
          ..write('tags: $tags, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, entryDate, content, tags, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is JournalEntry &&
          other.id == this.id &&
          other.entryDate == this.entryDate &&
          other.content == this.content &&
          other.tags == this.tags &&
          other.createdAt == this.createdAt);
}

class JournalEntriesCompanion extends UpdateCompanion<JournalEntry> {
  final Value<String> id;
  final Value<String> entryDate;
  final Value<String> content;
  final Value<String?> tags;
  final Value<DateTime> createdAt;
  final Value<int> rowid;
  const JournalEntriesCompanion({
    this.id = const Value.absent(),
    this.entryDate = const Value.absent(),
    this.content = const Value.absent(),
    this.tags = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  JournalEntriesCompanion.insert({
    required String id,
    required String entryDate,
    required String content,
    this.tags = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       entryDate = Value(entryDate),
       content = Value(content);
  static Insertable<JournalEntry> custom({
    Expression<String>? id,
    Expression<String>? entryDate,
    Expression<String>? content,
    Expression<String>? tags,
    Expression<DateTime>? createdAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (entryDate != null) 'entry_date': entryDate,
      if (content != null) 'content': content,
      if (tags != null) 'tags': tags,
      if (createdAt != null) 'created_at': createdAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  JournalEntriesCompanion copyWith({
    Value<String>? id,
    Value<String>? entryDate,
    Value<String>? content,
    Value<String?>? tags,
    Value<DateTime>? createdAt,
    Value<int>? rowid,
  }) {
    return JournalEntriesCompanion(
      id: id ?? this.id,
      entryDate: entryDate ?? this.entryDate,
      content: content ?? this.content,
      tags: tags ?? this.tags,
      createdAt: createdAt ?? this.createdAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (entryDate.present) {
      map['entry_date'] = Variable<String>(entryDate.value);
    }
    if (content.present) {
      map['content'] = Variable<String>(content.value);
    }
    if (tags.present) {
      map['tags'] = Variable<String>(tags.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('JournalEntriesCompanion(')
          ..write('id: $id, ')
          ..write('entryDate: $entryDate, ')
          ..write('content: $content, ')
          ..write('tags: $tags, ')
          ..write('createdAt: $createdAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $TasksTable tasks = $TasksTable(this);
  late final $DailyLogsTable dailyLogs = $DailyLogsTable(this);
  late final $AppLimitsTable appLimits = $AppLimitsTable(this);
  late final $TimetableEntriesTable timetableEntries = $TimetableEntriesTable(
    this,
  );
  late final $JournalEntriesTable journalEntries = $JournalEntriesTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    tasks,
    dailyLogs,
    appLimits,
    timetableEntries,
    journalEntries,
  ];
}

typedef $$TasksTableCreateCompanionBuilder =
    TasksCompanion Function({
      required String id,
      required String title,
      Value<String?> description,
      required String cadenceUnit,
      required int targetFrequency,
      Value<int?> targetDurationSeconds,
      Value<String?> bestTimeStart,
      Value<String?> bestTimeEnd,
      Value<int> xpValue,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<bool> isDeleted,
      Value<String> syncStatus,
      Value<String?> linkedPackageName,
      Value<int> rowid,
    });
typedef $$TasksTableUpdateCompanionBuilder =
    TasksCompanion Function({
      Value<String> id,
      Value<String> title,
      Value<String?> description,
      Value<String> cadenceUnit,
      Value<int> targetFrequency,
      Value<int?> targetDurationSeconds,
      Value<String?> bestTimeStart,
      Value<String?> bestTimeEnd,
      Value<int> xpValue,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<bool> isDeleted,
      Value<String> syncStatus,
      Value<String?> linkedPackageName,
      Value<int> rowid,
    });

final class $$TasksTableReferences
    extends BaseReferences<_$AppDatabase, $TasksTable, Task> {
  $$TasksTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static MultiTypedResultKey<$DailyLogsTable, List<DailyLog>>
  _dailyLogsRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.dailyLogs,
    aliasName: 'tasks__id__daily_logs__task_id',
  );

  $$DailyLogsTableProcessedTableManager get dailyLogsRefs {
    final manager = $$DailyLogsTableTableManager(
      $_db,
      $_db.dailyLogs,
    ).filter((f) => f.taskId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_dailyLogsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$TasksTableFilterComposer extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get cadenceUnit => $composableBuilder(
    column: $table.cadenceUnit,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get targetFrequency => $composableBuilder(
    column: $table.targetFrequency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get targetDurationSeconds => $composableBuilder(
    column: $table.targetDurationSeconds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get bestTimeStart => $composableBuilder(
    column: $table.bestTimeStart,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get bestTimeEnd => $composableBuilder(
    column: $table.bestTimeEnd,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get xpValue => $composableBuilder(
    column: $table.xpValue,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isDeleted => $composableBuilder(
    column: $table.isDeleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get linkedPackageName => $composableBuilder(
    column: $table.linkedPackageName,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> dailyLogsRefs(
    Expression<bool> Function($$DailyLogsTableFilterComposer f) f,
  ) {
    final $$DailyLogsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dailyLogs,
      getReferencedColumn: (t) => t.taskId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DailyLogsTableFilterComposer(
            $db: $db,
            $table: $db.dailyLogs,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$TasksTableOrderingComposer
    extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get cadenceUnit => $composableBuilder(
    column: $table.cadenceUnit,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get targetFrequency => $composableBuilder(
    column: $table.targetFrequency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get targetDurationSeconds => $composableBuilder(
    column: $table.targetDurationSeconds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get bestTimeStart => $composableBuilder(
    column: $table.bestTimeStart,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get bestTimeEnd => $composableBuilder(
    column: $table.bestTimeEnd,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get xpValue => $composableBuilder(
    column: $table.xpValue,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isDeleted => $composableBuilder(
    column: $table.isDeleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get linkedPackageName => $composableBuilder(
    column: $table.linkedPackageName,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$TasksTableAnnotationComposer
    extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<String> get cadenceUnit => $composableBuilder(
    column: $table.cadenceUnit,
    builder: (column) => column,
  );

  GeneratedColumn<int> get targetFrequency => $composableBuilder(
    column: $table.targetFrequency,
    builder: (column) => column,
  );

  GeneratedColumn<int> get targetDurationSeconds => $composableBuilder(
    column: $table.targetDurationSeconds,
    builder: (column) => column,
  );

  GeneratedColumn<String> get bestTimeStart => $composableBuilder(
    column: $table.bestTimeStart,
    builder: (column) => column,
  );

  GeneratedColumn<String> get bestTimeEnd => $composableBuilder(
    column: $table.bestTimeEnd,
    builder: (column) => column,
  );

  GeneratedColumn<int> get xpValue =>
      $composableBuilder(column: $table.xpValue, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<bool> get isDeleted =>
      $composableBuilder(column: $table.isDeleted, builder: (column) => column);

  GeneratedColumn<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => column,
  );

  GeneratedColumn<String> get linkedPackageName => $composableBuilder(
    column: $table.linkedPackageName,
    builder: (column) => column,
  );

  Expression<T> dailyLogsRefs<T extends Object>(
    Expression<T> Function($$DailyLogsTableAnnotationComposer a) f,
  ) {
    final $$DailyLogsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.dailyLogs,
      getReferencedColumn: (t) => t.taskId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$DailyLogsTableAnnotationComposer(
            $db: $db,
            $table: $db.dailyLogs,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$TasksTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $TasksTable,
          Task,
          $$TasksTableFilterComposer,
          $$TasksTableOrderingComposer,
          $$TasksTableAnnotationComposer,
          $$TasksTableCreateCompanionBuilder,
          $$TasksTableUpdateCompanionBuilder,
          (Task, $$TasksTableReferences),
          Task,
          PrefetchHooks Function({bool dailyLogsRefs})
        > {
  $$TasksTableTableManager(_$AppDatabase db, $TasksTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TasksTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TasksTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TasksTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String?> description = const Value.absent(),
                Value<String> cadenceUnit = const Value.absent(),
                Value<int> targetFrequency = const Value.absent(),
                Value<int?> targetDurationSeconds = const Value.absent(),
                Value<String?> bestTimeStart = const Value.absent(),
                Value<String?> bestTimeEnd = const Value.absent(),
                Value<int> xpValue = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<bool> isDeleted = const Value.absent(),
                Value<String> syncStatus = const Value.absent(),
                Value<String?> linkedPackageName = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TasksCompanion(
                id: id,
                title: title,
                description: description,
                cadenceUnit: cadenceUnit,
                targetFrequency: targetFrequency,
                targetDurationSeconds: targetDurationSeconds,
                bestTimeStart: bestTimeStart,
                bestTimeEnd: bestTimeEnd,
                xpValue: xpValue,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isDeleted: isDeleted,
                syncStatus: syncStatus,
                linkedPackageName: linkedPackageName,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String title,
                Value<String?> description = const Value.absent(),
                required String cadenceUnit,
                required int targetFrequency,
                Value<int?> targetDurationSeconds = const Value.absent(),
                Value<String?> bestTimeStart = const Value.absent(),
                Value<String?> bestTimeEnd = const Value.absent(),
                Value<int> xpValue = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<bool> isDeleted = const Value.absent(),
                Value<String> syncStatus = const Value.absent(),
                Value<String?> linkedPackageName = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TasksCompanion.insert(
                id: id,
                title: title,
                description: description,
                cadenceUnit: cadenceUnit,
                targetFrequency: targetFrequency,
                targetDurationSeconds: targetDurationSeconds,
                bestTimeStart: bestTimeStart,
                bestTimeEnd: bestTimeEnd,
                xpValue: xpValue,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isDeleted: isDeleted,
                syncStatus: syncStatus,
                linkedPackageName: linkedPackageName,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) =>
                    (e.readTable(table), $$TasksTableReferences(db, table, e)),
              )
              .toList(),
          prefetchHooksCallback: ({dailyLogsRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [if (dailyLogsRefs) db.dailyLogs],
              addJoins: null,
              getPrefetchedDataCallback: (items) async {
                return [
                  if (dailyLogsRefs)
                    await $_getPrefetchedData<Task, $TasksTable, DailyLog>(
                      currentTable: table,
                      referencedTable: $$TasksTableReferences
                          ._dailyLogsRefsTable(db),
                      managerFromTypedResult: (p0) =>
                          $$TasksTableReferences(db, table, p0).dailyLogsRefs,
                      referencedItemsForCurrentItem: (item, referencedItems) =>
                          referencedItems.where((e) => e.taskId == item.id),
                      typedResults: items,
                    ),
                ];
              },
            );
          },
        ),
      );
}

typedef $$TasksTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $TasksTable,
      Task,
      $$TasksTableFilterComposer,
      $$TasksTableOrderingComposer,
      $$TasksTableAnnotationComposer,
      $$TasksTableCreateCompanionBuilder,
      $$TasksTableUpdateCompanionBuilder,
      (Task, $$TasksTableReferences),
      Task,
      PrefetchHooks Function({bool dailyLogsRefs})
    >;
typedef $$DailyLogsTableCreateCompanionBuilder =
    DailyLogsCompanion Function({
      required String id,
      required String taskId,
      required String logDate,
      Value<int> durationLoggedSeconds,
      Value<bool> isCompleted,
      Value<int> xpAwarded,
      Value<String?> notes,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });
typedef $$DailyLogsTableUpdateCompanionBuilder =
    DailyLogsCompanion Function({
      Value<String> id,
      Value<String> taskId,
      Value<String> logDate,
      Value<int> durationLoggedSeconds,
      Value<bool> isCompleted,
      Value<int> xpAwarded,
      Value<String?> notes,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

final class $$DailyLogsTableReferences
    extends BaseReferences<_$AppDatabase, $DailyLogsTable, DailyLog> {
  $$DailyLogsTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $TasksTable _taskIdTable(_$AppDatabase db) =>
      db.tasks.createAlias('daily_logs__task_id__tasks__id');

  $$TasksTableProcessedTableManager get taskId {
    final $_column = $_itemColumn<String>('task_id')!;

    final manager = $$TasksTableTableManager(
      $_db,
      $_db.tasks,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_taskIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$DailyLogsTableFilterComposer
    extends Composer<_$AppDatabase, $DailyLogsTable> {
  $$DailyLogsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get logDate => $composableBuilder(
    column: $table.logDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get durationLoggedSeconds => $composableBuilder(
    column: $table.durationLoggedSeconds,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get xpAwarded => $composableBuilder(
    column: $table.xpAwarded,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  $$TasksTableFilterComposer get taskId {
    final $$TasksTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.taskId,
      referencedTable: $db.tasks,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TasksTableFilterComposer(
            $db: $db,
            $table: $db.tasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DailyLogsTableOrderingComposer
    extends Composer<_$AppDatabase, $DailyLogsTable> {
  $$DailyLogsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get logDate => $composableBuilder(
    column: $table.logDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get durationLoggedSeconds => $composableBuilder(
    column: $table.durationLoggedSeconds,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get xpAwarded => $composableBuilder(
    column: $table.xpAwarded,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$TasksTableOrderingComposer get taskId {
    final $$TasksTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.taskId,
      referencedTable: $db.tasks,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TasksTableOrderingComposer(
            $db: $db,
            $table: $db.tasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DailyLogsTableAnnotationComposer
    extends Composer<_$AppDatabase, $DailyLogsTable> {
  $$DailyLogsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get logDate =>
      $composableBuilder(column: $table.logDate, builder: (column) => column);

  GeneratedColumn<int> get durationLoggedSeconds => $composableBuilder(
    column: $table.durationLoggedSeconds,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<int> get xpAwarded =>
      $composableBuilder(column: $table.xpAwarded, builder: (column) => column);

  GeneratedColumn<String> get notes =>
      $composableBuilder(column: $table.notes, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  $$TasksTableAnnotationComposer get taskId {
    final $$TasksTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.taskId,
      referencedTable: $db.tasks,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TasksTableAnnotationComposer(
            $db: $db,
            $table: $db.tasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$DailyLogsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $DailyLogsTable,
          DailyLog,
          $$DailyLogsTableFilterComposer,
          $$DailyLogsTableOrderingComposer,
          $$DailyLogsTableAnnotationComposer,
          $$DailyLogsTableCreateCompanionBuilder,
          $$DailyLogsTableUpdateCompanionBuilder,
          (DailyLog, $$DailyLogsTableReferences),
          DailyLog,
          PrefetchHooks Function({bool taskId})
        > {
  $$DailyLogsTableTableManager(_$AppDatabase db, $DailyLogsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$DailyLogsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$DailyLogsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$DailyLogsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> taskId = const Value.absent(),
                Value<String> logDate = const Value.absent(),
                Value<int> durationLoggedSeconds = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<int> xpAwarded = const Value.absent(),
                Value<String?> notes = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => DailyLogsCompanion(
                id: id,
                taskId: taskId,
                logDate: logDate,
                durationLoggedSeconds: durationLoggedSeconds,
                isCompleted: isCompleted,
                xpAwarded: xpAwarded,
                notes: notes,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String taskId,
                required String logDate,
                Value<int> durationLoggedSeconds = const Value.absent(),
                Value<bool> isCompleted = const Value.absent(),
                Value<int> xpAwarded = const Value.absent(),
                Value<String?> notes = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => DailyLogsCompanion.insert(
                id: id,
                taskId: taskId,
                logDate: logDate,
                durationLoggedSeconds: durationLoggedSeconds,
                isCompleted: isCompleted,
                xpAwarded: xpAwarded,
                notes: notes,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$DailyLogsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({taskId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (taskId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.taskId,
                                referencedTable: $$DailyLogsTableReferences
                                    ._taskIdTable(db),
                                referencedColumn: $$DailyLogsTableReferences
                                    ._taskIdTable(db)
                                    .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$DailyLogsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $DailyLogsTable,
      DailyLog,
      $$DailyLogsTableFilterComposer,
      $$DailyLogsTableOrderingComposer,
      $$DailyLogsTableAnnotationComposer,
      $$DailyLogsTableCreateCompanionBuilder,
      $$DailyLogsTableUpdateCompanionBuilder,
      (DailyLog, $$DailyLogsTableReferences),
      DailyLog,
      PrefetchHooks Function({bool taskId})
    >;
typedef $$AppLimitsTableCreateCompanionBuilder =
    AppLimitsCompanion Function({
      required String id,
      required String packageName,
      required String appName,
      required int dailyCapMinutes,
      Value<String?> curfewStart,
      Value<String?> curfewEnd,
      Value<bool> blockReelsShorts,
      Value<bool> isEssayLocked,
      Value<int> rowid,
    });
typedef $$AppLimitsTableUpdateCompanionBuilder =
    AppLimitsCompanion Function({
      Value<String> id,
      Value<String> packageName,
      Value<String> appName,
      Value<int> dailyCapMinutes,
      Value<String?> curfewStart,
      Value<String?> curfewEnd,
      Value<bool> blockReelsShorts,
      Value<bool> isEssayLocked,
      Value<int> rowid,
    });

class $$AppLimitsTableFilterComposer
    extends Composer<_$AppDatabase, $AppLimitsTable> {
  $$AppLimitsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get packageName => $composableBuilder(
    column: $table.packageName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get appName => $composableBuilder(
    column: $table.appName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get dailyCapMinutes => $composableBuilder(
    column: $table.dailyCapMinutes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get curfewStart => $composableBuilder(
    column: $table.curfewStart,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get curfewEnd => $composableBuilder(
    column: $table.curfewEnd,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get blockReelsShorts => $composableBuilder(
    column: $table.blockReelsShorts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isEssayLocked => $composableBuilder(
    column: $table.isEssayLocked,
    builder: (column) => ColumnFilters(column),
  );
}

class $$AppLimitsTableOrderingComposer
    extends Composer<_$AppDatabase, $AppLimitsTable> {
  $$AppLimitsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get packageName => $composableBuilder(
    column: $table.packageName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get appName => $composableBuilder(
    column: $table.appName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get dailyCapMinutes => $composableBuilder(
    column: $table.dailyCapMinutes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get curfewStart => $composableBuilder(
    column: $table.curfewStart,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get curfewEnd => $composableBuilder(
    column: $table.curfewEnd,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get blockReelsShorts => $composableBuilder(
    column: $table.blockReelsShorts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isEssayLocked => $composableBuilder(
    column: $table.isEssayLocked,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$AppLimitsTableAnnotationComposer
    extends Composer<_$AppDatabase, $AppLimitsTable> {
  $$AppLimitsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get packageName => $composableBuilder(
    column: $table.packageName,
    builder: (column) => column,
  );

  GeneratedColumn<String> get appName =>
      $composableBuilder(column: $table.appName, builder: (column) => column);

  GeneratedColumn<int> get dailyCapMinutes => $composableBuilder(
    column: $table.dailyCapMinutes,
    builder: (column) => column,
  );

  GeneratedColumn<String> get curfewStart => $composableBuilder(
    column: $table.curfewStart,
    builder: (column) => column,
  );

  GeneratedColumn<String> get curfewEnd =>
      $composableBuilder(column: $table.curfewEnd, builder: (column) => column);

  GeneratedColumn<bool> get blockReelsShorts => $composableBuilder(
    column: $table.blockReelsShorts,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isEssayLocked => $composableBuilder(
    column: $table.isEssayLocked,
    builder: (column) => column,
  );
}

class $$AppLimitsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $AppLimitsTable,
          AppLimit,
          $$AppLimitsTableFilterComposer,
          $$AppLimitsTableOrderingComposer,
          $$AppLimitsTableAnnotationComposer,
          $$AppLimitsTableCreateCompanionBuilder,
          $$AppLimitsTableUpdateCompanionBuilder,
          (AppLimit, BaseReferences<_$AppDatabase, $AppLimitsTable, AppLimit>),
          AppLimit,
          PrefetchHooks Function()
        > {
  $$AppLimitsTableTableManager(_$AppDatabase db, $AppLimitsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AppLimitsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AppLimitsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AppLimitsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> packageName = const Value.absent(),
                Value<String> appName = const Value.absent(),
                Value<int> dailyCapMinutes = const Value.absent(),
                Value<String?> curfewStart = const Value.absent(),
                Value<String?> curfewEnd = const Value.absent(),
                Value<bool> blockReelsShorts = const Value.absent(),
                Value<bool> isEssayLocked = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => AppLimitsCompanion(
                id: id,
                packageName: packageName,
                appName: appName,
                dailyCapMinutes: dailyCapMinutes,
                curfewStart: curfewStart,
                curfewEnd: curfewEnd,
                blockReelsShorts: blockReelsShorts,
                isEssayLocked: isEssayLocked,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String packageName,
                required String appName,
                required int dailyCapMinutes,
                Value<String?> curfewStart = const Value.absent(),
                Value<String?> curfewEnd = const Value.absent(),
                Value<bool> blockReelsShorts = const Value.absent(),
                Value<bool> isEssayLocked = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => AppLimitsCompanion.insert(
                id: id,
                packageName: packageName,
                appName: appName,
                dailyCapMinutes: dailyCapMinutes,
                curfewStart: curfewStart,
                curfewEnd: curfewEnd,
                blockReelsShorts: blockReelsShorts,
                isEssayLocked: isEssayLocked,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$AppLimitsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $AppLimitsTable,
      AppLimit,
      $$AppLimitsTableFilterComposer,
      $$AppLimitsTableOrderingComposer,
      $$AppLimitsTableAnnotationComposer,
      $$AppLimitsTableCreateCompanionBuilder,
      $$AppLimitsTableUpdateCompanionBuilder,
      (AppLimit, BaseReferences<_$AppDatabase, $AppLimitsTable, AppLimit>),
      AppLimit,
      PrefetchHooks Function()
    >;
typedef $$TimetableEntriesTableCreateCompanionBuilder =
    TimetableEntriesCompanion Function({
      required String id,
      required String title,
      required int dayOfWeek,
      Value<String?> specificDate,
      required String startTime,
      required String endTime,
      Value<String?> locationOrNotes,
      Value<bool> isCanceled,
      Value<int> rowid,
    });
typedef $$TimetableEntriesTableUpdateCompanionBuilder =
    TimetableEntriesCompanion Function({
      Value<String> id,
      Value<String> title,
      Value<int> dayOfWeek,
      Value<String?> specificDate,
      Value<String> startTime,
      Value<String> endTime,
      Value<String?> locationOrNotes,
      Value<bool> isCanceled,
      Value<int> rowid,
    });

class $$TimetableEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $TimetableEntriesTable> {
  $$TimetableEntriesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get dayOfWeek => $composableBuilder(
    column: $table.dayOfWeek,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get specificDate => $composableBuilder(
    column: $table.specificDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get startTime => $composableBuilder(
    column: $table.startTime,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get endTime => $composableBuilder(
    column: $table.endTime,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get locationOrNotes => $composableBuilder(
    column: $table.locationOrNotes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCanceled => $composableBuilder(
    column: $table.isCanceled,
    builder: (column) => ColumnFilters(column),
  );
}

class $$TimetableEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $TimetableEntriesTable> {
  $$TimetableEntriesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get dayOfWeek => $composableBuilder(
    column: $table.dayOfWeek,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get specificDate => $composableBuilder(
    column: $table.specificDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get startTime => $composableBuilder(
    column: $table.startTime,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get endTime => $composableBuilder(
    column: $table.endTime,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get locationOrNotes => $composableBuilder(
    column: $table.locationOrNotes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCanceled => $composableBuilder(
    column: $table.isCanceled,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$TimetableEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $TimetableEntriesTable> {
  $$TimetableEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<int> get dayOfWeek =>
      $composableBuilder(column: $table.dayOfWeek, builder: (column) => column);

  GeneratedColumn<String> get specificDate => $composableBuilder(
    column: $table.specificDate,
    builder: (column) => column,
  );

  GeneratedColumn<String> get startTime =>
      $composableBuilder(column: $table.startTime, builder: (column) => column);

  GeneratedColumn<String> get endTime =>
      $composableBuilder(column: $table.endTime, builder: (column) => column);

  GeneratedColumn<String> get locationOrNotes => $composableBuilder(
    column: $table.locationOrNotes,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isCanceled => $composableBuilder(
    column: $table.isCanceled,
    builder: (column) => column,
  );
}

class $$TimetableEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $TimetableEntriesTable,
          TimetableEntry,
          $$TimetableEntriesTableFilterComposer,
          $$TimetableEntriesTableOrderingComposer,
          $$TimetableEntriesTableAnnotationComposer,
          $$TimetableEntriesTableCreateCompanionBuilder,
          $$TimetableEntriesTableUpdateCompanionBuilder,
          (
            TimetableEntry,
            BaseReferences<
              _$AppDatabase,
              $TimetableEntriesTable,
              TimetableEntry
            >,
          ),
          TimetableEntry,
          PrefetchHooks Function()
        > {
  $$TimetableEntriesTableTableManager(
    _$AppDatabase db,
    $TimetableEntriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TimetableEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TimetableEntriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TimetableEntriesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<int> dayOfWeek = const Value.absent(),
                Value<String?> specificDate = const Value.absent(),
                Value<String> startTime = const Value.absent(),
                Value<String> endTime = const Value.absent(),
                Value<String?> locationOrNotes = const Value.absent(),
                Value<bool> isCanceled = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TimetableEntriesCompanion(
                id: id,
                title: title,
                dayOfWeek: dayOfWeek,
                specificDate: specificDate,
                startTime: startTime,
                endTime: endTime,
                locationOrNotes: locationOrNotes,
                isCanceled: isCanceled,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String title,
                required int dayOfWeek,
                Value<String?> specificDate = const Value.absent(),
                required String startTime,
                required String endTime,
                Value<String?> locationOrNotes = const Value.absent(),
                Value<bool> isCanceled = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TimetableEntriesCompanion.insert(
                id: id,
                title: title,
                dayOfWeek: dayOfWeek,
                specificDate: specificDate,
                startTime: startTime,
                endTime: endTime,
                locationOrNotes: locationOrNotes,
                isCanceled: isCanceled,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$TimetableEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $TimetableEntriesTable,
      TimetableEntry,
      $$TimetableEntriesTableFilterComposer,
      $$TimetableEntriesTableOrderingComposer,
      $$TimetableEntriesTableAnnotationComposer,
      $$TimetableEntriesTableCreateCompanionBuilder,
      $$TimetableEntriesTableUpdateCompanionBuilder,
      (
        TimetableEntry,
        BaseReferences<_$AppDatabase, $TimetableEntriesTable, TimetableEntry>,
      ),
      TimetableEntry,
      PrefetchHooks Function()
    >;
typedef $$JournalEntriesTableCreateCompanionBuilder =
    JournalEntriesCompanion Function({
      required String id,
      required String entryDate,
      required String content,
      Value<String?> tags,
      Value<DateTime> createdAt,
      Value<int> rowid,
    });
typedef $$JournalEntriesTableUpdateCompanionBuilder =
    JournalEntriesCompanion Function({
      Value<String> id,
      Value<String> entryDate,
      Value<String> content,
      Value<String?> tags,
      Value<DateTime> createdAt,
      Value<int> rowid,
    });

class $$JournalEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $JournalEntriesTable> {
  $$JournalEntriesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get entryDate => $composableBuilder(
    column: $table.entryDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get content => $composableBuilder(
    column: $table.content,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$JournalEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $JournalEntriesTable> {
  $$JournalEntriesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get entryDate => $composableBuilder(
    column: $table.entryDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get content => $composableBuilder(
    column: $table.content,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$JournalEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $JournalEntriesTable> {
  $$JournalEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get entryDate =>
      $composableBuilder(column: $table.entryDate, builder: (column) => column);

  GeneratedColumn<String> get content =>
      $composableBuilder(column: $table.content, builder: (column) => column);

  GeneratedColumn<String> get tags =>
      $composableBuilder(column: $table.tags, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$JournalEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $JournalEntriesTable,
          JournalEntry,
          $$JournalEntriesTableFilterComposer,
          $$JournalEntriesTableOrderingComposer,
          $$JournalEntriesTableAnnotationComposer,
          $$JournalEntriesTableCreateCompanionBuilder,
          $$JournalEntriesTableUpdateCompanionBuilder,
          (
            JournalEntry,
            BaseReferences<_$AppDatabase, $JournalEntriesTable, JournalEntry>,
          ),
          JournalEntry,
          PrefetchHooks Function()
        > {
  $$JournalEntriesTableTableManager(
    _$AppDatabase db,
    $JournalEntriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$JournalEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$JournalEntriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$JournalEntriesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> entryDate = const Value.absent(),
                Value<String> content = const Value.absent(),
                Value<String?> tags = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => JournalEntriesCompanion(
                id: id,
                entryDate: entryDate,
                content: content,
                tags: tags,
                createdAt: createdAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String entryDate,
                required String content,
                Value<String?> tags = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => JournalEntriesCompanion.insert(
                id: id,
                entryDate: entryDate,
                content: content,
                tags: tags,
                createdAt: createdAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$JournalEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $JournalEntriesTable,
      JournalEntry,
      $$JournalEntriesTableFilterComposer,
      $$JournalEntriesTableOrderingComposer,
      $$JournalEntriesTableAnnotationComposer,
      $$JournalEntriesTableCreateCompanionBuilder,
      $$JournalEntriesTableUpdateCompanionBuilder,
      (
        JournalEntry,
        BaseReferences<_$AppDatabase, $JournalEntriesTable, JournalEntry>,
      ),
      JournalEntry,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$TasksTableTableManager get tasks =>
      $$TasksTableTableManager(_db, _db.tasks);
  $$DailyLogsTableTableManager get dailyLogs =>
      $$DailyLogsTableTableManager(_db, _db.dailyLogs);
  $$AppLimitsTableTableManager get appLimits =>
      $$AppLimitsTableTableManager(_db, _db.appLimits);
  $$TimetableEntriesTableTableManager get timetableEntries =>
      $$TimetableEntriesTableTableManager(_db, _db.timetableEntries);
  $$JournalEntriesTableTableManager get journalEntries =>
      $$JournalEntriesTableTableManager(_db, _db.journalEntries);
}
