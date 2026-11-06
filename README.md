# MonjaroTube

YouTube в Android WebView со встроенным ByeDPI для Geely Monjaro и других совместимых устройств на Android 11 и выше. Google Play Services не требуются.

**[Скачать APK — последний стабильный выпуск](https://github.com/hr-al/personal-monjarotube/releases/latest)** · [Установка и обновление](docs/UPDATING.md) · [Сообщить об ошибке](https://github.com/hr-al/personal-monjarotube/issues)

## Возможности

- Воспроизведение YouTube, официальный вход Google, профиль и подписки в текущем WebView.
- Встроенный обход запускается автоматически. Доступны профили, варианты завершения входа и собственные параметры ByeDPI.
- Нативное меню, полноэкранный просмотр, поиск через клавиатуру Android и веб-поле YouTube.
- Восстановление последней страницы, удержание экрана, диагностика соединения и видео.
- Ручная проверка новых стабильных версий на GitHub и ссылка на APK.

Обход действует для WebView MonjaroTube. Приложение не создаёт системный VPN и не меняет маршруты или DNS-настройки остальных приложений.

## Установка

Скачайте APK из Releases, перенесите на устройство и откройте его для установки. При обновлении выбирайте **«Обновить»**, сохраняя установленное приложение. Не удаляйте его и не очищайте данные: там хранятся вход и настройки.

Версия 1.0.0 сохраняет applicationId и сертификат подписи ранее распространённой версии 0.2.5. Подробности и отпечаток сертификата — в [инструкции обновления](docs/UPDATING.md). Для скачивания готового APK учётная запись GitHub не требуется.

## Совместимость

| Устройство | Результат |
|---|---|
| ГУ Geely Monjaro, Android 11 / API 30, модель 512013 KX11A5, WebView 83.0.4103.120 | Пользователь подтвердил вход, работу поиска и длительное воспроизведение, включая пятичасовую проверку базовой версии |
| Honor Pad 9, Android 14 | Проверены воспроизведение и вход на этапе разработки |
| Эмулятор Android 11 / WebView 83 | Проверки клавиатуры, обработки ошибок входа и обновления с сохранением тестовых данных |

Минимум: Android 11, системный WebView с поддержкой `PROXY_OVERRIDE` для встроенного обхода и доступ к Интернету. APK содержит arm64-v8a, armeabi-v7a и x86_64. Совместимость с другими ГУ зависит от прошивки, WebView и сети; её можно подтвердить через Issues.

Если после 2FA завершающий переход зависает, откройте «Настройки → Соединение», профиль «YouTube, видео и вход», вариант «Разбиение + TLS record». Меняйте вариант до начала новой попытки входа. Успешный профиль может отличаться в разных сетях.

## Обновления

«Настройки → Приложение → Проверить обновления». Проверка обращается к публичному GitHub API по HTTPS, отдельно от WebView. Скачать предложенный APK можно через системный браузер. Фоновый опрос, автоматическая установка и доступ к аккаунту GitHub не используются.

YouTube, Google и сетевые ограничения могут меняться. Стабильный выпуск означает проверенную текущую версию; дальнейшие изменения сайта, WebView и условий сети могут потребовать обновления приложения или профиля обхода.

## Разработка и данные

[Сборка и выпуск](docs/BUILDING.md) · [Данные и соединения](docs/PRIVACY.md) · [История изменений](CHANGELOG.md) · [Участие](CONTRIBUTING.md) · [Лицензии компонентов](THIRD_PARTY_NOTICES.md)

Собственный код — MIT. Встроенный ByeDPI — MIT; AndroidX — Apache-2.0. Уведомления правообладателей сохранены. Независимый проект, не связанный с Google, YouTube или Geely. Проверяйте настройки и смотрите видео только на стоянке.

## English

MonjaroTube is an independent Android WebView client for YouTube with an embedded ByeDPI proxy, intended for Geely Monjaro head units and other compatible Android 11+ devices. Google Play Services are not required. Download the signed APK from [Releases](https://github.com/hr-al/personal-monjarotube/releases/latest). Install updates over the existing app to preserve local settings and WebView sign-in data. Compatibility with additional devices and networks is not guaranteed; reports are welcome in Issues. The embedded proxy affects this app's WebView and does not create a device-wide VPN. Source code: MIT; third-party notices are included.
