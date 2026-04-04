package com.haryun.keywordalarm.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class KeywordRepository(private val context: Context) {

    companion object {
        private const val PREF_NAME = "keyword_alarm_prefs"
        private const val KEY_GLOBAL_KEYWORDS = "global_keywords"
        private const val KEY_APP_KEYWORDS = "app_keywords"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VOLUME_LEVEL = "volume_level"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ===== 통합(글로벌) 키워드 =====

    fun getGlobalKeywords(): List<String> {
        val keywordsString = prefs.getString(KEY_GLOBAL_KEYWORDS, "") ?: ""
        return if (keywordsString.isBlank()) {
            emptyList()
        } else {
            keywordsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    fun saveGlobalKeywords(keywords: List<String>) {
        val keywordsString = keywords.filter { it.isNotBlank() }.joinToString(",")
        prefs.edit().putString(KEY_GLOBAL_KEYWORDS, keywordsString).apply()
    }

    fun addGlobalKeyword(keyword: String) {
        if (keyword.isBlank()) return
        val currentKeywords = getGlobalKeywords().toMutableList()
        if (!currentKeywords.contains(keyword.trim())) {
            currentKeywords.add(keyword.trim())
            saveGlobalKeywords(currentKeywords)
        }
    }

    fun removeGlobalKeyword(keyword: String) {
        val currentKeywords = getGlobalKeywords().toMutableList()
        currentKeywords.remove(keyword)
        saveGlobalKeywords(currentKeywords)
    }

    // ===== 앱별 키워드 =====

    /**
     * 모든 앱별 키워드 설정 가져오기
     */
    fun getAllAppKeywords(): Map<String, List<String>> {
        val jsonString = prefs.getString(KEY_APP_KEYWORDS, "{}") ?: "{}"
        val result = mutableMapOf<String, List<String>>()

        try {
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val packageName = keys.next()
                val keywordsArray = jsonObject.getJSONArray(packageName)
                val keywords = mutableListOf<String>()
                for (i in 0 until keywordsArray.length()) {
                    keywords.add(keywordsArray.getString(i))
                }
                result[packageName] = keywords
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }

    /**
     * 특정 앱의 키워드 가져오기
     */
    fun getAppKeywords(packageName: String): List<String> {
        return getAllAppKeywords()[packageName] ?: emptyList()
    }

    /**
     * 특정 앱의 키워드 저장
     */
    fun saveAppKeywords(packageName: String, keywords: List<String>) {
        val allKeywords = getAllAppKeywords().toMutableMap()

        if (keywords.isEmpty()) {
            allKeywords.remove(packageName)
        } else {
            allKeywords[packageName] = keywords.filter { it.isNotBlank() }
        }

        val jsonObject = JSONObject()
        for ((pkg, kwList) in allKeywords) {
            jsonObject.put(pkg, JSONArray(kwList))
        }

        prefs.edit().putString(KEY_APP_KEYWORDS, jsonObject.toString()).apply()
    }

    /**
     * 특정 앱에 키워드 추가
     */
    fun addAppKeyword(packageName: String, keyword: String) {
        if (keyword.isBlank()) return
        val currentKeywords = getAppKeywords(packageName).toMutableList()
        if (!currentKeywords.contains(keyword.trim())) {
            currentKeywords.add(keyword.trim())
            saveAppKeywords(packageName, currentKeywords)
        }
    }

    /**
     * 특정 앱에서 키워드 삭제
     */
    fun removeAppKeyword(packageName: String, keyword: String) {
        val currentKeywords = getAppKeywords(packageName).toMutableList()
        currentKeywords.remove(keyword)
        saveAppKeywords(packageName, currentKeywords)
    }

    /**
     * 특정 앱의 모든 키워드 삭제
     */
    fun clearAppKeywords(packageName: String) {
        saveAppKeywords(packageName, emptyList())
    }

    /**
     * 설정된 앱별 키워드가 있는 앱 목록
     */
    fun getAppsWithKeywords(): List<AppKeywordConfig> {
        val allKeywords = getAllAppKeywords()
        return allKeywords.map { (packageName, keywords) ->
            AppKeywordConfig(
                packageName = packageName,
                appName = AppUtils.getAppName(context, packageName),
                keywords = keywords
            )
        }.filter { it.keywords.isNotEmpty() }
    }

    // ===== 키워드 매칭 (통합 + 앱별) =====

    /**
     * 특정 앱의 알림 내용에서 키워드 매칭 확인
     * @return 매칭된 키워드 (없으면 null)
     */
    fun findMatchingKeyword(packageName: String, content: String): String? {
        val lowerContent = content.lowercase()

        // 1. 글로벌 키워드 확인
        for (keyword in getGlobalKeywords()) {
            if (keyword.isNotBlank() && lowerContent.contains(keyword.lowercase())) {
                return keyword
            }
        }

        // 2. 앱별 키워드 확인
        for (keyword in getAppKeywords(packageName)) {
            if (keyword.isNotBlank() && lowerContent.contains(keyword.lowercase())) {
                return keyword
            }
        }

        return null
    }

    // ===== 레거시 호환 (기존 메서드 유지) =====

    @Deprecated("Use getGlobalKeywords() instead", ReplaceWith("getGlobalKeywords()"))
    fun getKeywords(): List<String> = getGlobalKeywords()

    @Deprecated("Use addGlobalKeyword() instead", ReplaceWith("addGlobalKeyword(keyword)"))
    fun addKeyword(keyword: String) = addGlobalKeyword(keyword)

    @Deprecated("Use removeGlobalKeyword() instead", ReplaceWith("removeGlobalKeyword(keyword)"))
    fun removeKeyword(keyword: String) = removeGlobalKeyword(keyword)

    // ===== 서비스 상태 =====

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    // ===== 진동 설정 =====

    fun isVibrationEnabled(): Boolean {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    // ===== 소리 설정 =====

    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    // ===== 볼륨 설정 (0 ~ 100) =====

    fun getVolumeLevel(): Int {
        return prefs.getInt(KEY_VOLUME_LEVEL, 70)
    }

    fun setVolumeLevel(level: Int) {
        prefs.edit().putInt(KEY_VOLUME_LEVEL, level.coerceIn(0, 100)).apply()
    }

    // ===== 커스텀 알람음 =====

    fun getCustomSoundUri(): String? {
        return prefs.getString(KEY_CUSTOM_SOUND_URI, null)
    }

    fun setCustomSoundUri(uri: String?) {
        prefs.edit().putString(KEY_CUSTOM_SOUND_URI, uri).apply()
    }

    fun clearCustomSoundUri() {
        prefs.edit().remove(KEY_CUSTOM_SOUND_URI).apply()
    }
}
