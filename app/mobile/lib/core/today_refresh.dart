import 'package:flutter/foundation.dart';

class TodayRefresh extends ChangeNotifier {
  TodayRefresh._();

  static final TodayRefresh instance = TodayRefresh._();

  void request() => notifyListeners();
}
