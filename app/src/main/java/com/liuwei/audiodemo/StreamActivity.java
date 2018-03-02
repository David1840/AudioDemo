package com.liuwei.audiodemo;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by liuwei on 2018/2/28.
 */

public class StreamActivity extends Activity {
    TextView textView;

    private ExecutorService mExecutorService;
    AudioRecord mAudioRecorder;

    byte[] mBuffer;
    FileOutputStream mFileOutputStream;
    File mAudioFile;
    long start;
    long end;

    private volatile boolean mIsRecording;
    private volatile boolean mIsPalying;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        textView = (TextView) findViewById(R.id.textViewStream);
        final Button button = (Button) findViewById(R.id.buttonStream);
        mBuffer = new byte[2048];
        mExecutorService = Executors.newSingleThreadExecutor();

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsRecording) {
                    button.setText("start");
                    mIsRecording = false;
                } else {
                    button.setText("stop");
                    mIsRecording = true;
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            if (!startRecorder()) {
                                recoderFail();
                            }
                        }
                    });
                }
            }
        });

    }

    private boolean startRecorder() {


        try {
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/RecorderTest/" +
                    System.currentTimeMillis() + ".pcm");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            mFileOutputStream = new FileOutputStream(mAudioFile);

            int audioSource = MediaRecorder.AudioSource.MIC;
            int sampleRate = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

            mAudioRecorder = new AudioRecord(audioSource, sampleRate, channelConfig,
                    audioFormat, Math.max(minBufferSize, 2048));
            mAudioRecorder.startRecording();

            start = System.currentTimeMillis();

            while (mIsRecording) {
                int read = mAudioRecorder.read(mBuffer, 0, 2048);
                if (read > 0) {
                    mFileOutputStream.write(mBuffer, 0, read);
                } else {
                    return false;
                }
            }
            return stopRecorder();

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (mAudioRecorder != null) {
                mAudioRecorder.release();
                mAudioRecorder = null;
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }

    private void recoderFail() {
        mAudioFile = null;
        mIsRecording = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this, "失败", Toast.LENGTH_SHORT).show();
            }
        });

    }


    private boolean stopRecorder() {

        try {
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
            mFileOutputStream.close();

            end = System.currentTimeMillis();

            final int second = (int) ((end - start) / 1000);
            if (second > 3) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(textView.getText() + "\nsuccess" + second + "S");
                    }
                });
            } else {
                mAudioFile.deleteOnExit();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void streamPlay(View view) {
        if (mAudioFile != null && !mIsPalying) {
            mIsPalying = true;

            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPaly(mAudioFile);
                }
            });
        }
    }

    private void doPaly(File mAudioFile) {
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mode = AudioTrack.MODE_STREAM;

        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        AudioTrack audioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat,
                Math.max(minBufferSize, 2048), mode);

        FileInputStream mFileInputStream = null;
        try {
            mFileInputStream = new FileInputStream(mAudioFile);
            int read;
            audioTrack.play();
            while ((read = mFileInputStream.read(mBuffer)) > 0) {
                int ret = audioTrack.write(mBuffer, 0, read);
                switch (ret) {
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioManager.ERROR_DEAD_OBJECT:
                        palyFaile();
                        break;
                    default:
                        break;
                }
            }
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
            palyFaile();
        } finally {
            mIsPalying = false;
            if (mFileInputStream != null) {
                closeQuietly(mFileInputStream);
            }
            audioTrack.stop();
            audioTrack.release();
        }
    }

    private void closeQuietly(FileInputStream mFileInputStream) {
        try {
            mFileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void palyFaile() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
