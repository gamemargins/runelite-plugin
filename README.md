# GameMargins

A RuneLite plugin that keeps your Grand Exchange buy-limit timers and your flips on
[gamemargins.com](https://gamemargins.com) up to date by itself.

Work in progress: this is the starting point, made from RuneLite's example plugin.

## Building

Needs a Java 11 JDK. On the development PC it is in `%USERPROFILE%\.jdks\temurin-11`:

```
JAVA_HOME="$USERPROFILE/.jdks/temurin-11" ./gradlew build
```

`./gradlew run` starts a development RuneLite with the plugin loaded. Logging in there with a
Jagex account: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
