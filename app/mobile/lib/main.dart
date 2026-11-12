import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart' show Theme;
import 'package:flutter/semantics.dart';

import 'core/firebase_push_messaging.dart';
import 'core/push_gateway.dart';
import 'core/test_backdoor.dart';
import 'core/theme.dart';
import 'features/login/login_screen.dart';

final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SemanticsBinding.instance.ensureSemantics();

  await TestBackdoor.maybeStart();

  installFirebasePushTokenService();

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
      theme: buildTmsCupertinoTheme(),
      builder: (context, child) {
        return Theme(data: theme, child: child!);
      },
      home: const LoginScreen(),
    );
  }
}
