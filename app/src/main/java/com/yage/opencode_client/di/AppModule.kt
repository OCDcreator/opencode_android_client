package com.yage.opencode_client.di

import com.yage.opencode_client.data.repository.OpenCodeRepository
import com.yage.opencode_client.ssh.JschTunnelManager
import com.yage.opencode_client.ssh.TunnelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOpenCodeRepository(): OpenCodeRepository = OpenCodeRepository()

    @Provides
    @Singleton
    fun provideTunnelManager(manager: JschTunnelManager): TunnelManager = manager
}
