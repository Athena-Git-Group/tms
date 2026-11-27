import 'dart:collection';
import 'dart:typed_data';

import 'package:flutter/cupertino.dart';

import 'api_client.dart';
import 'theme.dart';

enum RepairPhotoLoad { loading, loaded, failed }

final Expando<_PhotoContentCache> _caches = Expando();

class _PhotoContentCache {
  static const int _capacity = 40;

  final LinkedHashMap<String, Future<Uint8List>> _entries = LinkedHashMap();

  Future<Uint8List> read(ApiClient api, String photoId) {
    final cached = _entries.remove(photoId);
    if (cached != null) {
      _entries[photoId] = cached;
      return cached;
    }
    final content = api.fetchRepairPhotoContent(photoId);
    _entries[photoId] = content;
    if (_entries.length > _capacity) {
      _entries.remove(_entries.keys.first);
    }
    content.catchError((Object _) {
      if (identical(_entries[photoId], content)) {
        _entries.remove(photoId);
      }
      return Uint8List(0);
    });
    return content;
  }
}

Future<Uint8List> _readPhotoContent(String photoId) {
  final api = ApiClient.instance;
  return (_caches[api] ??= _PhotoContentCache()).read(api, photoId);
}

class RepairPhotoImage extends StatefulWidget {
  const RepairPhotoImage({
    super.key,
    required this.photoId,
    this.fit = BoxFit.cover,
    this.onDark = false,
  });

  final String photoId;

  final BoxFit fit;

  final bool onDark;

  @override
  State<RepairPhotoImage> createState() => _RepairPhotoImageState();
}

class _RepairPhotoImageState extends State<RepairPhotoImage> {
  RepairPhotoLoad _load = RepairPhotoLoad.loading;
  MemoryImage? _image;
  ImageStream? _stream;
  late final ImageStreamListener _listener = ImageStreamListener(
    (_, _) => _settle(RepairPhotoLoad.loaded),
    onError: (_, _) => _settle(RepairPhotoLoad.failed),
  );

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  @override
  void didUpdateWidget(RepairPhotoImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.photoId != widget.photoId) {
      _stopListening();
      _image = null;
      _load = RepairPhotoLoad.loading;
      _fetch();
    }
  }

  @override
  void dispose() {
    _stopListening();
    super.dispose();
  }

  Future<void> _fetch() async {
    final photoId = widget.photoId;
    try {
      final bytes = await _readPhotoContent(photoId);
      if (!mounted || photoId != widget.photoId) {
        return;
      }
      final image = MemoryImage(bytes);
      setState(() => _image = image);
      _stream = image.resolve(createLocalImageConfiguration(context))
        ..addListener(_listener);
    } catch (_) {
      if (mounted && photoId == widget.photoId) {
        _settle(RepairPhotoLoad.failed);
      }
    }
  }

  void _settle(RepairPhotoLoad load) {
    if (mounted && _load != load) {
      setState(() => _load = load);
    }
  }

  void _stopListening() {
    _stream?.removeListener(_listener);
    _stream = null;
  }

  @override
  Widget build(BuildContext context) {
    final image = _image;
    return Semantics(
      value: _load.name,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (!widget.onDark)
            const ColoredBox(color: TmsColors.neutralContainer),
          if (image != null && _load == RepairPhotoLoad.loaded)
            Image(image: image, fit: widget.fit, gaplessPlayback: true),
          if (_load == RepairPhotoLoad.failed)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(TmsSpacing.xs),
                child: Text(
                  '照片無法顯示',
                  textAlign: TextAlign.center,
                  style: TmsText.caption1.copyWith(
                    color: widget.onDark
                        ? const Color(0x99EBEBF5)
                        : TmsColors.secondaryLabel,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
