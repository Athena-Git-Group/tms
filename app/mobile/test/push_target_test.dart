import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/push_target.dart';

void main() {
  Future<String?> roomNoOf(String taskId) async =>
      const {'task-305': '305', 'task-316': '316'}[taskId];

  group('parsePushTarget', () {
    test('FCM data 裡的 JSON 字串還原成 map', () {
      expect(
        parsePushTarget('{"kind":"task","taskId":"task-305"}'),
        {'kind': 'task', 'taskId': 'task-305'},
      );
    });

    test('原生側已經是 map 時照收，鍵統一成字串', () {
      expect(
        parsePushTarget(<Object, Object>{'kind': 'home'}),
        {'kind': 'home'},
      );
    });

    test('不是 JSON 物件（壞字串、陣列、null）時回 null', () {
      expect(parsePushTarget('not json'), isNull);
      expect(parsePushTarget('[1,2]'), isNull);
      expect(parsePushTarget(''), isNull);
      expect(parsePushTarget(null), isNull);
    });
  });

  group('pushPayloadFromTarget', () {
    test('kind=task 以任務 id 查出房號，任務頁靠房號定位', () async {
      final payload = await pushPayloadFromTarget(
        {'kind': 'task', 'taskId': 'task-305'},
        roomNoOf: roomNoOf,
      );
      expect(payload, {'dest': 'task', 'taskId': 'task-305', 'room': '305'});
    });

    test('kind=task 任務查不到房號時不導頁', () async {
      final payload = await pushPayloadFromTarget(
        {'kind': 'task', 'taskId': 'task-gone'},
        roomNoOf: roomNoOf,
      );
      expect(payload, isNull);
    });

    test('kind=ticket 不查房號，直接帶任務 id 開工務單詳情', () async {
      var lookups = 0;
      final payload = await pushPayloadFromTarget(
        {'kind': 'ticket', 'taskId': 'task-w0620'},
        roomNoOf: (_) async {
          lookups++;
          return null;
        },
      );
      expect(payload, {'dest': 'ticket', 'taskId': 'task-w0620'});
      expect(lookups, 0);
    });

    test('kind=task／ticket 沒帶 taskId 時不導頁', () async {
      expect(
        await pushPayloadFromTarget({'kind': 'task'}, roomNoOf: roomNoOf),
        isNull,
      );
      expect(
        await pushPayloadFromTarget({'kind': 'ticket'}, roomNoOf: roomNoOf),
        isNull,
      );
    });

    test('kind=pool 帶 highlightTaskId 時換成要標亮的房號', () async {
      final payload = await pushPayloadFromTarget(
        {'kind': 'pool', 'dept': 'housekeeping', 'highlightTaskId': 'task-316'},
        roomNoOf: roomNoOf,
      );
      expect(payload, {'dest': 'pool', 'highlight': '316'});
    });

    test('kind=pool 標亮的那間查不到時仍導到池子，只是不標亮', () async {
      final payload = await pushPayloadFromTarget(
        {'kind': 'pool', 'dept': 'housekeeping', 'highlightTaskId': 'task-gone'},
        roomNoOf: roomNoOf,
      );
      expect(payload, {'dest': 'pool'});
    });

    test('kind=home 與不認得的 kind 都回 payload，交給導頁端決定', () async {
      expect(
        await pushPayloadFromTarget({'kind': 'home'}, roomNoOf: roomNoOf),
        {'dest': 'home'},
      );
      expect(
        await pushPayloadFromTarget({'kind': 'mystery'}, roomNoOf: roomNoOf),
        {'dest': 'mystery'},
      );
    });
  });
}
