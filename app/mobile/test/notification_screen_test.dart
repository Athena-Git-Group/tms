import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/notification/notification_screen.dart';

/// 驗通知中心改版之後，驗收用的節點全部還在、還讀得到同樣的值：分組節點、
/// 每一則的標題節點、未讀點、全部已讀按鈕與空狀態。
///
/// 版面大改時最容易掉的就是這幾個節點——分組從 `Padding` 換成白底群組、列從
/// 兩個元素變成五個，任何一步把節點包錯層，模擬器上的驗收才會紅，但那要跑
/// 二十分鐘。這組測試在十秒內先說。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  Future<void> pumpScreen(WidgetTester tester) async {
    await tester.pumpWidget(const CupertinoApp(home: NotificationScreen()));
    await tester.pumpAndSettle();
  }

  String labelOf(WidgetTester tester, String id) =>
      tester.getSemantics(find.bySemanticsIdentifier(id)).label;

  testWidgets('今天與更早各自成組，未讀的那則才有未讀點', (tester) async {
    final handle = tester.ensureSemantics();
    final now = DateTime.now();
    ApiClient.instance = _FakeApiClient([
      NotificationItem(
        id: 'n-1',
        title: '305 指派給你',
        body: '退房清潔，預估 30 分鐘。',
        target: const {'kind': 'home'},
        createdAt: now.subtract(const Duration(minutes: 5)),
      ),
      NotificationItem(
        id: 'n-2',
        title: '306 查房退回',
        body: null,
        target: const {'kind': 'home'},
        createdAt: now.subtract(const Duration(days: 3)),
        readAt: now,
      ),
    ]);
    await pumpScreen(tester);

    expect(
      find.bySemanticsIdentifier('notification.group.today.section'),
      findsOneWidget,
    );
    expect(
      find.bySemanticsIdentifier('notification.group.earlier.section'),
      findsOneWidget,
    );
    expect(labelOf(tester, 'notification.item.n-1.title'), '305 指派給你');
    expect(labelOf(tester, 'notification.item.n-2.title'), '306 查房退回');

    // 未讀＝節點存在，已讀＝節點不存在；驗收的「不存在或不可見」靠這個成立。
    expect(
      find.bySemanticsIdentifier('notification.item.n-1.unread-dot'),
      findsOneWidget,
    );
    expect(
      find.bySemanticsIdentifier('notification.item.n-2.unread-dot'),
      findsNothing,
    );

    expect(
      find.bySemanticsIdentifier('notification.mark-all-read-button'),
      findsOneWidget,
    );
    // 內文有就顯示，沒有就整段不畫。
    expect(find.text('退房清潔，預估 30 分鐘。'), findsOneWidget);
    expect(find.text('5 分鐘前'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('一則通知都沒有時顯示空狀態，而且沒有任何通知項', (tester) async {
    final handle = tester.ensureSemantics();
    ApiClient.instance = _FakeApiClient(const []);
    await pumpScreen(tester);

    expect(
      labelOf(tester, 'notification.empty-state'),
      '目前沒有通知。派單、急件與退回都會出現在這裡。',
    );
    expect(find.text('派單、急件與退回都會出現在這裡。'), findsOneWidget);

    handle.dispose();
  });

  test('相對時間照分鐘差換成員工看得懂的說法', () {
    final now = DateTime(2026, 11, 7, 10, 30);
    String at(Duration ago) => relativeTimeLabel(now.subtract(ago), now);

    expect(at(const Duration(seconds: 20)), '剛剛');
    expect(at(const Duration(minutes: 5)), '5 分鐘前');
    expect(at(const Duration(hours: 3)), '3 小時前');
    expect(at(const Duration(hours: 30)), '昨天 04:30');
    expect(at(const Duration(days: 5)), '11/2');
  });
}

/// 記憶體 fake：通知中心開啟時只打 `listNotifications`，回測試指定的清單。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.items);

  final List<NotificationItem> items;

  @override
  Future<List<NotificationItem>> listNotifications() async => items;
}
