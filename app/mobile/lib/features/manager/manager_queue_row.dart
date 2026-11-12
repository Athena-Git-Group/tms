import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/test_backdoor.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_tag.dart';
import 'manager_ids.dart';

/// 列上的一顆標籤。
class ManagerQueueTag {
  const ManagerQueueTag(this.label, this.tone);

  final String label;
  final TmsTagTone tone;
}

/// 清單一列要畫的東西，由兩種 item 各自換算；畫法只有 [ManagerQueueRow] 一套。
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

  /// 待查房：房號、任務標題、誰清的；被退回重做過的標「重做後再查」。
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

  /// 待驗收：單號、房號或地點、說明；標籤是完修結果（無法修復用琥珀色）、
  /// 定期保養與重修後再驗，次要字是完修人。
  factory ManagerQueueRowData.verify(VerifyQueueItem item) {
    return ManagerQueueRowData(
      taskId: item.taskId,
      key: item.ticketNo,
      ticketNo: item.ticketNo,
      where: _where(item.roomNo, item.isPublicArea, item.title),
      title: item.title,
      details: [if (item.assigneeName != null) '${item.assigneeName} 完修'],
      tags: [
        // 完修結果欄位出現之前就完修的舊單沒有值，這顆標籤就不畫。
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

  /// 這一列那張單的任務 id；點進去的畫面拿它向後端取單或送出動作。
  final String taskId;

  /// 列 id 的尾段：待查房是房號，工務單是單號。
  final String key;

  /// 工務單號；待查房沒有。
  final String? ticketNo;

  /// 列的主角：房號；公共區域的單沒有房號，地點說明在標題裡，就用標題。
  final String where;
  final String title;

  /// 第三行的次要字，畫面上以「 · 」串起來。
  final List<String> details;
  final List<ManagerQueueTag> tags;
  final String waitingSince;
  final bool waitingSinceIsToday;

  /// 整列節點的讀值：單號、房號或地點、內容、標籤、次要字。
  String get label => [
    ?ticketNo,
    where,
    if (title != where) title,
    for (final tag in tags) tag.label,
    ...details,
  ].join(' ');

  static String _where(String? roomNo, bool isPublicArea, String title) =>
      isPublicArea || roomNo == null ? title : roomNo;

  /// 完修結果的契約值 → 列上標籤的短說法。
  static const Map<String, String> _resolutionText = {
    'fixed': '已修好',
    'temporary': '暫時處理',
    'cannot_fix': '無法修復',
  };
}

/// 主管清單的一列：左邊單號（工務單才有）、房號或地點、內容、標籤與次要字，
/// 右邊開始等主管的時間。待查房與待驗收都是這一個元件，差別只在 [ManagerQueueRowData]
/// 依 item 型別填了哪些標籤與次要字。
///
/// 整列節點顯式帶 [ManagerQueueRowData.label]；右欄另掛一個節點，讀值是
/// [waitingSinceText] 的 label，驗收讀時間不必理會列上其他文字。
class ManagerQueueRow extends StatelessWidget {
  const ManagerQueueRow({
    super.key,
    required this.data,
    required this.showSeparator,
    this.onTap,
  });

  final ManagerQueueRowData data;
  final bool showSeparator;

  /// 點這一列要做的事；null 時這一列只看不點。
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

/// 右欄「開始等主管的時間」要顯示的字。
///
/// 時與分直接取 [waitingSince] 字串本身（帶後端時區位移），不轉成手機時區；
/// 要不要標日期只看後端給的 [isToday]。不是今天時，「昨天」或「N 天前」是拿
/// [now] 換算到同一個時區位移後的日期去比，跟後端的日界線同一把尺。
///
/// [label] 是給右欄節點的讀值：今天只有時刻；不是今天是「日期 時刻 幾天前開始等」。
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
