import 'package:flutter/cupertino.dart';

import 'connection_status.dart';
import 'theme.dart';
import 'tms_semantics.dart';
import 'widgets/tms_alert.dart';

const String offlineOnlyCompleteMessage = '目前離線，只能完成進行中的單';

const String offlineCannotResumeMessage = '目前離線，無法恢復處理';

const String pendingSyncMessage = '已離線暫存，連線後自動同步';

final String connectionIssueMessage =
    '資料可能延遲，每 ${degradedPollInterval.inSeconds} 秒自動更新';

const String connectionIssueTitle = '即時連線異常';

const String offlineTitle = '目前離線';

const String offlineDescription = '只能完成進行中的單';

class SystemStatusBar extends StatelessWidget {
  const SystemStatusBar({super.key, this.padding = EdgeInsets.zero});

  final EdgeInsets padding;

  static const double contentGap = TmsSpacing.lg;

  @override
  Widget build(BuildContext context) {
    assert(
      padding.bottom == 0,
      '狀態列的下緣留白是 contentGap，呼叫端再給一次會疊成兩段',
    );
    final barPadding = padding + const EdgeInsets.only(bottom: contentGap);
    return ValueListenableBuilder<bool>(
      valueListenable: ConnectionStatus.instance.offline,
      builder: (context, offline, _) {
        if (offline) {
          return Padding(padding: barPadding, child: const _OfflineBar());
        }
        return ValueListenableBuilder<bool>(
          valueListenable: ConnectionStatus.instance.unstable,
          builder: (context, unstable, _) {
            if (!unstable) {
              return const SizedBox.shrink();
            }
            return Padding(
              padding: barPadding,
              child: _StatusStrip(
                id: 'system.connection-issue-bar',
                icon: CupertinoIcons.exclamationmark_triangle,
                iconColor: TmsColors.systemOrange,
                title: connectionIssueTitle,
                description: connectionIssueMessage,
                tint: TmsColors.cardBackground,
                titleColor: TmsColors.label,
                descriptionColor: TmsColors.secondaryLabel,
              ),
            );
          },
        );
      },
    );
  }
}

class _OfflineBar extends StatelessWidget {
  const _OfflineBar();

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: ConnectionStatus.instance.pendingSyncCount,
      builder: (context, pendingCount, _) {
        return _StatusBarFrame(
          tint: TmsColors.offlineBarBackground,
          icon: CupertinoIcons.wifi_slash,
          iconColor: TmsColors.offlineBarLabel,
          title: TmsSemantics(
            id: 'system.offline-bar',
            label: offlineTitle,
            child: ExcludeSemantics(
              child: Text(
                offlineTitle,
                style: TmsText.subheadlineEmphasized.copyWith(
                  color: TmsColors.offlineBarLabel,
                ),
              ),
            ),
          ),
          description: Text(
            offlineDescription,
            style: TmsText.footnote.copyWith(
              color: TmsColors.offlineBarSecondaryLabel,
            ),
          ),
          trailing: TmsSemantics(
            id: 'system.offline-bar.pending-count',
            label: '$pendingCount',
            child: ExcludeSemantics(
              child: Text(
                '$pendingCount 筆待同步',
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.offlineBarSecondaryLabel,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _StatusStrip extends StatelessWidget {
  const _StatusStrip({
    required this.id,
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.description,
    required this.tint,
    required this.titleColor,
    required this.descriptionColor,
  });

  final String id;
  final IconData icon;
  final Color iconColor;
  final String title;

  final String description;
  final Color tint;
  final Color titleColor;
  final Color descriptionColor;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: description,
      child: ExcludeSemantics(
        child: _StatusBarFrame(
          tint: tint,
          icon: icon,
          iconColor: iconColor,
          title: Text(
            title,
            style: TmsText.subheadlineEmphasized.copyWith(color: titleColor),
          ),
          description: Text(
            description,
            style: TmsText.footnote.copyWith(color: descriptionColor),
          ),
        ),
      ),
    );
  }
}

class _StatusBarFrame extends StatelessWidget {
  const _StatusBarFrame({
    required this.tint,
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.description,
    this.trailing,
  });

  final Color tint;
  final IconData icon;
  final Color iconColor;
  final Widget title;
  final Widget description;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.lg,
        vertical: TmsSpacing.sm2,
      ),
      decoration: BoxDecoration(
        color: tint,
        borderRadius: BorderRadius.circular(TmsRadius.card),
      ),
      child: Row(
        children: [
          Icon(icon, size: TmsSize.statusIcon, color: iconColor),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                title,
                const SizedBox(height: TmsSpacing.xxxs),
                description,
              ],
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: TmsSpacing.md),
            trailing!,
          ],
        ],
      ),
    );
  }
}

Future<void> showOfflineBlockedAlert(
  BuildContext context, {
  String message = offlineOnlyCompleteMessage,
}) {
  return showCupertinoDialog<void>(
    context: context,
    builder: (dialogContext) => TmsAlert(
      message: message,
      messageId: 'system.offline-blocked-alert',
      actions: [
        TmsAlertAction(
          label: '知道了',
          id: 'system.offline-blocked-alert.dismiss-button',
          style: TmsAlertActionStyle.primary,
          onPressed: () => Navigator.of(dialogContext).pop(),
        ),
      ],
    ),
  );
}
