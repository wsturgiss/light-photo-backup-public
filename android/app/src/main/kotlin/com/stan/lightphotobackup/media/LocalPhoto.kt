package com.stan.lightphotobackup.media
import android.net.Uri
data class LocalPhoto(val id:Long,val volume:String,val uri:Uri,val name:String?,val relativePath:String?,val mimeType:String,val size:Long,val dateTaken:Long?,val dateAdded:Long?,val dateModified:Long?,val width:Int?,val height:Int?)
