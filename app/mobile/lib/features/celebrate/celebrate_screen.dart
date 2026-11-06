import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';

/// 完成慶祝畫面：員工把一間房做完之後看到的回饋，上面寫這次實際做了幾分鐘、
/// 當初預估幾分鐘。
///
/// 兩個數字都由任務頁在完成成功之後傳進來，取自後端回的已做分鐘與預估分鐘。
/// 任務沒有預估分鐘時只顯示實際時間。
class CelebrateScreen extends StatelessWidget {
  const CelebrateScreen({
    super.key,
    required this.actualMinutes,
    this.estimateMinutes,
  });

  /// 這一間實際做了幾分鐘。
  final int actualMinutes;

  /// 這一間預估幾分鐘；沒有估時的任務為 null。
  final int? estimateMinutes;

  @override
  Widget build(BuildContext context) {
    final actualText = '實際 $actualMinutes 分';
    final estimateText =
        estimateMinutes == null ? null : '預估 $estimateMinutes 分';
    return TmsSemantics(
      id: 'celebrate.screen',
      child: CupertinoPageScaffold(
        child: SafeArea(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const TmsSemantics(
                  id: 'celebrate.title',
                  child: Text('這間做完了！', style: TmsText.title1),
                ),
                const SizedBox(height: TmsSpacing.lg),
                TmsSemantics(
                  id: 'celebrate.actual-minutes',
                  label: actualText,
                  child: ExcludeSemantics(
                    child: Text(actualText, style: TmsText.headline),
                  ),
                ),
                if (estimateText != null) ...[
                  const SizedBox(height: TmsSpacing.xs),
                  TmsSemantics(
                    id: 'celebrate.estimate-minutes',
                    label: estimateText,
                    child: ExcludeSemantics(
                      child: Text(
                        estimateText,
                        style: TmsText.subheadline
                            .copyWith(color: TmsColors.secondaryLabel),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: TmsSpacing.xl2),
                TmsSemantics(
                  id: 'celebrate.back-home-button',
                  child: CupertinoButton.filled(
                    onPressed: () {
                      // 回到導航堆疊最底的首頁；慶祝頁之後的「下一間卡」
                      // 呈現是首頁 feature 的事，這裡只負責返回。
                      Navigator.of(context).popUntil((route) => route.isFirst);
                    },
                    child: const Text('回到首頁'),
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
