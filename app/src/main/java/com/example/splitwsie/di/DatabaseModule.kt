package com.example.splitwsie.di

import android.content.Context
import androidx.room.Room
import com.example.splitwsie.data.local.SettleDatabase
import com.example.splitwsie.data.local.dao.ExpenseDao
import com.example.splitwsie.data.local.dao.GroupDao
import com.example.splitwsie.data.local.dao.SettlementDao
import com.example.splitwsie.data.local.dao.UserDao
import com.example.splitwsie.data.local.dao.FriendDao
import com.example.splitwsie.data.local.dao.CommentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSettleDatabase(@ApplicationContext context: Context): SettleDatabase {
        return Room.databaseBuilder(
            context,
            SettleDatabase::class.java,
            SettleDatabase.DATABASE_NAME
        )
            .addMigrations(*SettleDatabase.ALL_MIGRATIONS)
            // Deliberately NOT fallbackToDestructiveMigration: this database holds the only
            // record of who owes whom and which payments were made. A missing migration must
            // fail loudly in testing rather than silently wipe financial history on a user's
            // device.
            .build()
    }

    @Provides
    @Singleton
    fun provideGroupDao(db: SettleDatabase): GroupDao = db.groupDao()

    @Provides
    @Singleton
    fun provideExpenseDao(db: SettleDatabase): ExpenseDao = db.expenseDao()

    @Provides
    @Singleton
    fun provideUserDao(db: SettleDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun provideSettlementDao(db: SettleDatabase): SettlementDao = db.settlementDao()

    @Provides
    @Singleton
    fun provideFriendDao(db: SettleDatabase): FriendDao = db.friendDao()

    @Provides
    @Singleton
    fun provideCommentDao(db: SettleDatabase): CommentDao = db.commentDao()

}
