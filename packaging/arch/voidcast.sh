#!/bin/sh
# Starting via the main class (not -jar) keeps the WM_CLASS that voidcast.desktop expects
exec /usr/bin/java --enable-native-access=ALL-UNNAMED \
    -cp /usr/share/java/voidcast/voidcast.jar de.ronnyporsch.voidcast.MainKt "$@"
