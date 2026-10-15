package com.example.splitwsie.di

import com.example.splitwsie.BuildConfig
import com.example.splitwsie.domain.payment.DeviceReportedVerifier
import com.example.splitwsie.domain.payment.PaymentVerifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {

    /**
     * Which verifier confirms a payment actually happened.
     *
     * With no payment backend configured this is [DeviceReportedVerifier], which trusts the
     * response the UPI app returned and records that fact on every settlement it confirms.
     * To move to real server-side verification, bind an implementation that calls the backend
     * described in PAYMENTS.md here -- no other code needs to change, because
     * SettlementEngine only ever talks to this interface.
     */
    @Provides
    @Singleton
    fun providePaymentVerifier(): PaymentVerifier = DeviceReportedVerifier()

    /**
     * Sandbox payments simulate the payment rail without moving money. Debug builds only:
     * shipping a "pay" button that silently does nothing would be worse than having none.
     */
    @Provides
    @Singleton
    @SandboxPaymentsEnabled
    fun provideSandboxEnabled(): Boolean = BuildConfig.DEBUG
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SandboxPaymentsEnabled
