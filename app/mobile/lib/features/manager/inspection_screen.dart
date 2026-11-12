import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/test_backdoor.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_pill_button.dart';
import 'inspection_item_row.dart';
import 'inspection_result_screen.dart';
import 'manager_conflict_alert.dart';
import 'manager_ids.dart';
import 'manager_queue_row.dart';

/// 查房畫面（ui-plan 畫面 3）：主管從待查房清單點一列進來，逐項判通過或不通過，
/// 全部判完才能送出。
///
/// 項目來自 `GET /sop-items` 裡 appliesTo=inspection 且啟用的那些，依飯店排序；
/// 一項都沒有時清單位置換成一句說明，送出鈕直接可按（後端視為全部通過）。沒有
/// 備註欄、沒有照片。
///
/// 送出後的三種去向：
/// - 成功：換成結果畫面，結案還是退回、退回去哪裡全照後端回的
///   [InspectionOutcome]，App 不看班表。
/// - 422 `inspection_items_mismatch`：飯店剛調整過查房項目。留在原畫面，項目換成
///   回應裡的現行集合、判定清空、頂部提示重新判定，不再另外打一次 `/sop-items`。
/// - 409 `state_conflict`：這張單已被別的主管處理。彈出「這張單已被處理」，按下
///   後退回清單，清單由開這一頁的那一端重取。
class InspectionScreen extends StatefulWidget {
  const InspectionScreen({super.key, required this.item});

  /// 待查房清單上被點的那一列。
  final InspectionQueueItem item;

  @override
  State<InspectionScreen> createState() => _InspectionScreenState();
}

/// 查房畫面上的一個項目：送出用的鍵與畫面上的名稱。
class _Item {
  const _Item(this.key, this.label);

  final String key;
  final String label;
}

class _InspectionScreenState extends State<InspectionScreen> {
  List<_Item>? _items;

  /// 項目鍵 → true 通過、false 不通過；還沒判的不在表裡。
  final Map<String, bool> _verdicts = {};

  bool _loadFailed = false;
  bool _submitting = false;

  /// 送出時查房項目剛被調整、畫面已換成現行項目時為 true，頂部顯示提示。
  bool _itemsChanged = false;

  String? _error;

  /// 這一輪判定的 Idempotency-Key。同一輪判定重送（例如沒收到回應再按一次）
  /// 帶同一把，後端才認得出是同一次查房；項目換過、判定清空之後是新的一輪，
  /// 換一把新的。
  String? _idempotencyKey;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final all = await ApiClient.instance.listSopItems();
      final items =
          all
              .where((item) => item.appliesTo == 'inspection' && item.active)
              .toList()
            ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
      if (!mounted) {
        return;
      }
      setState(() {
        _items = [for (final item in items) _Item(item.itemKey, item.label)];
        _loadFailed = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() => _loadFailed = true);
      }
    }
  }

  bool get _allJudged {
    final items = _items;
    return items != null &&
        items.every((item) => _verdicts.containsKey(item.key));
  }

  void _judge(String key, bool pass) {
    setState(() {
      _verdicts[key] = pass;
      _error = null;
    });
  }

  Future<void> _submit() async {
    final items = _items;
    if (items == null || !_allJudged || _submitting) {
      return;
    }
    final key = _idempotencyKey ??= ApiClient.newIdempotencyKey();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final outcome = await ApiClient.instance.createInspection(
        taskId: widget.item.taskId,
        items: [
          for (final item in items)
            InspectionItemJudgement(key: item.key, pass: _verdicts[item.key]!),
        ],
        idempotencyKey: key,
      );
      if (!mounted) {
        return;
      }
      Navigator.of(context).pushReplacement(
        CupertinoPageRoute<void>(
          builder: (_) => InspectionResultScreen(
            outcome: outcome,
            cleanerName: widget.item.cleanerName,
          ),
        ),
      );
    } on ApiException catch (e) {
      if (!mounted) {
        return;
      }
      final currentItems = e.inspectionCurrentItems;
      if (e.statusCode == 422 && currentItems != null) {
        final sorted = [...currentItems]
          ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
        setState(() {
          _submitting = false;
          _items = [for (final item in sorted) _Item(item.key, item.label)];
          _verdicts.clear();
          _itemsChanged = true;
          _idempotencyKey = null;
        });
        return;
      }
      if (e.statusCode == 409) {
        setState(() => _submitting = false);
        await showManagerConflictAlert(
          context,
          message: '可能是另一位主管剛查過這間房，或這張單被改派了。',
        );
        if (mounted) {
          Navigator.of(context).pop();
        }
        return;
      }
      setState(() {
        _submitting = false;
        _error = e.errorMessage ?? '送出失敗，請稍後再試。';
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = '送出失敗，請稍後再試。';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final where = widget.item.isPublicArea || widget.item.roomNo == null
        ? widget.item.title
        : widget.item.roomNo!;
    return TmsSemantics(
      id: ManagerIds.inspect,
      explicitChildNodes: true,
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        navigationBar: CupertinoNavigationBar(
          middle: Text('查房 · $where'),
          previousPageTitle: '待查房',
        ),
        child: SafeArea(
          bottom: false,
          child: Column(
            children: [
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.only(bottom: TmsSpacing.lg),
                  children: [
                    _summary(where),
                    if (_itemsChanged) _notice(),
                    ..._itemSection(),
                  ],
                ),
              ),
              if (_items != null) _actionBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _summary(String where) {
    final when = waitingSinceText(
      widget.item.waitingSince,
      isToday: widget.item.waitingSinceIsToday,
      now: deviceNow(),
    );
    final finishedAt = when.date == null
        ? when.time
        : '${when.date} ${when.time}';
    return Container(
      color: TmsColors.cardBackground,
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.md2,
        TmsSpacing.screenEdge,
        TmsSpacing.md2,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(where, style: TmsText.displayRoomNo),
          const SizedBox(height: TmsSpacing.sm),
          Text(widget.item.title, style: TmsText.headline),
          const SizedBox(height: TmsSpacing.xxs),
          Text(
            '${widget.item.cleanerName} 清的 · $finishedAt 完成',
            style: TmsText.footnote.copyWith(color: TmsColors.secondaryLabel),
          ),
        ],
      ),
    );
  }

  Widget _notice() {
    return Container(
      margin: const EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.md,
        TmsSpacing.screenEdge,
        0,
      ),
      padding: const EdgeInsets.all(TmsSpacing.md),
      decoration: BoxDecoration(
        color: TmsColors.brandAccentContainer,
        borderRadius: BorderRadius.circular(TmsRadius.md),
      ),
      child: Text(
        '查房項目剛更新，請重新判定',
        style: TmsText.subheadlineEmphasized.copyWith(
          color: TmsColors.brandAccent,
        ),
      ),
    );
  }

  List<Widget> _itemSection() {
    final items = _items;
    if (items == null) {
      return [
        Padding(
          padding: const EdgeInsets.all(TmsSpacing.xl),
          child: _loadFailed
              ? Text(
                  '查房項目載入失敗，請返回後再試一次。',
                  textAlign: TextAlign.center,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.secondaryLabel,
                  ),
                )
              : const CupertinoActivityIndicator(),
        ),
      ];
    }
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(
          TmsSpacing.screenEdge + TmsSpacing.xs,
          TmsSpacing.lg,
          TmsSpacing.screenEdge + TmsSpacing.xs,
          TmsSpacing.sm,
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              '查房項目',
              style: TmsText.footnoteEmphasized.copyWith(
                color: TmsColors.secondaryLabel,
              ),
            ),
            if (items.isNotEmpty)
              Text(
                '${items.length} 項',
                style: TmsText.footnote.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
          ],
        ),
      ),
      if (items.isEmpty)
        _emptyItems()
      else
        Container(
          margin: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: TmsColors.cardBackground,
            borderRadius: BorderRadius.circular(TmsRadius.card),
          ),
          child: Column(
            children: [
              for (var i = 0; i < items.length; i++)
                InspectionItemRow(
                  itemKey: items[i].key,
                  label: items[i].label,
                  verdict: _verdicts[items[i].key],
                  showSeparator: i < items.length - 1,
                  onJudge: _submitting
                      ? null
                      : (pass) => _judge(items[i].key, pass),
                ),
            ],
          ),
        ),
    ];
  }

  Widget _emptyItems() {
    return TmsSemantics(
      id: '${ManagerIds.inspect}.empty',
      label: '這家飯店目前沒有設定查房項目，送出即視為全部通過',
      child: ExcludeSemantics(
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.lg2,
            vertical: TmsSpacing.xl,
          ),
          decoration: BoxDecoration(
            color: TmsColors.cardBackground,
            borderRadius: BorderRadius.circular(TmsRadius.card),
          ),
          child: Column(
            children: [
              Text(
                '這家飯店目前沒有設定查房項目',
                textAlign: TextAlign.center,
                style: TmsText.headline,
              ),
              const SizedBox(height: TmsSpacing.xs2),
              Text(
                '送出即視為全部通過',
                textAlign: TextAlign.center,
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _actionBar() {
    final items = _items!;
    final judged = items
        .where((item) => _verdicts.containsKey(item.key))
        .length;
    final canSubmit = _allJudged && !_submitting;
    final progress = items.isEmpty
        ? '沒有項目要判，可以直接送出'
        : '已判 $judged / ${items.length} 項';
    final error = _error;
    return TmsActionBar(
      hint: Column(
        children: [
          if (error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: TmsSpacing.xs),
              child: Text(
                error,
                textAlign: TextAlign.center,
                style: TmsText.footnote.copyWith(
                  color: TmsColors.onErrorContainer,
                ),
              ),
            ),
          Text(
            progress,
            textAlign: TextAlign.center,
            style: TmsText.footnote.copyWith(color: TmsColors.secondaryLabel),
          ),
        ],
      ),
      primary: TmsSemantics(
        id: ManagerIds.inspectSubmitButton,
        // CupertinoButton 的 onPressed 為 null 時框架不會把 disabled 曝露到
        // accessibility tree，可否送出要顯式標出來。
        enabled: canSubmit,
        child: TmsPillButton(
          label: '送出查房結果',
          onPressed: canSubmit ? _submit : null,
          child: _submitting ? const CupertinoActivityIndicator() : null,
        ),
      ),
    );
  }
}
