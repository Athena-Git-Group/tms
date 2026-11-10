import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/connection_status.dart';

/// 對應「恢復連線後自動送出，網路不穩重送也只建立一張」：員工在電梯裡送出的
/// 工務單，回到有訊號的地方會自己送出去；中間就算送了幾次沒收到回應，後端也
/// 只會有一張單。
///
/// 這裡起一個真的 HTTP server 當後端，而不是換掉 [ApiClient]：要驗的是請求
/// 確實送到後端、後端也處理了，只是 App 收不到回應。把 [ApiClient] 整個換成
/// fake 的話，請求根本沒離開過 App，重送幾次後端都是乾淨的，驗不出來。
void main() {
  late _FakeBackend backend;

  setUp(() async {
    backend = await _FakeBackend.start();
    ApiClient.instance = ApiClient(baseUrl: backend.baseUrl);
  });

  tearDown(() async {
    await backend.stop();
    ApiClient.instance = ApiClient();
    ApiClient.dropWriteResponseCount = 0;
    ApiClient.dropWriteResponsePathPrefix = '';
    ConnectionStatus.instance.reset();
  });

  test('前 3 次送出在後端已處理之後逾時，重送用同一把鍵，後端只有 1 張單', () async {
    ApiClient.dropWriteResponseCount = 3;

    ConnectionStatus.instance.setOffline(true);
    final sentWhileOffline = await _submitAcTicket();

    expect(sentWhileOffline, isFalse, reason: '離線時只留在手機上');
    expect(ConnectionStatus.instance.pendingSyncCount.value, 1);
    expect(backend.ticketRequests, isEmpty, reason: '離線期間後端不該收到任何東西');

    ConnectionStatus.instance.setOffline(false);
    await _waitUntil(() => ConnectionStatus.instance.pendingSyncCount.value == 0);

    expect(backend.ticketRequests.length, 4,
        reason: '前 3 次逾時、第 4 次才收到回應');
    expect(backend.ticketKeys.toSet(), hasLength(1),
        reason: '重送必須帶第一次那把 Idempotency-Key');
    expect(backend.createdTicketIds, hasLength(1),
        reason: '後端認得出是同一次動作，只建立一張單');
    expect(ApiClient.dropWriteResponseCount, 0);
  });

  test('沒有注入時，恢復連線只送一次就清空待送出', () async {
    ConnectionStatus.instance.setOffline(true);
    await _submitAcTicket();
    ConnectionStatus.instance.setOffline(false);
    await _waitUntil(() => ConnectionStatus.instance.pendingSyncCount.value == 0);

    expect(backend.ticketRequests, hasLength(1));
    expect(backend.createdTicketIds, hasLength(1));
  });

  test('照片上傳不受注入影響，被丟掉回應的是帶 Idempotency-Key 的送出', () async {
    ApiClient.dropWriteResponseCount = 1;

    await ApiClient.instance.uploadRepairPhotoContent('photo-1', <int>[1, 2, 3]);

    expect(backend.photoUploads, hasLength(1));
    expect(ApiClient.dropWriteResponseCount, 1,
        reason: '照片上傳沒有帶 Idempotency-Key，不該消耗注入的次數');

    ConnectionStatus.instance.setOffline(true);
    await _submitAcTicket();
    ConnectionStatus.instance.setOffline(false);
    await _waitUntil(() => ConnectionStatus.instance.pendingSyncCount.value == 0);

    expect(backend.photoUploads, hasLength(1), reason: '重送不會把照片再傳一次');
    expect(backend.ticketRequests, hasLength(2));
    expect(backend.createdTicketIds, hasLength(1));
  });

  test('注入限定在開單路徑時，照片位置不吃額度，重送的是開單請求', () async {
    ApiClient.dropWriteResponseCount = 3;
    ApiClient.dropWriteResponsePathPrefix = '/repair-tickets';

    ConnectionStatus.instance.setOffline(true);
    await _submitAcTicketWithPhoto();
    ConnectionStatus.instance.setOffline(false);
    await _waitUntil(() => ConnectionStatus.instance.pendingSyncCount.value == 0);

    expect(backend.photoCreates, hasLength(1),
        reason: '建立照片位置也帶 Idempotency-Key，但不在限定的路徑上，不該被丟掉回應');
    expect(backend.ticketRequests, hasLength(4),
        reason: '前 3 次開單逾時、第 4 次才收到回應');
    expect(backend.createdTicketIds, hasLength(1));
    expect(ApiClient.dropWriteResponseCount, 0);
  });
}

/// 照 `features/task/repair_sheet.dart` 的送出動線：同一筆動作裡先替照片建立
/// 上傳位置，再開單。已經拿到位置的照片重送時不再建立一次。
Future<bool> _submitAcTicketWithPhoto() {
  String? photoId;
  return OfflineQueue.instance.submit(
    kind: 'repair-ticket',
    send: (idempotencyKey) async {
      photoId ??= await ApiClient.instance.createRepairPhoto(purpose: 'report');
      await ApiClient.instance.createRepairTicket(
        sourceTaskId: 'task-310',
        category: 'air_conditioning',
        photoIds: <String>[photoId!],
        idempotencyKey: idempotencyKey,
      );
    },
  );
}

Future<bool> _submitAcTicket() {
  return OfflineQueue.instance.submit(
    kind: 'repair-ticket',
    send: (idempotencyKey) async {
      await ApiClient.instance.createRepairTicket(
        sourceTaskId: 'task-310',
        category: 'air_conditioning',
        photoIds: const <String>['photo-1'],
        idempotencyKey: idempotencyKey,
      );
    },
  );
}

/// 等某件事成立。恢復連線是由 [ConnectionStatus] 自己把佇列送出去的，呼叫端
/// 拿不到那個 Future，只能看待送出筆數歸零。
Future<void> _waitUntil(
  bool Function() condition, {
  Duration timeout = const Duration(seconds: 10),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) {
      fail('等了 ${timeout.inSeconds} 秒，條件還是不成立');
    }
    await Future<void>.delayed(const Duration(milliseconds: 20));
  }
}

/// 只認得本測試用到的那幾個端點的假後端，開單走 `Idempotency-Key` 去重：同一把
/// 鍵再送一次回同一張單，不會多建一張。
class _FakeBackend {
  _FakeBackend._(this._server);

  static Future<_FakeBackend> start() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final backend = _FakeBackend._(server);
    server.listen(backend._handle);
    return backend;
  }

  final HttpServer _server;

  /// 每一次開單請求帶的 `Idempotency-Key`，照收到的順序。
  final List<String> ticketKeys = <String>[];

  final List<Map<String, dynamic>> ticketRequests = <Map<String, dynamic>>[];

  final Set<String> createdTicketIds = <String>{};

  final List<String> photoUploads = <String>[];

  /// 每一次「建立照片上傳位置」請求帶的 `Idempotency-Key`，照收到的順序。
  final List<String> photoCreates = <String>[];

  final Map<String, String> _ticketIdByKey = <String, String>{};

  String get baseUrl => 'http://127.0.0.1:${_server.port}';

  Future<void> stop() => _server.close(force: true);

  Future<void> _handle(HttpRequest request) async {
    final body = await utf8.decoder.bind(request).join();
    final path = request.uri.path;

    if (request.method == 'POST' && path == '/repair-tickets') {
      final key = request.headers.value('Idempotency-Key')!;
      ticketKeys.add(key);
      ticketRequests.add(jsonDecode(body) as Map<String, dynamic>);
      final ticketId = _ticketIdByKey.putIfAbsent(
          key, () => 'ticket-${_ticketIdByKey.length + 1}');
      createdTicketIds.add(ticketId);
      request.response.statusCode = HttpStatus.ok;
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({
        'taskId': ticketId,
        'ticketNo': 'W-0410',
        'roomNo': '310',
        'category': 'air_conditioning',
        'isUrgent': false,
        'state': 'pending_review',
        'reporterName': '阿美',
      }));
      await request.response.close();
      return;
    }

    if (request.method == 'POST' && path == '/repair-photos') {
      photoCreates.add(request.headers.value('Idempotency-Key')!);
      request.response.statusCode = HttpStatus.ok;
      request.response.headers.contentType = ContentType.json;
      request.response
          .write(jsonEncode({'id': 'photo-${photoCreates.length}'}));
      await request.response.close();
      return;
    }

    if (request.method == 'PUT' && path.startsWith('/repair-photos/')) {
      photoUploads.add(path.split('/')[2]);
      request.response.statusCode = HttpStatus.noContent;
      await request.response.close();
      return;
    }

    request.response.statusCode = HttpStatus.notFound;
    await request.response.close();
  }
}
