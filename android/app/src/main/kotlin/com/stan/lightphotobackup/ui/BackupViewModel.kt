package com.stan.lightphotobackup.ui
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.work.*
import com.stan.lightphotobackup.PhotoBackupApplication
import com.stan.lightphotobackup.backup.BackupScheduler
import com.stan.lightphotobackup.backup.BackupProvider
import com.stan.lightphotobackup.database.*
import com.stan.lightphotobackup.pairing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ManualStatus { IDLE, STARTING, WAITING_FOR_NETWORK, RUNNING, SUCCEEDED, FAILED, AUTHORIZATION_EXPIRED }
fun canStartManual(status:ManualStatus,cellularOverride:Boolean)=status !in setOf(ManualStatus.STARTING,ManualStatus.WAITING_FOR_NETWORK,ManualStatus.RUNNING)||cellularOverride
fun manualStatusFor(state:WorkInfo.State,category:String?=null)=when(state){WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED->ManualStatus.WAITING_FOR_NETWORK;WorkInfo.State.RUNNING->ManualStatus.RUNNING;WorkInfo.State.SUCCEEDED->ManualStatus.SUCCEEDED;WorkInfo.State.FAILED->if(category=="authorization_expired")ManualStatus.AUTHORIZATION_EXPIRED else ManualStatus.FAILED;WorkInfo.State.CANCELLED->ManualStatus.FAILED}
data class BackupUiState(val configured:Boolean=false,val connected:Boolean=false,val pairing:PairingSession?=null,val pairingError:String?=null,val summary:BackupSummary=BackupSummary(),val counts:Map<BackupStatus,Int> = emptyMap(),val manualStatus:ManualStatus=ManualStatus.IDLE,val cellular:Boolean=false,val periodic:Boolean=true,val periodicMinutes:Int=30,val provider:BackupProvider=BackupProvider.GOOGLE_PHOTOS,val providerLocked:Boolean=false){val running get()=manualStatus in setOf(ManualStatus.STARTING,ManualStatus.WAITING_FOR_NETWORK,ManualStatus.RUNNING)}
private data class TransientState(val pairing:PairingSession?=null,val error:String?=null,val manualStatus:ManualStatus=ManualStatus.IDLE)

class BackupViewModel(app:Application):AndroidViewModel(app){
 private val c=(app as PhotoBackupApplication).container;private val transient=MutableStateFlow(TransientState());private var workObserver:Job?=null
 val state:StateFlow<BackupUiState> = combine(c.database.summary().observe(),c.database.records().counts(),c.settings.flow,transient){summary,counts,prefs,t->val connected=when(prefs.provider){BackupProvider.GOOGLE_PHOTOS->c.pairing.connected();BackupProvider.IMMICH->c.immich.connected()};BackupUiState(c.pairing.configured,connected,t.pairing,t.error,summary?:BackupSummary(),counts.associate{it.status to it.count},t.manualStatus,prefs.cellular,prefs.periodic,prefs.periodicMinutes,prefs.provider,prefs.providerLocked)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),BackupUiState(configured=c.pairing.configured,connected=c.pairing.connected()))
 fun scan(){viewModelScope.launch{runCatching{c.coordinator.discover()}.onFailure{Log.e("PhotoBackup","scan failed",it)}}}
 fun pair(){if(!c.pairing.configured)return;viewModelScope.launch{runCatching{c.pairing.start()}.onSuccess{session->transient.value=transient.value.copy(pairing=session,error=null);poll(session)}.onFailure{transient.value=transient.value.copy(pairing=null,error="Server unavailable")}}}
 private suspend fun poll(session:PairingSession){while(currentCoroutineContext().isActive&&transient.value.pairing!=null){delay(session.pollIntervalSeconds*1000L);runCatching{c.pairing.poll(session.pairingId)}.onSuccess{when(it.status){"connected"->{c.settings.lockProvider();transient.value=transient.value.copy(pairing=null,error=null)};"expired","denied"->transient.value=transient.value.copy(pairing=null,error="Pairing ${it.status}")}}}}
 fun cancelPairing(){transient.value=transient.value.copy(pairing=null,error=null)}
 fun backUpNow(cellularOnce:Boolean=false){if(!canStartManual(state.value.manualStatus,cellularOnce)){Log.i("PhotoBackup","backup button ignored because manual work is active");return};Log.i("PhotoBackup","backup button tapped");transient.value=transient.value.copy(manualStatus=ManualStatus.STARTING,error=null);viewModelScope.launch{try{c.settings.armAutomatic();val useCellular=state.value.cellular||cellularOnce;val id=BackupScheduler.now(getApplication(),useCellular);Log.i("PhotoBackupWorker","manual backup requested id=$id eligible=${c.database.records().pendingCount()}");observeWork(id)}catch(e:Exception){Log.e("PhotoBackupWorker","manual enqueue failed",e);transient.value=transient.value.copy(manualStatus=ManualStatus.FAILED)}}}
 fun backup(cellularOnce:Boolean=false)=backUpNow(cellularOnce)
 private fun observeWork(id:java.util.UUID){workObserver?.cancel();workObserver=viewModelScope.launch{WorkManager.getInstance(getApplication()).getWorkInfoByIdFlow(id).filterNotNull().collect{info->val status=manualStatusFor(info.state,info.outputData.getString("category"));Log.i("PhotoBackup","UI observed WorkInfo state=${info.state}");transient.value=transient.value.copy(manualStatus=status)}}}
 fun stop(){BackupScheduler.stopManual(getApplication());transient.value=transient.value.copy(manualStatus=ManualStatus.IDLE)}
 fun cellular(value:Boolean){viewModelScope.launch{c.settings.cellular(value);BackupScheduler.periodic(getApplication(),value,state.value.periodic,state.value.periodicMinutes)}}
 fun periodic(value:Boolean){viewModelScope.launch{c.settings.periodic(value);BackupScheduler.periodic(getApplication(),state.value.cellular,value,state.value.periodicMinutes)}}
 fun periodicMinutes(value:Int){viewModelScope.launch{c.settings.periodicMinutes(value);BackupScheduler.periodic(getApplication(),state.value.cellular,state.value.periodic,value)}}
 fun provider(value:BackupProvider){viewModelScope.launch{if(!c.pairing.connected()&&!c.immich.connected())c.settings.unlockProvider();c.settings.provider(value);Log.i("PhotoBackupImmich","provider selected=$value")}}
 fun configureImmich(baseUrl:String,apiKey:String,allowHttp:Boolean){viewModelScope.launch{Log.i("PhotoBackupImmich","validating server");runCatching{c.immich.configure(baseUrl,apiKey,allowHttp)}.onSuccess{c.settings.lockProvider();transient.value=transient.value.copy(error=null);Log.i("PhotoBackupImmich","server validation succeeded")}.onFailure{transient.value=transient.value.copy(error=it.message?:"Unable to connect to Immich");Log.w("PhotoBackupImmich","server validation failed type=${it.javaClass.simpleName}")}}}
 fun requeueImmich(){if(state.value.provider!=BackupProvider.IMMICH||state.value.running)return;viewModelScope.launch{val count=c.database.records().requeueAll(System.currentTimeMillis());Log.i("PhotoBackupImmich","requeued local photos count=$count")}}
 fun retry(){viewModelScope.launch{c.database.records().retryFailed();backUpNow()}}
 fun disconnect(){viewModelScope.launch{when(state.value.provider){BackupProvider.GOOGLE_PHOTOS->c.pairing.disconnect();BackupProvider.IMMICH->c.immich.disconnect()};c.settings.unlockProvider();transient.value=TransientState()}}
}
