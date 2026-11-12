import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_segmented_control.dart';
import '../engineering/eng_pool_screen.dart';
import '../pool/pool_screen.dart';
import '../shell/main_shell.dart';
import 'manager_ids.dart';
import 'pool_dept_preference.dart';

/// 待認領（主管端，ui-plan 畫面 7）：看房務與工務兩個池，只看不搶。
///
/// 版面沿用員工端的待認領，只在大標下方多一個「房務 / 工務」兩段切換；切到
/// 房務時列表就是 [HousekeepingPoolList]、切到工務就是 [EngineeringPoolList]，
/// 單卡、分類磚、空狀態與員工端一致，差別只有單卡底下沒有「認領這單」
/// （`canClaim: false`）。主管沒有部門，兩個池都明帶 `dept` 查
/// `listDeptPool`。
///
/// 停在哪一池記在 [PoolDeptPreference]：第一次打開是房務，之後停在上次切到的
/// 部門，重開 App 也一樣。
class ManagerPoolScreen extends StatefulWidget {
  const ManagerPoolScreen({super.key});

  @override
  State<ManagerPoolScreen> createState() => _ManagerPoolScreenState();
}

class _ManagerPoolScreenState extends State<ManagerPoolScreen> {
  /// 目前停在哪一池；還沒讀到手機上的紀錄時是 null，這段時間先不畫列表，
  /// 免得先閃一下房務再跳成工務。
  String? _dept;

  String? _highlightRoomNo;

  @override
  void initState() {
    super.initState();
    _highlightRoomNo = MainShellTabRequest.poolHighlightRoomNo.value;
    MainShellTabRequest.poolHighlightRoomNo.addListener(_applyHighlightRequest);
    PoolDeptPreference.changes.addListener(_applyPreferenceChange);
    _load();
  }

  @override
  void dispose() {
    MainShellTabRequest.poolHighlightRoomNo.removeListener(
      _applyHighlightRequest,
    );
    PoolDeptPreference.changes.removeListener(_applyPreferenceChange);
    super.dispose();
  }

  Future<void> _load() async {
    final dept = await PoolDeptPreference.read();
    if (mounted) {
      setState(() => _dept = dept);
    }
  }

  /// 分頁建好後會留在樹上，之後的推播要在這裡接新的標亮房號。
  void _applyHighlightRequest() {
    setState(() {
      _highlightRoomNo = MainShellTabRequest.poolHighlightRoomNo.value;
    });
  }

  /// 推播先寫了部門再切分頁時，已經開著的這一頁要跟著換池。
  void _applyPreferenceChange() {
    final dept = PoolDeptPreference.changes.value;
    if (dept != null && dept != _dept) {
      setState(() => _dept = dept);
    }
  }

  Future<void> _switchTo(String dept) async {
    setState(() => _dept = dept);
    await PoolDeptPreference.write(dept);
  }

  @override
  Widget build(BuildContext context) {
    final dept = _dept;
    final Widget body;
    if (dept == null) {
      body = const Center(child: CupertinoActivityIndicator());
    } else if (dept == PoolDeptPreference.engineering) {
      body = EngineeringPoolList(
        key: const ValueKey<String>(PoolDeptPreference.engineering),
        dept: EngineeringPoolList.deptKey,
        canClaim: false,
        aboveCategories: _deptSwitch(dept),
      );
    } else {
      body = HousekeepingPoolList(
        key: const ValueKey<String>(PoolDeptPreference.housekeeping),
        dept: HousekeepingPoolList.deptKey,
        canClaim: false,
        highlightRoomNo: _highlightRoomNo,
        aboveCategories: _deptSwitch(dept),
      );
    }
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      child: body,
    );
  }

  /// 大標下方的「房務 / 工務」兩段切換。切換本身是 `pool.dept-switch`，兩段
  /// 各是 `pool.dept-switch.{housekeeping|engineering}`，選中的那一段帶
  /// selected 語意。
  Widget _deptSwitch(String dept) {
    return TmsSemantics(
      id: ManagerIds.poolDeptSwitch,
      explicitChildNodes: true,
      child: TmsSegmentedControl<String>(
        value: dept,
        onChanged: _switchTo,
        segments: [
          TmsSegment(
            value: PoolDeptPreference.housekeeping,
            label: '房務',
            id: ManagerIds.poolDeptSwitchSide(PoolDeptPreference.housekeeping),
          ),
          TmsSegment(
            value: PoolDeptPreference.engineering,
            label: '工務',
            id: ManagerIds.poolDeptSwitchSide(PoolDeptPreference.engineering),
          ),
        ],
      ),
    );
  }
}
