package com.stan.lightphotobackup.media
import android.content.ContentResolver
import android.net.Uri
import java.security.MessageDigest
object PhotoFingerprint { const val CHUNK=256*1024; fun calculate(resolver:ContentResolver,uri:Uri,size:Long,mime:String):String{val d=MessageDigest.getInstance("SHA-256");d.update(size.toString().toByteArray());d.update(0);d.update(mime.lowercase().toByteArray());resolver.openFileDescriptor(uri,"r")?.use{pfd->java.io.FileInputStream(pfd.fileDescriptor).use{input->val buffer=ByteArray(CHUNK);val first=input.read(buffer);if(first>0)d.update(buffer,0,first);if(size>CHUNK){runCatching{input.channel.position((size-CHUNK).coerceAtLeast(0));var n:Int;while(input.read(buffer).also{n=it}>0)d.update(buffer,0,n)}}}}?:throw java.io.FileNotFoundException(uri.toString());return d.digest().joinToString(""){"%02x".format(it)}} }
