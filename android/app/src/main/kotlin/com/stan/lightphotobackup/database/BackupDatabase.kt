package com.stan.lightphotobackup.database
import android.content.Context
import androidx.room.*
class Converters {@TypeConverter fun from(v:BackupStatus)=v.name;@TypeConverter fun to(v:String)=BackupStatus.valueOf(v)}
@Database(entities=[BackupRecord::class,BackupSummary::class],version=1,exportSchema=true) @TypeConverters(Converters::class)
abstract class BackupDatabase:RoomDatabase(){abstract fun records():BackupRecordDao;abstract fun summary():SummaryDao;companion object{fun create(c:Context)=Room.databaseBuilder(c,BackupDatabase::class.java,"photo-backup.sqlite").build()}}
