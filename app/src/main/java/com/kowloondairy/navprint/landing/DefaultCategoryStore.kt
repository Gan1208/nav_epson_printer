package com.kowloondairy.navprint.landing

import android.content.Context

/** The 本裝置預設類別: the 類別 the main page starts on. null means 全部, which is also what it starts as. */
interface DefaultCategoryStore {
    var category: Category?
}

class SharedPrefsDefaultCategoryStore(context: Context) : DefaultCategoryStore {
    private val prefs = context.getSharedPreferences("category", Context.MODE_PRIVATE)

    override var category: Category?
        get() = Category.entries.firstOrNull { it.name == prefs.getString(KEY_DEFAULT, null) }
        set(value) = prefs.edit().putString(KEY_DEFAULT, value?.name).apply()

    private companion object {
        const val KEY_DEFAULT = "default"
    }
}
