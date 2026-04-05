package com.haryun.keywordalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.haryun.keywordalarm.data.AlarmHistoryItem
import com.haryun.keywordalarm.data.AlarmRepeat
import com.haryun.keywordalarm.data.AppInfo
import com.haryun.keywordalarm.data.AppUtils
import com.haryun.keywordalarm.data.KeywordRepository
import com.haryun.keywordalarm.data.VibrationPattern
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.haryun.keywordalarm.ui.theme.KeywordAlarmTheme

// 디자인 색상 상수
private val PrimaryIndigo = Color(0xFF3949AB)
private val PrimaryIndigoDark = Color(0xFF1A237E)
private val AccentOrange = Color(0xFFFF6D00)
private val AlarmRed = Color(0xFFD32F2F)
private val SurfaceGray = Color(0xFFF5F5F7)
private val CardWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF757575)
private val GreenSuccess = Color(0xFF2E7D32)
private val GreenSuccessBg = Color(0xFFE8F5E9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.android.gms.ads.MobileAds.initialize(this)
        enableEdgeToEdge()
        setContent {
            KeywordAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceGray
                ) {
                    KeywordAlarmApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordAlarmApp() {
    val context = LocalContext.current
    val keywordRepository = remember { KeywordRepository(context) }

    var globalKeywords by remember { mutableStateOf(keywordRepository.getGlobalKeywords()) }
    var newGlobalKeyword by remember { mutableStateOf("") }
    var appKeywordsMap by remember { mutableStateOf(keywordRepository.getAllAppKeywords()) }
    var showAppSelectDialog by remember { mutableStateOf(false) }
    var selectedAppForKeyword by remember { mutableStateOf<AppInfo?>(null) }
    var newAppKeyword by remember { mutableStateOf("") }
    var isServiceEnabled by remember { mutableStateOf(keywordRepository.isServiceEnabled()) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var isWakeScreenEnabled by remember { mutableStateOf(keywordRepository.isWakeScreenEnabled()) }
    var selectedAlarmRepeat by remember {
        mutableStateOf(
            try { AlarmRepeat.valueOf(keywordRepository.getAlarmRepeat()) }
            catch (e: Exception) { AlarmRepeat.ONCE }
        )
    }
    var isVibrationEnabled by remember { mutableStateOf(keywordRepository.isVibrationEnabled()) }
    var selectedVibrationPattern by remember {
        mutableStateOf(
            try { VibrationPattern.valueOf(keywordRepository.getVibrationPattern()) }
            catch (e: Exception) { VibrationPattern.DEFAULT }
        )
    }
    var isSoundEnabled by remember { mutableStateOf(keywordRepository.isSoundEnabled()) }
    var volumeLevel by remember { mutableStateOf(keywordRepository.getVolumeLevel().toFloat()) }
    var customSoundUri by remember { mutableStateOf(keywordRepository.getCustomSoundUri()) }
    var isScheduleEnabled by remember { mutableStateOf(keywordRepository.isScheduleEnabled()) }
    var scheduleStartHour by remember { mutableStateOf(keywordRepository.getScheduleStartHour()) }
    var scheduleStartMinute by remember { mutableStateOf(keywordRepository.getScheduleStartMinute()) }
    var scheduleEndHour by remember { mutableStateOf(keywordRepository.getScheduleEndHour()) }
    var scheduleEndMinute by remember { mutableStateOf(keywordRepository.getScheduleEndMinute()) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var alarmHistory by remember { mutableStateOf(keywordRepository.getAlarmHistory()) }
    var isTestAlarmPlaying by remember { mutableStateOf(false) }
    var disabledGlobalKeywords by remember {
        mutableStateOf(
            keywordRepository.getGlobalKeywords()
                .filter { !keywordRepository.isKeywordEnabled("global", it) }
                .toSet()
        )
    }
    var isAlarmActive by remember { mutableStateOf(com.haryun.keywordalarm.service.AlarmController.isPlaying()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            val playing = com.haryun.keywordalarm.service.AlarmController.isPlaying()
            if (isAlarmActive != playing) isAlarmActive = playing
            if (!playing && isTestAlarmPlaying) isTestAlarmPlaying = false
        }
    }

    var showSystemRingtoneDialog by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    val previewMediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            previewMediaPlayer.value?.release()
            previewMediaPlayer.value = null
        }
    }

    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { }
            customSoundUri = it.toString()
            keywordRepository.setCustomSoundUri(it.toString())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = scheduleStartHour, initialMinute = scheduleStartMinute,
            onConfirm = { h, m ->
                scheduleStartHour = h; scheduleStartMinute = m
                keywordRepository.setScheduleStart(h, m); showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = scheduleEndHour, initialMinute = scheduleEndMinute,
            onConfirm = { h, m ->
                scheduleEndHour = h; scheduleEndMinute = m
                keywordRepository.setScheduleEnd(h, m); showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
    if (showSystemRingtoneDialog) {
        SystemRingtoneDialog(
            currentUri = customSoundUri,
            onDismiss = { showSystemRingtoneDialog = false },
            onSelected = { uri, _ ->
                customSoundUri = uri
                keywordRepository.setCustomSoundUri(uri)
                showSystemRingtoneDialog = false
            }
        )
    }
    if (showAppSelectDialog) {
        AppSelectDialog(
            onDismiss = { showAppSelectDialog = false },
            onAppSelected = { appInfo ->
                selectedAppForKeyword = appInfo
                showAppSelectDialog = false
            }
        )
    }
    selectedAppForKeyword?.let { appInfo ->
        AppKeywordDialog(
            appInfo = appInfo,
            currentKeywords = keywordRepository.getAppKeywords(appInfo.packageName),
            onDismiss = { selectedAppForKeyword = null; newAppKeyword = "" },
            onAddKeyword = { keyword ->
                keywordRepository.addAppKeyword(appInfo.packageName, keyword)
                appKeywordsMap = keywordRepository.getAllAppKeywords()
            },
            onRemoveKeyword = { keyword ->
                keywordRepository.removeAppKeyword(appInfo.packageName, keyword)
                appKeywordsMap = keywordRepository.getAllAppKeywords()
            },
            isKeywordEnabled = { keyword -> keywordRepository.isKeywordEnabled(appInfo.packageName, keyword) },
            onToggleKeyword = { keyword ->
                val cur = keywordRepository.isKeywordEnabled(appInfo.packageName, keyword)
                keywordRepository.setKeywordEnabled(appInfo.packageName, keyword, !cur)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "알람키",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryIndigoDark
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                BannerAdView()
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ===== 알람 울리는 중 정지 배너 =====
            if (isAlarmActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlarmRed)
                        .clickable {
                            com.haryun.keywordalarm.service.AlarmController.stop()
                            isAlarmActive = false
                            isTestAlarmPlaying = false
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("알람이 울리는 중", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("정지", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {

                // ===== 권한 없을 때만 표시 =====
                if (!hasNotificationAccess) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = AlarmRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("알림 접근 권한 필요", fontWeight = FontWeight.Bold, color = AlarmRed, fontSize = 14.sp)
                                Text("키워드를 감지하려면 권한이 필요합니다", fontSize = 12.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AlarmRed),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("설정", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 서비스 히어로 카드 =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(PrimaryIndigoDark, PrimaryIndigo)
                                ),
                                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    if (isServiceEnabled) "감지 중" else "비활성화됨",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    "키워드 알람",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isServiceEnabled) Color(0xFF69F0AE) else Color(0xFFBDBDBD))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isServiceEnabled) "알림을 감지하고 있습니다" else "켜기를 눌러 활성화하세요",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Switch(
                                checked = isServiceEnabled,
                                onCheckedChange = { enabled ->
                                    isServiceEnabled = enabled
                                    keywordRepository.setServiceEnabled(enabled)
                                },
                                enabled = hasNotificationAccess,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF69F0AE),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    // 테스트 알람 버튼
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (isTestAlarmPlaying) {
                                    com.haryun.keywordalarm.service.AlarmController.stop()
                                    isTestAlarmPlaying = false
                                } else {
                                    val listener = com.haryun.keywordalarm.service.KeywordNotificationListener.instance
                                    if (listener != null) {
                                        listener.triggerTest()
                                    } else {
                                        isTestAlarmPlaying = true
                                        triggerTestAlarm(
                                            context, isVibrationEnabled, selectedVibrationPattern,
                                            isSoundEnabled, volumeLevel.toInt(), customSoundUri,
                                            selectedAlarmRepeat, onDone = { isTestAlarmPlaying = false }
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTestAlarmPlaying) AlarmRed else AccentOrange
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (isTestAlarmPlaying) Icons.Default.Stop else Icons.Default.Notifications,
                                null, modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isTestAlarmPlaying) "알람 정지" else "테스트 알람",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 알람 설정 섹션 =====
                SectionHeader(icon = Icons.Default.Settings, title = "알람 설정")
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {

                        // 진동
                        SettingRow(
                            icon = Icons.Default.Vibration,
                            iconTint = PrimaryIndigo,
                            title = "진동",
                            trailing = {
                                Switch(
                                    checked = isVibrationEnabled,
                                    onCheckedChange = {
                                        isVibrationEnabled = it
                                        keywordRepository.setVibrationEnabled(it)
                                    }
                                )
                            }
                        )

                        if (isVibrationEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                            ) {
                                Text("진동 패턴", fontSize = 13.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                VibrationPattern.entries.forEach { pattern ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedVibrationPattern = pattern
                                                keywordRepository.setVibrationPattern(pattern)
                                            }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedVibrationPattern == pattern,
                                            onClick = {
                                                selectedVibrationPattern = pattern
                                                keywordRepository.setVibrationPattern(pattern)
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryIndigo)
                                        )
                                        Text(pattern.label, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))

                        // 소리
                        SettingRow(
                            icon = Icons.Default.VolumeUp,
                            iconTint = PrimaryIndigo,
                            title = "소리",
                            trailing = {
                                Switch(
                                    checked = isSoundEnabled,
                                    onCheckedChange = {
                                        isSoundEnabled = it
                                        keywordRepository.setSoundEnabled(it)
                                    }
                                )
                            }
                        )

                        if (isSoundEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("볼륨 ${volumeLevel.toInt()}%", fontSize = 13.sp, color = TextSecondary)
                                    OutlinedButton(
                                        onClick = {
                                            if (isPreviewPlaying) {
                                                previewMediaPlayer.value?.stop()
                                                previewMediaPlayer.value?.release()
                                                previewMediaPlayer.value = null
                                                isPreviewPlaying = false
                                            } else {
                                                previewMediaPlayer.value = startPreviewSound(
                                                    context, volumeLevel.toInt(), customSoundUri
                                                ) { isPreviewPlaying = false }
                                                isPreviewPlaying = true
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo)
                                    ) {
                                        Icon(
                                            if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            null, modifier = Modifier.size(15.dp),
                                            tint = PrimaryIndigo
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (isPreviewPlaying) "정지" else "미리 듣기",
                                            fontSize = 12.sp, color = PrimaryIndigo
                                        )
                                    }
                                }
                                Slider(
                                    value = volumeLevel,
                                    onValueChange = { volumeLevel = it },
                                    onValueChangeFinished = { keywordRepository.setVolumeLevel(volumeLevel.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(thumbColor = PrimaryIndigo, activeTrackColor = PrimaryIndigo)
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("알람음", fontSize = 13.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                val soundLabel = remember(customSoundUri) { getSystemRingtoneLabel(context, customSoundUri) }
                                val soundAccessible = remember(customSoundUri) { isUriAccessible(context, customSoundUri) }
                                if (!soundAccessible && customSoundUri != null) {
                                    Text("⚠ 파일에 접근할 수 없습니다. 다시 선택해주세요.", fontSize = 12.sp, color = AlarmRed)
                                } else {
                                    Text(soundLabel, fontSize = 12.sp, color = PrimaryIndigo, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showSystemRingtoneDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("시스템 알람음", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { soundPickerLauncher.launch("audio/*") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("내 파일", fontSize = 12.sp)
                                    }
                                }
                                if (customSoundUri != null) {
                                    TextButton(
                                        onClick = { customSoundUri = null; keywordRepository.clearCustomSoundUri() },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(13.dp), tint = TextSecondary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("기본 알람음으로 초기화", fontSize = 12.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))

                        // 알람 반복
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("알람 반복", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AlarmRepeat.entries.forEach { repeat ->
                                    FilterChip(
                                        selected = selectedAlarmRepeat == repeat,
                                        onClick = {
                                            selectedAlarmRepeat = repeat
                                            keywordRepository.setAlarmRepeat(repeat)
                                        },
                                        label = { Text(repeat.label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryIndigo,
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))

                        // 화면 켜기
                        SettingRow(
                            icon = Icons.Default.Notifications,
                            iconTint = PrimaryIndigo,
                            title = "화면 켜기",
                            subtitle = "알람 시 잠금화면 위에 알림 표시",
                            trailing = {
                                Switch(
                                    checked = isWakeScreenEnabled,
                                    onCheckedChange = {
                                        isWakeScreenEnabled = it
                                        keywordRepository.setWakeScreenEnabled(it)
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 시간대 설정 섹션 =====
                SectionHeader(icon = Icons.Default.Schedule, title = "시간대 설정", subtitle = "설정한 시간에만 알람이 울립니다")
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        SettingRow(
                            icon = Icons.Default.Schedule,
                            iconTint = PrimaryIndigo,
                            title = "시간대 제한 사용",
                            trailing = {
                                Switch(
                                    checked = isScheduleEnabled,
                                    onCheckedChange = {
                                        isScheduleEnabled = it
                                        keywordRepository.setScheduleEnabled(it)
                                    }
                                )
                            }
                        )
                        if (isScheduleEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TimeButton(
                                    label = "시작",
                                    time = "%02d:%02d".format(scheduleStartHour, scheduleStartMinute),
                                    modifier = Modifier.weight(1f),
                                    onClick = { showStartTimePicker = true }
                                )
                                Text("~", fontSize = 20.sp, color = TextSecondary, fontWeight = FontWeight.Light)
                                TimeButton(
                                    label = "종료",
                                    time = "%02d:%02d".format(scheduleEndHour, scheduleEndMinute),
                                    modifier = Modifier.weight(1f),
                                    onClick = { showEndTimePicker = true }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 통합 키워드 섹션 =====
                SectionHeader(icon = Icons.Default.Search, title = "통합 키워드", subtitle = "모든 앱의 알림에 적용됩니다")
                Spacer(modifier = Modifier.height(8.dp))

                KeywordInputRow(
                    value = newGlobalKeyword,
                    onValueChange = { newGlobalKeyword = it },
                    onAdd = {
                        if (newGlobalKeyword.isNotBlank()) {
                            keywordRepository.addGlobalKeyword(newGlobalKeyword)
                            globalKeywords = keywordRepository.getGlobalKeywords()
                            newGlobalKeyword = ""
                        }
                    },
                    placeholder = "키워드 입력"
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (globalKeywords.isEmpty()) {
                    EmptyState("등록된 통합 키워드가 없습니다")
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            globalKeywords.forEach { keyword ->
                                KeywordChip(
                                    keyword = keyword,
                                    isEnabled = keyword !in disabledGlobalKeywords,
                                    onToggle = {
                                        val nowEnabled = keyword !in disabledGlobalKeywords
                                        keywordRepository.setKeywordEnabled("global", keyword, !nowEnabled)
                                        disabledGlobalKeywords = if (nowEnabled) disabledGlobalKeywords + keyword
                                        else disabledGlobalKeywords - keyword
                                    },
                                    onDelete = {
                                        keywordRepository.removeGlobalKeyword(keyword)
                                        globalKeywords = keywordRepository.getGlobalKeywords()
                                        disabledGlobalKeywords = disabledGlobalKeywords - keyword
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 제외 키워드 섹션 =====
                SectionHeader(icon = Icons.Default.NotificationsOff, title = "제외 키워드", subtitle = "이 단어가 포함된 알림은 무시합니다")
                Spacer(modifier = Modifier.height(8.dp))

                var newExclusionKeyword by remember { mutableStateOf("") }
                var exclusionKeywords by remember { mutableStateOf(keywordRepository.getExclusionKeywords()) }

                KeywordInputRow(
                    value = newExclusionKeyword,
                    onValueChange = { newExclusionKeyword = it },
                    onAdd = {
                        if (newExclusionKeyword.isNotBlank()) {
                            keywordRepository.addExclusionKeyword(newExclusionKeyword)
                            exclusionKeywords = keywordRepository.getExclusionKeywords()
                            newExclusionKeyword = ""
                        }
                    },
                    placeholder = "예: 광고, 이벤트"
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (exclusionKeywords.isEmpty()) {
                    EmptyState("등록된 제외 키워드가 없습니다")
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            exclusionKeywords.forEach { keyword ->
                                KeywordChip(
                                    keyword = keyword,
                                    onDelete = {
                                        keywordRepository.removeExclusionKeyword(keyword)
                                        exclusionKeywords = keywordRepository.getExclusionKeywords()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 앱별 키워드 섹션 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(icon = Icons.Default.Apps, title = "앱별 키워드", subtitle = "특정 앱에만 적용됩니다")
                    Button(
                        onClick = { showAppSelectDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("앱 추가", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (appKeywordsMap.isEmpty()) {
                    EmptyState("등록된 앱별 키워드가 없습니다\n'앱 추가' 버튼을 눌러 설정하세요")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        appKeywordsMap.forEach { (packageName, keywords) ->
                            AppKeywordCard(
                                packageName = packageName,
                                keywords = keywords,
                                onClick = {
                                    selectedAppForKeyword = AppInfo(
                                        packageName = packageName,
                                        appName = AppUtils.getAppName(context, packageName),
                                        icon = AppUtils.getAppIcon(context, packageName)
                                    )
                                },
                                onDelete = {
                                    keywordRepository.clearAppKeywords(packageName)
                                    appKeywordsMap = keywordRepository.getAllAppKeywords()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 알림 이력 =====
                var showHistoryDialog by remember { mutableStateOf(false) }
                if (showHistoryDialog) {
                    AlarmHistoryDialog(
                        history = alarmHistory,
                        onClear = { keywordRepository.clearAlarmHistory(); alarmHistory = emptyList() },
                        onDismiss = { showHistoryDialog = false }
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            alarmHistory = keywordRepository.getAlarmHistory()
                            showHistoryDialog = true
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryIndigo.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.History, null, tint = PrimaryIndigo, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("알림 이력", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                if (alarmHistory.isEmpty()) "최근 24시간 기록 없음"
                                else "최근 24시간 ${alarmHistory.size}건",
                                fontSize = 12.sp, color = TextSecondary
                            )
                        }
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "등록한 키워드가 알림에 포함되면 무음 모드에서도 알람이 울립니다",
                    fontSize = 11.sp, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ===== 공통 UI 컴포넌트 =====

@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF212121))
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    iconTint: Color = PrimaryIndigo,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        trailing()
    }
}

@Composable
fun TimeButton(label: String, time: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(time, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryIndigoDark)
        }
    }
}

@Composable
fun KeywordInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryIndigo,
                unfocusedBorderColor = Color(0xFFDDDDDD)
            )
        )
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun KeywordChip(
    keyword: String,
    isEnabled: Boolean = true,
    onToggle: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isEnabled) PrimaryIndigo.copy(alpha = 0.07f) else Color(0xFFF0F0F0)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isEnabled) PrimaryIndigo else Color(0xFFBDBDBD))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = keyword,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            color = if (isEnabled) Color(0xFF212121) else TextSecondary,
            fontWeight = if (isEnabled) FontWeight.Medium else FontWeight.Normal
        )
        if (onToggle != null) {
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .height(24.dp)
                    .padding(end = 4.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryIndigo
                )
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(15.dp), tint = TextSecondary)
        }
    }
}

@Composable
fun AlarmHistoryDialog(
    history: List<AlarmHistoryItem>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm", Locale.KOREA) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("알림 이력", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("최근 24시간", fontSize = 12.sp, color = TextSecondary)
                    }
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClear) {
                            Text("전체 삭제", fontSize = 12.sp, color = AlarmRed)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("최근 24시간 내 알람 기록이 없습니다", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(history) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("\"${item.keyword}\"", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(item.appName, fontSize = 12.sp, color = TextSecondary)
                                }
                                Text(sdf.format(Date(item.timestamp)), fontSize = 11.sp, color = TextSecondary)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(8.dp)
                ) { Text("닫기", color = PrimaryIndigo) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("시간 선택", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소", color = TextSecondary) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("확인", color = PrimaryIndigo) }
                }
            }
        }
    }
}

@Composable
fun AppKeywordCard(
    packageName: String,
    keywords: List<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val appName = remember { AppUtils.getAppName(context, packageName) }
    val appIcon = remember { AppUtils.getAppIcon(context, packageName) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appIcon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = appName,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                )
            } ?: Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(PrimaryIndigo.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Apps, null, tint = PrimaryIndigo, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    keywords.joinToString(", "),
                    fontSize = 12.sp, color = TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFE57373))
            }
        }
    }
}

@Composable
fun AppSelectDialog(onDismiss: () -> Unit, onAppSelected: (AppInfo) -> Unit) {
    val context = LocalContext.current
    val installedApps = remember { AppUtils.getInstalledApps(context) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite)
        ) {
            Column {
                Text("앱 선택", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(20.dp))
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(installedApps) { appInfo ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onAppSelected(appInfo) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            appInfo.icon?.let { drawable ->
                                Image(
                                    bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                                    contentDescription = appInfo.appName,
                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))
                                )
                            } ?: Box(
                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(PrimaryIndigo.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Apps, null, tint = PrimaryIndigo)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(appInfo.appName, fontSize = 14.sp)
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF5F5F5))
                    }
                }
            }
        }
    }
}

@Composable
fun AppKeywordDialog(
    appInfo: AppInfo,
    currentKeywords: List<String>,
    onDismiss: () -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    isKeywordEnabled: (String) -> Boolean = { true },
    onToggleKeyword: (String) -> Unit = {}
) {
    var newKeyword by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf(currentKeywords) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    appInfo.icon?.let { drawable ->
                        Image(
                            bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                            contentDescription = appInfo.appName,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(appInfo.appName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("키워드 입력", fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = Color(0xFFDDDDDD)
                        )
                    )
                    Button(
                        onClick = {
                            if (newKeyword.isNotBlank()) {
                                onAddKeyword(newKeyword)
                                keywords = keywords + newKeyword
                                newKeyword = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.Add, null)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (keywords.isEmpty()) {
                    Text("키워드를 추가해주세요", color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp), fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        keywords.forEach { keyword ->
                            KeywordChip(
                                keyword = keyword,
                                isEnabled = isKeywordEnabled(keyword),
                                onToggle = { onToggleKeyword(keyword) },
                                onDelete = { onRemoveKeyword(keyword); keywords = keywords - keyword }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("닫기", color = PrimaryIndigo) }
                }
            }
        }
    }
}

@Composable
fun SystemRingtoneDialog(
    currentUri: String?,
    onDismiss: () -> Unit,
    onSelected: (uri: String, name: String) -> Unit
) {
    val context = LocalContext.current
    val ringtones = remember {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = manager.cursor
        val list = mutableListOf<Pair<String, String>>()
        while (cursor.moveToNext()) {
            val uri = manager.getRingtoneUri(cursor.position).toString()
            val name = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            list.add(uri to name)
        }
        cursor.close()
        list
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite)
        ) {
            Column {
                Text("알람음 선택", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(20.dp))
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(ringtones) { (uri, name) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelected(uri, name) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (currentUri == uri) {
                                Icon(Icons.Default.MusicNote, null, tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF5F5F5))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(8.dp)
                ) { Text("닫기", color = PrimaryIndigo) }
            }
        }
    }
}

fun getSystemRingtoneLabel(context: android.content.Context, uri: String?): String {
    if (uri == null) return "기본 알람음 (시스템)"
    return try {
        val parsedUri = Uri.parse(uri)
        // ContentResolver로 실제 파일명 조회 (내 파일 선택 시)
        val name = context.contentResolver.query(
            parsedUri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        if (!name.isNullOrBlank()) name
        else RingtoneManager.getRingtone(context, parsedUri)?.getTitle(context) ?: "커스텀 알람음"
    } catch (e: Exception) { "커스텀 알람음" }
}

// URI가 현재 접근 가능한지 확인
fun isUriAccessible(context: android.content.Context, uri: String?): Boolean {
    if (uri == null) return true
    return try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.close()
        true
    } catch (e: Exception) { false }
}

fun startPreviewSound(
    context: android.content.Context,
    volumePercent: Int,
    customSoundUri: String?,
    onStop: () -> Unit
): MediaPlayer? {
    return try {
        // 커스텀 URI가 접근 불가(앱 재시작 후 권한 만료)이면 기본 알람음으로 폴백
        val resolvedUri = if (customSoundUri != null && isUriAccessible(context, customSoundUri))
            Uri.parse(customSoundUri)
        else null
        val alarmUri = resolvedUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetVolume = (maxVolume * volumePercent / 100f).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(context, alarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            prepare()
            start()
            com.haryun.keywordalarm.service.AlarmController.register(this)
            setOnCompletionListener {
                release()
                com.haryun.keywordalarm.service.AlarmController.clear()
                onStop()
            }
        }
        handler.postDelayed({
            try { if (mediaPlayer.isPlaying) { mediaPlayer.stop(); mediaPlayer.release(); onStop() } } catch (e: Exception) {}
        }, 30000)
        mediaPlayer
    } catch (e: Exception) {
        android.util.Log.e("PreviewSound", "미리 듣기 실패: ${e.message}")
        onStop()
        null
    }
}

fun triggerTestAlarm(
    context: android.content.Context,
    vibrationEnabled: Boolean,
    vibrationPattern: VibrationPattern,
    soundEnabled: Boolean,
    volumePercent: Int,
    customSoundUri: String?,
    alarmRepeat: AlarmRepeat = AlarmRepeat.ONCE,
    onDone: () -> Unit = {}
) {
    val keywordRepository = KeywordRepository(context)
    if (keywordRepository.isWakeScreenEnabled()) {
        try {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            @Suppress("DEPRECATION")
            pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "알람키:TestWakeLock"
            ).acquire(5000)
        } catch (e: Exception) { }
    }
    if (vibrationEnabled) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        vibrator.cancel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val p = vibrationPattern.pattern
            val amplitudes = IntArray(p.size) { i -> if (i % 2 == 0) 0 else 255 }
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(p, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(vibrationPattern.pattern, -1)
        }
    }
    if (soundEnabled) {
        if (com.haryun.keywordalarm.service.AlarmController.isPlaying()) {
            com.haryun.keywordalarm.service.AlarmController.stop()
        }
        startPreviewSound(context, volumePercent, customSoundUri, onStop = onDone)
    } else {
        onDone()
    }
}

// 출시 전: 아래 TEST_AD_UNIT_ID → REAL_AD_UNIT_ID 로 교체
private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"  // Google 공식 테스트 ID
private const val REAL_AD_UNIT_ID = "ca-app-pub-7340199690245957/1967131510"  // 실제 광고 ID
private const val IS_TEST_MODE = true  // 출시 시 false 로 변경

@Composable
fun BannerAdView() {
    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        factory = {
            com.google.android.gms.ads.AdView(context).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                adUnitId = if (IS_TEST_MODE) TEST_AD_UNIT_ID else REAL_AD_UNIT_ID
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)  // 광고 미로딩 시에도 최소 높이 유지
    )
}

fun isNotificationServiceEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(context.packageName) == true
}
