package com.stan.lightphotobackup

import com.stan.lightphotobackup.database.BackupStatus
import com.stan.lightphotobackup.google.GooglePhotosUploader
import com.stan.lightphotobackup.google.uploadContentLength
import com.stan.lightphotobackup.media.*
import com.stan.lightphotobackup.ui.formatDate
import com.stan.lightphotobackup.ui.*
import com.stan.lightphotobackup.backup.BackupScheduler
import com.stan.lightphotobackup.backup.BackupProvider
import com.stan.lightphotobackup.immich.ImmichRepository
import com.stan.lightphotobackup.immich.ImmichApi
import com.stan.lightphotobackup.settings.PERIODIC_FREQUENCY_MINUTES
import com.stan.lightphotobackup.settings.validPeriodicMinutes
import androidx.work.*
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
 @Test fun lightPathsAccepted(){listOf("Pictures/Light/","Pictures/Light","pictures/light/","PICTURES/LIGHT/","/Pictures//Light/Nested/","Pictures\\Light\\Edited\\").forEach{assertEquals(it,PhotoFolder.LIGHT,MediaPolicy.folder(it))}}
 @Test fun screenshotPathsAccepted(){listOf("Pictures/Screenshots/","Pictures/Screenshots","pictures/screenshots/","PICTURES/SCREENSHOTS/","/Pictures//Screenshots/Nested/").forEach{assertEquals(it,PhotoFolder.SCREENSHOTS,MediaPolicy.folder(it))}}
 @Test fun unrelatedPathsRejected(){listOf("Pictures/","Pictures/Other/","Download/","DCIM/","Pictures/WhatsApp/","Pictures/Beeper/").forEach{assertNull(it,MediaPolicy.folder(it))}}
 @Test fun mimeTypesAreNarrow(){assertTrue("image/heic" in MediaPolicy.supported);assertFalse("image/gif" in MediaPolicy.supported);assertFalse("video/mp4" in MediaPolicy.supported)}
 @Test fun nullDateTakenFallsBackToDateAdded(){assertEquals(123_000L,SettlingPolicy.referenceTimeMillis(null,123));assertEquals(456L,SettlingPolicy.referenceTimeMillis(456,123))}
 @Test fun stableExistingAndRecentSettling(){val now=1_000_000L;assertTrue(SettlingPolicy.isStable(now,null,1,1,30_000));assertFalse(SettlingPolicy.isStable(now,null,990,990,30_000))}
 @Test fun volumesAndIdentityDeduplicate(){val photos=listOf(item(1,"external_primary","Pictures/Light/"),item(1,"sdcard","Pictures/Light/"),item(1,"external_primary","Pictures/Light/"));assertEquals(2,DiscoveryAccounting.deduplicateItems(photos).size);assertEquals(1,DiscoveryAccounting.newItemCount(photos,setOf("external_primary" to 1L)))}
 @Test fun existingRecordsAndCountsAggregate(){val photos=listOf(item(1,"v","Pictures/Light/"),item(2,"v","Pictures/Screenshots/"),item(2,"v","Pictures/Screenshots/"));assertEquals(FolderCounts(1,1),DiscoveryAccounting.itemCounts(photos));assertEquals(0,DiscoveryAccounting.newItemCount(photos,setOf("v" to 1L,"v" to 2L)))}
 @Test fun retryClassification(){assertTrue(GooglePhotosUploader.classify(429).retryable);assertTrue(GooglePhotosUploader.classify(503).retryable);assertFalse(GooglePhotosUploader.classify(403).retryable)}
 @Test fun unreachableImmichServerIsRetryable(){val error=ImmichApi.unreachable();assertEquals("immich_unreachable",error.code);assertTrue(error.retryable)}
 @Test fun zeroMediaStoreSizeUsesStreamingLength(){assertEquals(-1L,uploadContentLength(0));assertEquals(-1L,uploadContentLength(-1));assertEquals(123L,uploadContentLength(123))}
 @Test fun stateAndDateUtilities(){assertTrue(BackupStatus.entries.contains(BackupStatus.AWAITING_MEDIA_CREATION));assertTrue(formatDate(0).isNotBlank())}
 @Test fun manualWorkReplacesStaleRequests(){assertEquals(ExistingWorkPolicy.REPLACE,BackupScheduler.manualPolicy);assertEquals(NetworkType.UNMETERED,BackupScheduler.manualNetworkType(false));assertEquals(NetworkType.CONNECTED,BackupScheduler.manualNetworkType(true))}
 @Test fun workInfoReachesVisibleUiStates(){assertEquals(ManualStatus.WAITING_FOR_NETWORK,manualStatusFor(WorkInfo.State.ENQUEUED));assertEquals(ManualStatus.RUNNING,manualStatusFor(WorkInfo.State.RUNNING));assertEquals(ManualStatus.SUCCEEDED,manualStatusFor(WorkInfo.State.SUCCEEDED));assertEquals(ManualStatus.AUTHORIZATION_EXPIRED,manualStatusFor(WorkInfo.State.FAILED,"authorization_expired"));assertEquals(ManualStatus.FAILED,manualStatusFor(WorkInfo.State.FAILED))}
 @Test fun repeatedTapsAreBoundedAndButtonReenables(){assertFalse(canStartManual(ManualStatus.RUNNING,false));assertFalse(canStartManual(ManualStatus.WAITING_FOR_NETWORK,false));assertTrue(canStartManual(ManualStatus.WAITING_FOR_NETWORK,true));assertTrue(canStartManual(ManualStatus.SUCCEEDED,false));assertTrue(canStartManual(ManualStatus.FAILED,false))}
 @Test fun periodicFrequenciesAreLegalAndDefaultToThirtyMinutes(){assertEquals(listOf(15,30,60,360,1440),PERIODIC_FREQUENCY_MINUTES);assertTrue(PERIODIC_FREQUENCY_MINUTES.all{it>=15});assertEquals(30,validPeriodicMinutes(null));assertEquals(30,validPeriodicMinutes(7));assertEquals(360,validPeriodicMinutes(360))}
 @Test fun providerDefaultsToGooglePhotosForNewOrUnknownPreferences(){assertEquals(BackupProvider.GOOGLE_PHOTOS,BackupProvider.fromStoredValue(null));assertEquals(BackupProvider.GOOGLE_PHOTOS,BackupProvider.fromStoredValue("removed_provider"));assertEquals(BackupProvider.GOOGLE_PHOTOS,BackupProvider.fromStoredValue("GOOGLE_PHOTOS"));assertEquals(listOf(BackupProvider.GOOGLE_PHOTOS,BackupProvider.IMMICH),BackupProvider.available)}
  @Test fun immichUrlAddsHttpsAndRemovesTrailingSlash(){assertEquals("https://photos.example.com",ImmichRepository.normalizeUrl(" photos.example.com/ "));assertEquals("https://photos.example.com",ImmichRepository.normalizeUrl(" https://photos.example.com/ "));assertThrows(IllegalArgumentException::class.java){ImmichRepository.normalizeUrl("http://photos.example.com")};assertEquals("http://192.168.200.124:8081",ImmichRepository.normalizeUrl(" 192.168.200.124:8081/ ",true));assertEquals("http://192.168.200.124:8081",ImmichRepository.normalizeUrl(" http://192.168.200.124:8081/ ",true))}
 private fun item(id:Long,volume:String,path:String)=DiscoveryItem(volume,id,path)
}
