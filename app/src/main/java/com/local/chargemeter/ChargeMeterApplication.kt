package com.local.chargemeter

import android.app.Application
import com.local.chargemeter.data.ChargeDatabase
import com.local.chargemeter.data.ChargeRepository

class ChargeMeterApplication : Application() {
    val database by lazy { ChargeDatabase.get(this) }
    val repository by lazy { ChargeRepository(database.chargeDao()) }
}
