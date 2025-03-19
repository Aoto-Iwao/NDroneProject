package dji.sampleV5.aircraft.pages

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.LiveStreamVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.livestream.LiveStreamStatus
import dji.v5.manager.datacenter.livestream.LiveVideoBitrateMode
import dji.v5.manager.datacenter.livestream.StreamQuality
import dji.v5.manager.datacenter.livestream.VideoResolution
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.utils.common.StringUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ivs.IvsClient
import software.amazon.awssdk.services.ivs.model.GetChannelRequest
import software.amazon.awssdk.services.ivs.model.GetChannelResponse
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse
import software.amazon.awssdk.services.ivs.model.DeleteChannelRequest
import software.amazon.awssdk.services.ivs.model.DeleteChannelResponse

class LiveFragment : DJIFragment() {
    private val cameraStreamManager = MediaDataCenter.getInstance().cameraStreamManager

    private val liveStreamVM: LiveStreamVM by viewModels()

    private val emptyInputMessage = "input is empty"

    private lateinit var cameraIndex: ComponentIndexType
    private lateinit var rgProtocol: RadioGroup
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var rgCamera: RadioGroup
    private lateinit var rgQuality: RadioGroup
    private lateinit var rgBitRate: RadioGroup
    private lateinit var rgCameraStreamScaleType: RadioGroup
    private lateinit var rgLiveStreamScaleType: RadioGroup
    private lateinit var sbBitRate: SeekBar
    private lateinit var tvBitRate: TextView
    private lateinit var tvLiveInfo: TextView
    private lateinit var tvLiveError: TextView
    private lateinit var svCameraStream: SurfaceView
    private lateinit var tvArnId: TextView

    private var cameraStreamSurface: Surface? = null
    private var cameraStreamWidth = -1
    private var cameraStreamHeight = -1
    private var cameraStreamScaleType: ICameraStreamManager.ScaleType = ICameraStreamManager.ScaleType.CENTER_INSIDE
    private var ivsChannel: CreateChannelResponse? = null //Contains the created channel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_live, container, false)
    }

    /**
     * Ran when the view is created. setupAssetsAndLocation() is ran here.
     *
     * @authors DJI
     */
    //override fun onViewCreated: FragmentクラスのonViewCreatedメソッドをオーバーライドしています。
    // このメソッドはフラグメントのビューが作成された後に呼び出されます。
    //super.onViewCreated(view, savedInstanceState):
    // 親クラスのonViewCreatedメソッドを呼び出し、フラグメントの標準的な動作を維持します。
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //view.findViewById(R.id.xxx): レイアウトXMLで定義されたビューをIDで取得し、対応する変数に割り当てています。
        // これにより、コード内でこれらのビューを操作できるようになります。
        //例えば、rgProtocolはRadioGroup、btnStartとbtnStopはボタン、
        // sbBitRateはシークバー、tvBitRateはテキストビューなど、それぞれのUI要素を操作するための変数です。

        rgProtocol = view.findViewById(R.id.rg_protocol)
        btnStart = view.findViewById(R.id.btn_start)
        btnStop = view.findViewById(R.id.btn_stop)
        rgCamera = view.findViewById(R.id.rg_camera)
        rgQuality = view.findViewById(R.id.rg_quality)
        rgBitRate = view.findViewById(R.id.rg_bit_rate)
        rgCameraStreamScaleType = view.findViewById(R.id.rg_camera_scale_type)
        rgLiveStreamScaleType = view.findViewById(R.id.rg_live_scale_type)
        sbBitRate = view.findViewById(R.id.sb_bit_rate)
        tvBitRate = view.findViewById(R.id.tv_bit_rate)
        tvLiveInfo = view.findViewById(R.id.tv_live_info)
        tvLiveError = view.findViewById(R.id.tv_live_error)
        svCameraStream = view.findViewById(R.id.sv_camera_stream)
        tvArnId = view.findViewById(R.id.tv_arn_id) //Corresponds to the textView showing the ARN

        //初期化メソッドの呼び出し: 各種UIコンポーネントや機能を初期化するためのメソッドを順次呼び出しています。
        // これにより、ビューが適切に設定され、ユーザーの操作に応答できるようになります。
        initRGCamera()
        initRGQuality()
        initRGBitRate()
        initCameraStreamScaleType()
        initLiveStreamScaleType()
        initLiveButton()
        initCameraStream()
        initLiveData()


//      added by Aoto
        //トークンの取得: フラグメントの引数からtokenを取得しています。
//      from testingToolActivity
        val token = arguments?.getString("token")
        println("token in livefragment is $token")

        setupAssetsAndLocation(token)
    }

    /**
     * Sets up a drop down list with given values
     * @param {Spinner} spinnerView - The view of the Spinner (drop down list) to fill
     * @param {List<String>} values - List of values to fill the spinner with
     *
     * @author nelcy006
     */
    //関数の目的: 指定されたSpinner（ドロップダウンリスト）に対して、与えられた値のリストを設定します。
    //パラメータ:
    //spinnerView: 設定対象のSpinnerビュー。
    //values: スピナーに表示する文字列のリスト。
    //ArrayAdapterの作成: アクティビティコンテキストを使用してArrayAdapterを作成し、
    // シンプルなスピナーアイテムレイアウトと値のリストを指定します。
    //spinnerView.adapter = adapter: 作成したアダプターをスピナーに設定し、スピナーにリストが表示されるようにします。

    fun setupDropdownList(spinnerView: Spinner, values: List<String>) {
        val adapter = activity?.let {
            ArrayAdapter( //Changes to a format appropriate for the spinner
                it,
                android.R.layout.simple_spinner_item,
                values
            )
        }
        spinnerView.adapter = adapter
    }

    /**
     * Sends an API Request to SmartData to get locations and assets, then fills the drop down boxes
     * @param {String?} token - SmartData API token retrieved from login request
     *
     * @author iwaay002
     */
    //SmartData APIにリクエストを送り、場所と資産のデータを取得してスピナーに反映させます。
    fun setupAssetsAndLocation(token: String?){

        //Initialize spinner.
        val locationSpinner = view?.findViewById<Spinner>(R.id.spinner_location)
        val assetSpinner = view?.findViewById<Spinner>(R.id.spinner_asset)

        val url = "https://smartdata......" //replaced url for privacy
        //val header = token

        //コルーチンの開始: Dispatchers.IOを使用して、IO操作（ネットワークリクエスト）をバックグラウンドで実行します。
        //CoroutineScope(Dispatchers.IO).launch {} を使用することで、ネットワークリクエストがIO専用のバックグラウンドスレッドで実行されます。
        // これにより、メインスレッドがブロックされず、ユーザーインターフェースがスムーズに動作し続けます。
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assetsResponse = sendGetRequest(url, token)
                println("Assets in livefragment: $assetsResponse")

                // assets json format. assetsResponse is Json but, make sure correct format.
                val assetsJson = JSONObject(assetsResponse)
                println("assetsJson: $assetsJson")

                //Decomposite result.
                val resultsInAssets = assetsJson.getJSONArray("results")
                println("resultInAssets: $resultsInAssets")

                //This is for optimaze related list.
                //like one locaton has one or more assets so that we can display related assets,
                //need to change from a simple list to Map.
                val assetsMap = mutableMapOf<String, MutableList<String>>()

                //location list: name as location.
                for (i in 0 until resultsInAssets.length()) {
                    val results = resultsInAssets.getJSONObject(i)
                    //Added name to locationList

                    //locationList.add(results.getString("name"))
                    //now need to make the map for selection correctly.
                    val locationName = results.getString("name")

                    //datasetGroup
                    val datasetGroup = results.getJSONArray("dataset_groups")

                    //assetsList
                    val assetsList = mutableListOf<String>()

                    //for loop because datasetGroup is in result list
                    //add assets to assetsList.
                    for (d in 0 until datasetGroup.length()){
                        val dataset = datasetGroup.getJSONObject(d)
                        //name == assets
                        assetsList.add(dataset.getString("name"))
                    }

                    //Save assets list for each location (location as key) with map.
                    //assetsMapの作成: 場所とそれに関連する資産をマッピングするためのMapを作成します。
                    // キーは場所名、値はその場所に関連する資産名のリストです。
                    assetsMap[locationName] = assetsList
                    println("assetsMap test: $assetsMap")
                }
                //check the list is working correctly.
                //println("locationList:: $locationList")
                //println("assetsList:: $assetsList")

                //バックグラウンドスレッドで得られたデータをメインスレッド==UIスレッドに安全に送り、UIを更新.
                //ネットワークリクエスト後のUI更新は withContext(Dispatchers.Main) {} を使用してメインスレッドで行います。
                // これにより、バックグラウンドで取得したデータを安全にUIに反映させることができます。
                //update ui with withContext.
                withContext(Dispatchers.Main) {

                    // set location adapter to display locationName. adapter is used for lists to display on s
                    //場所スピナーの設定: assetsMapのキー（場所名）をリストに変換し、場所用スピナーに設定します。
                    // letブロック内でlocationSpinnerが非nullの場合にのみ実行されます。
                    locationSpinner?.let {
                        setupDropdownList(it, assetsMap.keys.toList())
                    }

                    // Define how to deal spinner handling.
                    locationSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        //For if something is selected.
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long

                        ) {
                            val selectedLocation = locationSpinner?.selectedItem.toString()
                            val relatedAssets = assetsMap[selectedLocation] ?: emptyList()

                            //set map for dropdown list.
                            assetSpinner?.let {
                                setupDropdownList(it, relatedAssets)
                            }
                        }

                        // if anything is not selected.
                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            //set as empty list.
                            assetSpinner?.let{
                                setupDropdownList(it, listOf<String>())
                            }
                        }
                    }
                }

            }catch (e: Exception){
                println("error. ")
            }
        }
    }

    /**
     * Sends a GET API request to a specified URL
     * @param {String} urlString - URL to send the GET request to
     * @param {String?} token - SmartData API token retrieved from login request
     * @returns {String} - API Request response
     *
     * @author iwaay002
     */
    //https://developer.android.com/reference/kotlin/java/net/HttpURLConnection

    fun sendGetRequest(urlString: String, token: String?): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            println("token is $token")
            //connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "token $token")
            //connectTimeout: 接続タイムアウトを5000ミリ秒（5秒）に設定します。
            //readTimeout: 読み取りタイムアウトを5000ミリ秒に設定します。
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

//            println("Request Method: ${connection.requestMethod}")
//            println("URL: $urlString")
//            println("Authorization: ${connection.getRequestProperty("Authorization")}")
//            println("Response Code: ${connection.responseCode}")
//            println("Response Message: ${connection.responseMessage}")

            //もし２００を返した場合、
            //成功時 (HTTP_OK): レスポンスコードが200（HTTP_OK）の場合、
            //入力ストリームを読み取り、レスポンス本文を文字列として返します。
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
     * Gets the currently selected location and asset and converts it in a form appropriate for an IVS channel name
     * @returns {String} - Converted location and name
     */
    fun getLocationAndAssetChannelName(): String {
        val locationSpinner = view?.findViewById<Spinner>(R.id.spinner_location)
        val assetSpinner = view?.findViewById<Spinner>(R.id.spinner_asset)
        val location = locationSpinner?.selectedItem.toString()
        val asset = assetSpinner?.selectedItem.toString()

        return "${location}_${asset}" //parsing string before it is sent
            .replace(" ", "_")
            .replace("'", "")
    }

    /**
     * Creates an Amazon IVS client object to send POST requests to.
     * TODO: Storing Access Key and Secret Access Key is a security vulnerability and this should be modified to use Environment Variables instead prior to production usage.
     * Once set as environment variables, they can be accessed by instead using DefaultCredentialsProvider.create()
     * For now, they are stored in the code for simplicity. For a production application, this should be changed.
     * @returns {IvsClient} - An IvsClient object
     *
     * @author nelcy006
     */
    fun createIvsClient(): IvsClient {
        //sets the access and secret access key (bad, change to environment variables before production)
        val credentials = AwsBasicCredentials.create("AKIAXGZAMICW6W6UGGOL", "QgPZpVt4dZTF7x7M6kge0y4prEyQmNBWHmvDg7in")

        return IvsClient.builder()
            .region(Region.of("ap-northeast-1")) // Set your desired region
            .credentialsProvider(StaticCredentialsProvider.create(credentials)) //Replace StaticCredentialsProvider.create(credentials) with DefaultCredentialsProvider.create() when switching to environment variables
            .httpClient(UrlConnectionHttpClient.create())  //sets http client to use for the request
            .build()
    }

    /**
     * Sends a request to Amazon IVS which returns a target channel
     * @param {String} arn - Arn of the channel you want to retrieve
     * @returns {GetChannelResponse} - Object similar to a JSON Object. Contains the requested channel
     *
     * @author nelcy006
     */
    fun awsGetChannel(arn: String): GetChannelResponse {
        val ivsClient = createIvsClient() //create the ivs client for the request

        val request = GetChannelRequest.builder() //request with ARN as a parameter
            .arn(arn)
            .build()

        return ivsClient.getChannel(request)
    }

    /**
     * Sends a request to Amazon IVS which creates a channel and returns it.
     * @param {String} name - Name of the channel
     * @returns {CreateChannelResponse} - Object similar to a JSON Object. Contains the newly created channel.
     *
     * @author nelcy006
     */
    fun awsCreateChannel(name: String): CreateChannelResponse {
        val ivsClient = createIvsClient() //create the ivs client for the request

        val request = CreateChannelRequest.builder()
            .name(name)
            .insecureIngest(true) //Required for RTMP streaming
            .latencyMode("LOW")
            .recordingConfigurationArn("arn:aws:ivs:ap-northeast-1:495599763629:recording-configuration/E0nCaCBZX1qd")
            .type("BASIC")
            .build()

        return ivsClient.createChannel(request)
    }

    /**
     * Sends a request to Amazon IVS which deletes a target channel
     * @param {String} arn - ARN of the channel
     * @returns {DeleteChannelResponse} = Returns empty if the channel was successfully deleted.
     */
    fun awsDeleteChannel(arn: String): DeleteChannelResponse {
        val ivsClient = createIvsClient()

        val request = DeleteChannelRequest.builder()
            .arn(arn)
            .build()

        ivsChannel = null
        return ivsClient.deleteChannel(request)
    }

    /**
     * Changes the ARN ID text below the Start and Stop buttons. This ID is useful to see where the recording would be stored.
     * @param {String} arn - Arn of the channel you want to show the ID of
     *
     * @author nelcy006
     */
    fun changeArnIdText(arn: String) {
        val newText = "ARN ID: ${arn.substringAfterLast("/")}"
        tvArnId.text = newText
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


    override fun onDestroyView() {
        super.onDestroyView()
        stopLive()
    }

    /**
     * Initializes various aspects of the frag_live.xml page.
     *
     * @author DJI
     */
    @SuppressLint("SetTextI18n")
    private fun initLiveData() {
        liveStreamVM.liveStreamStatus.observe(viewLifecycleOwner) { status ->
            var liveStreamStatus = status
            if (liveStreamStatus == null) {
                liveStreamStatus = LiveStreamStatus(0, 0, 0, 0, 0, false, VideoResolution(0, 0))
            }

            tvLiveInfo.text = liveStreamStatus.toString()
            rgProtocol.isEnabled = !liveStreamStatus.isStreaming
            for (i in 0 until rgProtocol.childCount) {
                rgProtocol.getChildAt(i).isEnabled = rgProtocol.isEnabled
            }
            btnStart.isEnabled = !liveStreamVM.isStreaming()
            btnStop.isEnabled = liveStreamVM.isStreaming()
        }

        liveStreamVM.liveStreamError.observe(viewLifecycleOwner) { error ->
            if (error == null) {
                tvLiveError.text = ""
                tvLiveError.visibility = View.GONE
            } else {
                tvLiveError.text = "error : $error"
                tvLiveError.visibility = View.VISIBLE
            }
        }

        liveStreamVM.availableCameraList.observe(viewLifecycleOwner) { cameraIndexList ->
            var firstAvailableView: View? = null
            var isNeedChangeCamera = false
            for (i in 0 until rgCamera.childCount) {
                val view = rgCamera.getChildAt(i)
                val index = ComponentIndexType.find((view.tag as String).toInt())
                if (cameraIndexList.contains(index)) {
                    view.visibility = View.VISIBLE
                    if (firstAvailableView == null) {
                        firstAvailableView = view
                    }
                } else {
                    view.visibility = View.GONE
                    if (rgCamera.checkedRadioButtonId == view.id) {
                        isNeedChangeCamera = true
                    }
                }
            }
            if (isNeedChangeCamera && firstAvailableView != null) {
                rgCamera.check(firstAvailableView.id)
            }
            if (cameraIndexList.isEmpty()) {
                stopLive()
            }
        }
    }

    /**
     * Sets up Camera radio buttons
     *
     * @author DJI
     */
    private fun initRGCamera() {
        rgCamera.setOnCheckedChangeListener { group: RadioGroup, checkedId: Int ->
            val view = group.findViewById<View>(checkedId)
            cameraIndex = ComponentIndexType.find((view.tag as String).toInt())
            val surface = svCameraStream.holder.surface
            if (surface != null && svCameraStream.width != 0) {
                cameraStreamManager.putCameraStreamSurface(
                    cameraIndex,
                    surface,
                    svCameraStream.width,
                    svCameraStream.height,
                    ICameraStreamManager.ScaleType.CENTER_INSIDE
                )
            }
            liveStreamVM.setCameraIndex(cameraIndex)
        }
        rgCamera.check(R.id.rb_camera_left)
    }

    /**
     * Sets up Quality radio buttons
     *
     * @author DJI
     */
    private fun initRGQuality() {
        rgQuality.setOnCheckedChangeListener { group: RadioGroup, checkedId: Int ->
            val view = group.findViewById<View>(checkedId)
            liveStreamVM.setLiveStreamQuality(StreamQuality.find((view.tag as String).toInt()))
        }
        rgQuality.check(R.id.rb_quality_hd)
    }

    /**
     * Sets up LiveScale radio buttons
     *
     * @author DJI
     */
    private fun initLiveStreamScaleType() {
        rgLiveStreamScaleType.setOnCheckedChangeListener { group: RadioGroup, checkedId: Int ->
            val view = group.findViewById<View>(checkedId)
            liveStreamVM.setLiveStreamScaleType(ICameraStreamManager.ScaleType.find((view.tag as String).toInt()))
        }
        rgLiveStreamScaleType.check(R.id.rb_live_scale_type_center_crop)
    }

    /**
     * Sets up CameraScale radio buttons
     *
     * @author DJI
     */
    private fun initCameraStreamScaleType() {
        rgCameraStreamScaleType.setOnCheckedChangeListener { group: RadioGroup, checkedId: Int ->
            val view = group.findViewById<View>(checkedId)
            cameraStreamScaleType = ICameraStreamManager.ScaleType.find((view.tag as String).toInt())
            updateCameraStreamWidth()
        }
        rgCameraStreamScaleType.check(R.id.rb_camera_scale_type_center_inside)
    }

    /**
     * Sets up BitRate radio buttons
     *
     * @author DJI
     */
    @SuppressLint("SetTextI18n")
    private fun initRGBitRate() {
        rgBitRate.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            if (checkedId == R.id.rb_bit_rate_auto) {
                sbBitRate.visibility = View.GONE
                tvBitRate.visibility = View.GONE
                liveStreamVM.setLiveVideoBitRateMode(LiveVideoBitrateMode.AUTO)
            } else if (checkedId == R.id.rb_bit_rate_manual) {
                sbBitRate.visibility = View.VISIBLE
                tvBitRate.visibility = View.VISIBLE
                liveStreamVM.setLiveVideoBitRateMode(LiveVideoBitrateMode.MANUAL)
                sbBitRate.progress = 20
            }
        }
        sbBitRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tvBitRate.text = (bitRate / 8 / 1024).toString() + " vbbs"
                if (!fromUser) {
                    liveStreamVM.setLiveVideoBitRate(bitRate)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                liveStreamVM.setLiveVideoBitRate(bitRate)
            }

            private val bitRate: Int
                get() = (8 * 1024 * 2048 * (0.1 + 0.9 * sbBitRate.progress / sbBitRate.max)).toInt()
        })
        rgBitRate.check(R.id.rb_bit_rate_auto)
    }

    /**
     * Sets onClickListeners for the start and stop stream buttons.
     *
     * @author DJI
     */
    private fun initLiveButton() {
        btnStart.setOnClickListener { _ ->
            val protocolCheckId = rgProtocol.checkedRadioButtonId
            if (protocolCheckId == R.id.rb_rtmp) {
                showSetLiveStreamRtmpConfigDialog()
            } else if (protocolCheckId == R.id.rb_rtsp) {
                showSetLiveStreamRtspConfigDialog()
            } else if (protocolCheckId == R.id.rb_gb28181) {
                showSetLiveStreamGb28181ConfigDialog()
            } else if (protocolCheckId == R.id.rb_agora) {
                showSetLiveStreamAgoraConfigDialog()
            }
        }
        btnStop.setOnClickListener {
            stopLive()
        }
    }

    private fun initCameraStream() {
        svCameraStream.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                cameraStreamWidth = width
                cameraStreamHeight = height
                cameraStreamSurface = holder.surface
                updateCameraStreamWidth()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                cameraStreamManager.removeCameraStreamSurface(holder.surface)
            }
        })
    }

    private fun updateCameraStreamWidth() {
        if (cameraIndex != ComponentIndexType.UNKNOWN) {
            cameraStreamSurface?.let {
                cameraStreamManager.putCameraStreamSurface(
                    cameraIndex,
                    it,
                    cameraStreamWidth,
                    cameraStreamHeight,
                    cameraStreamScaleType
                )
            }
        }
    }

    /**
     * Starts the live stream. Creates a toast if the stream start is successful or not.
     *
     * @author DJI
     */
    private fun startLive() {
        if (!liveStreamVM.isStreaming()) {
            liveStreamVM.startStream(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showShortToast(StringUtils.getResStr(R.string.msg_start_live_stream_success))
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showLongToast(
                        StringUtils.getResStr(R.string.msg_start_live_stream_failed, error.description())
                    )
                }
            });
        }
    }

    /**
     * Ends the live stream. Deletes the channel that was streamed to (currently disabled)
     *
     * @author DJI, nelcy006
     */
    private fun stopLive() {
        liveStreamVM.stopStream(null)
        /* TODO: Re-enable and properly test if possible - was unable to test properly as I don't have the drone.
        CoroutineScope(Dispatchers.IO).launch {
            if (ivsChannel != null) { //if channel exists, delete it after the stream stops
                awsDeleteChannel(ivsChannel!!.channel().arn())
            }
        }
         */
    }

    /**
     * Shows the RTMP config dialog. Modified to create an IVS stream channel and automatically set the RTMP URL. Updates the ARN shown on the frag_live page.
     *
     * @author DJI, nelcy006
     */
    private fun showSetLiveStreamRtmpConfigDialog() {
        val factory = LayoutInflater.from(requireContext())
        val rtmpConfigView = factory.inflate(R.layout.dialog_livestream_rtmp_config_view, null)
        val etRtmpUrl = rtmpConfigView.findViewById<EditText>(R.id.et_livestream_rtmp_config)

        CoroutineScope(Dispatchers.IO).launch { //Coroutine because API requests can not be done in main thread
            if (ivsChannel == null) {  //ensures no channel has been created before creating one
                ivsChannel = awsCreateChannel(getLocationAndAssetChannelName())
            }
            //ivsChannel accessed here not-null asserted because ivsChannel is always not null after the above code
            val streamUrl = "rtmp://${ivsChannel!!.channel().ingestEndpoint()}/app/${ivsChannel!!.streamKey().value()}" //sets the stream URL
            withContext(Dispatchers.Main) { //sets to main thread to update UI
                changeArnIdText(ivsChannel!!.channel().arn()) //changes the ARN displayed on the menu
                etRtmpUrl.setText(
                    streamUrl.toCharArray(), 0, streamUrl.length //places the stream url in the editText
                )
            }
        }

        val configDialog = requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_rtmp_config)
                .setCancelable(false)
                .setView(rtmpConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val inputValue = etRtmpUrl.text.toString()
                        if (TextUtils.isEmpty(inputValue)) {
                            ToastUtils.showToast(emptyInputMessage)
                        } else {
                            liveStreamVM.setRTMPConfig(inputValue)
                            startLive()
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun showSetLiveStreamRtspConfigDialog() {
        val factory = LayoutInflater.from(requireContext())
        val rtspConfigView = factory.inflate(R.layout.dialog_livestream_rtsp_config_view, null)
        val etRtspUsername = rtspConfigView.findViewById<EditText>(R.id.et_livestream_rtsp_username)
        val etRtspPassword = rtspConfigView.findViewById<EditText>(R.id.et_livestream_rtsp_password)
        val etRtspPort = rtspConfigView.findViewById<EditText>(R.id.et_livestream_rtsp_port)
        val rtspConfig = liveStreamVM.getRtspSettings()
        if (!TextUtils.isEmpty(rtspConfig) && rtspConfig.isNotEmpty()) {
            val configs = rtspConfig.trim().split("^_^")
            etRtspUsername.setText(
                configs[0].toCharArray(),
                0,
                configs[0].length
            )
            etRtspPassword.setText(
                configs[1].toCharArray(),
                0,
                configs[1].length
            )
            etRtspPort.setText(
                configs[2].toCharArray(),
                0,
                configs[2].length
            )
        }

        val configDialog = requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_rtsp_config)
                .setCancelable(false)
                .setView(rtspConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val inputUserName = etRtspUsername.text.toString()
                        val inputPassword = etRtspPassword.text.toString()
                        val inputPort = etRtspPort.text.toString()
                        if (TextUtils.isEmpty(inputUserName) || TextUtils.isEmpty(inputPassword) || TextUtils.isEmpty(
                                inputPort
                            )
                        ) {
                            ToastUtils.showToast(emptyInputMessage)
                        } else {
                            try {
                                liveStreamVM.setRTSPConfig(
                                    inputUserName,
                                    inputPassword,
                                    inputPort.toInt()
                                )
                                startLive()
                            } catch (e: NumberFormatException) {
                                ToastUtils.showToast("RTSP port must be int value")
                            }
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun showSetLiveStreamGb28181ConfigDialog() {
        val factory = LayoutInflater.from(requireContext())
        val gbConfigView = factory.inflate(R.layout.dialog_livestream_gb28181_config_view, null)
        val etGbServerIp = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_server_ip)
        val etGbServerPort = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_server_port)
        val etGbServerId = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_server_id)
        val etGbAgentId = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_agent_id)
        val etGbChannel = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_channel)
        val etGbLocalPort = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_local_port)
        val etGbPassword = gbConfigView.findViewById<EditText>(R.id.et_livestream_gb28181_password)

        val gbConfig = liveStreamVM.getGb28181Settings()
        if (!TextUtils.isEmpty(gbConfig) && gbConfig.isNotEmpty()) {
            val configs = gbConfig.trim().split("^_^")
            etGbServerIp.setText(
                configs[0].toCharArray(),
                0,
                configs[0].length
            )
            etGbServerPort.setText(
                configs[1].toCharArray(),
                0,
                configs[1].length
            )
            etGbServerId.setText(
                configs[2].toCharArray(),
                0,
                configs[2].length
            )
            etGbAgentId.setText(
                configs[3].toCharArray(),
                0,
                configs[3].length
            )
            etGbChannel.setText(
                configs[4].toCharArray(),
                0,
                configs[4].length
            )
            etGbLocalPort.setText(
                configs[5].toCharArray(),
                0,
                configs[5].length
            )
            etGbPassword.setText(
                configs[6].toCharArray(),
                0,
                configs[6].length
            )
        }

        val configDialog = requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_gb28181_config)
                .setCancelable(false)
                .setView(gbConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val serverIp = etGbServerIp.text.toString()
                        val serverPort = etGbServerPort.text.toString()
                        val serverId = etGbServerId.text.toString()
                        val agentId = etGbAgentId.text.toString()
                        val channel = etGbChannel.text.toString()
                        val localPort = etGbLocalPort.text.toString()
                        val password = etGbPassword.text.toString()
                        if (TextUtils.isEmpty(serverIp) || TextUtils.isEmpty(serverPort) || TextUtils.isEmpty(
                                serverId
                            ) || TextUtils.isEmpty(agentId) || TextUtils.isEmpty(channel) || TextUtils.isEmpty(
                                localPort
                            ) || TextUtils.isEmpty(password)
                        ) {
                            ToastUtils.showToast(emptyInputMessage)
                        } else {
                            try {
                                liveStreamVM.setGB28181(
                                    serverIp,
                                    serverPort.toInt(),
                                    serverId,
                                    agentId,
                                    channel,
                                    localPort.toInt(),
                                    password
                                )
                                startLive()
                            } catch (e: NumberFormatException) {
                                ToastUtils.showToast("RTSP port must be int value")
                            }
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun showSetLiveStreamAgoraConfigDialog() {
        val factory = LayoutInflater.from(requireContext())
        val agoraConfigView = factory.inflate(R.layout.dialog_livestream_agora_config_view, null)

        val etAgoraChannelId = agoraConfigView.findViewById<EditText>(R.id.et_livestream_agora_channel_id)
        val etAgoraToken = agoraConfigView.findViewById<EditText>(R.id.et_livestream_agora_token)
        val etAgoraUid = agoraConfigView.findViewById<EditText>(R.id.et_livestream_agora_uid)

        val agoraConfig = liveStreamVM.getAgoraSettings()
        if (!TextUtils.isEmpty(agoraConfig) && agoraConfig.length > 0) {
            val configs = agoraConfig.trim().split("^_^")
            etAgoraChannelId.setText(configs[0].toCharArray(), 0, configs[0].length)
            etAgoraToken.setText(configs[1].toCharArray(), 0, configs[1].length)
            etAgoraUid.setText(configs[2].toCharArray(), 0, configs[2].length)
        }

        val configDialog = requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle(R.string.ad_set_live_stream_agora_config)
                .setCancelable(false)
                .setView(agoraConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val channelId = etAgoraChannelId.text.toString()
                        val token = etAgoraToken.text.toString()
                        val uid = etAgoraUid.text.toString()
                        if (TextUtils.isEmpty(channelId) || TextUtils.isEmpty(token) || TextUtils.isEmpty(
                                uid
                            )
                        ) {
                            ToastUtils.showToast(emptyInputMessage)
                        } else {
                            liveStreamVM.setAgoraConfig(channelId, token, uid)
                            startLive()
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

}