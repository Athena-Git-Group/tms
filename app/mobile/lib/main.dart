import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart' show Theme;
import 'package:flutter/semantics.dart';

import 'core/push_gateway.dart';
import 'core/test_backdoor.dart';
import 'core/theme.dart';
import 'features/home/home_screen.dart';
import 'features/login/login_screen.dart';

/// 跟 [TestBackdoor] 共用同一個旗標：`TEST_BACKDOOR=true` 時 App 直接進
/// 首頁，跳過登入殼——這是給測試腳本用的捷徑，一般建置不帶這個旗標，
/// 照舊要求登入。
const bool _skipLoginForTesting = bool.fromEnvironment('TEST_BACKDOOR');

/// 推播導頁要用的全域 navigator key，交給 [PushGateway] 操作導頁。
final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();

Future<void> main() async {
  // 一定要先完成 binding 初始化，才能呼叫 ensureSemantics()；順序顛倒會讓
  // App 啟動時卡在白屏（spike 已驗證過這個順序問題）。
  WidgetsFlutterBinding.ensureInitialized();
  SemanticsBinding.instance.ensureSemantics();

  await TestBackdoor.maybeStart();

  runApp(const TmsStaffApp());
}

class TmsStaffApp extends StatefulWidget {
  const TmsStaffApp({super.key});

  @override
  State<TmsStaffApp> createState() => _TmsStaffAppState();
}

class _TmsStaffAppState extends State<TmsStaffApp> {
  @override
  void initState() {
    super.initState();
    PushGateway.instance.attach(rootNavigatorKey);
    // 冷啟動點開推播時，原生側收到「使用者點開通知」事件的時間點比
    // Flutter 引擎、Navigator 準備好還早，所以要等第一幀畫完才問原生側
    // 「啟動時是不是帶了推播」，不能在 initState 當下就問。
    WidgetsBinding.instance.addPostFrameCallback((_) {
      PushGateway.instance.consumeInitialPush();
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = buildTmsTheme();
    return CupertinoApp(
      title: 'TMS 員工 App',
      navigatorKey: rootNavigatorKey,
      theme: CupertinoThemeData(
        primaryColor: tmsBrandColor,
        brightness: Brightness.light,
      ),
      builder: (context, child) {
        // CupertinoApp 本身不帶 Material 主題，部分共用元件仍可能依賴
        // Theme.of(context)，所以在這裡補一層 Material 主題當底。
        return Theme(data: theme, child: child!);
      },
      home: _skipLoginForTesting ? const HomeScreen() : const LoginScreen(),
    );
  }
}
