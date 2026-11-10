# Моя музыка (нативный Android)

Kotlin + Jetpack Compose + AndroidX Media3. Без WebView, HTML и JavaScript.

## Структура
- `app/src/main/java/kz/moyamuzyka/app/PlaybackService.kt` — единственный плеер (ExoPlayer) с DSP в аудио-синке
- `.../dsp/DspProcessor.kt` — цепочка: preamp → тон → бас → 32-полосный EQ → усиление → баланс → лимитер
- `.../dsp/DspHub.kt` — настройки звука, правило «Бас ↔ Усиление» не работают вместе
- `.../ui/` — экраны: Библиотека, Плеер, Эквалайзер, Настройки → Бас
- `.github/workflows/build.yml` — сборка Debug APK на каждый push в `main`

## Сборка
Push в `main` → вкладка **Actions** → артефакт **MoyaMuzyka-debug-apk** → `app-debug.apk`.
