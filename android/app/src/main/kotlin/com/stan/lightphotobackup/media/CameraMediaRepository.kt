package com.stan.lightphotobackup.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.stan.lightphotobackup.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PhotoFolder { LIGHT, SCREENSHOTS }
data class MediaScanStats(val rows:Int,val light:Int,val screenshots:Int,val rejectedPath:Int,val rejectedMime:Int)
data class MediaScanResult(val photos:List<LocalPhoto>,val stats:MediaScanStats)

object MediaPolicy {
    val supported = setOf("image/jpeg", "image/png", "image/heic", "image/heif", "image/webp")
    fun normalizePath(path:String?):String? = path?.replace('\\','/')
        ?.replace(Regex("/+"), "/")?.trim('/')?.lowercase()?.takeIf(String::isNotBlank)
    fun folder(path:String?):PhotoFolder? = when {
        normalizePath(path)?.let { it=="pictures/light" || it.startsWith("pictures/light/") }==true -> PhotoFolder.LIGHT
        normalizePath(path)?.let { it=="pictures/screenshots" || it.startsWith("pictures/screenshots/") }==true -> PhotoFolder.SCREENSHOTS
        else -> null
    }
    fun isCameraPath(path:String?)=folder(path)!=null
}

object SettlingPolicy {
    fun referenceTimeMillis(dateTaken:Long?,dateAddedSeconds:Long?)=dateTaken ?: dateAddedSeconds?.times(1000)
    fun isStable(nowMillis:Long,dateTaken:Long?,dateAddedSeconds:Long?,dateModifiedSeconds:Long?,settleMillis:Long):Boolean {
        val reference=maxOf(referenceTimeMillis(dateTaken,dateAddedSeconds)?:0,dateModifiedSeconds?.times(1000)?:0)
        return nowMillis-reference>=settleMillis
    }
}
data class FolderCounts(val light:Int,val screenshots:Int)
data class DiscoveryItem(val volume:String,val id:Long,val relativePath:String?)
object DiscoveryAccounting {
    fun deduplicate(photos:List<LocalPhoto>)=photos.distinctBy { it.volume to it.id }
    fun newIdentityCount(photos:List<LocalPhoto>,existing:Set<Pair<String,Long>>)=deduplicate(photos).count { (it.volume to it.id) !in existing }
    fun counts(photos:List<LocalPhoto>):FolderCounts { val unique=deduplicate(photos);return FolderCounts(unique.count { MediaPolicy.folder(it.relativePath)==PhotoFolder.LIGHT },unique.count { MediaPolicy.folder(it.relativePath)==PhotoFolder.SCREENSHOTS }) }
    fun deduplicateItems(items:List<DiscoveryItem>)=items.distinctBy { it.volume to it.id }
    fun newItemCount(items:List<DiscoveryItem>,existing:Set<Pair<String,Long>>)=deduplicateItems(items).count { (it.volume to it.id) !in existing }
    fun itemCounts(items:List<DiscoveryItem>):FolderCounts { val unique=deduplicateItems(items);return FolderCounts(unique.count { MediaPolicy.folder(it.relativePath)==PhotoFolder.LIGHT },unique.count { MediaPolicy.folder(it.relativePath)==PhotoFolder.SCREENSHOTS }) }
}

class CameraMediaRepository(private val context:Context) {
    private val resolver=context.contentResolver
    suspend fun scan():MediaScanResult = withContext(Dispatchers.IO) {
        val volumes=if(Build.VERSION.SDK_INT>=29)MediaStore.getExternalVolumeNames(context) else setOf(MediaStore.VOLUME_EXTERNAL)
        val accepted=LinkedHashMap<Pair<String,Long>,LocalPhoto>();var rows=0;var light=0;var screenshots=0;var rejectedPath=0;var rejectedMime=0
        for(volume in volumes){
            val collection=MediaStore.Images.Media.getContentUri(volume)
            resolver.query(collection,projection,null,null,"${MediaStore.Images.Media.DATE_ADDED} ASC")?.use { cursor ->
                while(cursor.moveToNext()){
                    rows++;val mapped=map(cursor,collection.toString(),volume)
                    when(mapped){is RowResult.Accepted->{val key=mapped.photo.volume to mapped.photo.id;if(accepted.putIfAbsent(key,mapped.photo)==null)when(mapped.folder){PhotoFolder.LIGHT->light++;PhotoFolder.SCREENSHOTS->screenshots++}}
                        RowResult.PathRejected->rejectedPath++;RowResult.MimeRejected->rejectedMime++}
                }
            }
        }
        val stats=MediaScanStats(rows,light,screenshots,rejectedPath,rejectedMime)
        if(BuildConfig.DEBUG)Log.i("PhotoBackupMedia","scan rows=$rows light=$light screenshots=$screenshots rejectedPath=$rejectedPath rejectedMime=$rejectedMime volumes=${volumes.size}")
        MediaScanResult(accepted.values.toList(),stats)
    }

    private val projection=arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.MIME_TYPE,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.DATE_TAKEN,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.DATE_MODIFIED,MediaStore.Images.Media.WIDTH,MediaStore.Images.Media.HEIGHT,MediaStore.Images.Media.VOLUME_NAME)
    internal fun map(c:Cursor,collection:String,fallbackVolume:String):RowResult {
        fun index(name:String)=c.getColumnIndex(name)
        fun string(name:String)=index(name).takeIf{it>=0&&!c.isNull(it)}?.let(c::getString)
        fun long(name:String)=index(name).takeIf{it>=0&&!c.isNull(it)}?.let(c::getLong)
        fun int(name:String)=index(name).takeIf{it>=0&&!c.isNull(it)}?.let(c::getInt)
        val mime=string(MediaStore.Images.Media.MIME_TYPE)?.lowercase()?:return RowResult.MimeRejected
        if(mime !in MediaPolicy.supported)return RowResult.MimeRejected
        val path=string(MediaStore.Images.Media.RELATIVE_PATH);val folder=MediaPolicy.folder(path)?:return RowResult.PathRejected
        val id=long(MediaStore.Images.Media._ID)?:return RowResult.PathRejected;val volume=string(MediaStore.Images.Media.VOLUME_NAME)?:fallbackVolume;val uri=ContentUris.withAppendedId(android.net.Uri.parse(collection),id);val reportedSize=long(MediaStore.Images.Media.SIZE)?:0;val effectiveSize=resolveSize(uri,reportedSize)
        return RowResult.Accepted(LocalPhoto(id,volume,uri,string(MediaStore.Images.Media.DISPLAY_NAME),path,mime,effectiveSize,long(MediaStore.Images.Media.DATE_TAKEN),long(MediaStore.Images.Media.DATE_ADDED),long(MediaStore.Images.Media.DATE_MODIFIED),int(MediaStore.Images.Media.WIDTH),int(MediaStore.Images.Media.HEIGHT)),folder)
    }
    private fun resolveSize(uri:android.net.Uri,reported:Long):Long {if(reported>0)return reported;return runCatching{resolver.openAssetFileDescriptor(uri,"r")?.use{descriptor->descriptor.length.takeIf{it>0}?:descriptor.parcelFileDescriptor.statSize.takeIf{it>0}}}.getOrNull()?:0}
}
sealed interface RowResult { data class Accepted(val photo:LocalPhoto,val folder:PhotoFolder):RowResult;data object PathRejected:RowResult;data object MimeRejected:RowResult }
