import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';
import '../pool/pool_screen.dart';

/// 首頁骨架畫面。
///
/// 目前只做到「F3 點開推播的去處」斷言需要的程度：證明 App 確實停在
/// 首頁即可。F1（今日進度四項、下一間順序…）的實際內容留給後續
/// feature 補。
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'home.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('首頁')),
        child: SafeArea(
          child: Center(
            child: CupertinoButton(
              onPressed: () {
                Navigator.of(context).push(
                  CupertinoPageRoute<void>(builder: (_) => const PoolScreen()),
                );
              },
              child: const Text('待認領'),
            ),
          ),
        ),
      ),
    );
  }
}
