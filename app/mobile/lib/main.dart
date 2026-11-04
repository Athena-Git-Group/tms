import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart' show Theme;
import 'package:flutter/semantics.dart';

import 'core/test_backdoor.dart';
import 'core/theme.dart';
import 'features/login/login_screen.dart';

Future<void> main() async {
  // 一定要先完成 binding 初始化，才能呼叫 ensureSemantics()；順序顛倒會讓
  // App 啟動時卡在白屏（spike 已驗證過這個順序問題）。
  WidgetsFlutterBinding.ensureInitialized();
  SemanticsBinding.instance.ensureSemantics();

  await TestBackdoor.maybeStart();

  runApp(const TmsStaffApp());
}

class TmsStaffApp extends StatelessWidget {
  const TmsStaffApp({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = buildTmsTheme();
    return CupertinoApp(
      title: 'TMS 員工 App',
      theme: CupertinoThemeData(
        primaryColor: tmsBrandColor,
        brightness: Brightness.light,
      ),
      builder: (context, child) {
        // CupertinoApp 本身不帶 Material 主題，部分共用元件仍可能依賴
        // Theme.of(context)，所以在這裡補一層 Material 主題當底。
        return Theme(data: theme, child: child!);
      },
      home: const LoginScreen(),
    );
  }
}
