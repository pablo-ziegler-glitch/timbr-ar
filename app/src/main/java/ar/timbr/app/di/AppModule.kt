package ar.timbr.app.di

import ar.timbr.app.data.repository.FirebaseAuthRepository
import ar.timbr.app.data.repository.FirestoreDoorbellRepository
import ar.timbr.app.domain.repository.AuthRepository
import ar.timbr.app.domain.repository.DoorbellRepository
import ar.timbr.app.domain.usecase.AuthUseCases
import ar.timbr.app.domain.usecase.DoorbellUseCases
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
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
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideAuthRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
    ): AuthRepository = FirebaseAuthRepository(auth, firestore)

    @Provides
    @Singleton
    fun provideDoorbellRepository(
        firestore: FirebaseFirestore,
    ): DoorbellRepository = FirestoreDoorbellRepository(firestore)

    @Provides
    @Singleton
    fun provideAuthUseCases(repository: AuthRepository): AuthUseCases = AuthUseCases(repository)

    @Provides
    @Singleton
    fun provideDoorbellUseCases(repository: DoorbellRepository): DoorbellUseCases =
        DoorbellUseCases(repository)
}
