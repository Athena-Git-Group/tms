import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/navigation.dart';
import '../../core/push_token_service.dart';
import '../../core/system_status.dart';
import '../../core/test_backdoor.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../login/login_screen.dart';
import '../task/task_screen.dart';
import '../today/my_today_scope.dart';

/// 「我的」畫面（ui-plan 畫面 10）：員工在這裡確認自己是用誰的帳號在用這支
/// App、看一眼手上還有哪些工作、調整通知與顯示、換密碼或語言，以及登出。
///
/// 個人資料、交班單、遺失物、推播通知、文字大小、變更密碼、語言七個子頁都
/// 還沒有畫面，點了只會說「尚未開放」。入口仍然先擺出來，是因為員工要能在
/// 這一頁找得到自己的東西在哪一組底下；等子頁做好，位置不用再重排一次。
///
/// 工作磚上的兩個數字現在空著。後端缺的不是端點（`/handovers` 與
/// `/lost-items` 都在契約裡），是這兩個讀取面：`/handovers` 的回應不帶「當前
/// 員工簽收了沒」，`/lost-items` 沒有「只看我登記的」篩選。填一個 0 上去，
/// 員工會以為真的是零。
///
/// 登出分三段，和原型同一套（`Main.dc.html:4671-4674`）：
///
/// 1. **還有資料沒同步**：離線、或手機上還押著沒送出去的動作時攔住。這時
///    登出會丟掉本機憑證，那些還沒送出的紀錄就再也送不出去了。
/// 2. **名下有進行中的單**：以後端回應為準——用 `GET /me/today` 的
///    `todayTasks` 看有沒有任何一張單的 state 是 `doing`。「進行中」只算
///    正在做的那一間；待開始、已延後、已中斷、待查房的單都不是進行中——
///    延後與中斷本來就是員工為了可以先放下（包括下班登出）而做的動作，
///    拿「今天還沒全做完」或「剩餘工時 > 0」來攔，會讓名下掛著延後單的人
///    永遠登不出。App 不在本機另記任務狀態。
/// 3. **確認**：前兩段都過了才問「要登出嗎？」。
class MeScreen extends StatelessWidget {
  const MeScreen({super.key});

  Future<void> _attemptLogout(BuildContext context) async {
    final int pendingSyncCount =
        ConnectionStatus.instance.pendingSyncCount.value;
    if (ConnectionStatus.instance.isOffline) {
      _showBlockedAlert(
        context,
        title: '還有資料沒同步',
        message: '目前離線。進行中的紀錄還存在這支手機，恢復連線、同步完成後才能登出，避免資料遺失。',
      );
      return;
    }
    if (pendingSyncCount > 0) {
      _showBlockedAlert(
        context,
        title: '還有資料沒同步',
        message: '有 $pendingSyncCount 筆紀錄還在上傳，完成後才能登出，避免資料遺失。',
      );
      return;
    }

    final TodayTask? taskInProgress;
    try {
      final today = await ApiClient.instance.getMyToday();
      taskInProgress = today.todayTasks
          .where((task) => task.state == 'doing')
          .firstOrNull;
    } catch (_) {
      // 問不到權威狀態就不放行登出：寧可多攔一次，也不要讓還掛著單的人把
      // 憑證丟掉。這一段原型沒有，因為原型不連後端。
      if (context.mounted) {
        _showBlockedAlert(
          context,
          title: '現在無法登出',
          message: '連不上伺服器，確認不了名下的單是不是都結束了。等連線恢復再登出，避免資料遺失。',
        );
      }
      return;
    }
    if (!context.mounted) {
      return;
    }
    if (taskInProgress != null) {
      _showInProgressAlert(context, taskInProgress);
      return;
    }
    _confirmLogout(context);
  }

  /// 攔住登出並說明原因。只有一顆按鈕，因為員工在這個畫面上能做的只有把它
  /// 關掉。
  void _showBlockedAlert(
    BuildContext context, {
    required String title,
    required String message,
  }) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsAlert(
        title: title,
        message: message,
        messageId: 'me.logout-blocked-alert',
        actions: [
          TmsAlertAction(
            label: '好',
            id: 'me.logout-blocked-alert.confirm-button',
            style: TmsAlertActionStyle.primary,
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
        ],
      ),
    );
  }

  /// 名下還有一間在做時攔住登出，並給一條直接過去的路。
  ///
  /// 「前往」那一顆會把那張單的任務頁推上來。少了它，員工得自己關掉對話框、
  /// 切回首頁、再從清單裡找出是哪一間，而他剛剛被告知的就是那一間。
  ///
  /// 關掉對話框那一顆沿用 `me.logout-blocked-alert.confirm-button`：兩個攔阻
  /// 對話框的按鈕數不同，但驗收要做的事都是「把它關掉，確認人還在登入狀
  /// 態」，同一個 id 才找得到同一件事。
  void _showInProgressAlert(BuildContext context, TodayTask task) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsAlert(
        title: '${task.roomNo} 還在進行中',
        message: '登出不會停止計時。請先完成或暫時中斷這張單，再登出。',
        messageId: 'me.logout-blocked-alert',
        actions: [
          TmsAlertAction(
            label: '取消',
            id: 'me.logout-blocked-alert.confirm-button',
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
          TmsAlertAction(
            label: '前往 ${task.roomNo}',
            id: 'me.logout-blocked-alert.open-task-button',
            style: TmsAlertActionStyle.primary,
            onPressed: () {
              Navigator.of(dialogContext).pop();
              Navigator.of(context).push(
                CupertinoPageRoute<void>(
                  builder: (_) =>
                      TaskScreen(roomNo: task.roomNo, taskId: task.taskId),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  /// 最後問一次要不要登出。
  ///
  /// 說法分兩種，差別在這支手機是不是固定給同一個人用：共用手機上登出是為
  /// 了換人，個人手機上登出的代價是收不到派單推播。手機屬於哪一種，看登入
  /// 畫面記不記得住一個帳號（[TestHooks.rememberedAccount]）。
  void _confirmLogout(BuildContext context) {
    final bool sharedDevice =
        TestHooks.instance.rememberedAccount.value == null;
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsAlert(
        title: '要登出嗎？',
        message: sharedDevice
            ? '登出後，下一位同事就能用自己的帳號登入。指派給你的單會保留在你名下。'
            : '登出後，這支手機不會再收到派單推播。指派給你的單會保留在你名下。',
        messageId: 'me.logout-confirm-alert',
        actions: [
          TmsAlertAction(
            label: '取消',
            id: 'me.logout-confirm-alert.cancel-button',
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
          TmsAlertAction(
            label: '登出',
            id: 'me.logout-confirm-alert.confirm-button',
            style: TmsAlertActionStyle.destructive,
            onPressed: () async {
              Navigator.of(dialogContext).pop();
              // 趁憑證還在先把這支手機的推播 token 從帳號名下撤掉，下一位
              // 用這支手機的同事才不會收到前一位的派單。撤不掉也照樣登出。
              await PushTokenService.instance?.stop();
              if (!context.mounted) {
                return;
              }
              // 丟掉本機憑證，回登入畫面（replaceAllRoutes 清掉返回堆疊，
              // 登出後滑不回「我的」）。
              ApiClient.instance = ApiClient();
              replaceAllRoutes(context, const LoginScreen());
            },
          ),
        ],
      ),
    );
  }

  /// 告訴員工這個設定還不能用。還沒有子頁的幾個入口共用同一段說法，員工才
  /// 不會以為每一個各自壞在不同的地方。
  void _showNotAvailable(BuildContext context, String feature) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsAlert(
        title: feature,
        message: '這個設定尚未開放。',
        actions: [
          TmsAlertAction(
            label: '知道了',
            style: TmsAlertActionStyle.primary,
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'me.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        // 身分卡本身就是這一頁的標題區，上面不再放一行導覽列大標題——員工一
        // 眼要看到的是「我是誰」，不是「這頁叫我的」。
        // 底部不讓 SafeArea 收邊：內容要從浮動分頁列底下捲過去，讓開分頁列
        // 的那段留白由捲動內容自己帶。
        child: SafeArea(
          bottom: false,
          child: ListView(
            padding: EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md,
              TmsSpacing.screenEdge,
              TmsSpacing.scrollPadBottom(MediaQuery.paddingOf(context).bottom),
            ),
            children: [
              // 連線狀態列固定在內容最上方：登出與各項設定都要連得上後端，
              // 員工要先知道現在的連線狀況。
              const SystemStatusBar(),
              _IdentityCard(onTap: () => _showNotAvailable(context, '個人資料')),
              const SizedBox(height: TmsSpacing.xl),
              _WorkTiles(
                onOpenHandover: () => _showNotAvailable(context, '交班單'),
                onOpenLostAndFound: () => _showNotAvailable(context, '遺失物'),
              ),
              const SizedBox(height: TmsSpacing.xl),
              TmsGroupedSection(
                header: '通知與顯示',
                children: [
                  const _LiveActivityRow(),
                  // 這一列右側留白。要寫出原型那句「全部開啟」或「部分關
                  // 閉」，得先知道 iOS 的通知權限和各類通知的開關，兩者現在
                  // 都拿不到；猜一句寫上去，員工就會照著一句可能跟實際相反
                  // 的摘要決定要不要去改設定。
                  TmsGroupedRow(
                    label: '推播通知',
                    onTap: () => _showNotAvailable(context, '推播通知'),
                  ),
                  // App 沒有自己的字級設定，字級一律跟著 iOS 的 Dynamic Type
                  // 走，所以這個值是現況，不是佔位字。
                  TmsGroupedRow(
                    label: '文字大小',
                    value: '跟隨 iPhone',
                    onTap: () => _showNotAvailable(context, '文字大小'),
                  ),
                ],
              ),
              const SizedBox(height: TmsSpacing.xl),
              TmsGroupedSection(
                header: '帳號',
                children: [
                  TmsGroupedRow(
                    label: '變更密碼',
                    onTap: () => _showNotAvailable(context, '變更密碼'),
                  ),
                  // 介面目前只做得出繁體中文一種，所以這個值寫死；它不是佔
                  // 位字，是現在真正的語言。
                  TmsGroupedRow(
                    label: '語言',
                    value: '繁體中文',
                    onTap: () => _showNotAvailable(context, '語言'),
                  ),
                ],
              ),
              const SizedBox(height: TmsSpacing.xl),
              TmsSemantics(
                id: 'me.logout-button',
                child: TmsCard(
                  padding: EdgeInsets.zero,
                  child: CupertinoButton(
                    padding: EdgeInsets.zero,
                    minimumSize: const Size(
                      double.infinity,
                      TmsSize.rowMinHeight,
                    ),
                    onPressed: () => _attemptLogout(context),
                    child: Text(
                      '登出',
                      style: TmsText.body.copyWith(color: TmsColors.systemRed),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: TmsSpacing.xl),
              const _VersionLabel(),
            ],
          ),
        ),
      ),
    );
  }
}

/// 讓員工確認現在是用誰的帳號在用這支 App：大頭像、姓名，以及兩行副標。
///
/// 姓名與部門來自登入時後端回的身分（[ApiClient.session]），班別與負責樓層
/// 來自整個外殼共用的那一份 `GET /me/today`，都不另外打一支 API 問「我是
/// 誰」。沒有登入身分時整張卡不出現；班別或樓層缺值時那一段跟著不出現，不
/// 留一行空的副標。
///
/// 整張卡是一個點擊目標，進個人資料。
class _IdentityCard extends StatelessWidget {
  const _IdentityCard({required this.onTap});

  final VoidCallback onTap;

  /// 頭像裡那個字的字級。60pt 的圓裡放一個中文字，26 是看起來填得剛好的大小。
  static const double _initialFontSize = 26;

  @override
  Widget build(BuildContext context) {
    final session = ApiClient.instance.session;
    if (session == null) {
      return const SizedBox.shrink();
    }
    final today = MyTodayScope.todayOf(context);
    final subtitles = <String>[
      ?_dutyLine(session, today?.floors),
      ?_shiftText(today?.shift),
    ];
    return TmsSemantics(
      id: 'me.identity-card',
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: TmsCard(
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.screenEdge,
            vertical: TmsSpacing.md2,
          ),
          child: Row(
            children: [
              Container(
                width: TmsSize.avatarLarge,
                height: TmsSize.avatarLarge,
                alignment: Alignment.center,
                decoration: const BoxDecoration(
                  color: TmsColors.brandAccentContainer,
                  shape: BoxShape.circle,
                ),
                child: Text(
                  _initial(session.name),
                  style: const TextStyle(
                    fontSize: _initialFontSize,
                    fontWeight: FontWeight.w700,
                    color: TmsColors.brandOnAccentContainer,
                  ),
                ),
              ),
              const SizedBox(width: TmsSpacing.md2),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      session.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TmsText.title3.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    for (final line in subtitles) ...[
                      const SizedBox(height: TmsSpacing.xxs),
                      Text(
                        line,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.subheadline.copyWith(
                          color: TmsColors.secondaryLabel,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: TmsSpacing.sm),
              const TmsChevron(),
            ],
          ),
        ),
      ),
    );
  }

  /// 取姓名的最後一個字放進頭像。中文名字的名比姓更認得出是誰——一個班裡三
  /// 個姓陳的人，頭像全寫「陳」就分不出來了。
  static String _initial(String name) {
    if (name.isEmpty) {
      return '';
    }
    return name.characters.last;
  }

  /// 第一行副標：部門，後面接今天負責的樓層（例如「房務 · 3、4 樓」）。
  ///
  /// 主管與老闆不綁部門，改寫職務。兩者都認不出來時整行不畫——這一行要回答
  /// 的是「我現在以什麼身分在用這支 App」，答不出來就不要畫一行半截的答案。
  static String? _dutyLine(LoginResult session, List<int>? floors) {
    const deptLabels = {
      'housekeeping': '房務',
      'engineering': '工務',
      'frontdesk': '櫃檯',
    };
    const roleLabels = {'manager': '主管', 'owner': '老闆'};
    final duty = deptLabels[session.dept] ?? roleLabels[session.role];
    if (duty == null) {
      return null;
    }
    if (floors == null || floors.isEmpty) {
      return duty;
    }
    return '$duty · ${floors.join('、')} 樓';
  }

  /// 第二行副標：今天的班別。
  ///
  /// 後端回的是租戶設定 `shift_times` 的鍵（`morning`／`afternoon`），不是中
  /// 文名稱——系統裡沒有任何地方存班別的中文說法。認不得的鍵原樣顯示，不猜
  /// 一個說法。
  static String? _shiftText(String? shift) {
    if (shift == null || shift.isEmpty) {
      return null;
    }
    switch (shift) {
      case 'morning':
        return '早班';
      case 'afternoon':
        return '晚班';
      default:
        return shift;
    }
  }
}

/// 「工作」雙欄磚：交班單與遺失物（`Main.dc.html:422-446`）。
///
/// 兩張磚是員工進交班單與遺失物的唯一入口，所以即使兩頁都還沒做、磚上的數
/// 字也還拿不到，位置也要先在這裡站住。
class _WorkTiles extends StatelessWidget {
  const _WorkTiles({
    required this.onOpenHandover,
    required this.onOpenLostAndFound,
  });

  final VoidCallback onOpenHandover;

  final VoidCallback onOpenLostAndFound;

  /// 兩張磚之間的距離。`Main.dc.html:424`。
  static const double _gap = TmsSpacing.md;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(
            TmsSpacing.screenEdge,
            0,
            TmsSpacing.screenEdge,
            TmsSpacing.xs2,
          ),
          child: Text('工作', style: TmsText.footnote),
        ),
        IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: _WorkTile(
                  id: 'me.work.handover-tile',
                  label: '交班單',
                  glyph: _WorkGlyph.clipboard,
                  onTap: onOpenHandover,
                ),
              ),
              const SizedBox(width: _gap),
              Expanded(
                child: _WorkTile(
                  id: 'me.work.lost-and-found-tile',
                  label: '遺失物',
                  glyph: _WorkGlyph.box,
                  onTap: onOpenLostAndFound,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// 一張工作磚：左上一個圖示，底下一行名稱。
///
/// 圖示那一列固定 32 高，右半邊是原型放數字的位置（例如交班單還有幾份沒簽
/// 收）。那兩個數字後端還給不出來，所以現在空著；版面先照原型留好，數字接
/// 上的時候磚不會整個長高一截。
class _WorkTile extends StatelessWidget {
  const _WorkTile({
    required this.id,
    required this.label,
    required this.glyph,
    required this.onTap,
  });

  final String id;

  final String label;

  final _WorkGlyph glyph;

  final VoidCallback onTap;

  /// 磚的最小高度與內距。`Main.dc.html:426`。
  static const double _minHeight = 84;
  static const EdgeInsets _padding = EdgeInsets.fromLTRB(12, 10, 12, 12);

  /// 圖示那一列的高度。`Main.dc.html:427`。
  static const double _glyphRowHeight = 32;

  /// 磚上名稱的字級。`Main.dc.html:441`。
  static const double _labelFontSize = 16;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: TmsCard(
          padding: _padding,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: _minHeight),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                SizedBox(
                  height: _glyphRowHeight,
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: CustomPaint(
                      size: const Size(
                        _WorkGlyphPainter.glyphSize,
                        _WorkGlyphPainter.glyphSize,
                      ),
                      painter: _WorkGlyphPainter(glyph),
                    ),
                  ),
                ),
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TmsText.callout.copyWith(
                    fontSize: _labelFontSize,
                    fontWeight: FontWeight.w600,
                    color: TmsColors.secondaryLabel,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

enum _WorkGlyph { clipboard, box }

/// 畫工作磚的圖示。
///
/// 自己畫而不用 `CupertinoIcons`。`CupertinoIcons` 沒有夾板（交班單）這個字
/// 形，只有一張純文件；而兩張磚並排，一邊用字型字形、一邊用描邊路徑的話，
/// 筆畫粗細由字型決定，兩個圖示會一粗一細。
class _WorkGlyphPainter extends CustomPainter {
  const _WorkGlyphPainter(this.glyph);

  final _WorkGlyph glyph;

  /// 畫出來的邊長。`Main.dc.html:428`。
  static const double glyphSize = 28;

  /// 原型的路徑畫在 24 × 24 的 viewBox 上，筆畫 1.8。
  static const double _viewBox = 24;
  static const double _strokeWidth = 1.8;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / _viewBox, size.height / _viewBox);
    canvas.drawPath(
      glyph == _WorkGlyph.clipboard ? _clipboardPath() : _boxPath(),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = _strokeWidth
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = TmsColors.brandPrimary,
    );
    canvas.restore();
  }

  /// 夾板。`Main.dc.html:429`。
  static Path _clipboardPath() {
    const corner = Radius.circular(1);
    return Path()
      ..addRect(const Rect.fromLTWH(9, 4.5, 6, 2.5))
      ..moveTo(9, 5.5)
      ..lineTo(6.5, 5.5)
      ..arcToPoint(const Offset(5.5, 6.5), radius: corner, clockwise: false)
      ..lineTo(5.5, 20)
      ..arcToPoint(const Offset(6.5, 21), radius: corner, clockwise: false)
      ..lineTo(17.5, 21)
      ..arcToPoint(const Offset(18.5, 20), radius: corner, clockwise: false)
      ..lineTo(18.5, 6.5)
      ..arcToPoint(const Offset(17.5, 5.5), radius: corner, clockwise: false)
      ..lineTo(15, 5.5)
      ..moveTo(9, 11)
      ..lineTo(15, 11)
      ..moveTo(9, 14.5)
      ..lineTo(15, 14.5)
      ..moveTo(9, 18)
      ..lineTo(13, 18);
  }

  /// 紙箱。`Main.dc.html:431`。
  static Path _boxPath() {
    return Path()
      ..moveTo(3.5, 7.5)
      ..lineTo(12, 3.2)
      ..lineTo(20.5, 7.5)
      ..lineTo(20.5, 16.5)
      ..lineTo(12, 20.8)
      ..lineTo(3.5, 16.5)
      ..close()
      ..moveTo(3.5, 7.5)
      ..lineTo(12, 11.8)
      ..lineTo(20.5, 7.5)
      ..moveTo(12, 11.8)
      ..lineTo(12, 20.8)
      ..moveTo(7.8, 5.3)
      ..lineTo(16.2, 9.6);
  }

  @override
  bool shouldRepaint(_WorkGlyphPainter oldDelegate) =>
      oldDelegate.glyph != glyph;
}

/// 「即時動態」開關（`Main.dc.html:4665`）。
///
/// 開著時，進行中的任務會出現在動態島與鎖定畫面。這件事要 iOS 的 ActivityKit
/// 才做得到，App 還沒接，所以現在這個開關只記得自己被撥到哪一邊。位置先站
/// 住，是因為 Apple 的規範要求這類顯示一定要能在 App 內關掉，真的接上時關閉
/// 的入口就該在這裡。
class _LiveActivityRow extends StatefulWidget {
  const _LiveActivityRow();

  @override
  State<_LiveActivityRow> createState() => _LiveActivityRowState();
}

class _LiveActivityRowState extends State<_LiveActivityRow> {
  /// 原型的預設值是開（`Main.dc.html:4665` 的 `s.liveOn !== false`）。
  bool _on = true;

  @override
  Widget build(BuildContext context) {
    return TmsGroupedToggleRow(
      label: '即時動態',
      value: _on,
      toggleId: 'me.live-activity-toggle',
      onChanged: (value) => setState(() => _on = value),
    );
  }
}

/// 讓員工回報問題時講得出自己手上這支是哪一版。
///
/// 版本與 build 號跟著 `pubspec.yaml` 的 `version` 走（目前 `1.0.0+1`）。兩
/// 個值現在是寫死的：讀執行檔自己的 build 號要另外裝一個套件，而這支 App 還
/// 沒有別的地方需要它。改版號時這一行要跟著改。
class _VersionLabel extends StatelessWidget {
  const _VersionLabel();

  @override
  Widget build(BuildContext context) {
    return Text(
      'TMS 1.0.0（1）',
      textAlign: TextAlign.center,
      style: TmsText.footnote.copyWith(color: TmsColors.quaternaryLabel),
    );
  }
}
