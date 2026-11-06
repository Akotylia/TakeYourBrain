package com.takeyourbrain.app.data

/** Как сортировать приложения в разделе списка. */
enum class AppSort {
    USAGE,        // по времени использования за сегодня (больше — выше)
    NAME,         // по названию (А–Я)
    INSTALL_DATE, // по дате установки (новые — выше)
}
