import 'package:flutter/cupertino.dart';

/// 把 [screen] 設成唯一的 route，清掉整個返回堆疊。
///
/// 登入、登出這類「換一個身分流程」的導頁都走這裡：換頁後返回手勢
/// 無處可去，使用者不可能滑回上一個流程的畫面（例如登出後滑回「我的」、
/// 登入成功後滑回登入表單）。留著返回路徑會讓沒有憑證（或換了身分）的
/// 狀態下看到舊流程的畫面。
void replaceAllRoutes(BuildContext context, Widget screen) {
  Navigator.of(context).pushAndRemoveUntil(
    CupertinoPageRoute<void>(builder: (_) => screen),
    (route) => false,
  );
}
