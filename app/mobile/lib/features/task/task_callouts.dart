import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';

/// 要員工先讀一段交代、再往下做事的區塊用這個底色（淡黃）。
const Color calloutNoticeTint = Color(0xFFFFF7E0);

/// 有東西被判定不合格、得重做的區塊用這個底色（淡紅）。
const Color calloutAlertTint = Color(0xFFFDECEC);

/// 擋路的事情已經排除、可以繼續做下去的區塊用這個底色（淡綠）。
const Color calloutClearedTint = Color(0xFFE6F7EC);

/// 任務頁的提示卡片：整頁寬、有底色的圓角方塊，內容由上而下靠左排。
///
/// 任務頁上要特別提醒員工的幾件事——交辦、查房退回資訊、工務完修後可以恢復
/// 原任務——都用這張卡片呈現。呼叫端只決定底色、accessibility id 與裡面放哪些
/// 內容，寬度、圓角與內距由卡片自己統一，所以各區塊看起來是同一種東西。
///
/// 卡片不管自己與頁面其他元素之間的上下間距，呼叫端要自己包一層 `Padding`。
/// 各區塊在頁面上的位置不同，前後該留多少空白也不同，把間距寫進卡片會讓其中
/// 幾個位置的間距永遠是錯的。
class TaskCallout extends StatelessWidget {
  const TaskCallout({
    super.key,
    required this.id,
    required this.tint,
    required this.children,
    this.explicitChildNodes = false,
  });

  /// 這張卡片的 accessibility id。
  final String id;

  /// 卡片底色。
  final Color tint;

  /// 測試要不要以這張卡片為範圍往下找子項目；意義見 [TmsSemantics]。
  final bool explicitChildNodes;

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      explicitChildNodes: explicitChildNodes,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: tint,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: children,
        ),
      ),
    );
  }
}

/// 交辦區：後端已經把交班單、櫃檯備註、接手進度併成一份清單，這裡照順序逐則
/// 顯示「來源 · 作者 · 時間」與內容。
///
/// 沒看過的交辦在區塊標頭掛一個紅圓點（`task.handover.unread-dot`）——未讀在
/// 畫面上只有這個記號，不寫「未讀」兩個字。看過了沒有一律以後端回傳的 [read]
/// 為準：開工那一刻後端自己記下已讀，所以按完「開始」拿回來的任務就不再帶圓點。
class HandoverCallout extends StatelessWidget {
  const HandoverCallout({
    super.key,
    required this.entries,
    required this.read,
  });

  final List<HandoverEntry> entries;

  /// 這些交辦是否已經被看過。
  final bool read;

  /// 交辦來源的畫面用詞。對照表出自 `房間交辦/dsl.md` 的「來源」欄，與後端
  /// `HandoverEntry.kind` 的 enum 一對一。
  static String _sourceText(String kind) {
    switch (kind) {
      case 'shift_handover':
        return '交班單';
      case 'desk_note':
        return '櫃檯備註';
      case 'requeue_progress':
        return '接手進度';
      case 'manager_note':
        return '主管交代';
      default:
        return kind;
    }
  }

  static String _hhmm(DateTime at) {
    return '${at.hour.toString().padLeft(2, '0')}:'
        '${at.minute.toString().padLeft(2, '0')}';
  }

  /// 一則交辦的開頭那行：來源、誰寫的、幾點寫的，以 ` · ` 串起來。時間只有
  /// 交班單這類來源帶得出來，沒有就不佔位。
  static String _metaLine(HandoverEntry entry) {
    return <String>[
      _sourceText(entry.kind),
      entry.author,
      if (entry.at != null) _hhmm(entry.at!),
    ].join(' · ');
  }

  @override
  Widget build(BuildContext context) {
    return TaskCallout(
      id: 'task.handover.callout',
      tint: calloutNoticeTint,
      explicitChildNodes: true,
      children: [
        Row(
          children: [
            const Text('交辦'),
            if (!read) ...[
              const SizedBox(width: 6),
              TmsSemantics(
                id: 'task.handover.unread-dot',
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: Color(0xFFD9342B),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ],
        ),
        for (var i = 0; i < entries.length; i++)
          Padding(
            padding: EdgeInsets.only(top: i == 0 ? 8 : 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TmsSemantics(
                  id: 'task.handover.$i.meta',
                  child: Text(
                    _metaLine(entries[i]),
                    style: const TextStyle(fontSize: 13),
                  ),
                ),
                const SizedBox(height: 2),
                TmsSemantics(
                  id: 'task.handover.$i.content',
                  child: Text(entries[i].content),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// 查房退回資訊：標題與原因分兩行顯示，底下再加一行操作提示。
///
/// 標題與原因的文字都來自後端的 `returnInfo.summary`，這裡只負責把它切成兩段
/// 排版——不合格項是哪一項、第幾次重做、為什麼換人，都是後端說了算。第三行的
/// 操作提示是畫面自己的話：退回重做時計時與 SOP 勾記都歸零了，員工需要在這裡
/// 被告知接下來要做什麼。
class ReturnInfoCallout extends StatelessWidget {
  const ReturnInfoCallout({super.key, required this.info});

  final ReturnInfo info;

  @override
  Widget build(BuildContext context) {
    return TaskCallout(
      id: 'task.status-row.return-info',
      tint: calloutAlertTint,
      explicitChildNodes: true,
      children: [
        TmsSemantics(
          id: 'task.status-row.return-info.title',
          child: Text(
            info.title,
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
        ),
        const SizedBox(height: 4),
        TmsSemantics(
          id: 'task.status-row.return-info.reason',
          child: Text(info.reason),
        ),
        const SizedBox(height: 4),
        const TmsSemantics(
          id: 'task.status-row.return-info.body',
          child: Text('計時與 SOP 勾記已歸零，請重做不合格的項目後再送出。'),
        ),
      ],
    );
  }
}
