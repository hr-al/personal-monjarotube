# Сторонние компоненты

| Компонент | Лицензия | Источник |
|---|---|---|
| ByeDPI, hufrea, исходная ревизия `078842b084853bc30f33eaaec7acc510cf67e560` | MIT | https://github.com/hufrea/byedpi |
| kavl.h, Attractive Chaos | MIT | Заголовок `app/src/main/cpp/byedpi/kavl.h` |
| AndroidX WebKit 1.12.1 и транзитивные AndroidX-компоненты | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |

Лицензия ByeDPI сохранена в `app/src/main/cpp/byedpi/LICENSE` и в APK: `assets/licenses/ByeDPI-MIT.txt`. Уведомление kavl сохранено в заголовке и в APK. Полный текст Apache-2.0 включён в `assets/licenses/Apache-2.0.txt`.

Изменения встроенного ByeDPI: диагностические счётчики и ошибки в `proxy.c`; вызов собственного IPv4 DNS-резолвера из JNI bridge. Приложение использует собственную Android-обёртку. Исходники и бинарные компоненты сторонних Android-приложений в репозиторий не включены.

Google, YouTube и Geely — обозначения их правообладателей. Проект независимый и не является официальным продуктом этих компаний. Лицензия MIT на исходный код не предоставляет прав на сторонние товарные знаки. Вектор иконки в `ic_launcher.xml` основан на геометрии значка YouTube; это отдельно отмечено от лицензии собственного кода.
