import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/widgets/tms_category_square.dart';
import 'package:tms_staff_app/features/engineering/eng_pool_screen.dart';

import 'support/test_environment.dart';

void main() {
  turnOffAnimationsForEachTest();

  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  testWidgets('單卡房號方塊寫工務單類別的中文，不是任務類型代碼', (tester) async {
    ApiClient.instance = _FakeApiClient(
      DeptPool(
        activeStaffCount: 2,
        counts: const {'all': 2},
        items: [
          _item('W-0601', '401', 'air_conditioning'),
          _item('W-0621', '305', 'other'),
        ],
      ),
    );
    await tester.pumpWidget(const CupertinoApp(home: EngPoolScreen()));
    await tester.pumpAndSettle();

    String squareLabel(String roomNo) => tester
        .widget<TmsRoomSquare>(find.byWidgetPredicate(
          (widget) => widget is TmsRoomSquare && widget.where == roomNo,
        ))
        .label;

    expect(squareLabel('401'), '冷氣');
    expect(squareLabel('305'), '其他');
    expect(find.text('repair'), findsNothing);
  });
}

PoolItem _item(String ticketNo, String roomNo, String repairCategory) {
  return PoolItem(
    taskId: 'task-$ticketNo',
    ticketNo: ticketNo,
    roomNo: roomNo,
    floor: int.parse(roomNo.substring(0, 1)),
    title: '房間有怪聲',
    category: 'repair',
    repairCategory: repairCategory,
    source: 'desk_forward',
    urgent: false,
    priorityNotify: false,
    sameFloor: false,
    createdAt: '2026-11-23T09:00:00Z',
  );
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.pool);

  final DeptPool pool;

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async => pool;
}
