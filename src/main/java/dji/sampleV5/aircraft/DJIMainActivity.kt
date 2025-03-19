package dji.sampleV5.aircraft

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.models.BaseMainActivityVm
import dji.sampleV5.aircraft.models.MSDKInfoVm
import dji.sampleV5.aircraft.models.MSDKManagerVM
import dji.sampleV5.aircraft.models.globalViewModels
import dji.sampleV5.aircraft.util.Helper
import dji.sampleV5.aircraft.util.ToastUtils
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.PermissionUtil
import dji.v5.utils.common.StringUtils
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL


/**
 * Class Description
 * Handles interactions with activity_main.xml as well as associated initialization functions.
 * Requests permissions required for the app to function.
 *
 * @author Hoker
 * @date 2022/2/10
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
abstract class DJIMainActivity : AppCompatActivity() {

    val tag: String = LogUtils.getTag(this)
    private val permissionArray = arrayListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.KILL_BACKGROUND_PROCESSES,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    init {
        permissionArray.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private val baseMainActivityVm: BaseMainActivityVm by viewModels()
    private val msdkInfoVm: MSDKInfoVm by viewModels()
    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val disposable = CompositeDisposable()
    private var smartdataApiKey = "d"//This should be changed to the key from the login response

    //The following abstract functions are overridden in DJIAircraftMainActivity
    abstract fun prepareUxActivity()

    abstract fun prepareTestingToolsActivity()

    /**
     * Sets content view to activity_main.xml
     * Begins initialization of activity_main elements, including the login button.
     *
     * @author DJI
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 有一些手机从系统桌面进入的时候可能会重启main类型的activity
        // 需要校验这种情况，业界标准做法，基本所有app都需要这个
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN == intent.action) {

                finish()
                return

        }

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        initMSDKInfoView()
        observeSDKManager()
        checkPermissionAndRequest()
        //setupAotoButton()
        assetsAndLocationButton()
        setupDroneButton() //Debug button, should be commented out or removed later.
        // Login Button setup is ran in prepareUxActivity which is ran in observeSDKManager

    }

    /**
     * Sets up the debug button to bypass the login feature. This should be used for debug purposes only
     * @author nelcy006
     */
    fun setupDroneButton() {
        drone_stream_login.setOnClickListener { //When button drone_stream_login button is pressed, should open login page
            drone_stream_login.text = "clicked";
            //setContentView(R.layout.dls_login);
            default_layout_button.isEnabled = true
            widget_list_button.isEnabled = true
            testing_tool_button.isEnabled = true
        }
    }

    /**
     * This function was previously used to set up a button, but is no longer used.
     * @author iwaay002
     */
//    // Set the navigation by button click.
//    fun setupAotoButton(){
//        testing_aoto_button.setOnClickListener{
//            testing_aoto_button.text = "clicked";
//            setContentView(R.layout.activity_testing_aoto);
//        }
//    }

    fun assetsAndLocationButton(){
        assetsAndLocationButton.setOnClickListener{
            assetsAndLocationButton.text = "clicked";
            setContentView(R.layout.activity_testing_aoto);
        }
    }

    /**
     * This function sets up the onClickListener for the login button, as well as enabling it. It is run when the application is launched.
     * When the login button is clicked, it will get the currently entered username and password and use it to send a login request to SmartData using the API
     * @returns API Key for SmartData
     *
     * @author nelcy006, iwaay002
     */
    fun setupLoginButton (){
        //enable the login button once it is loaded
        aoto_login_button.isEnabled = true

        // to use username and passwords for request, have to get them.
        val userNameEditText = findViewById<EditText>(R.id.nd_username_text)
        val userPasswordEditText = findViewById<EditText>(R.id.nd_password_text)
        val loginButton = findViewById<Button>(R.id.aoto_login_button)

        //If the login button is pressed.
        loginButton.setOnClickListener {

            // the username and password have to be a string type.
            val userName = userNameEditText.text.toString()
            val userPassword = userPasswordEditText.text.toString()

            //make sure the both of info are not empty.
            if (userName.isNotEmpty() && userPassword.isNotEmpty()){
                // URLとJSONボディを設定
                val url = "http://smartdata........" //smartdata url
                val jsonBody = """
            {
                "username": "$userName",
                "password": "$userPassword"
            }
        """
                // Coroutineを使用して非同期処理を実行する。
                //To avoid network request error.
                //To avoid NetworkOnMainThreadException error, using CoroutineScope
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = postJsonRequest(url, jsonBody)
                        //response type is json so we have to convert it to string and only the key value.
                        // this is like {"key: ","5489yhrfwirbhv"} but, we just have to have like this value 5489yhrfwirbhv for token.
                        val token = JSONObject(response).getString("key")
                        //println("testing token $token")

                        //バックグラウンドスレッドで得られたデータをメインスレッド==UIスレッドに安全に送り、UIを更新.
                        withContext(Dispatchers.Main) {
                            //smartdataApiKey = key
                            // ToastメッセージでAPIからのレスポンス内容を表示します。
                            //Display the response from API with Toast.
                            Toast.makeText(applicationContext, "Response: $response", Toast.LENGTH_LONG).show()
                            //Toast.makeText(applicationContext, "Logged in with key: $key", Toast.LENGTH_LONG).show()
                            //setupAssetsAndLocation(token)

                            smartdataApiKey = response //{"key":"value}.


                            //Enable the other buttons after successful login
                            default_layout_button.isEnabled = true
                            widget_list_button.isEnabled = true
                            testing_tool_button.isEnabled = true


                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }


    fun setupAssetsAndLocation(token: String){
        val url = "https://smartdata.....//asset/////"// replaced url for privacy
        //val header = token
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assets = sendGetRequest(url, token)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "tokennnnn: $token", Toast.LENGTH_LONG).show()

                    Toast.makeText(applicationContext, "Response: $assets", Toast.LENGTH_LONG).show()

                }

            }catch (e: Exception){
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    //This is for get request like if we want to get the dataset groups.
    @Throws(Exception::class)
    fun getRequest(urlString: String, token: String): String{
        val url = URL(urlString)
        //val tokenString = token
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            //get a responsCode like 200(Status code.).
            val responseCode = connection.responseCode

            //if response is not failed. If status code is 200.
            if (responseCode == HttpURLConnection.HTTP_OK){
                //return the response as a string.
                BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    return reader.readText()
                }
            }
            //Otherwise error.
            else{
                return "Error: $responseCode"
            }
        } finally {
            connection.disconnect()
        }
    }

    //for testing
    fun sendGetRequest(urlString: String, token: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            println("token is $token")
            //connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "token $token")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            println("Request Method: ${connection.requestMethod}")
            println("URL: $urlString")
            println("Authorization: ${connection.getRequestProperty("Authorization")}")
            println("Response Code: ${connection.responseCode}")
            println("Response Message: ${connection.responseMessage}")

            return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                "Error: ${connection.responseCode} - ${connection.responseMessage}"
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Sends a POST request to a URL with a JsonBody
     * @param {String} urlString - The URL of the API which the call should be made to
     * @param {String} jsonBody - JSON data associated with the POST request
     * @returns {String} The result of the POST request
     *
     * @author iwaay002
     */
    @Throws(Exception::class)
    fun postJsonRequest(urlString: String, jsonBody: String): String {
        val url = URL(urlString) // Turns the urlString into a url
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            //write Json body for request body
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            //get a responsCode like 200(Status code.).
            val responseCode = connection.responseCode

            //if response is not failed. If status code is 200.
            if (responseCode == HttpURLConnection.HTTP_OK){
                //return the response as a string.
                BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    return reader.readText()
                }
            }
            //Otherwise error.
            else{
                return "Error."
            }
        } finally {
            connection.disconnect() //Ends the request when everything is done.
        }
    }

    /**
     * Related to requesting permissions required for the app to function. Runs handleAfterPermissionPermitted() if permissions have been granted
     *
     * @author DJI
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }
    }

    /**
     * Related to requesting permissions required for the app to function. Runs handleAfterPermissionPermitted() if permissions have been granted
     *
     * @author DJI
     */
    override fun onResume() {
        super.onResume()
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }
    }

    /**
     * Runs prepareTestingToolsActivity()
     *
     * @author DJI
     */
    private fun handleAfterPermissionPermitted() {
        prepareTestingToolsActivity()
    }

    /**
     * Sets on click listeners for browser icons and fills in the information in the top left of the activity view.
     *
     * @author DJI
     */
    @SuppressLint("SetTextI18n")
    private fun initMSDKInfoView() {
        msdkInfoVm.msdkInfo.observe(this) {
            text_view_version.text = StringUtils.getResStr(R.string.sdk_version, it.SDKVersion + " " + it.buildVer)
            text_view_product_name.text = StringUtils.getResStr(R.string.product_name, it.productType.name)
            text_view_package_product_category.text = StringUtils.getResStr(R.string.package_product_category, it.packageProductCategory)
            text_view_is_debug.text = StringUtils.getResStr(R.string.is_sdk_debug, it.isDebug)
            text_core_info.text = it.coreInfo.toString()
        }

        icon_sdk_forum.setOnClickListener {
            Helper.startBrowser(this, StringUtils.getResStr(R.string.sdk_forum_url))
        }
        icon_release_node.setOnClickListener {
            Helper.startBrowser(this, StringUtils.getResStr(R.string.release_node_url))
        }
        icon_tech_support.setOnClickListener {
            Helper.startBrowser(this, StringUtils.getResStr(R.string.tech_support_url))
        }
        view_base_info.setOnClickListener {
            baseMainActivityVm.doPairing {
                showToast(it)
            }
        }
    }

    /**
     * Observes various live data and shows toasts when things are changed.
     * When lvRegisterState changes, it runs prepareUxActivity() with a delay. This initializes various buttons on the main page.
     *
     * @author DJI
     */
    private fun observeSDKManager() {
        msdkManagerVM.lvRegisterState.observe(this) { resultPair ->
            val statusText: String?
            if (resultPair.first) {
                ToastUtils.showToast("Register Success")
                statusText = StringUtils.getResStr(this, R.string.registered)
                msdkInfoVm.initListener()
                handler.postDelayed({
                    prepareUxActivity()
                }, 5000)
            } else {
                showToast("Register Failure: ${resultPair.second}")
                statusText = StringUtils.getResStr(this, R.string.unregistered)
            }
            text_view_registered.text = StringUtils.getResStr(R.string.registration_status, statusText)
        }

        msdkManagerVM.lvProductConnectionState.observe(this) { resultPair ->
            showToast("Product: ${resultPair.second} ,ConnectionState:  ${resultPair.first}")
        }

        msdkManagerVM.lvProductChanges.observe(this) { productId ->
            showToast("Product: $productId Changed")
        }

        msdkManagerVM.lvInitProcess.observe(this) { processPair ->
            showToast("Init Process event: ${processPair.first.name}")
        }

        msdkManagerVM.lvDBDownloadProgress.observe(this) { resultPair ->
            showToast("Database Download Progress current: ${resultPair.first}, total: ${resultPair.second}")
        }
    }

    /**
     * Shows a toast message in the app
     * @params {String} content - Content of the toast message
     *
     * @author DJI
     */
    private fun showToast(content: String) {
        ToastUtils.showToast(content)

    }


    /**
     * Sets on click listener for the corresponding activity button via enableShowCaseButton
     * This corresponds with the titles of the 3 buttons in the bottom right of the main view.
     * @params {Class<T>} cl - The class of the activity which it will change to
     *
     * @author DJI
     */
    fun <T> enableDefaultLayout(cl: Class<T>) { //following 3 functions get called from DJIAircraftMainActivity.kt
        enableShowCaseButton(default_layout_button, cl)
    }

    fun <T> enableWidgetList(cl: Class<T>) {
        enableShowCaseButton(widget_list_button, cl)
    }

    fun <T> enableTestingTools(cl: Class<T>) {
        enableShowCaseButton(testing_tool_button, cl)
    }

    /**
     * Sets onClickListener for button parameter view. SmartData API Key is passed through the Intent in the onClickListener.
     * @params {View} view - The view of the button to apply the onClickListener to
     * @params {Class<T>} cl - The class of the activity which it will change to
     *
     * @author DJI, nelcy006
     */
    private fun <T> enableShowCaseButton(view: View, cl: Class<T>) {
        //disabling this to change it to be behind the login button. this will allow for things to initalize but not enable the button until its ready
        //view.isEnabled = true

        view.setOnClickListener {
            //Original code here has been commented out
            //Intent(this, cl).also {
            //    startActivity(it)
            //}

            //.alsoスコープ関数:
            //
            // Kotlinのスコープ関数の一つで、オブジェクトに対して追加の処理を行うために使用。
            // alsoは、オブジェクト自体をitとして参照し、後続の処理を実行します。
            //利点: コードの可読性を向上させ、チェーンメソッドとして使用することで流れをスムーズにします。
            //startActivity(it):
            //役割: 作成したIntent（it）を使って、新しいアクティビティを起動します。
            //it: .also内では、前段階で作成されたIntentオブジェクトを指します。
            //全体の流れ:
            //Intentを作成し、必要なデータを.putExtraで添付します。
            //.alsoを使って、そのIntentをstartActivityメソッドに渡し、アクティビティを起動します。

            //Added .putExtra for passing through response
            Intent(this, cl).putExtra("key",smartdataApiKey).also {
                startActivity(it)
            }

        }
    }

    /**
     * Checks if permissions are granted, and if not, requests permissions
     *
     * @author DJI
     */
    private fun checkPermissionAndRequest() {
        if (!checkPermission()) {
            requestPermission()
        }
    }

    private fun checkPermission(): Boolean {
        for (i in permissionArray.indices) {
            if (!PermissionUtil.isPermissionGranted(this, permissionArray[i])) {
                return false
            }
        }
        return true
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result?.entries?.forEach {
            if (!it.value) {
                requestPermission()
                return@forEach
            }
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(permissionArray.toArray(arrayOf()))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        disposable.dispose()
    }
}