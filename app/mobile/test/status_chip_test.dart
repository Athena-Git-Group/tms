import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/widgets/tms_status_chip.dart';

/// 狀態標籤的用詞是驗收的斷言目標：BDD 直接比對任務頁 `task.status-chip`
/// 節點讀到的字串，所以這些字不能隨版面調整一起被改掉。這組測試把每一種
/// 狀態的字面值釘在測試裡，改動用詞時會先在這裡紅掉，而不是等模擬器跑完
/// 整套 BDD 才發現。
void main() {
  test('每一種任務狀態對到固定的中文說法', () {
    expect(tmsStatusAppearance(state: 'pool').text, '待認領');
    expect(tmsStatusAppearance(state: 'accepted').text, '待開始');
    expect(tmsStatusAppearance(state: 'doing').text, '進行中');
    expect(tmsStatusAppearance(state: 'deferred').text, '已延後');
    expect(tmsStatusAppearance(state: 'interrupted').text, '已中斷');
    expect(tmsStatusAppearance(state: 'cancelled').text, '已取消');
  });

  test('做完的單依任務類型分成待查房與已送達', () {
    expect(
      tmsStatusAppearance(state: 'done', type: 'cleaning').text,
      '待查房',
    );
    expect(tmsStatusAppearance(state: 'done', type: 'amenity').text, '已送達');
  });

  test('查房結束的單依是否免查房分成兩種說法', () {
    expect(
      tmsStatusAppearance(state: 'inspected', inspection: 'waived').text,
      '完成 · 免查房',
    );
    expect(
      tmsStatusAppearance(state: 'inspected', inspection: 'passed').text,
      '查房通過',
    );
  });

  test('認不得的狀態值原樣顯示', () {
    expect(tmsStatusAppearance(state: 'brand-new-state').text,
        'brand-new-state');
  });
}
