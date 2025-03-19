package dji.sampleV5.aircraft

import android.app.Application
import dji.sampleV5.aircraft.models.MSDKManagerVM
import dji.sampleV5.aircraft.models.globalViewModels

/**
 * Class Description
 * Initial activity, as denoted in AndroidManifest.xml. Initializes the app and the mobile SDK.
 *
 * @author Hoker
 * @date 2022/3/1
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
open class DJIApplication : Application() {

    private val msdkManagerVM: MSDKManagerVM by globalViewModels()

    override fun onCreate() {
        super.onCreate()

        // Initializes the mobile SDK
        // Ensure initialization is called first
        msdkManagerVM.initMobileSDK(this)
    }

}
