package com.stan.lightphotobackup.google
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.stan.lightphotobackup.backup.BackupUploader
import com.stan.lightphotobackup.backup.BackupCredential
import com.stan.lightphotobackup.database.BackupRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

@Serializable data class CreateRequest(val newMediaItems:List<NewItem>)
@Serializable data class NewItem(val description:String="Backed up from Light Phone",val simpleMediaItem:SimpleItem)
@Serializable data class SimpleItem(val uploadToken:String,val fileName:String)
@Serializable data class CreateResponse(val newMediaItemResults:List<ItemResult> = emptyList())
@Serializable data class ItemResult(val uploadToken:String?=null,val status:ApiStatus?=null,val mediaItem:MediaItem?=null)
@Serializable data class ApiStatus(val code:Int?=null,val message:String?=null)
@Serializable data class MediaItem(val id:String?=null)
class UploadException(val code:String,override val message:String,val retryable:Boolean,val retryAfterMillis:Long?=null):IOException(message)
fun uploadContentLength(size:Long)=if(size>0)size else -1L

class GooglePhotosUploader(private val resolver:ContentResolver,private val client:OkHttpClient): BackupUploader {
 private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
  override fun uploadBytes(credential:BackupCredential,item:BackupRecord):String {
   val token=(credential as? BackupCredential.Google)?.accessToken?:error("Google uploader requires a Google credential")
   val uri=Uri.parse(item.contentUri);val mime=item.mimeType;val size=item.sizeBytes
  val body=object:RequestBody(){override fun contentType()="application/octet-stream".toMediaType();override fun contentLength()=uploadContentLength(size);override fun writeTo(sink:BufferedSink){resolver.openInputStream(uri)?.use{Log.i("PhotoBackupUpload","photo stream opened");sink.writeAll(it.source())}?:throw java.io.FileNotFoundException("Local photo unavailable")}}
  val request=Request.Builder().url("https://photoslibrary.googleapis.com/v1/uploads").header("Authorization","Bearer $token").header("X-Goog-Upload-Content-Type",mime).header("X-Goog-Upload-Protocol","raw").post(body).build()
  return client.newCall(request).execute().use{Log.i("PhotoBackupUpload","byte upload HTTP status=${it.code}");if(!it.isSuccessful)throw classify(it.code,it.header("Retry-After"));it.body?.string()?.trim().takeUnless(String?::isNullOrEmpty)?.also{Log.i("PhotoBackupUpload","upload token received")}?:throw UploadException("malformed_response","Empty upload token",true)}
 }
  override fun create(credential:BackupCredential,uploadToken:String,name:String):String? {
   val token=(credential as? BackupCredential.Google)?.accessToken?:error("Google uploader requires a Google credential")
  val payload=json.encodeToString(CreateRequest.serializer(),CreateRequest(listOf(NewItem(simpleMediaItem=SimpleItem(uploadToken,name)))))
  val request=Request.Builder().url("https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate").header("Authorization","Bearer $token").post(payload.toRequestBody("application/json".toMediaType())).build()
  return client.newCall(request).execute().use{Log.i("PhotoBackupUpload","media creation HTTP status=${it.code}");if(!it.isSuccessful)throw classify(it.code,it.header("Retry-After"));val result=json.decodeFromString<CreateResponse>(it.body?.string().orEmpty()).newMediaItemResults.firstOrNull()?:throw UploadException("malformed_response","Missing creation result",true);if((result.status?.code?:0)!=0)throw UploadException("google_${result.status?.code}",result.status?.message?:"Media creation failed",(result.status?.code?:0) in setOf(408,429,500,502,503,504));result.mediaItem?.id}
 }
 companion object{fun classify(code:Int,retry:String?=null)=UploadException("http_$code","Google Photos returned HTTP $code",code==408||code==429||code>=500,retry?.toLongOrNull()?.times(1000))}
}
