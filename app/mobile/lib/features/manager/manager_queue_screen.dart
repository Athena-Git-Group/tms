import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/landing.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../ticket/ticket_screen.dart';
import 'inspection_screen.dart';
import 'manager_ids.dart';
import 'manager_queue_row.dart';
import 'manager_queues_store.dart';

/// 等主管處理的單（ui-plan 畫面 2）：一個清單元件吃待查房與待驗收兩類。
///
/// 內容就是 [ManagerQueuesStore] 裡那一份 `GET /me/manager-queues` 回應的對應
/// 陣列，與首頁格子同源；重取後兩邊一起更新。某一類沒有單時是一句說明，不是空白。
///
/// 工務主管沒有待查房：首頁本來就不畫那一格，萬一還是以待查房開進來，這裡
/// 直接退回首頁，不畫任何一列。
class ManagerQueueScreen extends StatelessWidget {
  const ManagerQueueScreen({
    super.key,
    required this.kind,
    required this.queue,
    required this.store,
  });

  /// 打開清單的是哪種主管。
  final ManagerKind kind;

  /// inspection 或 verify。
  final String queue;

  /// 與主管首頁共用的那一份主管待辦。
  final ManagerQueuesStore store;

  static const Map<String, String> _titles = {
    'inspection': '待查房',
    'verify': '待驗收',
  };

  static const Map<String, String> _emptyTitles = {
    'inspection': '目前沒有等你查的房',
    'verify': '目前沒有等你驗收的工務單',
  };

  /// 這種主管能不能看這一類清單；工務主管不能看待查房。
  bool get _allowed => kind.queues.contains(queue);

  @override
  Widget build(BuildContext context) {
    if (!_allowed) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted) {
          Navigator.of(context).maybePop();
        }
      });
      return const CupertinoPageScaffold(child: SizedBox.shrink());
    }
    return TmsSemantics(
      id: ManagerIds.queue,
      explicitChildNodes: true,
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        navigationBar: CupertinoNavigationBar(
          middle: Text(_titles[queue] ?? ''),
        ),
        child: SafeArea(
          child: ValueListenableBuilder<ManagerQueues?>(
            valueListenable: store,
            builder: (context, queues, _) => ListView(
              children: [
                ..._rows(context, queues),
                if (_entries(queue, queues).isEmpty)
                  TmsContentUnavailable(
                    id: ManagerIds.queueEmpty,
                    copy: TmsEmptyCopy(
                      icon: CupertinoIcons.tray,
                      title: _emptyTitles[queue] ?? '',
                      description: '有新的單進來時會出現在這裡',
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 這一類清單的每一列與點下去要開的畫面，都由 item 型別一次決定：待查房
  /// 進查房畫面，待驗收進工務單詳情（主管版）。
  static List<({ManagerQueueRowData data, Widget Function() page})> _entries(
    String queue,
    ManagerQueues? queues,
  ) {
    if (queues == null) {
      return const [];
    }
    return switch (queue) {
      'inspection' => [
        for (final item in queues.inspection)
          (
            data: ManagerQueueRowData.inspection(item),
            page: () => InspectionScreen(item: item),
          ),
      ],
      _ => [
        for (final item in queues.verify)
          (
            data: ManagerQueueRowData.verify(item),
            page: () =>
                TicketScreen(view: TicketView.manager, taskId: item.taskId),
          ),
      ],
    };
  }

  /// 點一列進它的畫面。查房送出、驗收送出或遇到單已被處理，都會讓那張單的
  /// 去向變了；不管怎麼回到清單，回來時一律重取一次，清單與首頁格子一起更新。
  Future<void> _open(BuildContext context, Widget page) async {
    await Navigator.of(context)
        .push(CupertinoPageRoute<void>(builder: (_) => page));
    await store.refresh();
  }

  List<Widget> _rows(BuildContext context, ManagerQueues? queues) {
    final rows = _entries(queue, queues);
    if (rows.isEmpty) {
      return const [];
    }
    return [
      Container(
        margin: const EdgeInsets.fromLTRB(
          TmsSpacing.screenEdge,
          TmsSpacing.sm,
          TmsSpacing.screenEdge,
          0,
        ),
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          color: TmsColors.cardBackground,
          borderRadius: BorderRadius.circular(TmsRadius.card),
        ),
        child: Column(
          children: [
            for (var i = 0; i < rows.length; i++)
              ManagerQueueRow(
                data: rows[i].data,
                showSeparator: i < rows.length - 1,
                onTap: () => _open(context, rows[i].page()),
              ),
          ],
        ),
      ),
    ];
  }
}
