package com.liuwei.audiodemo;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: liuwei
 * @Create: 2019/1/9 9:57
 * @Description:
 */
public class MediaCodecActivity extends Activity {
    private static final String TAG = MediaCodecActivity.class.getSimpleName();
    TextView textView;
    Button recoderBtn;
    Button palyBtn;

    private ExecutorService mExecutorService;
    private AudioRecord mAudioRecorder;
    private AudioTrack audioTrack;
    private MediaCodec mAudioEncoder;//音频编码器
    private MediaCodec mAudioDecoder;//音频解码器
    private MediaExtractor mMediaExtractor;

    byte[] mBuffer;
    FileOutputStream mFileOutputStream;
    private BufferedOutputStream mAudioBos;
    File mAudioFile;
    String mFilePath;
    long start;
    long end;
    int count = 0;

    private volatile boolean mIsRecording;
    private volatile boolean mIsPalying;
    private volatile boolean codeOver;

    private ArrayBlockingQueue<byte[]> queue;
    private int MAX_BUFFER_SIZE = 8192;
    private MediaCodec.BufferInfo mAudioEncodeBufferInfo;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        init();
    }

    private void init() {
        textView = findViewById(R.id.textViewStream);
        mBuffer = new byte[2048];
        mExecutorService = Executors.newFixedThreadPool(2);
        queue = new ArrayBlockingQueue<byte[]>(10);
        recoderBtn = findViewById(R.id.buttonStream);
        palyBtn = findViewById(R.id.button4);
    }

    /**
     * 开始按钮 点击开始录音
     */
    public void start(View view) {
        if (mIsRecording) {
            mIsRecording = false;
        } else {
            textView.setText(textView.getText() + "\n开始录音。。。");
            recoderBtn.setText("结束录音");
            initAudioEncoder(); // 初始化编码器
            initAudioRecord(); // 初始化AudioRecord
            mIsRecording = true;
            // 开启录音线程
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    startRecorder();
                }
            });
            // 开启编码线程
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    encodePCM();
                }
            });
        }
    }

    /**
     * 初始化编码器
     */
    private void initAudioEncoder() {
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_BUFFER_SIZE);
            mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mAudioEncoder == null) {
            Log.e(TAG, "create mediaEncode failed");
            return;
        }

        mAudioEncoder.start(); // 启动MediaCodec,等待传入数据
        encodeInputBuffers = mAudioEncoder.getInputBuffers();
        encodeOutputBuffers = mAudioEncoder.getOutputBuffers();
        mAudioEncodeBufferInfo = new MediaCodec.BufferInfo();
    }

    /**
     * 初始化解码器
     */
    private void initAudioDecoder() {
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(mFilePath);

            MediaFormat format = mMediaExtractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio")) {//获取音频轨道
                mMediaExtractor.selectTrack(0);//选择此音频轨道

                format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 0);
                format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
                format.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, 0);

                mAudioDecoder = MediaCodec.createDecoderByType(mime);//创建Decode解码器
                mAudioDecoder.configure(format, null, null, 0);
            } else {
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mAudioDecoder == null) {
            Log.e(TAG, "mAudioDecoder is null");
            return;
        }

        mAudioDecoder.start();//启动MediaCodec ，等待传入数据

    }

    private void initAudioRecord() {
        int audioSource = MediaRecorder.AudioSource.MIC;
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        mAudioRecorder = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, Math.max(minBufferSize, 2048));
    }

    private void initAudioTrack() {
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mode = AudioTrack.MODE_STREAM;

        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        audioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat,
                Math.max(minBufferSize, 2048), mode);
        audioTrack.play();
    }

    /**
     * 获取音频数据
     */
    private void startRecorder() {
        try {
            mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/RecorderTest/" +
                    System.currentTimeMillis() + ".aac";
            mAudioFile = new File(mFilePath);
            if (!mAudioFile.getParentFile().exists()) {
                mAudioFile.getParentFile().mkdirs();
            }
            mAudioFile.createNewFile();
            mFileOutputStream = new FileOutputStream(mAudioFile);
            mAudioBos = new BufferedOutputStream(mFileOutputStream, 200 * 1024);
            mAudioRecorder.startRecording();

            start = System.currentTimeMillis();

            while (mIsRecording) {
                int read = mAudioRecorder.read(mBuffer, 0, 2048);
                if (read > 0) {
                    byte[] audio = new byte[read];
                    System.arraycopy(mBuffer, 0, audio, 0, read);
                    putPCMData(audio); // PCM数据放入队列，等待编码
                }
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
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
        stopRecorder();
        stopPlay();
        mExecutorService.shutdownNow();
    }


    private boolean stopRecorder() {
        try {
            if (mAudioBos != null) {
                mAudioBos.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mAudioBos != null) {
                try {
                    mAudioBos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    mAudioBos = null;
                }
            }
        }
        if (mAudioRecorder != null) {
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
        }

        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        end = System.currentTimeMillis();

        final int second = (int) ((end - start) / 1000);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recoderBtn.setText("开始录音");

                if (second > 3) {
                    textView.setText(textView.getText() + "\n录音成功，时间为" + second + "秒");
                } else {
                    textView.setText(textView.getText() + "\n录音失败，时间少于三秒");
                    mAudioFile.deleteOnExit();
                }
            }
        });

        return true;
    }


    /**
     * 将PCM数据存入队列
     *
     * @param pcmChunk PCM数据块
     */
    private void putPCMData(byte[] pcmChunk) {
        Log.e(TAG, "putPCMData");
        try {
            queue.put(pcmChunk);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 在Container中队列取出PCM数据
     *
     * @return PCM数据块
     */
    private byte[] getPCMData() {
        try {
            if (queue.isEmpty()) {
                return null;
            }
            return queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 编码PCM
     */
    private void encodePCM() {
        int inputIndex;
        ByteBuffer inputBuffer;
        int outputIndex;
        ByteBuffer outputBuffer;
        byte[] chunkAudio;
        int outBitSize;
        int outPacketSize;
        byte[] chunkPCM;

        while (mIsRecording || !queue.isEmpty()) {
            chunkPCM = getPCMData();//获取解码器所在线程输出的数据 代码后边会贴上
            if (chunkPCM == null) {
                continue;
            }
            inputIndex = mAudioEncoder.dequeueInputBuffer(-1);//同解码器
            if (inputIndex >= 0) {
                inputBuffer = encodeInputBuffers[inputIndex];//同解码器
                inputBuffer.clear();//同解码器
                inputBuffer.limit(chunkPCM.length);
                inputBuffer.put(chunkPCM);//PCM数据填充给inputBuffer
                mAudioEncoder.queueInputBuffer(inputIndex, 0, chunkPCM.length, 0, 0);//通知编码器 编码
            }

            outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);
            while (outputIndex >= 0) {
                outBitSize = mAudioEncodeBufferInfo.size;
                outPacketSize = outBitSize + 7;//7为ADTS头部的大小
                outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
                outputBuffer.position(mAudioEncodeBufferInfo.offset);
                outputBuffer.limit(mAudioEncodeBufferInfo.offset + outBitSize);
                chunkAudio = new byte[outPacketSize];
                addADTStoPacket(44100, chunkAudio, outPacketSize);//添加ADTS
                outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7
                outputBuffer.position(mAudioEncodeBufferInfo.offset);
                try {
                    mAudioBos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mAudioEncoder.releaseOutputBuffer(outputIndex, false);
                outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);
            }
        }

        stopRecorder();
    }

    /**
     * 添加ADTS头
     *
     * @param packet
     * @param packetLen
     */
    public static void addADTStoPacket(int sampleRateType, byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int chanCfg = 2; // CPE

        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (sampleRateType << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }


    /**
     * ======================= 解码播放 ==========================
     */

    public void streamPlay(View view) {
        if (mAudioFile == null) {
            textView.setText(textView.getText() + "\n文件为空，请先录音");
            return;
        }

        if (!mIsPalying) {
            textView.setText(textView.getText() + "\n开始播放。。。");
            palyBtn.setText("结束播放");
            mIsPalying = true;
            initAudioDecoder();
            initAudioTrack();
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    // 解码
                    decodeAndPlay();
                }
            });
        } else {
            mIsPalying = false;
        }

    }


    private void decodeAndPlay() {
        boolean isFinish = false;
        MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();
        while (!isFinish && mIsPalying) {
            int inputIdex = mAudioDecoder.dequeueInputBuffer(10000);
            if (inputIdex < 0) {
                isFinish = true;
            }
            ByteBuffer inputBuffer = mAudioDecoder.getInputBuffer(inputIdex);
            inputBuffer.clear();
            int samplesize = mMediaExtractor.readSampleData(inputBuffer, 0);
            if (samplesize > 0) {
                mAudioDecoder.queueInputBuffer(inputIdex, 0, samplesize, 0, 0);
                mMediaExtractor.advance();
            } else {
                isFinish = true;
            }
            int outputIndex = mAudioDecoder.dequeueOutputBuffer(decodeBufferInfo, 10000);

            ByteBuffer outputBuffer;
            byte[] chunkPCM;
            //每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
            while (outputIndex >= 0) {
                outputBuffer = mAudioDecoder.getOutputBuffer(outputIndex);
                chunkPCM = new byte[decodeBufferInfo.size];
                outputBuffer.get(chunkPCM);
                outputBuffer.clear();
                audioTrack.write(chunkPCM, 0, decodeBufferInfo.size);
                mAudioDecoder.releaseOutputBuffer(outputIndex, false);
                outputIndex = mAudioDecoder.dequeueOutputBuffer(decodeBufferInfo, 10000);
            }
        }
        stopPlay();
    }


    private void stopPlay() {
        mIsPalying = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                palyBtn.setText("播放");
                textView.setText(textView.getText() + "\n播放结束");
            }
        });
        if (mAudioDecoder != null) {
            mAudioDecoder.stop();
            mAudioDecoder.release();
            mAudioDecoder = null;
        }
    }
}
