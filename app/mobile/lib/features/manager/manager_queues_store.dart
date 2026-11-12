import 'package:flutter/foundation.dart';

import '../../core/api_client.dart';

class ManagerQueuesStore extends ValueNotifier<ManagerQueues?> {
  ManagerQueuesStore() : super(null);

  Future<void> refresh() async {
    final ManagerQueues queues;
    try {
      queues = await ApiClient.instance.getManagerQueues();
    } catch (_) {
      return;
    }
    value = queues;
  }

  int? countOf(String queue) {
    final counts = value?.counts;
    if (counts == null) {
      return null;
    }
    return queue == 'inspection' ? counts.inspection : counts.verify;
  }
}
