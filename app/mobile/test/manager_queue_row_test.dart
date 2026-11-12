import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/manager/manager_queue_row.dart';

/// 主管清單的列：右欄時間照後端字串的時區位移取時分、只看 waitingSinceIsToday
/// 決定標不標日期；兩種 item 換成同一份列資料，公共區域用標題當地點。
void main() {
  // 台北時間 2026-11-10 11:30。
  final now = DateTime.utc(2026, 11, 10, 3, 30);

  group('waitingSinceText', () {
    test('今天只顯示時刻', () {
      final when = waitingSinceText(
        '2026-11-10T10:20:00+08:00',
        isToday: true,
        now: now,
      );
      expect(when.label, '10:20');
      expect(when.date, isNull);
    });

    test('昨天標日期與「昨天開始等」，時刻不轉手機時區', () {
      final when = waitingSinceText(
        '2026-11-09T17:40:00+08:00',
        isToday: false,
        now: now,
      );
      expect(when.date, '11/09');
      expect(when.time, '17:40');
      expect(when.label, '11/09 17:40 昨天開始等');
    });

    test('更早的單寫幾天前', () {
      final when = waitingSinceText(
        '2026-11-07T08:05+08:00',
        isToday: false,
        now: now,
      );
      expect(when.label, '11/07 08:05 3 天前開始等');
    });
  });

  test('待查房列：房號、標題、誰清的，鍵是房號', () {
    final row = ManagerQueueRowData.inspection(
      const InspectionQueueItem(
        taskId: 't1',
        roomNo: '301',
        isPublicArea: false,
        title: '退房清潔',
        waitingSince: '2026-11-10T10:20:00+08:00',
        waitingSinceIsToday: true,
        cleanerName: '小美',
        redoCount: 0,
      ),
    );
    expect(row.key, '301');
    expect(row.label, '301 退房清潔 小美 清的');
  });

  test('待驗收列：公共區域用標題當地點，標出完修結果與定期保養', () {
    final row = ManagerQueueRowData.verify(
      const VerifyQueueItem(
        taskId: 't2',
        roomNo: null,
        isPublicArea: true,
        title: '5 樓製冰機定期保養',
        waitingSince: '2026-11-10T11:05:00+08:00',
        waitingSinceIsToday: true,
        ticketNo: 'W-0111',
        category: 'other',
        origin: 'scheduled',
        resolutionType: 'cannot_fix',
        assigneeName: '阿強',
      ),
    );
    expect(row.key, 'W-0111');
    expect(row.where, '5 樓製冰機定期保養');
    expect(row.label, 'W-0111 5 樓製冰機定期保養 無法修復 定期保養 阿強 完修');
  });

  test('待驗收列：重修後再完修的單多一顆「重修後再驗」', () {
    final row = ManagerQueueRowData.verify(
      const VerifyQueueItem(
        taskId: 't3',
        roomNo: '312',
        isPublicArea: false,
        title: '門把鬆動',
        waitingSince: '2026-11-10T11:25:00+08:00',
        waitingSinceIsToday: true,
        ticketNo: 'W-0116',
        category: 'lock',
        origin: 'reported',
        resolutionType: 'fixed',
        assigneeName: '阿強',
        wasVerificationFailed: true,
      ),
    );
    expect(row.label, 'W-0116 312 門把鬆動 已修好 重修後再驗 阿強 完修');
  });

  test('待驗收列：沒有完修結果的舊單不畫結果標籤', () {
    final row = ManagerQueueRowData.verify(
      const VerifyQueueItem(
        taskId: 't4',
        roomNo: '208',
        isPublicArea: false,
        title: '水龍頭滴水',
        waitingSince: '2026-11-09T16:10:00+08:00',
        waitingSinceIsToday: false,
        ticketNo: 'W-0110',
        category: 'plumbing',
        origin: 'reported',
        resolutionType: null,
      ),
    );
    expect(row.tags, isEmpty);
    expect(row.label, 'W-0110 208 水龍頭滴水');
  });

  testWidgets('列畫得出來，右欄節點讀值只有時刻', (tester) async {
    final row = ManagerQueueRowData.inspection(
      const InspectionQueueItem(
        taskId: 't1',
        roomNo: '301',
        isPublicArea: false,
        title: '退房清潔',
        waitingSince: '2026-11-10T10:20:00+08:00',
        waitingSinceIsToday: true,
        cleanerName: '小美',
        redoCount: 0,
      ),
    );
    await tester.pumpWidget(
      CupertinoApp(home: ManagerQueueRow(data: row, showSeparator: false)),
    );
    expect(find.bySemanticsLabel('10:20'), findsOneWidget);
    expect(find.text('退房清潔'), findsOneWidget);
  });
}
