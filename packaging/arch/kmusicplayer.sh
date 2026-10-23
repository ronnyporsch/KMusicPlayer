#!/bin/sh
# Starting via the main class (not -jar) keeps the WM_CLASS that kmusicplayer.desktop expects
exec /usr/bin/java --enable-native-access=ALL-UNNAMED \
    -cp /usr/share/java/kmusicplayer/kmusicplayer.jar de.ronnyporsch.kmusicplayer.MainKt "$@"
