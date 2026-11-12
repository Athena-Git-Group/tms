import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_card.dart';

class TmsCountTile extends StatelessWidget {
  const TmsCountTile({
    super.key,
    required this.id,
    required this.countId,
    required this.title,
    this.hint,
    this.wide = false,
    required this.count,
    required this.onTap,
  });

  final String id;
  final String countId;
  final String title;

  final String? hint;

  final bool wide;

  final int? count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final countText = count?.toString() ?? '—';
    return TmsSemantics(
      id: id,
      explicitChildNodes: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: TmsCard(
          padding: const EdgeInsets.all(TmsSpacing.md),
          child: wide
              ? Row(
                  children: [
                    Expanded(child: _label()),
                    _count(countText),
                  ],
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _title(),
                    const SizedBox(height: TmsSpacing.xs),
                    _count(countText),
                    if (hint != null) ...[
                      const SizedBox(height: TmsSpacing.xs),
                      _hint(hint!),
                    ],
                  ],
                ),
        ),
      ),
    );
  }

  Widget _label() => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      _title(),
      if (hint != null) ...[
        const SizedBox(height: TmsSpacing.xs),
        _hint(hint!),
      ],
    ],
  );

  Widget _title() => Text(
    title,
    style: TmsText.footnoteEmphasized.copyWith(color: TmsColors.secondaryLabel),
  );

  Widget _count(String countText) => TmsSemantics(
    id: countId,
    label: countText,
    child: ExcludeSemantics(child: Text(countText, style: TmsText.displayStat)),
  );

  Widget _hint(String text) => Text(
    text,
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
    style: TmsText.caption1Regular.copyWith(color: TmsColors.tertiaryLabel),
  );
}
