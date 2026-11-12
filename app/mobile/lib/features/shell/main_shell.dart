import 'package:flutter/cupertino.dart';

import '../../core/landing.dart';
import '../../core/theme.dart';
import '../engineering/eng_home_screen.dart';
import '../engineering/eng_pool_screen.dart';
import '../home/home_screen.dart';
import '../manager/manager_home_screen.dart';
import '../manager/manager_pool_screen.dart';
import '../me/me_screen.dart';
import '../../core/widgets/tms_tab_bar.dart';
import '../notification/notification_screen.dart';
import '../pool/pool_screen.dart';
import '../today/my_today_scope.dart';

abstract final class MainShellTabRequest {
  static const int _poolIndex = 1;

  static final ValueNotifier<int?> pending = ValueNotifier<int?>(null);

  static final ValueNotifier<String?> poolHighlightRoomNo =
      ValueNotifier<String?>(null);

  static void goToPool({String? highlightRoomNo}) {
    poolHighlightRoomNo.value = highlightRoomNo;
    pending.value = _poolIndex;
  }
}

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

class _MainShell extends StatelessWidget {
  const _MainShell({required this.homeTab, required this.poolTab});

  final Widget homeTab;

  final Widget poolTab;

  @override
  Widget build(BuildContext context) {
    return MyTodayScope(
      child: _MainShellBody(homeTab: homeTab, poolTab: poolTab),
    );
  }
}

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

class ManagerShell extends StatelessWidget {
  const ManagerShell({super.key, required this.kind});

  final ManagerKind kind;

  @override
  Widget build(BuildContext context) {
    return _MainShell(
      homeTab: ManagerHomeScreen(kind: kind),
      poolTab: const ManagerPoolScreen(),
    );
  }
}
