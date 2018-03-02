package com.liuwei.audiodemo;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by liuwei on 2018/2/28.
 */

public class FileActivity extends Activity {

    TextView textView;

    private ExecutorService mExecutorService;
    MediaRecorder mMediaRecorder;
    File mAudioFile;
    long start;
    long end;

    private volatile boolean mIsPalying;
    MediaPlayer mMediaPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        textView = (TextView) findViewById(R.id.textViewFile);
        Button button = (Button) findViewById(R.id.buttonFile);

        mExecutorService = Executors.newSingleThreadExecutor();


        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopRecode();
                        break;
                }

                return true;
            }
        });
    }

    private void startRecord() {
        textView.setText("正在说话");
        textView.setBackgroundColor(123456);

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {

                releaseRecorder();

                if (!doStart()) {
                    recoderFail();
                }
            }
        });

    }


    private void stopRecode() {
        textView.setText("按住说话");
        textView.setBackgroundColor(123456);

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if (!doStop()) {
                    recoderFail();
                }
                releaseRecorder();

            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
        releaseRecorder();
        stopPlay();
    }


    private boolean doStart() {
        mMediaRecorder = new MediaRecorder();


        try {
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/RecorderTest/" +
                    System.currentTimeMillis() + ".m4a");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();

            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setAudioSamplingRate(44100);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            mMediaRecorder.setAudioEncodingBitRate(96000);

            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());

            mMediaRecorder.prepare();
            mMediaRecorder.start();

            start = System.currentTimeMillis();

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
        return true;
    }

    private void recoderFail() {
        mAudioFile = null;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this, "失败", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private boolean doStop() {
        mMediaRecorder.stop();
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
        return true;
    }

    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    public void play(View view) {
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
        mMediaPlayer = new MediaPlayer();
        try {
            mMediaPlayer.setDataSource(mAudioFile.getAbsolutePath());
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    stopPlay();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                    palyFaile();
                    stopPlay();
                    return true;
                }
            });

            mMediaPlayer.setVolume(1, 1);
            mMediaPlayer.setLooping(false);

            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            palyFaile();
            stopPlay();
        }
    }

    private void palyFaile() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopPlay() {
        mIsPalying = false;
        if (mMediaPlayer != null) {
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}
