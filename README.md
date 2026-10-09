# Singapore PSI widget

An Android home-screen widget (4×2) showing the 24-hour PSI, PM2.5 and PM10 for each region of Singapore, from [data.gov.sg](https://data.gov.sg/datasets/d_fe37906a0182569d891506e815e819b7/view).

This is a widget only. The APK has no app screens.

## What it does
- **Metric switch:** tap PSI, PM2.5 or PM10. Each widget remembers its own choice, and switching doesn't use the network.
- **Nearest region:** the big number is your nearest region, found from your approximate location. If location is off, it shows the highest region instead.
- **Colours:** NEA PSI bands. PM2.5 and PM10 are coloured by their sub-index, so all three metrics use the same scale.
- **Refresh:** every 15 minutes in the background (the rate the API publishes at), or tap ↻.
- **Offline:** if a fetch fails, the widget keeps the last reading, dims it and shows "Offline". Readings older than 90 minutes are dimmed too.

## Location
Location is requested once, when the widget is added. To avoid needing "Allow all the time", the background refresh never reads location. Your region is updated when you add the widget or tap ↻.

## Build
Requires JDK 17 and the Android SDK (compileSdk 36).

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then long-press the home screen → Widgets → PSI Widget.

## Layout
| Path | Purpose |
|---|---|
| `data/` | API client and parser, models, NEA bands, shared cache |
| `location/` | Nearest-region lookup, and the invisible activity that asks for location |
| `widget/` | Glance UI, metric switch, pushing cached data into each widget |
| `work/` | WorkManager refresh |
