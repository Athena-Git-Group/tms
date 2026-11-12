import 'package:flutter/cupertino.dart';

void replaceAllRoutes(BuildContext context, Widget screen) {
  Navigator.of(context).pushAndRemoveUntil(
    CupertinoPageRoute<void>(builder: (_) => screen),
    (route) => false,
  );
}
