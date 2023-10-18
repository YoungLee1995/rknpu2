package com.rockchip.gpadc.demo.libs;

import static com.rockchip.gpadc.demo.rga.HALDefine.CAMERA_PREVIEW_HEIGHT;
import static com.rockchip.gpadc.demo.rga.HALDefine.CAMERA_PREVIEW_WIDTH;
import static com.rockchip.gpadc.demo.yolo.PostProcess.INPUT_CHANNEL;

import static java.lang.Thread.sleep;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.TextView;
import android.widget.Toast;

import com.rockchip.gpadc.demo.ImageBufferQueue;
import com.rockchip.gpadc.demo.InferenceResult;
import com.rockchip.gpadc.demo.R;
import com.rockchip.gpadc.demo.yolo.InferenceWrapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.DecimalFormat;

public class Inference {

    private final String TAG = "rkyolo";

    private String mModelName = "yolov5s.rknn";
    private InferenceWrapper mInferenceWrapper;
    private String fileDirPath;
    private Context context;
    private InferenceResult mInferenceResult = new InferenceResult();
    private ImageBufferQueue mImageBufferQueue;
    private volatile boolean mStopInference = false;

    private TextView mFpsNum1;
    private TextView mFpsNum2;
    private TextView mFpsNum3;
    private TextView mFpsNum4;


    public Inference(Context context) {
        this.context=context;
        fileDirPath = this.context.getCacheDir().getAbsolutePath();
        initializeInference();
    }

    private void initializeInference() {
        // Initialize inference related components here
        mInferenceWrapper = new InferenceWrapper();
        createModelFile();
        initModel();
        // Now you can use mInferenceWrapper to perform inference on images.
    }

    private void createModelFile() {
        createFile(mModelName, R.raw.yolov5s_rk3588);
    }

    private void createFile(String fileName, int id) {
        String filePath = fileDirPath + "/" + fileName;
        try {
            File dir = new File(fileDirPath);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(filePath);

            if (!file.exists() || isFirstRun()) {

                InputStream ins = this.context.getResources().openRawResource(id);
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[8192];
                int count;

                while ((count = ins.read(buffer)) > 0) {
                    fos.write(buffer, 0, count);
                }

                fos.close();
                ins.close();

                Log.d(TAG, "Create " + filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");

        startTrack();

    }

    private boolean isFirstRun() {
        SharedPreferences sharedPreferences = context.getSharedPreferences("setting", context.MODE_PRIVATE);
        boolean isFirstRun = sharedPreferences.getBoolean("isFirstRun", true);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (isFirstRun) {
            editor.putBoolean("isFirstRun", false);
            editor.commit();
        }

        return isFirstRun;
    }

    private void initModel() {
        String paramPath = fileDirPath + "/" + mModelName;

        try {
            mInferenceWrapper.initModel(CAMERA_PREVIEW_HEIGHT, CAMERA_PREVIEW_WIDTH, INPUT_CHANNEL, paramPath);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void startTrack() {
        mInferenceResult.reset();
        mImageBufferQueue = new ImageBufferQueue(3, CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT);
        mStopInference = false;
        mInferenceThread = new Thread(mInferenceRunnable);
        mInferenceThread.start();
    }

    private void stopTrack() {

        mStopInference = true;
        try {
            if (mInferenceThread != null) {
                mInferenceThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mImageBufferQueue != null) {
            mImageBufferQueue.release();
            mImageBufferQueue = null;
        }
    }

    private Thread mInferenceThread;
    private Runnable mInferenceRunnable = new Runnable() {
        public void run() {

            int count = 0;
            long oldTime = System.currentTimeMillis();
            long currentTime;

            updateMainUI(1, 0);

            String paramPath = fileDirPath + "/" + mModelName;

            try {
                mInferenceWrapper.initModel(CAMERA_PREVIEW_HEIGHT, CAMERA_PREVIEW_WIDTH, INPUT_CHANNEL, paramPath);mInferenceWrapper.initModel(CAMERA_PREVIEW_HEIGHT, CAMERA_PREVIEW_WIDTH, INPUT_CHANNEL, paramPath);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }


            while (!mStopInference) {
                ImageBufferQueue.ImageBuffer buffer = mImageBufferQueue.getReadyBuffer();

                if (buffer == null) {
                    try {
//                        Log.w(TAG, "buffer is null.");
                        sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                InferenceResult.OutputBuffer outputs = mInferenceWrapper.run(buffer.mImage);

                mInferenceResult.setResult(outputs);

                mImageBufferQueue.releaseBuffer(buffer);

                if (++count >= 30) {
                    currentTime = System.currentTimeMillis();

                    float fps = count * 1000.f / (currentTime - oldTime);

//                    Log.d(TAG, "current fps = " + fps);

                    oldTime = currentTime;
                    count = 0;
                    updateMainUI(0, fps);

                }

                updateMainUI(1, 0);
            }

            mInferenceWrapper.deinit();
        }
    };

    private void updateMainUI(int type, Object data) {
        Message msg = mHandler.obtainMessage();
        msg.what = type;
        msg.obj = data;
        mHandler.sendMessage(msg);
    }

    // UI线程，用于更新处理结果
    private Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            if (msg.what == 0) {
                float fps = (float) msg.obj;

                DecimalFormat decimalFormat = new DecimalFormat("00.00");
                String fpsStr = decimalFormat.format(fps);
                mFpsNum1.setText(String.valueOf(fpsStr.charAt(0)));
                mFpsNum2.setText(String.valueOf(fpsStr.charAt(1)));
                mFpsNum3.setText(String.valueOf(fpsStr.charAt(3)));
                mFpsNum4.setText(String.valueOf(fpsStr.charAt(4)));
            } else {
                //showTrackSelectResults();
            }
        }
    };


    private String getPlatform() {
        String platform = null;
        try {
            Class<?> classType = Class.forName("android.os.SystemProperties");
            Method getMethod = classType.getDeclaredMethod("get", new Class<?>[]{String.class});
            platform = (String) getMethod.invoke(classType, new Object[]{"ro.board.platform"});
        } catch (Exception e) {
            e.printStackTrace();
        }
        return platform;
    }
}
