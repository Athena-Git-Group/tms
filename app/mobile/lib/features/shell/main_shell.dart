import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../engineering/eng_home_screen.dart';
import '../engineering/eng_pool_screen.dart';
import '../home/home_screen.dart';
import '../me/me_screen.dart';
import '../notification/notification_screen.dart';
import '../pool/pool_screen.dart';

/// 登入後的主畫面外殼：底部四個分頁，首頁／待認領／通知／我的。
///
/// 四個分頁是員工整天會來回切的地方，所以它們彼此平行、互相之間沒有返回
/// 關係：切到待認領再切回首頁，首頁要還是原來那一屏，不是重新載入的新頁。
/// 分頁數固定四個，部門切換不是第五個分頁——工務與房務是兩套不同的工作
/// 內容，由 [EngineeringShell] 與 [HousekeepingShell] 各自提供自己的首頁與
/// 待認領，通知與我的兩頁兩邊共用。
///
/// 分頁內容刻意不包在各自的 Navigator 裡。任務頁、工務單詳情這些從分頁點
/// 進去的畫面要蓋住整個螢幕（含分頁列），登出也要能一次清掉整個返回堆疊；
/// 分頁各帶一個 Navigator 的話，推進去的畫面會卡在分頁列上方，而登出只會
/// 清掉當下那個分頁的堆疊，員工會留在一個還有分頁列的登入畫面上。
class _MainShell extends StatelessWidget {
  const _MainShell({required this.homeTab, required this.poolTab});

  /// 第一個分頁的內容。房務是房務首頁，工務是工務首頁。
  final Widget homeTab;

  /// 第二個分頁的內容。房務與工務各看自己部門的待認領。
  final Widget poolTab;

  @override
  Widget build(BuildContext context) {
    return CupertinoTabScaffold(
      tabBar: CupertinoTabBar(
        backgroundColor: _tabBarBackground,
        activeColor: TmsColors.brandPrimary,
        inactiveColor: TmsColors.quaternaryLabel,
        iconSize: _tabIconSize,
        border: const Border(
          top: BorderSide(
            color: TmsColors.opaqueSeparator,
            width: TmsSize.hairline,
          ),
        ),
        items: const [
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.house_fill),
            label: '首頁',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.tray_full),
            label: '待認領',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.bell),
            label: '通知',
          ),
          BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.person),
            label: '我的',
          ),
        ],
      ),
      tabBuilder: (context, index) {
        switch (index) {
          case 0:
            return homeTab;
          case 1:
            return poolTab;
          case 2:
            return const NotificationScreen();
          default:
            return const MeScreen();
        }
      },
    );
  }
}

/// 分頁列的底色。它帶一點透明度，捲到底的內容會在分頁列底下透出來一點，
/// 員工因此看得出清單還沒到底。
const Color _tabBarBackground = Color(0xF0F9F9F9);

/// 分頁圖示的邊長。
const double _tabIconSize = 26;

/// 房務員工登入後的主畫面。
class HousekeepingShell extends StatelessWidget {
  const HousekeepingShell({super.key});

  @override
  Widget build(BuildContext context) {
    return const _MainShell(
      homeTab: HomeScreen(),
      poolTab: PoolScreen(),
    );
  }
}

/// 工務員工登入後的主畫面。
class EngineeringShell extends StatelessWidget {
  const EngineeringShell({super.key});

  @override
  Widget build(BuildContext context) {
    return const _MainShell(
      homeTab: EngHomeScreen(),
      poolTab: EngPoolScreen(),
    );
  }
}
