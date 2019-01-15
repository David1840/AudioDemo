package com.liuwei.audiodemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

/**
 * @Author: liuwei
 * @Create: 2019/1/11 11:40
 * @Description:
 */
public class OpenGLActivity extends Activity {
    static {
        System.loadLibrary("native-lib");
    }

    TextView textView;
    Button recoderBtn;
    Button palyBtn;

    private volatile boolean mIsRecording;
    private volatile boolean mIsPalying;

    String filePath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        textView = findViewById(R.id.textViewStream);
        recoderBtn = findViewById(R.id.buttonStream);
        palyBtn = findViewById(R.id.button4);
    }

    /**
     * 开始按钮 点击开始录音
     */
    public void start(View view) {
        if (mIsRecording) {
            mIsRecording = false;
            recoderBtn.setText("开始录音");
            textView.setText(textView.getText() + "\n录音成功");
            stopRecod();
        } else {
            textView.setText(textView.getText() + "\n开始录音。。。");
            recoderBtn.setText("结束录音");
            mIsRecording = true;
            filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/RecorderTest/" +
                    System.currentTimeMillis() + ".pcm";
            File mAudioFile = new File(filePath);
            if (!mAudioFile.getParentFile().exists()) {
                mAudioFile.getParentFile().mkdirs();
            }
            try {
                mAudioFile.createNewFile();
                record(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void streamPlay(View view) {
        if (!mIsPalying) {
            textView.setText(textView.getText() + "\n开始播放。。。");
            palyBtn.setText("结束播放");
            mIsPalying = true;
            play(filePath);
        } else {
            mIsPalying = false;
            palyBtn.setText("播放");
            textView.setText(textView.getText() + "\n播放结束");
            playStop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playStop();
        stopRecod();
    }

    public native int play(String filePath);

    public native int playStop();

    public native int record(String filePath);

    public native int stopRecod();

}
