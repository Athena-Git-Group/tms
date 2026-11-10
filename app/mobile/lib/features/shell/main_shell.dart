import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../engineering/eng_home_screen.dart';
import '../engineering/eng_pool_screen.dart';
import '../home/home_screen.dart';
import '../me/me_screen.dart';
import '../../core/widgets/tms_tab_bar.dart';
import '../notification/notification_screen.dart';
import '../pool/pool_screen.dart';
import '../today/my_today_scope.dart';

/// 推到外殼之上的畫面要求外殼切分頁的通道。
///
/// 完成畫面的「去『待認領』看看」疊在外殼上好幾層，手上拿不到外殼那個
/// state，所以它只在這裡留下要去哪一個分頁，外殼自己讀走並切過去。外殼
/// 還沒掛上時留下的要求不會有人處理，下一次要求會蓋掉它。
abstract final class MainShellTabRequest {
  static const int _poolIndex = 1;

  static final ValueNotifier<int?> pending = ValueNotifier<int?>(null);

  static void goToPool() => pending.value = _poolIndex;
}

/// 現在停在第幾個分頁，給分頁內容自己讀。
///
/// [IndexedStack] 會把建過的分頁留在樹上，所以切走的分頁不會收到任何通知，自
/// 己也看不出來已經被切走。工務首頁的首次使用教學氣泡要在員工離開首頁之後就
/// 不再出現，少了這個通道它永遠以為自己還在畫面上。
///
/// 外殼還沒掛上時（例如元件測試只 pump 一個分頁）當成停在首頁，那是分頁內容
/// 單獨出現時唯一說得通的位置。
class MainShellTab extends InheritedWidget {
  const MainShellTab({super.key, required this.index, required super.child});

  final int index;

  static const int homeIndex = 0;

  static int indexOf(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<MainShellTab>();
    return scope?.index ?? homeIndex;
  }

  @override
  bool updateShouldNotify(MainShellTab oldWidget) =>
      oldWidget.index != index;
}

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

  final Widget homeTab;

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
///
/// 分頁列是浮在內容上方的玻璃板，不是把內容往上推的實心列，所以這裡用
/// [Stack] 自己疊三層：分頁內容鋪滿整個畫面、底部的 soft scroll edge、最上
/// 面的 [TmsTabBar]。Flutter 內建的 `CupertinoTabScaffold` 做不到這件事，它
/// 一定把分頁列的高度從內容區扣掉，內容就不會從玻璃底下透出來。
///
/// 切過的分頁要留在原地：員工切到待認領再切回首頁，首頁得還是原來那一屏。
/// [IndexedStack] 會把每一個建過的分頁留在樹上，捲動位置與畫面狀態因此不會
/// 掉。沒去過的分頁先擺一個空殼，進去的時候才真的建起來，不然四個分頁會在
/// 登入當下同時打自己的讀取請求。
class _MainShellBody extends StatefulWidget {
  const _MainShellBody({required this.homeTab, required this.poolTab});

  final Widget homeTab;

  final Widget poolTab;

  @override
  State<_MainShellBody> createState() => _MainShellBodyState();
}

class _MainShellBodyState extends State<_MainShellBody> {
  int _index = 0;

  final Set<int> _built = <int>{0};

  @override
  void initState() {
    super.initState();
    MainShellTabRequest.pending.addListener(_applyTabRequest);
  }

  @override
  void dispose() {
    MainShellTabRequest.pending.removeListener(_applyTabRequest);
    super.dispose();
  }

  void _applyTabRequest() {
    final requested = MainShellTabRequest.pending.value;
    if (requested == null) {
      return;
    }
    MainShellTabRequest.pending.value = null;
    setState(() {
      _index = requested;
      _built.add(requested);
    });
  }

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    final tabs = <Widget>[
      widget.homeTab,
      widget.poolTab,
      const NotificationScreen(),
      const MeScreen(),
    ];

    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      child: Stack(
        children: [
          Positioned.fill(
            child: MainShellTab(
              index: _index,
              child: IndexedStack(
                index: _index,
                children: [
                  for (var i = 0; i < tabs.length; i++)
                    if (_built.contains(i))
                      tabs[i]
                    else
                      const SizedBox.shrink(),
                ],
              ),
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: TmsBottomScrollEdge(
              height: TmsSpacing.bottomScrollEdgeHeight(bottomInset),
            ),
          ),
          Positioned(
            left: TmsSpacing.floatingTabBarSide,
            right: TmsSpacing.floatingTabBarSide,
            bottom: TmsSpacing.floatingTabBarBottom(bottomInset),
            child: TmsTabBar(
              currentIndex: _index,
              onSelect: (index) {
                setState(() {
                  _index = index;
                  _built.add(index);
                });
              },
              items: [
                const TmsTabItem(label: '首頁', glyph: TmsTabGlyph.home),
                TmsTabItem(
                  label: '待認領',
                  glyph: TmsTabGlyph.pool,
                  badgeCount: today?.poolBadgeCount ?? 0,
                ),
                TmsTabItem(
                  label: '通知',
                  glyph: TmsTabGlyph.notification,
                  badgeCount: today?.unreadNotificationBadgeCount ?? 0,
                ),
                const TmsTabItem(label: '我的', glyph: TmsTabGlyph.me),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class HousekeepingShell extends StatelessWidget {
  const HousekeepingShell({super.key});

  @override
  Widget build(BuildContext context) {
    return const _MainShell(homeTab: HomeScreen(), poolTab: PoolScreen());
  }
}

class EngineeringShell extends StatelessWidget {
  const EngineeringShell({super.key});

  @override
  Widget build(BuildContext context) {
    return const _MainShell(homeTab: EngHomeScreen(), poolTab: EngPoolScreen());
  }
}
