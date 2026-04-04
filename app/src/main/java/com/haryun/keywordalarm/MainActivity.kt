package com.haryun.keywordalarm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.haryun.keywordalarm.data.KeywordRepository
import com.haryun.keywordalarm.ui.theme.KeywordAlarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    var keywords by remember { mutableStateOf(keywordRepository.getKeywords()) }
    var newKeyword by remember { mutableStateOf("") }
    var isServiceEnabled by remember { mutableStateOf(keywordRepository.isServiceEnabled()) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    // 설정 상태
    var isVibrationEnabled by remember { mutableStateOf(keywordRepository.isVibrationEnabled()) }
    var isSoundEnabled by remember { mutableStateOf(keywordRepository.isSoundEnabled()) }
    var volumeLevel by remember { mutableStateOf(keywordRepository.getVolumeLevel().toFloat()) }
    var customSoundUri by remember { mutableStateOf(keywordRepository.getCustomSoundUri()) }

    // 파일 선택기
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 영구 권한 요청
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "키워드 알람",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
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

                    // 소리가 활성화된 경우에만 볼륨과 알람음 설정 표시
                    if (isSoundEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 볼륨 조절
                        Text(
                            text = "볼륨: ${volumeLevel.toInt()}%",
                            fontSize = 14.sp
                        )
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

                        // 알람음 선택
                        Text(
                            text = "알람음",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { soundPickerLauncher.launch("audio/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (customSoundUri != null) "변경" else "파일 선택",
                                    fontSize = 14.sp
                                )
                            }

                            if (customSoundUri != null) {
                                OutlinedButton(
                                    onClick = {
                                        customSoundUri = null
                                        keywordRepository.clearCustomSoundUri()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("기본음", fontSize = 14.sp)
                                }
                            }
                        }

                        if (customSoundUri != null) {
                            Text(
                                text = "커스텀 알람음 설정됨",
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 키워드 관리 섹션 =====
            Text(
                text = "키워드 관리",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                            keywordRepository.addKeyword(newKeyword)
                            keywords = keywordRepository.getKeywords()
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "등록된 키워드가 없습니다\n키워드를 추가해주세요",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keywords.forEach { keyword ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = keyword,
                                    fontSize = 16.sp
                                )
                                IconButton(
                                    onClick = {
                                        keywordRepository.removeKeyword(keyword)
                                        keywords = keywordRepository.getKeywords()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "삭제",
                                        tint = Color(0xFFE57373)
                                    )
                                }
                            }
                        }
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

            // 하단 여백
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
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
