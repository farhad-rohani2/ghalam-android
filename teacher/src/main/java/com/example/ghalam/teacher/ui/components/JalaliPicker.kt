package com.example.ghalam.teacher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ghalam.teacher.util.Jalali
import com.example.ghalam.teacher.util.fa
import java.time.LocalDateTime

/** Date (Jalali calendar, Saturday first) and time picker; returns the Gregorian [LocalDateTime]. */
@Composable
fun JalaliDateTimeDialog(
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val start = remember { Jalali.fromDateTime(initial) }
    var year by remember { mutableIntStateOf(start.year) }
    var month by remember { mutableIntStateOf(start.month) }
    var day by remember { mutableIntStateOf(start.day) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }

    val monthDays = Jalali.daysInMonth(year, month)
    val safeDay = day.coerceAtMost(monthDays)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // in a right-to-left layout the first child is on the right: "previous" ▶ ... "next" ◀
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { year -= 1 }) { Text("▶") }
                    Text("سال ${year.fa()}", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { year += 1 }) { Text("◀") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (month == 1) { month = 12; year -= 1 } else month -= 1
                    }) { Text("▶") }
                    Text(Jalali.monthNames[month - 1], fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        if (month == 12) { month = 1; year += 1 } else month += 1
                    }) { Text("◀") }
                }

                Row(Modifier.fillMaxWidth()) {
                    Jalali.weekdayLetters.forEach { letter ->
                        Text(
                            letter,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val cells = List(Jalali.firstWeekdayIndex(year, month)) { 0 } + (1..monthDays).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i) ?: 0
                            val selected = d != 0 && d == safeDay
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .then(if (d != 0) Modifier.clickable { day = d } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (d != 0) {
                                    Text(
                                        d.fa(),
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Stepper("ساعت", "%02d".format(hour).fa(), onMinus = { hour = (hour + 23) % 24 }, onPlus = { hour = (hour + 1) % 24 })
                    Stepper("دقیقه", "%02d".format(minute).fa(), onMinus = { minute = (minute + 55) % 60 }, onPlus = { minute = (minute + 5) % 60 })
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    Button(onClick = { onConfirm(Jalali.toDateTime(year, month, safeDay, hour, minute)) }) { Text("تأیید") }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
