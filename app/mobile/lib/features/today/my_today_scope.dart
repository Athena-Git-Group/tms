import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/today_refresh.dart';

class MyTodayScope extends StatefulWidget {
  const MyTodayScope({super.key, required this.child});

  final Widget child;

  static MyToday? todayOf(BuildContext context) => _dataOf(context)?.today;

  static Object? errorOf(BuildContext context) => _dataOf(context)?.error;

  static _MyTodayData? _dataOf(BuildContext context) {
    final data = context.dependOnInheritedWidgetOfExactType<_MyTodayData>();
    assert(data != null, '要讀今日進度的元件必須放在 MyTodayScope 底下');
    return data;
  }

  @override
  State<MyTodayScope> createState() => _MyTodayScopeState();
}

class _MyTodayScopeState extends State<MyTodayScope> {
  MyToday? _today;
  Object? _error;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _load();
    TodayRefresh.instance.addListener(_load);
    ConnectionStatus.instance.unstable.addListener(_syncPolling);
    _syncPolling();
  }

  @override
  void dispose() {
    TodayRefresh.instance.removeListener(_load);
    ConnectionStatus.instance.unstable.removeListener(_syncPolling);
    _pollTimer?.cancel();
    super.dispose();
  }

  void _syncPolling() {
    _pollTimer?.cancel();
    if (!ConnectionStatus.instance.isUnstable) {
      _pollTimer = null;
      return;
    }
    _pollTimer = Timer.periodic(degradedPollInterval, (_) => _load());
  }

  Future<void> _load() async {
    final MyToday today;
    try {
      today = await ApiClient.instance.getMyToday();
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
        });
      }
      return;
    }
    if (mounted) {
      setState(() {
        _today = today;
        _error = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return _MyTodayData(today: _today, error: _error, child: widget.child);
  }
}

class _MyTodayData extends InheritedWidget {
  const _MyTodayData({
    required this.today,
    required this.error,
    required super.child,
  });

  final MyToday? today;
  final Object? error;

  @override
  bool updateShouldNotify(_MyTodayData oldWidget) =>
      today != oldWidget.today || error != oldWidget.error;
}
