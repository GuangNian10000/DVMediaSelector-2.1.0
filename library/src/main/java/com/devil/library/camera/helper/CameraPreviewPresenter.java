package com.devil.library.camera.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.text.TextUtils;
import android.util.Log;

import com.cgfay.filter.glfilter.color.bean.DynamicColor;
import com.cgfay.filter.glfilter.makeup.bean.DynamicMakeup;
import com.cgfay.filter.glfilter.resource.FilterHelper;
import com.cgfay.filter.glfilter.resource.ResourceHelper;
import com.cgfay.filter.glfilter.resource.ResourceJsonCodec;
import com.cgfay.filter.glfilter.resource.bean.ResourceData;
import com.cgfay.filter.glfilter.resource.bean.ResourceType;
import com.cgfay.filter.glfilter.stickers.bean.DynamicSticker;
import com.cgfay.landmark.LandmarkEngine;
import com.cgfay.uitls.utils.BitmapUtils;
import com.cgfay.uitls.utils.FileUtils;
import com.devil.library.camera.listener.FocusCallback;
import com.devil.library.camera.listener.OnCaptureListener;
import com.devil.library.camera.listener.OnFpsListener;
import com.devil.library.camera.listener.OnFrameAvailableListener;
import com.devil.library.camera.listener.OnPreviewCaptureListener;
import com.devil.library.camera.listener.OnRecordStateListener;
import com.devil.library.camera.listener.OnSurfaceTextureListener;
import com.devil.library.camera.listener.PreviewCallback;
import com.devil.library.camera.media.HWMediaRecorder;
import com.devil.library.camera.params.AudioParams;
import com.devil.library.camera.params.CameraParam;
import com.devil.library.camera.params.MediaType;
import com.devil.library.camera.params.RecordInfo;
import com.devil.library.camera.params.VideoParams;
import com.devil.library.camera.render.CameraRenderer;
import com.devil.library.camera.view.DVCameraView;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import VideoHandle.EpEditor;
import VideoHandle.OnEditorListener;

//import VideoHandle.EpEditor;
//import VideoHandle.OnEditorListener;

/**
 * ???????????????presenter
 */
public class CameraPreviewPresenter implements PreviewCallback, OnCaptureListener, OnFpsListener,
        OnSurfaceTextureListener, OnFrameAvailableListener, OnRecordStateListener {
    private static final String TAG = "CameraPreviewPresenter";

    // ????????????
    private int mFilterIndex = 0;

    // ????????????
    private CameraParam mCameraParam;
    //????????????????????????
    private String videoSaveDir;

    private Activity mActivity;

    // ????????????
    private String mMusicPath;

    // ???????????????
    private final VideoParams mVideoParams;
    private final AudioParams mAudioParams;
    // ??????????????????
    private boolean mOperateStarted = false;

    // ????????????
    private long mMaxDuration;

    // ???????????????
    private HWMediaRecorder mHWMediaRecorder;

    // ??????????????????
    private RecordInfo mAudioInfo;
    // ??????????????????
    private RecordInfo mVideoInfo;

    // ????????????
    private CameraController mCameraController;

    // ?????????
    private final CameraRenderer mCameraRenderer;

    // ???????????????
    private OnPreviewCaptureListener captureListener;

    public CameraPreviewPresenter(DVCameraView target) {
        mCameraParam = CameraParam.getInstance();

        mCameraRenderer = new CameraRenderer(this);

        // ???????????????
        mVideoParams = new VideoParams();
        mAudioParams = new AudioParams();

    }

    /**
     * ??????????????????
     * @return
     */
    public CameraParam getCameraParam(){
        return mCameraParam;
    }
    /**
     * ?????????????????????
     */
    public boolean isFront(){
        return mCameraController.isFront();
    }

    public void setOnPreviewCaptureListener(OnPreviewCaptureListener captureListener){
        this.captureListener = captureListener;
    }

    public void onCreate(Activity activity) {
        mActivity = activity;
        mVideoParams.setVideoPath(VideoParams.getVideoTempPath(mActivity));
        mAudioParams.setAudioPath(AudioParams.getAudioTempPath(mActivity));

        mCameraRenderer.initRenderer();

        mCameraController = new CameraController(mActivity);
        mCameraController.setPreviewCallback(this);
        mCameraController.setOnFrameAvailableListener(this);
        mCameraController.setOnSurfaceTextureListener(this);

        if (com.cgfay.uitls.utils.BrightnessUtils.getSystemBrightnessMode(mActivity) == 1) {
            mCameraParam.brightness = -1;
        } else {
            mCameraParam.brightness = com.cgfay.uitls.utils.BrightnessUtils.getSystemBrightness(mActivity);
        }

    }

    public void onStart() {

    }

    public void onResume() {
        openCamera();
        mCameraParam.captureCallback = this;
        mCameraParam.fpsCallback = this;
    }

    public void onPause() {
        mCameraRenderer.onPause();
        closeCamera();
        mCameraParam.captureCallback = null;
        mCameraParam.fpsCallback = null;
    }

    public void onStop() {

    }

    public void onDestroy() {
        // ???????????????
        LandmarkEngine.getInstance().clearAll();
        if (mHWMediaRecorder != null) {
            mHWMediaRecorder.release();
            mHWMediaRecorder = null;
        }
        mActivity = null;
    }


    public Context getContext() {
        return mActivity;
    }


    public void onBindSharedContext(EGLContext context) {
        mVideoParams.setEglContext(context);
        Log.d(TAG, "onBindSharedContext: ");
    }


    public void onRecordFrameAvailable(int texture, long timestamp) {
        if (mOperateStarted && mHWMediaRecorder != null && mHWMediaRecorder.isRecording()) {
            mHWMediaRecorder.frameAvailable(texture, timestamp);
        }
    }


    public void onSurfaceCreated(SurfaceTexture surfaceTexture) {
        mCameraRenderer.onSurfaceCreated(surfaceTexture);
    }

    int width;
    int height;
    public void onSurfaceChanged(int width, int height) {
        mCameraRenderer.onSurfaceChanged(width, height);
        this.width = width;
        this.height = height;
    }


    public void onSurfaceDestroyed() {
        mCameraRenderer.onSurfaceDestroyed();
    }

    /**
     * ??????????????????????????????
     */
    public void setVideoSaveDir(String saveDir){
        this.videoSaveDir = saveDir;
    }
    /**
     * ????????????
     */
    public  void handleFocus(final Context context, final float x, final float y, final FocusCallback callback){
        if (mCameraController !=  null)
        mCameraController.handleFocus(context,x,y,callback);
    }

    /**
     * ???????????????
     * @param on ?????????????????????
     */
    public void setFlashLight(boolean on){
        if (mCameraController !=  null)
        mCameraController.setFlashLight(on);
    }

    /**
     * ?????????????????????
     * @param flashMode
     */
    public void setFlashMode(String flashMode) {
        if (mCameraController !=  null)
        mCameraController.setFlashMode(flashMode);
    }

    public void changeResource( ResourceData resourceData) {
        ResourceType type = resourceData.type;
        String unzipFolder = resourceData.unzipFolder;
        if (type == null) {
            return;
        }
        try {
            switch (type) {
                // ???????????????
                case FILTER: {
                    String folderPath = ResourceHelper.getResourceDirectory(mActivity) + File.separator + unzipFolder;
                    DynamicColor color = ResourceJsonCodec.decodeFilterData(folderPath);
                    mCameraRenderer.changeResource(color);
                    break;
                }

                // ??????
                case STICKER: {
                    String folderPath = ResourceHelper.getResourceDirectory(mActivity) + File.separator + unzipFolder;
                    DynamicSticker sticker = ResourceJsonCodec.decodeStickerData(folderPath);
                    mCameraRenderer.changeResource(sticker);
                    break;
                }

                // TODO ??????????????????
                case MULTI: {
                    break;
                }

                // ?????????????????????
                case NONE: {
                    mCameraRenderer.changeResource((DynamicSticker) null);
                    break;
                }

                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "parseResource: ", e);
        }
    }


    public void changeDynamicFilter(DynamicColor color) {
        mCameraRenderer.changeFilter(color);
    }


    public void changeDynamicMakeup(DynamicMakeup makeup) {
        mCameraRenderer.changeMakeup(makeup);
    }


    public void changeDynamicFilter(int filterIndex) {
        if (mActivity == null) {
            return;
        }
        String folderPath = FilterHelper.getFilterDirectory(mActivity) + File.separator +
                FilterHelper.getFilterList().get(filterIndex).unzipFolder;
        DynamicColor color = null;
        if (!FilterHelper.getFilterList().get(filterIndex).unzipFolder.equalsIgnoreCase("none")) {
            try {
                color = ResourceJsonCodec.decodeFilterData(folderPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mCameraRenderer.changeFilter(color);
    }


    public int previewFilter() {
        mFilterIndex--;
        if (mFilterIndex < 0) {
            int count = FilterHelper.getFilterList().size();
            mFilterIndex = count > 0 ? count - 1 : 0;
        }
        changeDynamicFilter(mFilterIndex);
        return mFilterIndex;
    }


    public int nextFilter() {
        mFilterIndex++;
        mFilterIndex = mFilterIndex % FilterHelper.getFilterList().size();
        changeDynamicFilter(mFilterIndex);
        return mFilterIndex;
    }


    public int getFilterIndex() {
        return mFilterIndex;
    }


    public void showCompare(boolean enable) {
        mCameraParam.showCompare = enable;
    }

    /**
     * ????????????
     */
    private void openCamera() {
        mCameraController.openCamera();
        calculateImageSize();
    }

    /**
     * ??????imageView ?????????
     */
    private void calculateImageSize() {
        int width;
        int height;
        if (mCameraController.getOrientation() == 90 || mCameraController.getOrientation() == 270) {
            width = mCameraController.getPreviewHeight();
            height = mCameraController.getPreviewWidth();
        } else {
            width = mCameraController.getPreviewWidth();
            height = mCameraController.getPreviewHeight();
        }
        mVideoParams.setVideoSize(width, height);
        mCameraRenderer.setTextureSize(width, height);
    }

    /**
     * ????????????
     */
    private void closeCamera() {
        mCameraController.closeCamera();
    }


    public void takePicture() {
        mCameraRenderer.takePicture();
    }


    public void switchCamera() {
        mCameraController.switchCamera();
    }

    /**
     * ????????????
     */
    public void doStartPreview(){
//        onSurfaceCreated(surfaceTexture);
//        onSurfaceChanged(width, height);
        mCameraController.doStartPreview();
    }

    /**
     * ????????????
     */
    public void doStopPreview(){
//        mCameraRenderer.release();
        mCameraController.doStopPreview();
    }

    public void startRecord() {
        if (mOperateStarted) {
            return;
        }
        if (mHWMediaRecorder == null) {
            mHWMediaRecorder = new HWMediaRecorder(this);
        }
        mHWMediaRecorder.startRecord(mVideoParams, mAudioParams);
        mOperateStarted = true;
    }


    public void stopRecord() {
        if (!mOperateStarted) {
            return;
        }
        mOperateStarted = false;
        if (mHWMediaRecorder != null) {
            mHWMediaRecorder.stopRecord();
        }
    }


    public void cancelRecord() {
        stopRecord();
    }


    public boolean isRecording() {
        return (mOperateStarted && mHWMediaRecorder != null && mHWMediaRecorder.isRecording());
    }


    public void setRecordAudioEnable(boolean enable) {
        if (mHWMediaRecorder != null) {
            mHWMediaRecorder.setEnableAudio(enable);
        }
    }


    public void setRecordSeconds(int seconds) {
        mMaxDuration = seconds * HWMediaRecorder.SECOND_IN_US;
        mVideoParams.setMaxDuration(mMaxDuration);
        mAudioParams.setMaxDuration(mMaxDuration);
    }


    public void enableEdgeBlurFilter(boolean enable) {
        mCameraRenderer.changeEdgeBlur(enable);
    }


    public void setMusicPath(String path) {
        mMusicPath = path;
    }

    /**
     * ??????????????????
     */
    public void onCameraOpened() {
        Log.d(TAG, "onCameraOpened: " +
                "orientation - " + mCameraController.getOrientation()
                + "width - " + mCameraController.getPreviewWidth()
                + ", height - " + mCameraController.getPreviewHeight());

    }

    // ------------------------- Camera ??????SurfaceTexture?????????????????? -------------------------------
    @Override
    public void onSurfaceTexturePrepared( SurfaceTexture surfaceTexture) {
        onCameraOpened();
        mCameraRenderer.bindInputSurfaceTexture(surfaceTexture);
    }

    // ---------------------------------- ???????????????????????? ------------------------------------------
    @Override
    public void onPreviewFrame(byte[] data) {
        Log.d(TAG, "onPreviewFrame: width - " + mCameraController.getPreviewWidth()
                + ", height - " + mCameraController.getPreviewHeight());
        mCameraRenderer.requestRender();
    }



    // ------------------------------ SurfaceTexture??????????????? --------------------------------------
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//        mCameraRenderer.requestRender();
    }

    // ---------------------------------- ?????? start ------------------------------------------
    @Override
    public void onRecordStart() {

    }

    @Override
    public void onRecording(long duration) {
//        float progress = duration * 1.0f / mVideoParams.getMaxDuration();
//        if (duration > mRemainDuration) {
//            stopRecord();
//        }
    }

    @Override
    public void onRecordFinish(RecordInfo info) {
        if (info.getType() == MediaType.AUDIO) {
            mAudioInfo = info;
        } else if (info.getType() == MediaType.VIDEO) {
            mVideoInfo = info;
        }
        if (mHWMediaRecorder == null) {
            return;
        }
        if (mHWMediaRecorder.enableAudio() && (mAudioInfo == null || mVideoInfo == null)) {
            return;
        }
        if (mHWMediaRecorder.enableAudio()){
            final File currentFile = FileUtils.createFile(videoSaveDir,"DVCamera_" + System.currentTimeMillis() + ".mp4");
            EpEditor.music(mVideoInfo.getFileName(), mAudioInfo.getFileName(), currentFile.getAbsolutePath(), 1, 1, new OnEditorListener() {
                @Override
                public void onSuccess() {

                    // ??????????????????
                    FileUtils.deleteFile(mAudioInfo.getFileName());
                    FileUtils.deleteFile(mVideoInfo.getFileName());
                    mAudioInfo = null;
                    mVideoInfo = null;
                    if (captureListener != null) {
                        captureListener.onPreviewCapture(currentFile.getAbsolutePath(), OnPreviewCaptureListener.MediaTypeVideo);
                    }
                }

                @Override
                public void onFailure() {
                    if (captureListener != null) {
                        captureListener.onPreviewCapture(null, OnPreviewCaptureListener.MediaTypeVideo);
                    }
                }

                @Override
                public void onProgress(float progress) {

                }
            });
        }

        if (mHWMediaRecorder != null) {
            mHWMediaRecorder.release();
            mHWMediaRecorder = null;
        }
    }


    // ------------------------------------ ?????????????????? --------------------------------------------

    @Override
    public void onCapture(Bitmap bitmap) {
        final File currentFile = FileUtils.createFile(videoSaveDir,"DVCamera_" + System.currentTimeMillis() + ".jpg");
        BitmapUtils.saveBitmap(currentFile.getAbsolutePath(), bitmap);
        if (captureListener != null) {
            captureListener.onPreviewCapture(currentFile.getAbsolutePath(), OnPreviewCaptureListener.MediaTypePicture);
        }
    }

    // ------------------------------------ ??????fps?????? ------------------------------------------

    /**
     * fps????????????
     *
     * @param fps
     */
    @Override
    public void onFpsCallback(float fps) {

    }
}