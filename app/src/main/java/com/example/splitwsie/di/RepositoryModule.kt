package com.example.splitwsie.di

import com.example.splitwsie.data.repository.ExpenseRepositoryImpl
import com.example.splitwsie.data.repository.GroupRepositoryImpl
import com.example.splitwsie.data.repository.SettlementRepositoryImpl
import com.example.splitwsie.data.repository.UserRepositoryImpl
import com.example.splitwsie.domain.repository.ExpenseRepository
import com.example.splitwsie.domain.repository.GroupRepository
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.repository.FriendRepository
import com.example.splitwsie.data.repository.FriendRepositoryImpl
import com.example.splitwsie.domain.repository.AuthRepository
import com.example.splitwsie.data.repository.FirebaseAuthRepositoryImpl
import com.example.splitwsie.domain.repository.FileRepository
import com.example.splitwsie.data.repository.FileRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindSettlementRepository(impl: SettlementRepositoryImpl): SettlementRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository
}
