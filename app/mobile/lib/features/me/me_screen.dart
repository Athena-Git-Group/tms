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
import '../ticket/ticket_screen.dart';
import '../today/my_today_scope.dart';

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
                  builder: (_) => task.type == 'repair'
                      ? TicketScreen(
                          view: TicketView.engineering,
                          taskId: task.taskId,
                        )
                      : TaskScreen(roomNo: task.roomNo, taskId: task.taskId),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

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
              await PushTokenService.instance?.stop();
              if (!context.mounted) {
                return;
              }
              ApiClient.instance = ApiClient();
              replaceAllRoutes(context, const LoginScreen());
            },
          ),
        ],
      ),
    );
  }

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
                  TmsGroupedRow(
                    label: '推播通知',
                    onTap: () => _showNotAvailable(context, '推播通知'),
                  ),
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

class _IdentityCard extends StatelessWidget {
  const _IdentityCard({required this.onTap});

  final VoidCallback onTap;

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

  static String _initial(String name) {
    if (name.isEmpty) {
      return '';
    }
    return name.characters.last;
  }

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

class _WorkTiles extends StatelessWidget {
  const _WorkTiles({
    required this.onOpenHandover,
    required this.onOpenLostAndFound,
  });

  final VoidCallback onOpenHandover;

  final VoidCallback onOpenLostAndFound;

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

  static const double _minHeight = 84;
  static const EdgeInsets _padding = EdgeInsets.fromLTRB(12, 10, 12, 12);

  static const double _glyphRowHeight = 32;

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

class _WorkGlyphPainter extends CustomPainter {
  const _WorkGlyphPainter(this.glyph);

  final _WorkGlyph glyph;

  static const double glyphSize = 28;

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

class _LiveActivityRow extends StatefulWidget {
  const _LiveActivityRow();

  @override
  State<_LiveActivityRow> createState() => _LiveActivityRowState();
}

class _LiveActivityRowState extends State<_LiveActivityRow> {
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
