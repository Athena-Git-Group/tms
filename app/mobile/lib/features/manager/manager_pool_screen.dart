import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_segmented_control.dart';
import '../engineering/eng_pool_screen.dart';
import '../pool/pool_screen.dart';
import '../shell/main_shell.dart';
import 'manager_ids.dart';
import 'pool_dept_preference.dart';

class ManagerPoolScreen extends StatefulWidget {
  const ManagerPoolScreen({super.key});

  @override
  State<ManagerPoolScreen> createState() => _ManagerPoolScreenState();
}

class _ManagerPoolScreenState extends State<ManagerPoolScreen> {
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

  void _applyHighlightRequest() {
    setState(() {
      _highlightRoomNo = MainShellTabRequest.poolHighlightRoomNo.value;
    });
  }

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
