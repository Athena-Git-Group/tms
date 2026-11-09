import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_card.dart';

/// 一個空狀態要顯示的圖示與文字。
///
/// 文案跟畫面分開，是因為同一個空狀態的句子會隨著當下的資料換好幾種說法
/// （手上沒有任務、今天都做完了、這個分類沒有單），而版型自始至終只有一套。
/// 由 [TmsEmptyStates] 的函式算出一個 [TmsEmptyCopy]，再交給
/// [TmsContentUnavailable] 畫，句子就只會有一份，畫面也不必自己拼字串。
@immutable
class TmsEmptyCopy {
  const TmsEmptyCopy({
    required this.icon,
    required this.title,
    required this.description,
    this.isPositive = false,
    this.actionLabel,
  });

  /// 置中的描邊圖示。
  final IconData icon;

  /// 22pt 粗體標題，一句話說明現在是什麼狀況。
  final String title;

  /// 15pt 說明，一句話說明接下來會發生什麼或可以做什麼。
  final String description;

  /// 這個空狀態是不是正向回饋（例如「今天的任務都完成了」）。
  ///
  /// 設計上只有正向回饋用成功綠，其餘空狀態維持灰色（`Main.dc.html:212`）。
  final bool isPositive;

  /// 說明底下那顆文字按鈕的字；null 代表這個空狀態沒有按鈕。
  final String? actionLabel;
}

/// 全 App 的空狀態版型，對應 iOS 的 ContentUnavailableView。
///
/// 預設版型不包卡片，把剩下的一屏撐開並置中（`Main.dc.html:296-306`）：描邊
/// 圖示 56、14 的間距、22pt 標題、6 的間距、15pt 說明，需要時再接一顆文字
/// 按鈕。待認領、通知、遺失物三處都是這一種。
///
/// 首頁是例外，用 [TmsContentUnavailable.inCard]（`Main.dc.html:212-219`）：
/// 首頁上下都是卡片，空狀態不包卡會看起來像掉出版面，所以改成包一張圓角 20
/// 的白卡，上下內距 28、左右 20，標題間距也收到 12。
class TmsContentUnavailable extends StatelessWidget {
  /// 不包卡片的版型，內容在 [minHeight] 撐開的空間裡置中。
  const TmsContentUnavailable({
    super.key,
    required this.id,
    required this.copy,
    this.onAction,
    this.minHeight = poolMinHeight,
  }) : _inCard = false;

  /// 包白卡的版型，只給首頁用。卡片高度隨內容，所以沒有 [minHeight]。
  const TmsContentUnavailable.inCard({
    super.key,
    required this.id,
    required this.copy,
    this.onAction,
  }) : _inCard = true,
       minHeight = 0;

  /// accessibility id，例如 `home.empty-state`。
  final String id;

  final TmsEmptyCopy copy;

  /// 按下 [TmsEmptyCopy.actionLabel] 那顆按鈕要做的事。
  ///
  /// 文字與動作分開傳，是因為文字屬於文案、動作屬於畫面。兩者都有值才畫
  /// 按鈕，畫面上就不會出現按了沒反應的按鈕。
  final VoidCallback? onAction;

  /// 不包卡片時，整塊空狀態至少要佔多高。
  ///
  /// 原型按畫面各給一個值，讓內容落在那一頁的視覺中央而不是黏在標題底下。
  final double minHeight;

  final bool _inCard;

  /// 待認領兩頁的空狀態高度（`Main.dc.html:297`、`1012`）。
  static const double poolMinHeight = 380;

  /// 通知中心的空狀態高度（`Main.dc.html:398`）。
  static const double notificationMinHeight = 460;

  /// 遺失物清單的空狀態高度（`Main.dc.html:621`）。
  static const double lostAndFoundMinHeight = 520;

  /// 不包卡片時的左右內距（`Main.dc.html:297`）。比畫面邊距寬，說明文才不會
  /// 長到兩邊見底。
  static const double _horizontalPadding = 32;

  /// 不包卡片時的下內距（`Main.dc.html:297`）。比上內距大，整塊內容在視覺上
  /// 才落在偏上一點的位置。
  static const double _bottomPadding = 40;

  @override
  Widget build(BuildContext context) {
    final actionLabel = copy.actionLabel;
    final body = Column(
      mainAxisSize: _inCard ? MainAxisSize.min : MainAxisSize.max,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        TmsSemantics(
          id: id,
          label: '${copy.title}。${copy.description}',
          child: ExcludeSemantics(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  copy.icon,
                  size: TmsSize.emptyStateIcon,
                  color: copy.isPositive
                      ? TmsColors.systemGreen
                      : TmsColors.quaternaryLabel,
                ),
                SizedBox(height: _inCard ? TmsSpacing.md : TmsSpacing.md2),
                Text(
                  copy.title,
                  textAlign: TextAlign.center,
                  style: TmsText.title2,
                ),
                const SizedBox(height: TmsSpacing.xs2),
                Text(
                  copy.description,
                  textAlign: TextAlign.center,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.secondaryLabel,
                  ),
                ),
              ],
            ),
          ),
        ),
        if (actionLabel != null && onAction != null) ...[
          const SizedBox(height: TmsSpacing.xs2),
          TmsSemantics(
            id: '$id.action',
            // 按鈕的字同時掛在這個節點上，驗收才讀得到：容器型節點本身沒有
            // label，以 id 定位後拿到的會是空字串。
            label: actionLabel,
            child: CupertinoButton(
              padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.md),
              minimumSize: const Size(0, TmsSize.minTouchTarget),
              onPressed: onAction,
              child: Text(
                actionLabel,
                style: TmsText.body.copyWith(color: TmsColors.brandPrimary),
              ),
            ),
          ),
        ],
      ],
    );

    if (_inCard) {
      return TmsCard(
        style: TmsCardStyle.hero,
        padding: const EdgeInsets.symmetric(
          vertical: TmsSpacing.xxl,
          horizontal: TmsSpacing.xl,
        ),
        child: body,
      );
    }
    return Container(
      constraints: BoxConstraints(minHeight: minHeight),
      padding: const EdgeInsets.fromLTRB(
        _horizontalPadding,
        TmsSpacing.xl2,
        _horizontalPadding,
        _bottomPadding,
      ),
      child: body,
    );
  }
}

/// App 各處空狀態的文案。
///
/// 每一條都按當下的資料算出該說哪一句，呼叫端只要把手上的數字交出來。文案
/// 集中在這裡，是因為同一句話會同時被畫面與驗收測試讀到；句子散在各畫面裡
/// 時，改一句話得找遍所有畫面，漏掉的那個畫面會繼續講舊的說法。
abstract final class TmsEmptyStates {
  /// 托盤。中性的「沒有資料」一律用它（`Main.dc.html:298`）。
  static const IconData _trayIcon = CupertinoIcons.tray;

  /// 打勾圓圈。只給「都做完了」這種正向回饋用（`Main.dc.html:215`）。
  static const IconData _doneIcon = CupertinoIcons.check_mark_circled;

  /// 待認領空狀態那顆切回全部分類的按鈕（`Main.dc.html:302`）。
  static const String showAllCategoriesLabel = '顯示全部分類';

  /// 通知中心一則通知都沒有（`Main.dc.html:402-404`）。
  static const TmsEmptyCopy notifications = TmsEmptyCopy(
    icon: CupertinoIcons.bell,
    title: '目前沒有通知',
    description: '派單、急件與退回都會出現在這裡。',
  );

  /// 遺失物清單一件拾獲物品都沒有（`Main.dc.html:621-624`）。
  static const TmsEmptyCopy lostAndFound = TmsEmptyCopy(
    icon: CupertinoIcons.cube_box,
    title: '目前沒有拾獲的物品',
    description: '拾獲物品時，點右上角「＋」登記。',
  );

  /// 房務待認領清單是空的（`Main.dc.html:5121`）。
  ///
  /// [filterApplied] 是員工現在有沒有選定某個分類，[poolHasItems] 是整池
  /// 還有沒有單。兩者同時成立時說的是「這個分類沒有單」，並附一顆切回全部
  /// 分類的按鈕；其餘情況說的是「整池都沒有單」，不附按鈕，因為切回全部
  /// 分類看到的還是空清單。
  static TmsEmptyCopy housekeepingPool({
    required bool filterApplied,
    required bool poolHasItems,
  }) {
    if (filterApplied && poolHasItems) {
      return const TmsEmptyCopy(
        icon: _trayIcon,
        title: '這個分類沒有待認領的單',
        description: '換個分類看看，或等新需求進來。',
        actionLabel: showAllCategoriesLabel,
      );
    }
    return const TmsEmptyCopy(
      icon: _trayIcon,
      title: '目前沒有待認領的單',
      description: '新需求進來時會推播通知你。',
    );
  }

  /// 工務待認領清單是空的（`Main.dc.html:4093`）。說法跟房務同一套，只有
  /// 「新需求」換成「新的工務單」。
  static TmsEmptyCopy engineeringPool({
    required bool filterApplied,
    required bool poolHasItems,
  }) {
    if (filterApplied && poolHasItems) {
      return const TmsEmptyCopy(
        icon: _trayIcon,
        title: '這個分類沒有待認領的單',
        description: '換個分類看看，或等新的工務單進來。',
        actionLabel: showAllCategoriesLabel,
      );
    }
    return const TmsEmptyCopy(
      icon: _trayIcon,
      title: '目前沒有待認領的單',
      description: '新的工務單進來會推播通知你。',
    );
  }

  /// 房務首頁沒有可以開始的任務（`Main.dc.html:5109-5118`）。
  ///
  /// [snoozedCount] 是已認領但還沒開始的單，[doneCount] 是今天已完成的單，
  /// [poolHasItems] 是待認領池裡還有沒有單。手上還有延後的單時指路到下方的
  /// 「我的任務」，員工才不會以為那些單不見了；沒有延後的單但今天完成過，
  /// 說的是一天的收尾回饋；兩者都沒有才是真正的手上沒事。
  static TmsEmptyCopy housekeepingHome({
    required int snoozedCount,
    required int doneCount,
    required bool poolHasItems,
  }) {
    if (snoozedCount > 0) {
      return const TmsEmptyCopy(
        icon: _trayIcon,
        title: '目前沒有可以開始的單',
        description: '延後的單在下方「我的任務」，準備好再開始。',
      );
    }
    final tail = poolHasItems ? '到「待認領」認領新的單。' : '新需求進來時會推播通知你。';
    if (doneCount > 0) {
      return TmsEmptyCopy(
        icon: _doneIcon,
        title: '今天的任務都完成了',
        description: '今天完成 $doneCount 單，$tail',
        isPositive: true,
      );
    }
    return TmsEmptyCopy(icon: _trayIcon, title: '手上沒有任務', description: tail);
  }

  /// 工務首頁沒有在處理的工務單（`Main.dc.html:4080-4086`）。
  ///
  /// 工務這一側沒有「延後的單」那一種說法，只分今天完修過與完全沒事兩種。
  static TmsEmptyCopy engineeringHome({
    required int doneCount,
    required bool poolHasItems,
  }) {
    final tail = poolHasItems ? '到「待認領」認領新的單。' : '新的工務單進來會推播通知你。';
    if (doneCount > 0) {
      return TmsEmptyCopy(
        icon: _doneIcon,
        title: '今天的工務單都完修了',
        description: '今天完修 $doneCount 張，$tail',
        isPositive: true,
      );
    }
    return TmsEmptyCopy(icon: _trayIcon, title: '手上沒有工務單', description: tail);
  }
}
