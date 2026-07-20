package com.stan.lightphotobackup.backup

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.work.*
import com.stan.lightphotobackup.BuildConfig
import com.stan.lightphotobackup.PhotoBackupApplication
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit

class BackupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {
  val container=(applicationContext as PhotoBackupApplication).container
  val manual=inputData.getBoolean("manual",false)
  if(BuildConfig.DEBUG){val connected=(applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork!=null;Log.i("PhotoBackupWorker","worker started id=$id attempt=$runAttemptCount manual=$manual network=$connected credential=${if(container.pairing.connected())"present" else "absent"}")}
  if(!manual&&!container.settings.flow.first().automaticArmed)return Result.success()
  return try{val outcome=container.coordinator.run();when{outcome.authorizationExpired->{Log.w("PhotoBackupWorker","worker result=failure category=authorization_expired");Result.failure(workDataOf("category" to "authorization_expired"))};outcome.retryNeeded->{Log.w("PhotoBackupWorker","worker result=retry");Result.retry()};else->{Log.i("PhotoBackupWorker","worker result=success uploaded=${outcome.uploaded} failed=${outcome.failed}");Result.success(workDataOf("uploaded" to outcome.uploaded,"failed" to outcome.failed))}}}catch(e:java.io.IOException){Log.w("PhotoBackupWorker","worker result=retry category=network");Result.retry()}catch(e:Exception){Log.e("PhotoBackupWorker","worker result=failure category=unexpected",e);Result.failure(workDataOf("category" to "unexpected"))}
 }
}

object BackupScheduler {
 const val PERIODIC="periodic-photo-backup";const val MANUAL="photo-backup-manual";private const val LEGACY_MANUAL="manual-photo-backup";const val MANUAL_TAG="PhotoBackupManual"
 val manualPolicy=ExistingWorkPolicy.REPLACE
 fun manualNetworkType(cellular:Boolean)=if(cellular)NetworkType.CONNECTED else NetworkType.UNMETERED
 fun periodic(context:Context,cellular:Boolean,enabled:Boolean,intervalMinutes:Int=30){val wm=WorkManager.getInstance(context);if(!enabled){wm.cancelUniqueWork(PERIODIC);return};require(intervalMinutes>=15);val constraints=Constraints.Builder().setRequiredNetworkType(if(cellular)NetworkType.CONNECTED else NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build();val request=PeriodicWorkRequestBuilder<BackupWorker>(intervalMinutes.toLong(),TimeUnit.MINUTES).setConstraints(constraints).setInputData(workDataOf("manual" to false)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();wm.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,request)}
 fun now(context:Context,cellular:Boolean=false):UUID {val wm=WorkManager.getInstance(context);wm.cancelUniqueWork(LEGACY_MANUAL);val constraints=Constraints.Builder().setRequiredNetworkType(manualNetworkType(cellular)).build();val request=OneTimeWorkRequestBuilder<BackupWorker>().setInputData(workDataOf("manual" to true)).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag(MANUAL_TAG).build();wm.enqueueUniqueWork(MANUAL,manualPolicy,request);if(BuildConfig.DEBUG)Log.i("PhotoBackupWorker","unique work enqueued id=${request.id} network=${if(cellular)"connected" else "unmetered"} policy=REPLACE");return request.id}
 fun stopManual(context:Context)=WorkManager.getInstance(context).cancelUniqueWork(MANUAL)
 fun cancelLegacyManual(context:Context)=WorkManager.getInstance(context).cancelUniqueWork(LEGACY_MANUAL)
}
