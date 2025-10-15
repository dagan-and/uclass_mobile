package com.ubase.uclass.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 권한 관리를 위한 헬퍼 클래스
 */
object PermissionHelper {

    private const val PERMISSION_REQUEST_SHOWN_KEY = "permission_request_shown"

    /**
     * 필요한 권한들을 반환
     */
    fun getRequiredPermissions(): Array<String> {
        return mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            //add(Manifest.permission.CALL_PHONE)
        }.toTypedArray()
    }

    /**
     * 모든 필수 권한이 승인되었는지 확인
     */
    fun checkPermissions(context: Context): Boolean {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 특정 권한이 승인되었는지 확인
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 거부된 권한들을 반환
     */
    fun getDeniedPermissions(context: Context): List<String> {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 권한 요청 화면을 보여줘야 하는지 확인
     * 앱을 처음 실행할 때만 보여줌
     */
    fun shouldShowPermissionRequest(context: Context): Boolean {
        return PreferenceManager.getBoolean(context, PERMISSION_REQUEST_SHOWN_KEY, false)
    }

    /**
     * 권한 요청 화면을 보여줬음을 기록
     */
    fun markPermissionRequestShown(context: Context) {
        PreferenceManager.putBoolean(context, PERMISSION_REQUEST_SHOWN_KEY, true)
    }

    /**
     * 권한 요청 기록을 초기화 (테스트용)
     */
    fun clearPermissionRequestHistory(context: Context) {
        PreferenceManager.remove(context, PERMISSION_REQUEST_SHOWN_KEY)
    }
}