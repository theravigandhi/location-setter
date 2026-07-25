package com.locationsetter.app.di

import android.content.Context
import com.locationsetter.app.data.repository.LocationRepository
import com.locationsetter.app.data.room.AppDatabase
import com.locationsetter.app.data.subscription.SubscriptionRepository

class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    val locationRepository: LocationRepository = LocationRepository(database.locationDao())
    val subscriptionRepository: SubscriptionRepository = SubscriptionRepository(context)
}
