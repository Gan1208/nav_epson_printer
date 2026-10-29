package com.kowloondairy.navprint

import com.kowloondairy.navprint.landing.SqlConnectionSettings

/** The NAV database, from local.properties at build time. Shared by the screens and the 未打印提醒. */
val navSqlSettings = SqlConnectionSettings(
    host = BuildConfig.NAV_SQL_HOST,
    port = BuildConfig.NAV_SQL_PORT,
    instance = BuildConfig.NAV_SQL_INSTANCE,
    database = BuildConfig.NAV_SQL_DATABASE,
    user = BuildConfig.NAV_SQL_USER,
    password = BuildConfig.NAV_SQL_PASSWORD,
)
