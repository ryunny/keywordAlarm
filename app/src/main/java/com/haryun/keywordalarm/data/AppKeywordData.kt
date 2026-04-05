package com.haryun.keywordalarm.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * 앱 정보를 담는 데이터 클래스
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

/**
 * 알림 이력 아이템
 */
data class AlarmHistoryItem(
    val timestamp: Long,
    val keyword: String,
    val appPackage: String,
    val appName: String
)

/**
 * 앱별 키워드 설정을 담는 데이터 클래스
 */
data class AppKeywordConfig(
    val packageName: String,
    val appName: String,
    val keywords: List<String>,
    val isEnabled: Boolean = true
)

/**
 * 설치된 앱 목록을 가져오는 유틸리티
 */
object AppUtils {

    /**
     * 알림을 보낼 수 있는 앱 목록 가져오기 (시스템 앱 제외)
     */
    fun getInstalledApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { appInfo ->
                // 홈화면에 아이콘이 있는 앱만 (Driver, Agent 등 백그라운드 서비스 제외)
                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = try {
                        packageManager.getApplicationIcon(appInfo.packageName)
                    } catch (e: Exception) {
                        null
                    }
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * 특정 패키지의 앱 이름 가져오기
     */
    fun getAppName(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * 특정 패키지의 앱 아이콘 가져오기
     */
    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
}
