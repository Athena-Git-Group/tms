import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import 'inspection_screen.dart';
import 'manager_ids.dart';
import 'manager_queue_row.dart';
import 'manager_queues_store.dart';

/// 等主管處理的單（ui-plan 畫面 2）：一個清單元件吃三種類別。
///
/// 內容就是 [ManagerQueuesStore] 裡那一份 `GET /me/manager-queues` 回應的對應
/// 陣列，與首頁三格同源；重取後兩邊一起更新。某一類沒有單時是一句說明，不是空白。
class ManagerQueueScreen extends StatelessWidget {
  const ManagerQueueScreen({
    super.key,
    required this.queue,
    required this.store,
  });

  /// inspection、review 或 verify。
  final String queue;

  /// 與主管首頁共用的那一份主管待辦。
  final ManagerQueuesStore store;

  static const Map<String, String> _titles = {
    'inspection': '待查房',
    'review': '待審核',
    'verify': '待驗收',
  };

  static const Map<String, String> _emptyTitles = {
    'inspection': '目前沒有等你查的房',
    'review': '目前沒有等你審核的報修',
    'verify': '目前沒有等你驗收的工務單',
  };

  @override
  Widget build(BuildContext context) {
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
                if (_rowData(queue, queues).isEmpty)
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

  static List<ManagerQueueRowData> _rowData(
    String queue,
    ManagerQueues? queues,
  ) {
    if (queues == null) {
      return const [];
    }
    return switch (queue) {
      'inspection' => [
        for (final item in queues.inspection)
          ManagerQueueRowData.inspection(item),
      ],
      'review' => [
        for (final item in queues.review) ManagerQueueRowData.review(item),
      ],
      _ => [for (final item in queues.verify) ManagerQueueRowData.verify(item)],
    };
  }

  /// 點待查房的一列進查房畫面。查房畫面送出成功會換成結果畫面、遇到單已被處理
  /// 會自己退回來；不管怎麼回到清單，那張單的去向都已經變了，所以回來時一律
  /// 重取一次，清單與首頁三格一起更新。
  Future<void> _openInspection(
    BuildContext context,
    InspectionQueueItem item,
  ) async {
    await Navigator.of(context).push(
      CupertinoPageRoute<void>(builder: (_) => InspectionScreen(item: item)),
    );
    await store.refresh();
  }

  /// 列被點下去要做的事；目前只有待查房的列可以點進去。
  VoidCallback? _onTapRow(
    BuildContext context,
    ManagerQueues? queues,
    ManagerQueueRowData row,
  ) {
    if (queue != 'inspection' || queues == null) {
      return null;
    }
    for (final item in queues.inspection) {
      if (item.taskId == row.taskId) {
        return () => _openInspection(context, item);
      }
    }
    return null;
  }

  List<Widget> _rows(BuildContext context, ManagerQueues? queues) {
    final rows = _rowData(queue, queues);
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
                data: rows[i],
                showSeparator: i < rows.length - 1,
                onTap: _onTapRow(context, queues, rows[i]),
              ),
          ],
        ),
      ),
    ];
  }
}
