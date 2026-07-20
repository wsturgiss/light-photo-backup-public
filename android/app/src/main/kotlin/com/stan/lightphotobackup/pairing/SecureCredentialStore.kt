package com.stan.lightphotobackup.pairing
import android.content.Context
import android.security.keystore.*
import android.util.Base64
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
class SecureCredentialStore(context:Context){private val prefs=context.getSharedPreferences("secure_credentials",Context.MODE_PRIVATE);private val alias="photo_backup_device_key";private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(ks.getKey(alias,null) as? SecretKey)?.let{return it};return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build())}.generateKey()};fun save(value:String){val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())};prefs.edit().putString("credential",Base64.encodeToString(c.iv+c.doFinal(value.toByteArray()),Base64.NO_WRAP)).apply()};fun get():String?=try{prefs.getString("credential",null)?.let{val b=Base64.decode(it,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)))};String(c.doFinal(b.copyOfRange(12,b.size)))}}catch(_:Exception){clear();null};fun clear(){prefs.edit().clear().apply()}}
