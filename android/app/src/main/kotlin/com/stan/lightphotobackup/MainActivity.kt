package com.stan.lightphotobackup
import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stan.lightphotobackup.ui.*
class MainActivity:ComponentActivity(){private val permission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){recreate()};override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{PhotoBackupTheme{val vm:BackupViewModel=viewModel();val state by vm.state.collectAsStateWithLifecycle();var details by remember{mutableStateOf(false)};val full=ContextCompat.checkSelfPermission(this,if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;val limited=Build.VERSION.SDK_INT>=34&&ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;val access=if(full)PhotoAccess.FULL else if(limited)PhotoAccess.LIMITED else PhotoAccess.NONE;LaunchedEffect(access){if(access==PhotoAccess.FULL)vm.scan()};if(details)DetailsScreen(state,{details=false},vm)else BackupScreen(state,access,{permission.launch(if(Build.VERSION.SDK_INT>=34)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else arrayOf(if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE))},{details=true},vm)}}}}
