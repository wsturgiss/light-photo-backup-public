package com.stan.lightphotobackup.pairing
import kotlinx.serialization.Serializable
@Serializable data class PairingSession(val pairingId:String,val pairingCode:String,val verificationUrl:String,val expiresAt:String,val pollIntervalSeconds:Int)
@Serializable data class PairingPoll(val status:String,val deviceCredential:String?=null)
@Serializable data class AccessTokenResponse(val accessToken:String,val expiresAt:String,val scope:String)
sealed interface PairingState{data object Disconnected:PairingState;data class Waiting(val session:PairingSession):PairingState;data object Connected:PairingState;data class Error(val message:String):PairingState}
