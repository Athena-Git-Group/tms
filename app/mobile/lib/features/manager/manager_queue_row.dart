import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/test_backdoor.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_tag.dart';
import 'manager_ids.dart';

class ManagerQueueTag {
  const ManagerQueueTag(this.label, this.tone);

  final String label;
  final TmsTagTone tone;
}

class ManagerQueueRowData {
  const ManagerQueueRowData({
    required this.taskId,
    required this.key,
    required this.ticketNo,
    required this.where,
    required this.title,
    required this.details,
    required this.tags,
    required this.waitingSince,
    required this.waitingSinceIsToday,
  });

  factory ManagerQueueRowData.inspection(InspectionQueueItem item) {
    final where = _where(item.roomNo, item.isPublicArea, item.title);
    return ManagerQueueRowData(
      taskId: item.taskId,
      key: where,
      ticketNo: null,
      where: where,
      title: item.title,
      details: ['${item.cleanerName} 清的'],
      tags: [
        if (item.redoCount > 0) const ManagerQueueTag('重做後再查', TmsTagTone.warn),
      ],
      waitingSince: item.waitingSince,
      waitingSinceIsToday: item.waitingSinceIsToday,
    );
  }

  factory ManagerQueueRowData.verify(VerifyQueueItem item) {
    return ManagerQueueRowData(
      taskId: item.taskId,
      key: item.ticketNo,
      ticketNo: item.ticketNo,
      where: _where(item.roomNo, item.isPublicArea, item.title),
      title: item.title,
      details: [if (item.assigneeName != null) '${item.assigneeName} 完修'],
      tags: [
        if (item.resolutionType case final resolutionType?)
          ManagerQueueTag(
            _resolutionText[resolutionType] ?? resolutionType,
            resolutionType == 'cannot_fix'
                ? TmsTagTone.warn
                : TmsTagTone.neutralStrong,
          ),
        if (item.origin == 'scheduled')
          const ManagerQueueTag('定期保養', TmsTagTone.info),
        if (item.wasVerificationFailed)
          const ManagerQueueTag('重修後再驗', TmsTagTone.warn),
      ],
      waitingSince: item.waitingSince,
      waitingSinceIsToday: item.waitingSinceIsToday,
    );
  }

  final String taskId;

  final String key;

  final String? ticketNo;

  final String where;
  final String title;

  final List<String> details;
  final List<ManagerQueueTag> tags;
  final String waitingSince;
  final bool waitingSinceIsToday;

  String get label => [
    ?ticketNo,
    where,
    if (title != where) title,
    for (final tag in tags) tag.label,
    ...details,
  ].join(' ');

  static String _where(String? roomNo, bool isPublicArea, String title) =>
      isPublicArea || roomNo == null ? title : roomNo;

  static const Map<String, String> _resolutionText = {
    'fixed': '已修好',
    'temporary': '暫時處理',
    'cannot_fix': '無法修復',
  };
}

class ManagerQueueRow extends StatelessWidget {
  const ManagerQueueRow({
    super.key,
    required this.data,
    required this.showSeparator,
    this.onTap,
  });

  final ManagerQueueRowData data;
  final bool showSeparator;

  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final when = waitingSinceText(
      data.waitingSince,
      isToday: data.waitingSinceIsToday,
      now: deviceNow(),
    );
    return TmsSemantics(
      id: ManagerIds.queueRow(data.key),
      label: data.label,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.md2,
            vertical: TmsSpacing.md,
          ),
          decoration: BoxDecoration(
            border: showSeparator
                ? const Border(
                    bottom: BorderSide(
                      color: TmsColors.separator,
                      width: TmsSize.hairline,
                    ),
                  )
                : null,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: ExcludeSemantics(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (data.ticketNo != null)
                        Text(
                          data.ticketNo!,
                          style: TmsText.caption1.copyWith(
                            color: TmsColors.tertiaryLabel,
                          ),
                        ),
                      Text(data.where, style: TmsText.title2),
                      if (data.title != data.where) ...[
                        const SizedBox(height: TmsSpacing.xxs),
                        Text(data.title, style: TmsText.subheadlineEmphasized),
                      ],
                      if (data.tags.isNotEmpty || data.details.isNotEmpty) ...[
                        const SizedBox(height: TmsSpacing.xs),
                        Wrap(
                          spacing: TmsTag.gap,
                          runSpacing: TmsTag.gap,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            for (final tag in data.tags)
                              TmsTag(label: tag.label, tone: tag.tone),
                            if (data.details.isNotEmpty)
                              Text(
                                data.details.join(' · '),
                                style: TmsText.footnote.copyWith(
                                  color: TmsColors.secondaryLabel,
                                ),
                              ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(width: TmsSpacing.md),
              TmsSemantics(
                id: ManagerIds.queueRowWaitingSince(data.key),
                label: when.label,
                child: ExcludeSemantics(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      if (when.date != null)
                        Text(
                          when.date!,
                          style: TmsText.caption1.copyWith(
                            color: TmsColors.brandAccent,
                          ),
                        ),
                      Text(when.time, style: TmsText.headline),
                      Text(
                        when.caption,
                        style: TmsText.caption1Regular.copyWith(
                          color: TmsColors.tertiaryLabel,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

({String? date, String time, String caption, String label}) waitingSinceText(
  String waitingSince, {
  required bool isToday,
  required DateTime now,
}) {
  final match = RegExp(
    r'^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$',
  ).firstMatch(waitingSince);
  if (match == null) {
    return (date: null, time: waitingSince, caption: '', label: waitingSince);
  }
  final year = int.parse(match.group(1)!);
  final month = int.parse(match.group(2)!);
  final day = int.parse(match.group(3)!);
  final time = '${match.group(4)}:${match.group(5)}';
  if (isToday) {
    return (date: null, time: time, caption: '開始等', label: time);
  }
  final date = '${match.group(2)}/${match.group(3)}';
  final offsetText = match.group(6);
  final offset = offsetText == null
      ? now.timeZoneOffset
      : _parseOffset(offsetText);
  final nowThere = now.toUtc().add(offset);
  final daysAgo = DateTime.utc(
    nowThere.year,
    nowThere.month,
    nowThere.day,
  ).difference(DateTime.utc(year, month, day)).inDays;
  final caption = switch (daysAgo) {
    1 => '昨天開始等',
    > 1 => '$daysAgo 天前開始等',
    _ => '開始等',
  };
  return (
    date: date,
    time: time,
    caption: caption,
    label: '$date $time $caption',
  );
}

Duration _parseOffset(String text) {
  if (text == 'Z') {
    return Duration.zero;
  }
  final digits = text.substring(1).replaceAll(':', '');
  final minutes =
      int.parse(digits.substring(0, 2)) * 60 + int.parse(digits.substring(2));
  return Duration(minutes: text.startsWith('-') ? -minutes : minutes);
}
