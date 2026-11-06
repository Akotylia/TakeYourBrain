# Анимация логотипа на заставке Android (Android 12+)

1. Скопируй `avd_tyb_splash.xml` в `app/src/main/res/drawable/`.
2. Добавь зависимость `implementation("androidx.core:core-splashscreen:1.0.1")`.
3. В `res/values/themes.xml` создай тему заставки:

```xml
<style name="Theme.TakeYourBrain.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#1E2024</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/avd_tyb_splash</item>
    <item name="windowSplashScreenAnimationDuration">1000</item>
    <item name="postSplashScreenTheme">@style/Theme.TakeYourBrain</item>
</style>
```

4. В манифесте поставь эту тему активити (или приложению), а в `MainActivity.onCreate` до `super.onCreate` вызови `installSplashScreen()`.

Звук системная заставка не проигрывает. Если нужен звук при первом запуске, проиграй `tyb_logo_sound.mp3` (положи в `res/raw/`) через `MediaPlayer` в момент показа заставки, лучше только при самом первом запуске.
