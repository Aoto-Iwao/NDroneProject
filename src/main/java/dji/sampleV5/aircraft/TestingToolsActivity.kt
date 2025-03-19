package dji.sampleV5.aircraft

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.navigation.Navigation
import dji.sampleV5.aircraft.models.MSDKCommonOperateVm
import dji.sampleV5.aircraft.pages.LiveFragment
import dji.sampleV5.aircraft.util.DJIToastUtil
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sampleV5.aircraft.views.MSDKInfoFragment
import dji.v5.ux.core.util.ViewUtil
import kotlinx.android.synthetic.main.activity_testing_tools.keytest
import kotlinx.android.synthetic.main.activity_testing_tools.nav_host_fragment_container
import org.json.JSONObject

/**
 * Class Description
 *
 * @author Hoker
 * @date 2021/7/23
 *
 * Copyright (c) 2021, DJI All Rights Reserved.
 */
abstract class TestingToolsActivity : AppCompatActivity() {

    protected val msdkCommonOperateVm: MSDKCommonOperateVm by viewModels()

    private val testToolsVM: TestToolsVM by viewModels()

    /**
     * Initializes the TestingTools activity (modified to only show the LiveFragment.kt/frag_live.xml page.
     * SmartData login token is received from the main activity and passed through to LiveFragment.kt
     *
     * @authors DJI, nelcy006, iwaay002
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_testing_tools) //changed from activity_testing_tools

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // 设置Listener防止系统UI获取焦点后进入到非全屏状态
        window.decorView.setOnSystemUiVisibilityChangeListener() {
            if (it and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            }
        }

        loadTitleView()

        DJIToastUtil.dJIToastLD = testToolsVM.djiToastResult
        testToolsVM.djiToastResult.observe(this) { result ->
            result?.msg?.let {
                ToastUtils.showToast(it)
            }
        }

        msdkCommonOperateVm.mainPageInfoList.observe(this) { list ->
            list.iterator().forEach {
                addDestination(it.vavGraphId)
            }
        }

        loadPages()


        //Extracting the response and setting it to a test value for now
        val keyPassthroughJson = intent.getStringExtra("key")
        //keytest.text = keyPassthroughJson //this text shows on all pages so i think it should be accessible in any subsequent page }

        //Extract json to only keystring.
        val tokenString = JSONObject(keyPassthroughJson).getString("key")
        keytest.text = tokenString

        //added by Aoto.
        val liveFragment = LiveFragment()
        val bundle = Bundle()
        println("testing keyPassthrough tokenken $tokenString")

        bundle.putString("token", tokenString)
        //fragobj.setArguments(bundle);
        liveFragment.arguments = bundle

        //supportFragmentManager.commit { ... }は、フラグメントトランザクションを開始し、指定された操作（ここでは置換）を実行します。
        //replace(R.id.livestream_fragment_container, liveFragment)は、指定されたコンテナビュー（R.id.livestream_fragment_container）内
        // の既存のフラグメントをLiveFragmentに置き換えます。

        supportFragmentManager.commit {
            replace(R.id.livestream_fragment_container, liveFragment) //ive noticed changing this to nav_host_fragment_container works but also crashes after 1 second
            //replaced main_info_fragment_container with livestream_fragment_container and hid the nav_host_fragment_container in the xml
            //this prevents the crash but also shows the live stream fragment instead of the list thing - hopefully this works
        }
        //added by Aoto.
    }

    override fun onResume() {
        super.onResume()
        ViewUtil.setKeepScreen(this, true)
    }

    override fun onPause() {
        super.onPause()
        ViewUtil.setKeepScreen(this, false)
    }

    /**
     * 本activity的NavController，都是基于nav_host_fragment_container的
     * Translated: The NavController of this activity is based on nav_host_fragment_container
     */
    private fun addDestination(id: Int) {
        val v = Navigation.findNavController(nav_host_fragment_container).navInflater.inflate(id)
        Navigation.findNavController(nav_host_fragment_container).graph.addAll(v)
    }

    override fun onDestroy() {
        super.onDestroy()
        DJIToastUtil.dJIToastLD = null
    }

    open fun loadTitleView() {
        supportFragmentManager.commit {
            replace(R.id.main_info_fragment_container, MSDKInfoFragment())
        }
    }

    abstract fun loadPages()
}