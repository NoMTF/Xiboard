/*
 * SPDX-FileCopyrightText: 2026 Xiboard contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xiboard.R

class VoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        } else {
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.offline_voice_permission_denied, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 31
    }
}
