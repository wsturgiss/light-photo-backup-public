package com.stan.lightphotobackup
import android.app.Application
import com.stan.lightphotobackup.backup.*
import com.stan.lightphotobackup.database.*
import com.stan.lightphotobackup.google.*
import com.stan.lightphotobackup.media.*
import com.stan.lightphotobackup.pairing.*
import com.stan.lightphotobackup.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
class AppContainer(app:Application){val database=BackupDatabase.create(app);val settings=SettingsRepository(app);private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(2,TimeUnit.MINUTES).callTimeout(3,TimeUnit.MINUTES).build();val pairing=PairingRepository(PairingApi(http),SecureCredentialStore(app));val coordinator=BackupCoordinator(app,database,CameraMediaRepository(app),pairing,GooglePhotosUploader(app.contentResolver,http),settings)}
class PhotoBackupApplication:Application(){lateinit var container:AppContainer;private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.Default);override fun onCreate(){super.onCreate();container=AppContainer(this);BackupScheduler.cancelLegacyManual(this);appScope.launch{val prefs=container.settings.flow.first();BackupScheduler.periodic(this@PhotoBackupApplication,prefs.cellular,prefs.periodic,prefs.periodicMinutes)}}}
