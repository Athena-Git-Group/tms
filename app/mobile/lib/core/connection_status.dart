import 'package:flutter/foundation.dart';

import 'api_client.dart';
import 'today_refresh.dart';

class ConnectionStatus {
  ConnectionStatus._();

  static final ConnectionStatus instance = ConnectionStatus._();

  final ValueNotifier<bool> _offline = ValueNotifier<bool>(false);

  final ValueNotifier<bool> _unstable = ValueNotifier<bool>(false);

  ValueListenable<bool> get offline => _offline;

  ValueListenable<bool> get unstable => _unstable;

  ValueListenable<int> get pendingSyncCount => OfflineQueue.instance.pendingCount;

  bool get isOffline => _offline.value;

  bool get isUnstable => _unstable.value;

  void setOffline(bool offline) {
    if (_offline.value == offline) {
      return;
    }
    _offline.value = offline;
    if (!offline) {
      OfflineQueue.instance.flush();
    }
  }

  void setUnstable(bool unstable) => _unstable.value = unstable;

  void reset() {
    _offline.value = false;
    _unstable.value = false;
  }
}

const Duration degradedPollInterval = Duration(seconds: 5);

class OfflineQueue {
  OfflineQueue._();

  static final OfflineQueue instance = OfflineQueue._();

  static const int _maxAttempts = 5;

  static const Duration _retryDelay = Duration(milliseconds: 300);

  final List<PendingOperation> _pending = <PendingOperation>[];

  bool _flushing = false;

  final ValueNotifier<int> pendingCount = ValueNotifier<int>(0);

  Future<bool> submit({
    required String kind,
    required Future<void> Function(String idempotencyKey) send,
  }) async {
    final operation = PendingOperation(
      kind: kind,
      idempotencyKey: ApiClient.newIdempotencyKey(),
      send: send,
    );
    if (ConnectionStatus.instance.isOffline) {
      _add(operation);
      return false;
    }
    await send(operation.idempotencyKey);
    return true;
  }

  void enqueue(PendingOperation operation) => _add(operation);

  Future<void> flush() async {
    if (_flushing) {
      return;
    }
    _flushing = true;
    try {
      while (_pending.isNotEmpty && !ConnectionStatus.instance.isOffline) {
        final operation = _pending.first;
        if (!await _sendWithRetry(operation)) {
          return;
        }
        _pending.remove(operation);
        pendingCount.value = _pending.length;
        TodayRefresh.instance.request();
      }
    } finally {
      _flushing = false;
    }
  }

  Future<bool> _sendWithRetry(PendingOperation operation) async {
    for (var attempt = 1; attempt <= _maxAttempts; attempt++) {
      try {
        await operation.send(operation.idempotencyKey);
        return true;
      } catch (_) {
        if (attempt == _maxAttempts || ConnectionStatus.instance.isOffline) {
          return false;
        }
        await Future<void>.delayed(_retryDelay);
      }
    }
    return false;
  }

  void _add(PendingOperation operation) {
    _pending.add(operation);
    pendingCount.value = _pending.length;
  }
}

class PendingOperation {
  PendingOperation({
    required this.kind,
    required this.idempotencyKey,
    required this.send,
  });

  final String kind;

  final String idempotencyKey;

  final Future<void> Function(String idempotencyKey) send;
}
