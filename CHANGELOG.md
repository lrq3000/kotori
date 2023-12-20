## Kotori 4.2

- New: show latitude and longitude values
- Fix: app crash if exit and location is disable after first launch
- Fix: some lints warnings
- Reduce average and maximum speed text size
- Toolchain updates: update android and kotlin gradle plugins
- Remove obsolete jcenter repo
- Move code into new package name
- Set compile and min sdk version to 33 to meet with play store requirements
- Add coarse location permission, this is required in newer android versions
- Remove old JVM args to compile with newer JDK versions
- Minimal code cleanups

## Kotori 4.1.0

- Keep screen on in main activity
- Does not show dialog when location is disabled
- Reset data when location provider is disabled
- Add option to show altitude above mean sea level in android 7+
- Show units in notification text
- Fix remove location updates and notification when app is finished
- Fix measure of stopping time (thanks to @w-rj)

## Kotori 4.0.0

- Port code base to kotlin
- Use android ktx location
- Update application logic
- Night theme is darker
- Change app theme from preferences
- Add spanish traslation
- Display current speed in notification instead of max speed
- Add Exit option
- Portrait support
- Change status and navigation bar colors
- Remove Gson dependency
- Update dependencies
- Compile sdk version is 31
- Fix: average speed

## Kotori 3.1.0

- Show location altitude
- Use GNSS API on devices running android 7 or above (Migrate to androidX in future release)
- Code clean up (refactor, deprecated APIs...)

## Kotori 3.0.2

Emergency release

- Fix: Application does not completly start after grant location permision
- Update gradle again

## Kotori 3.0.1

- Update gradle wrapper

## Kotori 3.0.0

First fork release

- Port to androidX
- Use Material Components
- Cosmetic changes (icons, colors, ui elements position)
- Night mode (android 10+)
