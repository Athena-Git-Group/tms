import 'dart:typed_data';

import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/features/manager/manager_ids.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';

import 'support/fixture_jpeg.dart';
import 'support/test_environment.dart';

class _FakeApi extends ApiClient {
  _FakeApi({
    this.state = 'done',
    this.photoIds = const ['p-1', 'p-2'],
    this.completionPhotoIds = const ['c-1'],
  });

  final String state;
  final List<String> photoIds;
  final List<String> completionPhotoIds;
  final fetched = <String>[];

  @override
  Future<RepairTicketDetail> readRepairTicket(String taskId) async =>
      RepairTicketDetail(
        taskId: 't-0108',
        ticketNo: 'W-0108',
        roomNo: '301',
        category: 'door_lock',
        isUrgent: false,
        state: state,
        reporterName: '阿芳',
        assigneeName: '阿強',
        resolutionType: state == 'done' ? 'fixed' : null,
        photos: [
          for (final id in photoIds)
            RepairTicketPhoto(id: id, status: 'uploaded'),
        ],
        completionPhotos: [
          for (final id in completionPhotoIds)
            RepairTicketPhoto(id: id, status: 'uploaded'),
        ],
      );

  @override
  Future<Uint8List> fetchRepairPhotoContent(String photoId) async {
    fetched.add(photoId);
    if (photoId.startsWith('missing')) {
      throw const ApiException(statusCode: 404, body: '');
    }
    return fixtureJpeg;
  }
}

String? photoState(WidgetTester tester, String id) =>
    tester.getSemantics(find.bySemanticsIdentifier(id)).value;

/// 圖片解碼在測試的假時鐘之外進行，要讓出真的時間才會完成。
Future<void> letImagesDecode(WidgetTester tester) async {
  for (var i = 0; i < 5; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 20)),
    );
    await tester.pump();
  }
}

void main() {
  late ApiClient original;

  turnOffAnimationsForEachTest();

  setUp(() {
    original = ApiClient.instance;
    ConnectionStatus.instance.setOffline(false);
  });

  tearDown(() {
    ConnectionStatus.instance.reset();
    ApiClient.instance = original;
  });

  Future<void> openTicket(
    WidgetTester tester,
    TicketView view, {
    TicketEntry entry = TicketEntry.elsewhere,
  }) async {
    usePhoneSurface(tester);
    await tester.pumpWidget(
      CupertinoApp(
        home: TicketScreen(view: view, taskId: 't-0108', entry: entry),
      ),
    );
    await tester.pumpAndSettle();
    await letImagesDecode(tester);
  }

  testWidgets('工務視角：報修與完修照片格子的張數照回應，每格都畫出圖片', (tester) async {
    ApiClient.instance = _FakeApi();
    await openTicket(tester, TicketView.engineering);

    expect(photoState(tester, 'ticket.photo.1'), 'loaded');
    expect(photoState(tester, 'ticket.photo.2'), 'loaded');
    expect(find.bySemanticsIdentifier('ticket.photo.3'), findsNothing);
    expect(photoState(tester, 'ticket.completion-photo.1'), 'loaded');
    expect(
      find.bySemanticsIdentifier('ticket.completion-photo.2'),
      findsNothing,
    );
  });

  testWidgets('讀不到的照片顯示照片無法顯示，其他照片照常', (tester) async {
    ApiClient.instance = _FakeApi(photoIds: const ['p-1', 'missing-2']);
    await openTicket(tester, TicketView.engineering);

    expect(photoState(tester, 'ticket.photo.1'), 'loaded');
    expect(photoState(tester, 'ticket.photo.2'), 'failed');
    expect(find.text('照片無法顯示'), findsOneWidget);
  });

  testWidgets('房務視角的照片格子也畫出圖片', (tester) async {
    ApiClient.instance = _FakeApi(state: 'pool', completionPhotoIds: const []);
    await openTicket(tester, TicketView.housekeeping);

    expect(photoState(tester, 'ticket.photo.1'), 'loaded');
    expect(photoState(tester, 'ticket.photo.2'), 'loaded');
  });

  testWidgets('看大圖照照片清單逐張顯示圖片，讀不到的那張顯示照片無法顯示', (tester) async {
    ApiClient.instance = _FakeApi(photoIds: const ['p-1', 'missing-2']);
    await openTicket(tester, TicketView.engineering);

    await tester.tap(find.bySemanticsIdentifier('ticket.photo.1'));
    await tester.pumpAndSettle();
    await letImagesDecode(tester);

    expect(find.text('1 / 2'), findsOneWidget);
    expect(photoState(tester, 'photoViewer.photo.1'), 'loaded');

    await tester.tap(find.bySemanticsIdentifier('photoViewer.next-button'));
    await tester.pumpAndSettle();
    await letImagesDecode(tester);

    expect(find.text('2 / 2'), findsOneWidget);
    expect(photoState(tester, 'photoViewer.photo.2'), 'failed');
  });

  testWidgets('點完修照片打開完修照片的大圖', (tester) async {
    ApiClient.instance = _FakeApi(completionPhotoIds: const ['c-1', 'c-2']);
    await openTicket(tester, TicketView.engineering);

    await tester.tap(find.bySemanticsIdentifier('ticket.completion-photo.2'));
    await tester.pumpAndSettle();
    await letImagesDecode(tester);

    expect(find.text('2 / 2'), findsOneWidget);
    expect(photoState(tester, 'photoViewer.photo.2'), 'loaded');
  });

  testWidgets('同一張照片只向後端讀一次', (tester) async {
    final api = _FakeApi(photoIds: const ['p-1'], completionPhotoIds: const []);
    ApiClient.instance = api;
    await openTicket(tester, TicketView.engineering);

    await tester.tap(find.bySemanticsIdentifier('ticket.photo.1'));
    await tester.pumpAndSettle();
    await letImagesDecode(tester);

    expect(api.fetched, ['p-1']);
  });

  testWidgets('主管從待驗收清單進來：按通過或不通過之前就看得到兩種照片', (tester) async {
    ApiClient.instance = _FakeApi();
    await openTicket(
      tester,
      TicketView.manager,
      entry: TicketEntry.verifyQueue,
    );

    expect(photoState(tester, 'ticket.photo.1'), 'loaded');
    expect(photoState(tester, 'ticket.photo.2'), 'loaded');
    expect(photoState(tester, 'ticket.completion-photo.1'), 'loaded');
    expect(
      find.bySemanticsIdentifier(ManagerIds.ticketPassButton),
      findsOneWidget,
    );
    expect(
      find.bySemanticsIdentifier(ManagerIds.ticketFailButton),
      findsOneWidget,
    );
    final passTop = tester
        .getRect(find.bySemanticsIdentifier(ManagerIds.ticketPassButton))
        .top;
    for (final id in ['ticket.photo.1', 'ticket.completion-photo.1']) {
      expect(
        tester.getRect(find.bySemanticsIdentifier(id)).bottom,
        lessThan(passTop),
        reason: '$id 不必捲動就看得到',
      );
    }
  });
}
