package dji.sampleV5.aircraft

import dji.v5.common.utils.GeoidManager
import dji.v5.ux.core.communication.DefaultGlobalPreferences
import dji.v5.ux.core.communication.GlobalPreferencesManager
import dji.v5.ux.core.util.UxSharedPreferencesUtil
import dji.v5.ux.sample.showcase.defaultlayout.DefaultLayoutActivity
import dji.v5.ux.sample.showcase.widgetlist.WidgetsActivity

/**
 * Class Description
 * Extends DJIMainActivity to override preparation functions.
 *
 * @author Hoker
 * @date 2022/2/14
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
class DJIAircraftMainActivity : DJIMainActivity() {

    /**
     * Initializes preferences, geoid, and buttons for switching to the Default Layout and Widget List activities.
     * Runs the function to initialize the login button for logging into SmartData
     * @author DJI
     */
    override fun prepareUxActivity() {
        //Initializes preferences and geoid
        UxSharedPreferencesUtil.initialize(this)
        GlobalPreferencesManager.initialize(DefaultGlobalPreferences(this))
        GeoidManager.getInstance().init(this)

        //The following functions implement onCLickListeners for each the DefaultLayout and WidgetList buttons.
        enableDefaultLayout(DefaultLayoutActivity::class.java)
        enableWidgetList(WidgetsActivity::class.java)
        //Login button is enabled here to ensure everything is loaded before the user is able to log in and press other buttons - this avoids a crash.
        setupLoginButton()
    }


    /**
     * Initializes and enables the button to switch to the Testing Tools activity
     * @author DJI
     */
    override fun prepareTestingToolsActivity() {
        enableTestingTools(AircraftTestingToolsActivity::class.java)
    }

}