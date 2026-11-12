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

class ManagerQueueScreen extends StatelessWidget {
  const ManagerQueueScreen({
    super.key,
    required this.kind,
    required this.queue,
    required this.store,
  });

  final ManagerKind kind;

  final String queue;

  final ManagerQueuesStore store;

  static const Map<String, String> _titles = {
    'inspection': '待查房',
    'verify': '待驗收',
  };

  static const Map<String, String> _emptyTitles = {
    'inspection': '目前沒有等你查的房',
    'verify': '目前沒有等你驗收的工務單',
  };

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
