package com.stan.lightphotobackup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stan.lightphotobackup.ui.BackupScreen
import com.stan.lightphotobackup.ui.BackupViewModel
import com.stan.lightphotobackup.ui.DetailsScreen
import com.stan.lightphotobackup.ui.ImmichSetupScreen
import com.stan.lightphotobackup.ui.PhotoAccess
import com.stan.lightphotobackup.ui.PhotoBackupTheme

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { recreate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoBackupTheme {
                val vm: BackupViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var details by remember { mutableStateOf(false) }
                var immichSetup by remember { mutableStateOf(false) }
                val full = ContextCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                val limited = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                val access = if (full) PhotoAccess.FULL else if (limited) PhotoAccess.LIMITED else PhotoAccess.NONE

                LaunchedEffect(access) { if (access == PhotoAccess.FULL) vm.scan() }
                LaunchedEffect(state.connected, state.provider) {
                    if (state.provider.name == "IMMICH" && state.connected) immichSetup = false
                }
                when {
                    details -> DetailsScreen(state, { details = false }, vm)
                    immichSetup -> ImmichSetupScreen(state, { immichSetup = false }, vm)
                    else -> BackupScreen(
                        state = state,
                        access = access,
                        requestPermission = {
                            permission.launch(
                                if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                                else arrayOf(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE),
                            )
                        },
                        details = { details = true },
                        configureImmich = { immichSetup = true },
                        vm = vm,
                    )
                }
            }
        }
    }
}
