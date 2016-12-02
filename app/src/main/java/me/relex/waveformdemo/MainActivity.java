package me.relex.waveformdemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.alibaba.fastjson.JSON;
import java.io.IOException;
import java.io.InputStream;
import me.relex.widget.waveform.WaveFormInfo;
import me.relex.widget.waveform.WaveFormListener;
import me.relex.widget.waveform.WaveFormThumbView;
import me.relex.widget.waveform.WaveFormView;

public class MainActivity extends AppCompatActivity {

    private WaveFormView mWaveFormView;
    private WaveFormThumbView mWaveFormThumbView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWaveFormView = (WaveFormView) findViewById(R.id.wave_form_view);
        mWaveFormView.setWaveFormListener(new WaveFormListener() {
            @Override public void onScrollChanged(double startTime, double endTime) {
                mWaveFormThumbView.updateThumb(startTime, endTime);
            }
        });

        mWaveFormThumbView = (WaveFormThumbView) findViewById(R.id.wave_form_thumb_view);
        mWaveFormThumbView.setOnDragThumbListener(new WaveFormThumbView.OnDragThumbListener() {
            @Override public void onDrag(double startTime) {
                mWaveFormView.setStartTime(startTime);
            }
        });

        new ReaderTask() {
            @Override protected void onPostExecute(WaveFormInfo waveFormInfo) {
                mWaveFormThumbView.setWave(waveFormInfo); // Must be first.
                mWaveFormView.setWave(waveFormInfo);
            }
        }.execute();
    }

    public class ReaderTask extends AsyncTask<Void, Void, WaveFormInfo> {

        @Override protected WaveFormInfo doInBackground(Void... params) {
            InputStream inputStream = null;
            try {
                inputStream = getResources().openRawResource(R.raw.waveform);
                byte[] data = new byte[inputStream.available()];
                inputStream.read(data);
                return JSON.parseObject(data, WaveFormInfo.class);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }
}
