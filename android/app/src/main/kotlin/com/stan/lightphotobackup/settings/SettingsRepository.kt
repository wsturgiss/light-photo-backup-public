package com.stan.lightphotobackup.settings
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
private val Context.dataStore by preferencesDataStore("backup_preferences")
val PERIODIC_FREQUENCY_MINUTES = listOf(15,30,60,360,1440)
fun validPeriodicMinutes(value:Int?)=value?.takeIf{it in PERIODIC_FREQUENCY_MINUTES}?:30
data class BackupPreferences(val cellular:Boolean=false,val periodic:Boolean=true,val lastSuccess:Long?=null,val automaticArmed:Boolean=false,val periodicMinutes:Int=30)
class SettingsRepository(private val context:Context){private val cellular=booleanPreferencesKey("cellular");private val periodic=booleanPreferencesKey("periodic");private val success=longPreferencesKey("last_success");private val armed=booleanPreferencesKey("automatic_armed");private val frequency=intPreferencesKey("periodic_minutes");val flow=context.dataStore.data.map{BackupPreferences(cellular=it[cellular]?:false,periodic=it[periodic]?:true,lastSuccess=it[success],automaticArmed=it[armed]?:false,periodicMinutes=validPeriodicMinutes(it[frequency]))};suspend fun cellular(v:Boolean){context.dataStore.edit{it[cellular]=v}};suspend fun periodic(v:Boolean){context.dataStore.edit{it[periodic]=v}};suspend fun periodicMinutes(v:Int){require(v in PERIODIC_FREQUENCY_MINUTES);context.dataStore.edit{it[frequency]=v}};suspend fun success(v:Long){context.dataStore.edit{it[success]=v}};suspend fun armAutomatic(){context.dataStore.edit{it[armed]=true}}}
