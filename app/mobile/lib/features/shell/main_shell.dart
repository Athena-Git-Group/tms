import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../engineering/eng_home_screen.dart';
import '../engineering/eng_pool_screen.dart';
import '../home/home_screen.dart';
import '../me/me_screen.dart';
import '../../core/widgets/tms_category_tile.dart';
import '../notification/notification_screen.dart';
import '../pool/pool_screen.dart';
import '../today/my_today_scope.dart';

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
    // 今日進度取在外殼這一層，不是取在首頁裡。分頁列的兩顆徽章與首頁的每一
    // 個數字都出自同一支 `GET /me/today`，誰先要用都讀同一份：擺在首頁裡的
    // 話外殼讀不到（InheritedWidget 只往下傳），外殼只好自己再打一次，一次
    // 進場就變成兩個請求、兩份可能對不起來的數字。
    return MyTodayScope(
      child: _MainShellBody(homeTab: homeTab, poolTab: poolTab),
    );
  }
}

/// 外殼的分頁骨架。獨立成一個元件，是為了讓它的 `context` 落在
/// [MyTodayScope] 底下——分頁列的徽章要靠這一點才讀得到今日進度。
class _MainShellBody extends StatelessWidget {
  const _MainShellBody({required this.homeTab, required this.poolTab});

  final Widget homeTab;

  final Widget poolTab;

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
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
        items: [
          const BottomNavigationBarItem(
            icon: Icon(CupertinoIcons.house_fill),
            label: '首頁',
          ),
          BottomNavigationBarItem(
            icon: _TabIcon(
              icon: CupertinoIcons.tray_full,
              badgeCount: today?.poolBadgeCount ?? 0,
            ),
            label: '待認領',
          ),
          BottomNavigationBarItem(
            icon: _TabIcon(
              icon: CupertinoIcons.bell,
              badgeCount: today?.unreadNotificationBadgeCount ?? 0,
            ),
            label: '通知',
          ),
          const BottomNavigationBarItem(
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

/// 分頁列上的一格圖示，右上角可以掛一顆紅色數量徽章。
///
/// 計數是 0 時整顆徽章不畫。分類磚上的徽章 0 也照顯示，那是因為那幾顆數字
/// 本身就是驗收要讀的值；分頁列這兩顆沒有人讀，留一顆「0」在圖示旁邊只會
/// 讓員工以為有東西等著處理。
class _TabIcon extends StatelessWidget {
  const _TabIcon({required this.icon, required this.badgeCount});

  final IconData icon;

  final int badgeCount;

  @override
  Widget build(BuildContext context) {
    if (badgeCount <= 0) {
      return Icon(icon);
    }
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Icon(icon),
        Positioned(
          top: -_tabBadgeInset,
          left: _tabIconSize * _tabBadgeLeftRatio,
          child: TmsCountBadge(count: badgeCount),
        ),
      ],
    );
  }
}

/// 分頁列的底色。它帶一點透明度，捲到底的內容會在分頁列底下透出來一點，
/// 員工因此看得出清單還沒到底。
const Color _tabBarBackground = Color(0xF0F9F9F9);

/// 分頁圖示的邊長。
const double _tabIconSize = 26;

/// 徽章相對圖示上緣往外探出的距離。
const double _tabBadgeInset = TmsSpacing.xs;

/// 徽章左緣落在圖示寬度的哪一個比例上（原型是 54%）。
const double _tabBadgeLeftRatio = 0.54;

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
