package dji.sampleV5.aircraft

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.drone_stream_login
import kotlinx.android.synthetic.main.activity_testing_aoto.live_stream_button
import kotlinx.android.synthetic.main.frag_main_title.msdk_info_text_main
import kotlinx.android.synthetic.main.frag_main_title.msdk_info_text_second

abstract class TestingAotoActivity : DJIMainActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_testing_aoto)
        setupLiveAotoButton()

    }


    fun setupLiveAotoButton(){
        live_stream_button.setOnClickListener{
            live_stream_button.text = "clicked";
            setContentView(R.layout.activity_main);
        }
    }
}