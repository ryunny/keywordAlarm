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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AdMob 초기화
        com.google.android.gms.ads.MobileAds.initialize(this)
        enableEdgeToEdge()
        setContent {
            KeywordAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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

    // 글로벌 키워드
    var globalKeywords by remember { mutableStateOf(keywordRepository.getGlobalKeywords()) }
    var newGlobalKeyword by remember { mutableStateOf("") }

    // 앱별 키워드
    var appKeywordsMap by remember { mutableStateOf(keywordRepository.getAllAppKeywords()) }
    var showAppSelectDialog by remember { mutableStateOf(false) }
    var selectedAppForKeyword by remember { mutableStateOf<AppInfo?>(null) }
    var newAppKeyword by remember { mutableStateOf("") }

    var isServiceEnabled by remember { mutableStateOf(keywordRepository.isServiceEnabled()) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    // Android 13+ 알림 권한 요청
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 결과 무시, 권한 거부해도 앱은 동작 */ }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 설정 상태
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

    // 시간대 설정
    var isScheduleEnabled by remember { mutableStateOf(keywordRepository.isScheduleEnabled()) }
    var scheduleStartHour by remember { mutableStateOf(keywordRepository.getScheduleStartHour()) }
    var scheduleStartMinute by remember { mutableStateOf(keywordRepository.getScheduleStartMinute()) }
    var scheduleEndHour by remember { mutableStateOf(keywordRepository.getScheduleEndHour()) }
    var scheduleEndMinute by remember { mutableStateOf(keywordRepository.getScheduleEndMinute()) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // 알림 이력
    var alarmHistory by remember { mutableStateOf(keywordRepository.getAlarmHistory()) }

    // 시스템 알람음 다이얼로그
    var showSystemRingtoneDialog by remember { mutableStateOf(false) }

    // 미리 듣기 상태
    var isPreviewPlaying by remember { mutableStateOf(false) }
    val previewMediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            previewMediaPlayer.value?.release()
            previewMediaPlayer.value = null
        }
    }

    // 파일 선택기
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // 권한 부여 실패해도 진행
            }
            customSoundUri = it.toString()
            keywordRepository.setCustomSoundUri(it.toString())
        }
    }

    // 화면 복귀 시 권한 상태 다시 확인
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 시작 시간 피커
    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = scheduleStartHour,
            initialMinute = scheduleStartMinute,
            onConfirm = { h, m ->
                scheduleStartHour = h; scheduleStartMinute = m
                keywordRepository.setScheduleStart(h, m)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    // 종료 시간 피커
    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = scheduleEndHour,
            initialMinute = scheduleEndMinute,
            onConfirm = { h, m ->
                scheduleEndHour = h; scheduleEndMinute = m
                keywordRepository.setScheduleEnd(h, m)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    // 시스템 알람음 선택 다이얼로그
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

    // 앱 선택 다이얼로그
    if (showAppSelectDialog) {
        AppSelectDialog(
            onDismiss = { showAppSelectDialog = false },
            onAppSelected = { appInfo ->
                selectedAppForKeyword = appInfo
                showAppSelectDialog = false
            }
        )
    }

    // 앱별 키워드 추가 다이얼로그
    selectedAppForKeyword?.let { appInfo ->
        AppKeywordDialog(
            appInfo = appInfo,
            currentKeywords = appKeywordsMap[appInfo.packageName] ?: emptyList(),
            onDismiss = {
                selectedAppForKeyword = null
                newAppKeyword = ""
            },
            onAddKeyword = { keyword ->
                keywordRepository.addAppKeyword(appInfo.packageName, keyword)
                appKeywordsMap = keywordRepository.getAllAppKeywords()
            },
            onRemoveKeyword = { keyword ->
                keywordRepository.removeAppKeyword(appInfo.packageName, keyword)
                appKeywordsMap = keywordRepository.getAllAppKeywords()
            },
            isKeywordEnabled = { keyword ->
                keywordRepository.isKeywordEnabled(appInfo.packageName, keyword)
            },
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
                    Text(
                        "알람키",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            BannerAdView()
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 알림 접근 권한 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasNotificationAccess)
                        Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (hasNotificationAccess) "알림 접근 권한: 허용됨" else "알림 접근 권한: 필요함",
                        fontWeight = FontWeight.Medium,
                        color = if (hasNotificationAccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )

                    if (!hasNotificationAccess) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC62828)
                            )
                        ) {
                            Text("권한 설정하기")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 서비스 활성화 스위치
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "키워드 알람 활성화",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isServiceEnabled) "알림을 감지하고 있습니다" else "비활성화됨",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = isServiceEnabled,
                        onCheckedChange = { enabled ->
                            isServiceEnabled = enabled
                            keywordRepository.setServiceEnabled(enabled)
                            com.haryun.keywordalarm.service.KeywordNotificationListener
                                .instance?.updateStatusNotification()
                        },
                        enabled = hasNotificationAccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 알람 설정 섹션 =====
            Text(
                text = "알람 설정",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 진동 설정
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("진동")
                        Switch(
                            checked = isVibrationEnabled,
                            onCheckedChange = { enabled ->
                                isVibrationEnabled = enabled
                                keywordRepository.setVibrationEnabled(enabled)
                            }
                        )
                    }

                    if (isVibrationEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("진동 패턴", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            VibrationPattern.entries.forEach { pattern ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedVibrationPattern = pattern
                                            keywordRepository.setVibrationPattern(pattern)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedVibrationPattern == pattern,
                                        onClick = {
                                            selectedVibrationPattern = pattern
                                            keywordRepository.setVibrationPattern(pattern)
                                        }
                                    )
                                    Text(pattern.label, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 소리 설정
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("소리")
                        Switch(
                            checked = isSoundEnabled,
                            onCheckedChange = { enabled ->
                                isSoundEnabled = enabled
                                keywordRepository.setSoundEnabled(enabled)
                            }
                        )
                    }

                    if (isSoundEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "볼륨: ${volumeLevel.toInt()}%",
                                fontSize = 14.sp
                            )
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
                                        ) {
                                            isPreviewPlaying = false
                                        }
                                        isPreviewPlaying = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPreviewPlaying) "정지" else "미리 듣기", fontSize = 13.sp)
                            }
                        }
                        Slider(
                            value = volumeLevel,
                            onValueChange = { volumeLevel = it },
                            onValueChangeFinished = {
                                keywordRepository.setVolumeLevel(volumeLevel.toInt())
                            },
                            valueRange = 0f..100f,
                            steps = 9
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "알람음",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 현재 선택된 알람음 이름 표시
                        val soundLabel = remember(customSoundUri) {
                            getSystemRingtoneLabel(context, customSoundUri)
                        }
                        Text(
                            text = soundLabel,
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 시스템 알람음 선택
                            OutlinedButton(
                                onClick = { showSystemRingtoneDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("시스템 알람음", fontSize = 13.sp)
                            }

                            // 내 파일 선택
                            OutlinedButton(
                                onClick = { soundPickerLauncher.launch("audio/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("내 파일", fontSize = 13.sp)
                            }
                        }

                        // 기본음으로 초기화
                        if (customSoundUri != null) {
                            TextButton(
                                onClick = {
                                    customSoundUri = null
                                    keywordRepository.clearCustomSoundUri()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("기본 알람음으로 초기화", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 알람 반복 횟수
                    Text("알람 반복", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlarmRepeat.entries.forEach { repeat ->
                            FilterChip(
                                selected = selectedAlarmRepeat == repeat,
                                onClick = {
                                    selectedAlarmRepeat = repeat
                                    keywordRepository.setAlarmRepeat(repeat)
                                },
                                label = { Text(repeat.label, fontSize = 12.sp) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 화면 켜기
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("화면 켜기")
                            Text(
                                "알람 시 잠금화면 위에 알림 표시",
                                fontSize = 12.sp, color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isWakeScreenEnabled,
                            onCheckedChange = {
                                isWakeScreenEnabled = it
                                keywordRepository.setWakeScreenEnabled(it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 테스트 알람 버튼
                    Button(
                        onClick = {
                            triggerTestAlarm(
                                context,
                                isVibrationEnabled,
                                selectedVibrationPattern,
                                isSoundEnabled,
                                volumeLevel.toInt(),
                                customSoundUri,
                                selectedAlarmRepeat
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("테스트 알람 울리기")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 시간대 설정 섹션 =====
            Text("시간대 설정", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("설정한 시간에만 알람이 울립니다", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("시간대 제한 사용")
                        Switch(
                            checked = isScheduleEnabled,
                            onCheckedChange = {
                                isScheduleEnabled = it
                                keywordRepository.setScheduleEnabled(it)
                            }
                        )
                    }

                    if (isScheduleEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showStartTimePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("시작", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        "%02d:%02d".format(scheduleStartHour, scheduleStartMinute),
                                        fontWeight = FontWeight.Bold, fontSize = 18.sp
                                    )
                                }
                            }
                            Text("~", modifier = Modifier.align(Alignment.CenterVertically), fontSize = 18.sp)
                            OutlinedButton(
                                onClick = { showEndTimePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("종료", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        "%02d:%02d".format(scheduleEndHour, scheduleEndMinute),
                                        fontWeight = FontWeight.Bold, fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 통합 키워드 섹션 =====
            Text(
                text = "통합 키워드",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "모든 앱의 알림에 적용됩니다",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newGlobalKeyword,
                    onValueChange = { newGlobalKeyword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("키워드 입력") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (newGlobalKeyword.isNotBlank()) {
                            keywordRepository.addGlobalKeyword(newGlobalKeyword)
                            globalKeywords = keywordRepository.getGlobalKeywords()
                            newGlobalKeyword = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "추가")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 글로벌 키워드 목록
            if (globalKeywords.isEmpty()) {
                Text(
                    text = "등록된 통합 키워드가 없습니다",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    globalKeywords.forEach { keyword ->
                        KeywordChip(
                            keyword = keyword,
                            isEnabled = keywordRepository.isKeywordEnabled("global", keyword),
                            onToggle = {
                                val cur = keywordRepository.isKeywordEnabled("global", keyword)
                                keywordRepository.setKeywordEnabled("global", keyword, !cur)
                                globalKeywords = keywordRepository.getGlobalKeywords()
                            },
                            onDelete = {
                                keywordRepository.removeGlobalKeyword(keyword)
                                globalKeywords = keywordRepository.getGlobalKeywords()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 제외 키워드 섹션 =====
            Text("제외 키워드", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("이 단어가 포함된 알림은 무시합니다", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            var newExclusionKeyword by remember { mutableStateOf("") }
            var exclusionKeywords by remember { mutableStateOf(keywordRepository.getExclusionKeywords()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newExclusionKeyword,
                    onValueChange = { newExclusionKeyword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("예: 광고, 이벤트") },
                    singleLine = true
                )
                Button(onClick = {
                    if (newExclusionKeyword.isNotBlank()) {
                        keywordRepository.addExclusionKeyword(newExclusionKeyword)
                        exclusionKeywords = keywordRepository.getExclusionKeywords()
                        newExclusionKeyword = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "추가")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (exclusionKeywords.isEmpty()) {
                Text(
                    "등록된 제외 키워드가 없습니다",
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 앱별 키워드 섹션 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "앱별 키워드",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "특정 앱에만 적용됩니다",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Button(
                    onClick = { showAppSelectDialog = true }
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("앱 추가")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 앱별 키워드 목록
            if (appKeywordsMap.isEmpty()) {
                Text(
                    text = "등록된 앱별 키워드가 없습니다\n'앱 추가' 버튼을 눌러 설정하세요",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // 안내 텍스트
            Text(
                text = "등록한 키워드가 알림에 포함되면\n무음 모드에서도 알람이 울립니다",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 알림 이력 섹션 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("알림 이력", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("최근 울린 알람 기록", fontSize = 12.sp, color = Color.Gray)
                }
                if (alarmHistory.isNotEmpty()) {
                    TextButton(onClick = {
                        keywordRepository.clearAlarmHistory()
                        alarmHistory = emptyList()
                    }) {
                        Text("전체 삭제", fontSize = 12.sp, color = Color(0xFFE57373))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (alarmHistory.isEmpty()) {
                Text(
                    "아직 알람이 울린 기록이 없습니다",
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alarmHistory.forEach { item ->
                        AlarmHistoryCard(item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun KeywordChip(
    keyword: String,
    isEnabled: Boolean = true,
    onToggle: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyword,
                fontSize = 14.sp,
                color = if (isEnabled) MaterialTheme.colorScheme.onSecondaryContainer
                        else Color.Gray
            )
            if (onToggle != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.height(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "삭제",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
            }
        }
    }
}

@Composable
fun AlarmHistoryCard(item: AlarmHistoryItem) {
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm", Locale.KOREA) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\"${item.keyword}\"",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = item.appName,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = sdf.format(Date(item.timestamp)),
                fontSize = 12.sp,
                color = Color.Gray
            )
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
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("시간 선택", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("확인") }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 앱 아이콘
            appIcon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } ?: Icon(
                Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = keywords.joinToString(", "),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color(0xFFE57373)
                )
            }
        }
    }
}

@Composable
fun AppSelectDialog(
    onDismiss: () -> Unit,
    onAppSelected: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val installedApps = remember { AppUtils.getInstalledApps(context) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                Text(
                    text = "앱 선택",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(installedApps) { appInfo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppSelected(appInfo) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            appInfo.icon?.let { drawable ->
                                Image(
                                    bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                                    contentDescription = appInfo.appName,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            } ?: Icon(
                                Icons.Default.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = appInfo.appName,
                                fontSize = 14.sp
                            )
                        }
                        HorizontalDivider()
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
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 헤더
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    appInfo.icon?.let { drawable ->
                        Image(
                            bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                            contentDescription = appInfo.appName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = appInfo.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 키워드 입력
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("키워드 입력") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newKeyword.isNotBlank()) {
                                onAddKeyword(newKeyword)
                                keywords = keywords + newKeyword
                                newKeyword = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "추가")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 키워드 목록
                if (keywords.isEmpty()) {
                    Text(
                        text = "키워드를 추가해주세요",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        keywords.forEach { keyword ->
                            KeywordChip(
                                keyword = keyword,
                                isEnabled = isKeywordEnabled(keyword),
                                onToggle = { onToggleKeyword(keyword) },
                                onDelete = {
                                    onRemoveKeyword(keyword)
                                    keywords = keywords - keyword
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 닫기 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("닫기")
                    }
                }
            }
        }
    }
}

// 시스템 알람음 목록 다이얼로그
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
        val list = mutableListOf<Pair<String, String>>() // uri to name
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
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                Text(
                    text = "알람음 선택",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(ringtones) { (uri, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(uri, name) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (currentUri == uri) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(8.dp)
                ) {
                    Text("닫기")
                }
            }
        }
    }
}

// 현재 설정된 알람음 이름 반환
fun getSystemRingtoneLabel(context: android.content.Context, uri: String?): String {
    if (uri == null) return "기본 알람음 (시스템)"
    return try {
        val ringtone = RingtoneManager.getRingtone(context, Uri.parse(uri))
        ringtone?.getTitle(context) ?: "커스텀 알람음"
    } catch (e: Exception) {
        "커스텀 알람음"
    }
}

// 볼륨 미리 듣기 — MediaPlayer 반환, 재생 종료 시 onStop 콜백 호출
fun startPreviewSound(
    context: android.content.Context,
    volumePercent: Int,
    customSoundUri: String?,
    onStop: () -> Unit
): MediaPlayer? {
    return try {
        val alarmUri = if (customSoundUri != null) {
            Uri.parse(customSoundUri)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

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

        // 30초 후 강제 중지
        handler.postDelayed({
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
                mediaPlayer.release()
                onStop()
            }
        }, 30000)

        mediaPlayer
    } catch (e: Exception) {
        android.util.Log.e("PreviewSound", "미리 듣기 실패: ${e.message}")
        onStop()
        null
    }
}

// 테스트 알람 — 현재 설정 그대로 진동 + 소리 + 화면 켜기 즉시 실행
fun triggerTestAlarm(
    context: android.content.Context,
    vibrationEnabled: Boolean,
    vibrationPattern: VibrationPattern,
    soundEnabled: Boolean,
    volumePercent: Int,
    customSoundUri: String?,
    alarmRepeat: AlarmRepeat = AlarmRepeat.ONCE
) {
    // 화면 켜기 + 알림 표시
    val keywordRepository = KeywordRepository(context)
    if (keywordRepository.isWakeScreenEnabled()) {
        // WakeLock
        try {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            @Suppress("DEPRECATION")
            pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "알람키:TestWakeLock"
            ).acquire(5000)
        } catch (e: Exception) { }

        // 알림 표시
        val channelId = "alarm_key_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "알람키 알림", android.app.NotificationManager.IMPORTANCE_HIGH
            )
            (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("알람키 — 테스트 알람")
            .setContentText("테스트 알람이 울렸습니다")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(9999, notification)
    }

    // 진동
    if (vibrationEnabled) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(vibrationPattern.pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(vibrationPattern.pattern, -1)
        }
    }

    // 소리 (이미 울리는 중이면 먼저 정지 후 재생)
    if (soundEnabled) {
        if (com.haryun.keywordalarm.service.AlarmController.isPlaying()) {
            com.haryun.keywordalarm.service.AlarmController.stop()
        }
        startPreviewSound(context, volumePercent, customSoundUri) {}
    }
}

// 배너 광고
// 테스트 광고 ID: ca-app-pub-3940256099942544/6300978111
// 출시 전 실제 AdMob 광고 ID로 교체 필요
@Composable
fun BannerAdView() {
    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        factory = {
            com.google.android.gms.ads.AdView(context).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                adUnitId = "ca-app-pub-7340199690245957/1967131510"
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

// 알림 리스너 권한 확인
fun isNotificationServiceEnabled(context: android.content.Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.contains(packageName) == true
}
