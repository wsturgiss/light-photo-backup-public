package com.stan.lightphotobackup.database
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface BackupRecordDao {
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(records:List<BackupRecord>):List<Long>
 @Query("SELECT * FROM backup_records WHERE (status IN ('DISCOVERED','PENDING','RETRYABLE_FAILURE','AWAITING_MEDIA_CREATION')) AND (nextRetryMillis IS NULL OR nextRetryMillis<=:now) ORDER BY COALESCE(dateTakenMillis,createdAtMillis),id LIMIT 1") suspend fun next(now:Long):BackupRecord?
 @Query("SELECT EXISTS(SELECT 1 FROM backup_records WHERE contentFingerprint=:fingerprint AND status='UPLOADED')") suspend fun uploadedFingerprint(fingerprint:String):Boolean
 @Query("UPDATE backup_records SET status=:status, contentFingerprint=COALESCE(:fingerprint,contentFingerprint), uploadToken=:token, uploadTokenCreatedAtMillis=:tokenAt, googleMediaItemId=:googleId, uploadedAtMillis=:uploadedAt, attemptCount=attemptCount+:attemptDelta,lastAttemptMillis=:attemptAt,nextRetryMillis=:retryAt,lastErrorCode=:errorCode,lastErrorMessage=:errorMessage,updatedAtMillis=:now WHERE id=:id") suspend fun updateState(id:Long,status:BackupStatus,fingerprint:String?=null,token:String?=null,tokenAt:Long?=null,googleId:String?=null,uploadedAt:Long?=null,attemptDelta:Int=0,attemptAt:Long?=null,retryAt:Long?=null,errorCode:String?=null,errorMessage:String?=null,now:Long)
 @Query("UPDATE backup_records SET status='MISSING_LOCAL_FILE',updatedAtMillis=:now WHERE contentUri NOT IN (:uris) AND status!='UPLOADED'") suspend fun markMissing(uris:List<String>,now:Long)
 @Query("SELECT status,COUNT(*) count FROM backup_records GROUP BY status") fun counts():Flow<List<StatusCount>>
 @Query("SELECT COUNT(*) FROM backup_records WHERE status='UPLOADED'") suspend fun uploadedCount():Int
 @Query("SELECT COUNT(*) FROM backup_records WHERE status IN ('DISCOVERED','PENDING','RETRYABLE_FAILURE','AWAITING_MEDIA_CREATION')") suspend fun pendingCount():Int
  @Query("UPDATE backup_records SET status='PENDING',nextRetryMillis=NULL WHERE status IN ('RETRYABLE_FAILURE','PERMANENT_FAILURE')") suspend fun retryFailed()
  @Query("UPDATE backup_records SET status='PENDING',uploadToken=NULL,uploadTokenCreatedAtMillis=NULL,nextRetryMillis=NULL,lastErrorCode=NULL,lastErrorMessage=NULL,updatedAtMillis=:now WHERE status!='MISSING_LOCAL_FILE'") suspend fun requeueAll(now:Long):Int
  @Query("UPDATE backup_records SET status='PENDING',lastErrorCode='interrupted',lastErrorMessage='Previous upload was interrupted',updatedAtMillis=:now WHERE status='UPLOADING_BYTES'") suspend fun recoverInterrupted(now:Long):Int
 @Query("UPDATE backup_records SET sizeBytes=:size,updatedAtMillis=:now WHERE mediaStoreVolume=:volume AND mediaStoreId=:mediaId AND sizeBytes<=0 AND :size>0") suspend fun repairSize(volume:String,mediaId:Long,size:Long,now:Long):Int
}
data class StatusCount(val status:BackupStatus,val count:Int)
@Dao interface SummaryDao { @Query("SELECT * FROM backup_summary WHERE id=1") fun observe():Flow<BackupSummary?>; @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:BackupSummary) }
