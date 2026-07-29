package com.stan.lightphotobackup.backup
import android.content.Context
import android.net.Uri
import android.util.Log
import com.stan.lightphotobackup.BuildConfig
import com.stan.lightphotobackup.database.*
import com.stan.lightphotobackup.google.UploadException
import com.stan.lightphotobackup.media.*
import com.stan.lightphotobackup.pairing.PairingRepository
import com.stan.lightphotobackup.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
data class BackupResult(val discovered:Int,val uploaded:Int,val failed:Int,val retryNeeded:Boolean=false,val authorizationExpired:Boolean=false)
class BackupCoordinator(private val context:Context,private val db:BackupDatabase,private val media:CameraMediaRepository,private val credential:(suspend (BackupProvider)->BackupCredential),private val uploaders:Map<BackupProvider,BackupUploader>,private val settings:SettingsRepository){
 companion object{private val mutex=Mutex();const val SETTLE_MS=30_000L}
 suspend fun discover():Int=mutex.withLock { withContext(Dispatchers.IO) { val now=System.currentTimeMillis();val scan=media.scan();val records=scan.photos.map { BackupRecord(mediaStoreId=it.id,mediaStoreVolume=it.volume,contentUri=it.uri.toString(),displayName=it.name,relativePath=it.relativePath,mimeType=it.mimeType,sizeBytes=it.size,dateTakenMillis=it.dateTaken,dateAddedSeconds=it.dateAdded,dateModifiedSeconds=it.dateModified,width=it.width,height=it.height,createdAtMillis=now,updatedAtMillis=now) };val inserted=db.records().insert(records).count { it>0 };val repaired=scan.photos.sumOf{db.records().repairSize(it.volume,it.id,it.size,now)};if(scan.photos.isNotEmpty())db.records().markMissing(scan.photos.map { it.uri.toString() },now);val pending=db.records().pendingCount();db.summary().save(BackupSummary(lastScanMillis=now,discovered=inserted,waiting=pending,operation="Idle",pathCategories="Pictures/Light, Pictures/Screenshots"));if(BuildConfig.DEBUG)Log.i("PhotoBackupMedia","database already=${records.size-inserted} inserted=$inserted repairedSizes=$repaired pending=$pending");inserted } }
 suspend fun run(): BackupResult = mutex.withLock { withContext(Dispatchers.IO) {
  val now=System.currentTimeMillis();val provider=settings.flow.first().provider;val uploader=checkNotNull(uploaders[provider]) { "Selected backup provider is unavailable" };val recovered=db.records().recoverInterrupted(now);if(BuildConfig.DEBUG&&recovered>0)Log.i("PhotoBackupWorker","recovered interrupted=$recovered"); val scan=media.scan();val photos=scan.photos
  val records=photos.map { BackupRecord(mediaStoreId=it.id,mediaStoreVolume=it.volume,contentUri=it.uri.toString(),displayName=it.name,relativePath=it.relativePath,mimeType=it.mimeType,sizeBytes=it.size,dateTakenMillis=it.dateTaken,dateAddedSeconds=it.dateAdded,dateModifiedSeconds=it.dateModified,width=it.width,height=it.height,createdAtMillis=now,updatedAtMillis=now) }
  val inserted=db.records().insert(records).count { it>0 };val alreadyInDatabase=records.size-inserted
  val repairedSizes=photos.sumOf{db.records().repairSize(it.volume,it.id,it.size,now)}
  if(photos.isNotEmpty()) db.records().markMissing(photos.map { it.uri.toString() },now)
  var uploaded=0; var failed=0;var retryNeeded=false;var authorizationExpired=false
  if(BuildConfig.DEBUG)Log.i("PhotoBackupMedia","database already=$alreadyInDatabase inserted=$inserted repairedSizes=$repairedSizes pending=${db.records().pendingCount()}")
  db.summary().save(BackupSummary(lastScanMillis=now,lastAttemptMillis=now,discovered=inserted,operation="Scanning",pathCategories=photos.mapNotNull { it.relativePath?.substringBefore('/') }.distinct().joinToString()))
  while(currentCoroutineContext().isActive) {
   val item=db.records().next(System.currentTimeMillis()) ?: break
   val reference=maxOf(SettlingPolicy.referenceTimeMillis(item.dateTakenMillis,item.dateAddedSeconds)?:0,item.dateModifiedSeconds?.times(1000)?:0);val age=System.currentTimeMillis()-reference
   if(!SettlingPolicy.isStable(System.currentTimeMillis(),item.dateTakenMillis,item.dateAddedSeconds,item.dateModifiedSeconds,SETTLE_MS)) { if(BuildConfig.DEBUG)Log.i("PhotoBackupMedia","settling rejected=1");db.records().updateState(item.id,BackupStatus.PENDING,retryAt=System.currentTimeMillis()+(SETTLE_MS-age).coerceAtLeast(1000),now=System.currentTimeMillis()); continue }
   try {
    val backupCredential=credential(provider);val fingerprint=PhotoFingerprint.calculate(context.contentResolver,Uri.parse(item.contentUri),item.sizeBytes,item.mimeType)
    if(db.records().uploadedFingerprint(fingerprint)) { db.records().updateState(item.id,BackupStatus.UPLOADED,fingerprint=fingerprint,uploadedAt=System.currentTimeMillis(),now=System.currentTimeMillis()); continue }
     val tokenFresh=item.status==BackupStatus.AWAITING_MEDIA_CREATION && item.uploadToken!=null && item.uploadTokenCreatedAtMillis?.let { System.currentTimeMillis()-it<23*60*60_000 }==true
    val uploadToken = if(tokenFresh) { item.uploadToken!! } else {
     db.records().updateState(item.id,BackupStatus.UPLOADING_BYTES,fingerprint=fingerprint,attemptDelta=1,attemptAt=System.currentTimeMillis(),now=System.currentTimeMillis())
      uploader.uploadBytes(backupCredential,item).also { db.records().updateState(item.id,BackupStatus.AWAITING_MEDIA_CREATION,fingerprint=fingerprint,token=it,tokenAt=System.currentTimeMillis(),now=System.currentTimeMillis()) }
    }
    val googleId=uploader.create(backupCredential,uploadToken,item.displayName?:"LightPhone-${item.id}")
    db.records().updateState(item.id,BackupStatus.UPLOADED,fingerprint=fingerprint,googleId=googleId,uploadedAt=System.currentTimeMillis(),now=System.currentTimeMillis()); uploaded++
   } catch(e:com.stan.lightphotobackup.pairing.AuthorizationExpiredException) { db.records().updateState(item.id,BackupStatus.RETRYABLE_FAILURE,errorCode="authorization_expired",errorMessage="Account connection expired",now=System.currentTimeMillis());failed++;authorizationExpired=true;break
   } catch(e:java.io.FileNotFoundException) { db.records().updateState(item.id,BackupStatus.MISSING_LOCAL_FILE,errorCode="local_missing",errorMessage="Local photo is no longer available",now=System.currentTimeMillis()); failed++
   } catch(e:UploadException) { val delay=e.retryAfterMillis?:((1L shl item.attemptCount.coerceAtMost(8))*30_000L).coerceAtMost(6*60*60_000L); db.records().updateState(item.id,if(e.retryable)BackupStatus.RETRYABLE_FAILURE else BackupStatus.PERMANENT_FAILURE,attemptDelta=1,attemptAt=System.currentTimeMillis(),retryAt=if(e.retryable)System.currentTimeMillis()+delay else null,errorCode=e.code,errorMessage=e.message.take(160),now=System.currentTimeMillis()); failed++;retryNeeded=e.retryable; if(e.retryable) break
   } catch(e:Exception) { if(BuildConfig.DEBUG)Log.w("PhotoBackupWorker","item failed category=temporary type=${e.javaClass.simpleName}");db.records().updateState(item.id,BackupStatus.RETRYABLE_FAILURE,attemptDelta=1,attemptAt=System.currentTimeMillis(),retryAt=System.currentTimeMillis()+60_000,errorCode="temporary",errorMessage="Temporary backup error",now=System.currentTimeMillis()); failed++;retryNeeded=true; break }
  }
  val end=System.currentTimeMillis(); if(uploaded>0)settings.success(end)
  db.summary().save(BackupSummary(lastScanMillis=now,lastAttemptMillis=end,lastSuccessMillis=if(uploaded>0)end else null,discovered=inserted,uploaded=uploaded,failed=failed,operation="Idle",lastError=if(authorizationExpired)"Account connection expired" else if(failed>0)"Backup needs attention" else null))
  BackupResult(inserted,uploaded,failed,retryNeeded,authorizationExpired)
 }}
}
