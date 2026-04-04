# keywordAlarm 프로젝트 구조

## 개요
무음/진동 모드에서도 **특정 키워드가 포함된 알림**은 소리로 울리게 해주는 Android 앱.

- 플랫폼: Android (Kotlin + Jetpack Compose)
- 최소 SDK: (build.gradle.kts 확인)
- 패키지명: `com.haryun.keywordalarm`

---

## 파일 구조

```
keywordAlarm/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml             # 권한 선언, 서비스 등록
│       └── java/com/haryun/keywordalarm/
│           ├── MainActivity.kt             # UI 전체 (Jetpack Compose)
│           ├── data/
│           │   ├── AppKeywordData.kt       # 데이터 클래스 + AppUtils
│           │   └── KeywordRepository.kt    # SharedPreferences 저장소
│           ├── service/
│           │   └── KeywordNotificationListener.kt  # 알림 감지 + 알람 실행
│           └── ui/theme/
│               ├── Color.kt
│               ├── Theme.kt
│               └── Type.kt
├── build.gradle.kts                        # 루트 빌드 설정
└── settings.gradle.kts
```

---

## 핵심 파일 설명

### `MainActivity.kt`
앱의 유일한 화면. Jetpack Compose로 구성.

| Composable | 역할 |
|---|---|
| `KeywordAlarmApp()` | 메인 화면 전체 (스크롤 가능한 설정 페이지) |
| `KeywordChip()` | 키워드 태그 UI (삭제 버튼 포함) |
| `AppKeywordCard()` | 앱별 키워드 카드 |
| `AppSelectDialog()` | 앱 선택 팝업 |
| `AppKeywordDialog()` | 앱별 키워드 추가/삭제 팝업 |

| 일반 함수 | 역할 |
|---|---|
| `isNotificationServiceEnabled()` | 알림 접근 권한 확인 |
| `playPreviewSound()` | 볼륨 미리 듣기 (슬라이더 옆 버튼) |

**화면 구성 순서:**
1. 알림 접근 권한 상태 카드
2. 키워드 알람 활성화 스위치
3. 알람 설정 (진동 / 소리 / 볼륨 / 알람음 파일 선택)
4. 통합 키워드 (모든 앱에 적용)
5. 앱별 키워드 (특정 앱에만 적용)

---

### `data/AppKeywordData.kt`
```
AppInfo           - 앱 정보 (packageName, appName, icon)
AppKeywordConfig  - 앱별 키워드 설정 묶음
AppUtils          - 설치된 앱 목록, 앱 이름/아이콘 조회 유틸
```

---

### `data/KeywordRepository.kt`
**SharedPreferences** 기반 저장소. 앱 설정과 키워드를 저장/불러옴.

| 저장 키 | 내용 |
|---|---|
| `global_keywords` | 통합 키워드 목록 (콤마 구분 문자열) |
| `app_keywords` | 앱별 키워드 (JSON 형식) |
| `service_enabled` | 서비스 활성화 여부 |
| `vibration_enabled` | 진동 설정 |
| `sound_enabled` | 소리 설정 |
| `volume_level` | 볼륨 (0~100) |
| `custom_sound_uri` | 커스텀 알람음 URI |

주요 메서드: `findMatchingKeyword(packageName, content)` — 글로벌 → 앱별 순으로 키워드 탐색

---

### `service/KeywordNotificationListener.kt`
Android `NotificationListenerService`를 상속한 백그라운드 서비스.

**흐름:**
```
알림 수신 (onNotificationPosted)
  → 서비스 활성화 확인
  → 키워드 매칭 (findMatchingKeyword)
  → triggerAlarm()
      ├── vibrate()         : STREAM_ALARM 진동
      └── playAlarmSound()  : STREAM_ALARM으로 소리 재생 (무음 모드 우회)
                              → 3초 후 자동 중지
```

---

### `AndroidManifest.xml` 권한 목록

| 권한 | 용도 |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 다른 앱 알림 읽기 |
| `VIBRATE` | 진동 |
| `ACCESS_NOTIFICATION_POLICY` | 방해금지 모드 접근 |
| `FOREGROUND_SERVICE` | 포그라운드 서비스 |
| `POST_NOTIFICATIONS` | Android 13+ 알림 권한 |

---

## 데이터 흐름

```
사용자 설정 (MainActivity)
    ↓ SharedPreferences 저장
KeywordRepository
    ↑ 읽기
KeywordNotificationListener (백그라운드)
    ↑ 알림 수신
Android NotificationListenerService
```

---

## 다음 구현 예정

- [ ] 알람 울릴 때 화면 켜기 (WakeLock)
- [ ] 알람 이력 로그
- [ ] 위젯 지원
