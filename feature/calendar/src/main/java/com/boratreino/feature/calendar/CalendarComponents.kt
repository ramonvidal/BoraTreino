package com.boratreino.feature.calendar

import android.content.res.Resources
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "6 machines", with the right singular form. */
internal fun Resources.machinesText(count: Int): String = getQuantityString(R.plurals.calendar_machines, count, count)

/** "18 sets", with the right singular form. */
internal fun Resources.setsText(count: Int): String = getQuantityString(R.plurals.calendar_sets, count, count)

/** "Mon 2 Nov". */
internal fun dayTitleFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
