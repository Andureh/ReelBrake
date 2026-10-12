package com.andureh.reelbrake

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val serviceOn = mutableStateOf(false)
    private val stats = mutableStateOf(DayStats())
    private val config = mutableStateOf(Config())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config.value = Prefs.load(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Bg)) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    Home(
                        serviceOn = serviceOn.value,
                        stats = stats.value,
                        config = config.value,
                        onConfig = {
                            config.value = it
                            Prefs.save(this, it)
                        },
                        openAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        openAppInfo = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        },
                        openBattery = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                        copyReport = { copyReport() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn.value = isServiceEnabled(this)
        stats.value = Stats.today(this)
    }

    private fun copyReport() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ReelBrake", Diagnostics.report()))
        Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }

    private fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, ReelBrakeService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

private val Accent = Color(0xFFFF7A59)
private val Bg = Color(0xFF101216)
private val CardBg = Color(0xFF1B1D23)
private val Muted = Color(0xFF9A9AA5)

@Composable
private fun Home(
    serviceOn: Boolean,
    stats: DayStats,
    config: Config,
    onConfig: (Config) -> Unit,
    openAccessibility: () -> Unit,
    openAppInfo: () -> Unit,
    openBattery: () -> Unit,
    copyReport: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ReelBrake", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.tagline), color = Muted)

        Section {
            if (serviceOn) {
                Text(stringResource(R.string.status_on), color = Color(0xFF6BD49A), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.status_on_desc), color = Muted, fontSize = 14.sp)
            } else {
                Text(stringResource(R.string.status_off), color = Accent, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.status_off_desc),
                    color = Muted, fontSize = 14.sp,
                )
                Button(onClick = openAccessibility, Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_accessibility)) }
                if (Build.VERSION.SDK_INT >= 33) {
                    Text(
                        stringResource(R.string.restricted_hint),
                        color = Muted, fontSize = 14.sp,
                    )
                    OutlinedButton(onClick = openAppInfo, Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_app_info)) }
                }
            }
        }

        Section {
            Text(stringResource(R.string.today), fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("${stats.reels}", stringResource(R.string.stat_reels))
                Stat("${stats.brakes}", stringResource(R.string.stat_pauses))
                Stat("${stats.exits}", stringResource(R.string.stat_exits))
                Stat("${stats.continues}", stringResource(R.string.stat_more))
            }
        }

        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brake_enabled), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = config.enabled, onCheckedChange = { onConfig(config.copy(enabled = it)) })
            }
            Text(stringResource(R.string.modes_title), color = Muted, fontSize = 14.sp)
            Source.entries.forEach { source ->
                ModeRow(stringResource(label(source)), config.mode(source)) { onConfig(config.copy(modes = config.modes + (source to it))) }
            }
            Text(
                stringResource(R.string.modes_hint),
                color = Muted, fontSize = 13.sp,
            )
        }

        Section {
            Stepper(stringResource(R.string.free_reels), config.freeReels, 0..5, 1, "") { onConfig(config.copy(freeReels = it)) }
            Stepper(stringResource(R.string.wait), config.baseWaitSec, 5..30, 5, " s") { onConfig(config.copy(baseWaitSec = it)) }
            Text(stringResource(R.string.wait_hint), color = Muted, fontSize = 13.sp)
            Stepper(stringResource(R.string.max_reel), config.maxReelSec, 60..300, 30, " s") { onConfig(config.copy(maxReelSec = it)) }
            Text(stringResource(R.string.max_reel_hint), color = Muted, fontSize = 13.sp)
        }

        Section {
            Text(stringResource(R.string.battery_title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.battery_desc),
                color = Muted, fontSize = 14.sp,
            )
            OutlinedButton(onClick = openBattery, Modifier.fillMaxWidth()) { Text(stringResource(R.string.battery_button)) }
        }

        Section {
            val live by Diagnostics.live.collectAsState()
            Text(stringResource(R.string.diag_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.diag_desc), color = Muted, fontSize = 13.sp)
            Text(live ?: stringResource(R.string.diag_empty), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            OutlinedButton(onClick = copyReport, Modifier.fillMaxWidth()) { Text(stringResource(R.string.diag_copy)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun label(source: Source) = when (source) {
    Source.REELS_TAB -> R.string.source_reels_tab
    Source.FEED -> R.string.source_feed
    Source.DIRECT -> R.string.source_direct
    Source.OTHER -> R.string.source_other
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ModeRow(label: String, mode: Mode, onMode: (Mode) -> Unit) {
    Column {
        Text(label, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Mode.OFF to R.string.mode_off, Mode.BRAKE to R.string.mode_brake, Mode.BLOCK to R.string.mode_block).forEach { (m, text) ->
                FilterChip(selected = mode == m, onClick = { onMode(m) }, label = { Text(stringResource(text)) })
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        TextButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Text("−", fontSize = 20.sp) }
        Text("$value$unit", Modifier.width(52.dp), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Text("+", fontSize = 20.sp) }
    }
}
