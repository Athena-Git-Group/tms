import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_selection_chip.dart';
import 'repair_labels.dart';
import 'ticket_screen.dart';

/// 工務自行開工單的表單（工務待認領的「開工單」入口）。
///
/// 走契約 createRepairTicket 的「不經過任何房務任務」路徑：不帶
/// sourceTaskId，改帶公共區域或客房其中之一。急件由系統判定，表單沒有
/// 急件欄位。送出成功後導到新建那張單的詳情。
///
/// accessibility id 沿用房務報修面板的 `repair.` 前綴（兩個入口是同一張
/// 表單的兩種進法），差別只在這裡不預先帶入任何原任務。
class RepairTicketFormScreen extends StatefulWidget {
  const RepairTicketFormScreen({super.key});

  @override
  State<RepairTicketFormScreen> createState() => _RepairTicketFormScreenState();
}

class _RepairTicketFormScreenState extends State<RepairTicketFormScreen> {
  final TextEditingController _roomController = TextEditingController();

  bool _isPublicArea = false;
  String? _category;
  final List<String> _photoIds = [];
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _roomController.dispose();
    super.dispose();
  }

  Future<void> _addPhoto() async {
    try {
      final photoId = await captureAndUploadRepairPhoto(purpose: 'report');
      if (mounted) {
        setState(() {
          _photoIds.add(photoId);
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '照片上傳失敗，請再試一次。';
        });
      }
    }
  }

  Future<void> _submit() async {
    final category = _category;
    if (category == null || _photoIds.isEmpty || _submitting) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final ticket = await ApiClient.instance.createRepairTicket(
        isPublicArea: _isPublicArea,
        category: category,
        photoIds: _photoIds,
      );
      if (mounted) {
        Navigator.of(context).pushReplacement(
          CupertinoPageRoute<void>(
            builder: (_) => TicketScreen(taskId: ticket.taskId),
          ),
        );
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = e.errorMessage ?? '開工單失敗，請稍後再試。';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = '開工單失敗，請稍後再試。';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final canSubmit =
        _category != null && _photoIds.isNotEmpty && !_submitting;
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(middle: Text('開工單')),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TmsSemantics(
              id: 'repair.public-area-toggle',
              child: TmsSelectionChip(
                label: '公共區域',
                selected: _isPublicArea,
                onTap: () {
                  setState(() {
                    _isPublicArea = true;
                    _roomController.clear();
                  });
                },
              ),
            ),
            const SizedBox(height: 12),
            TmsSemantics(
              id: 'repair.room-field',
              child: CupertinoTextField(
                controller: _roomController,
                placeholder: '房號',
                padding: const EdgeInsets.all(12),
                onTap: () {
                  setState(() {
                    _isPublicArea = false;
                  });
                },
              ),
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              children: [
                for (final category in repairCategories)
                  TmsSemantics(
                    id: 'repair.category.${category.key}.chip',
                    child: TmsSelectionChip(
                      label: category.label,
                      selected: _category == category.key,
                      onTap: () {
                        setState(() {
                          _category = category.key;
                        });
                      },
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                TmsSemantics(
                  id: 'repair.photo.add-button',
                  child: CupertinoButton(
                    onPressed: _addPhoto,
                    child: const Text('拍照片'),
                  ),
                ),
                TmsSemantics(
                  id: 'repair.photo.count-label',
                  label: '已拍 ${_photoIds.length} 張 · 至少 1 張、最多 3 張',
                  child: Text('已拍 ${_photoIds.length} 張'),
                ),
              ],
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  _error!,
                  style: const TextStyle(color: CupertinoColors.destructiveRed),
                ),
              ),
            const SizedBox(height: 16),
            TmsSemantics(
              id: 'repair.submit-button',
              enabled: canSubmit,
              child: CupertinoButton.filled(
                onPressed: canSubmit ? _submit : null,
                child: _submitting
                    ? const CupertinoActivityIndicator()
                    : const Text('送出'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
