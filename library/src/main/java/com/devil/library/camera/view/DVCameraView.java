package com.devil.library.camera.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import com.cgfay.filter.glfilter.color.bean.DynamicColor;
import com.cgfay.filter.glfilter.resource.FilterHelper;
import com.cgfay.filter.glfilter.resource.ResourceJsonCodec;
import com.cgfay.filter.glfilter.resource.bean.ResourceData;
import com.devil.library.camera.CaptureLayout;
import com.devil.library.camera.FoucsView;
import com.devil.library.camera.helper.CameraPreviewPresenter;
import com.devil.library.camera.listener.CaptureListener;
import com.devil.library.camera.listener.ClickListener;
import com.devil.library.camera.listener.ErrorListener;
import com.devil.library.camera.listener.FocusCallback;
import com.devil.library.camera.listener.JCameraListener;
import com.devil.library.camera.listener.OnPreviewCaptureListener;
import com.devil.library.camera.listener.TypeListener;
import com.devil.library.camera.util.FileUtil;
import com.devil.library.camera.util.LogUtil;
import com.devil.library.camera.util.ScreenUtils;
import com.devil.library.media.R;
import com.devil.library.media.enumtype.ImageFilterType;
import com.devil.library.media.listener.OnFilterActionListener;
import com.devil.library.media.view.FilterToolView;
import com.devil.library.media.view.TipLoadDialog;

import java.io.File;


/**
 * camera??????????????????????????????
 */
public class DVCameraView extends FrameLayout implements View.OnClickListener {
//    private static final String TAG = "JCameraView";
    public static final int BUTTON_STATE_ONLY_CAPTURE = 0x101;      //????????????
    public static final int BUTTON_STATE_ONLY_RECORDER = 0x102;     //????????????
    public static final int BUTTON_STATE_BOTH = 0x103;              //???????????????

    //???????????????
    private boolean flashStatus = false;

    //???????????????????????????
    private static final int TYPE_PICTURE = 0x001;
    private static final int TYPE_VIDEO = 0x002;
    private static final int TYPE_SHORT = 0x003;
    private static final int TYPE_DEFAULT = 0x004;
    //??????????????????
    private int currentTakeType = TYPE_DEFAULT;


    //????????????
    private JCameraListener jCameraListener;
    private ClickListener leftClickListener;
    private ClickListener rightClickListener;

    private Context mContext;
    //????????????
    private View mContentView;
    private DVTextureView mTextureView;
    private ImageView mPhoto;
    private ImageView mSwitchCamera;
    private ImageView mFlashLamp;
    private CaptureLayout mCaptureLayout;
    private FoucsView mFoucsView;
    private MediaPlayer mMediaPlayer;

    private int layout_width;

    private Bitmap captureBitmap;   //???????????????
    private String firstFramePath;  //?????????????????????????????????
    private Bitmap firstFrame;      //???????????????????????????
    private String videoUrl;        //????????????URL


    //??????????????????????????????
    private int iconSize = 0;       //????????????
    private int iconMargin = 0;     //????????????
    private int iconSrc = 0;        //????????????
    private int iconLeft = 0;       //?????????
    private int iconRight = 0;      //?????????
    private int duration = 0;       //????????????

    //????????????
    private CameraPreviewPresenter mPreviewPresenter;
    //???????????????????????????
    private String saveVideoDirPath = Environment.getExternalStorageDirectory().getPath() + "/media/";
    //??????
    private FilterToolView mFilterLayout;
    private ImageView image_filter;

    //??????
    private ImageView image_beauty;
    //????????????
    private LinearLayout line_beautySeekBar;
    private SeekBar sb_adjust;
    private SeekBar sb_complexionLevel;
    //??????icon??????
    private LinearLayout line_topIcon;
    //?????????
    private TipLoadDialog loadDialog;
    //????????????????????????
    private boolean isRecordTooShort;
    //???????????????Surface
    private SurfaceView mPlayerSurface;;
    //?????????????????????
    private boolean flashLightEnable;


    public DVCameraView(Context context) {
        this(context, null);
    }

    public DVCameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DVCameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        //get AttributeSet
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.DVCameraView, defStyleAttr, 0);
        iconSize = a.getDimensionPixelSize(R.styleable.DVCameraView_iconSize, (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 35, getResources().getDisplayMetrics()));
        iconMargin = a.getDimensionPixelSize(R.styleable.DVCameraView_iconMargin, (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 15, getResources().getDisplayMetrics()));
        iconSrc = a.getResourceId(R.styleable.DVCameraView_iconSrc, R.mipmap.icon_dv_switch_camera);
        iconLeft = a.getResourceId(R.styleable.DVCameraView_iconLeft, 0);
        iconRight = a.getResourceId(R.styleable.DVCameraView_iconRight, 0);
        duration = a.getInteger(R.styleable.DVCameraView_duration_max, 10 * 1000);       //??????????????????10s
        a.recycle();
        initData();
        initView();

    }

    /**
     * ????????????????????????
     * @param maxDuration ?????????
     */
    public void setMaxDuration(int maxDuration){
        this.duration = maxDuration * 1000;
        if (mCaptureLayout != null){
            mCaptureLayout.setDuration(duration);
        }
        if (mPreviewPresenter != null){
            mPreviewPresenter.setRecordSeconds(this.duration / 1000);
        }

    }

    private void initData() {
        layout_width = ScreenUtils.getScreenWidth(mContext);
    }

    /**
     * ?????????view
     */
    private void initView() {
        setWillNotDraw(false);
        mContentView = LayoutInflater.from(mContext).inflate(R.layout.view_dv_camera, this);

        initPreviewSurface();
        initFilterView();
        //??????????????????
        line_topIcon = mContentView.findViewById(R.id.line_topIcon);


        mPhoto = (ImageView) mContentView.findViewById(R.id.image_photo);
        mSwitchCamera = (ImageView) mContentView.findViewById(R.id.image_switch);
        mSwitchCamera.setImageResource(iconSrc);
        mFlashLamp = (ImageView) mContentView.findViewById(R.id.image_flash);
        setFlashRes();
        mFlashLamp.setOnClickListener(this);
        mCaptureLayout = (CaptureLayout) mContentView.findViewById(R.id.capture_layout);
        mCaptureLayout.setDuration(duration);
        mCaptureLayout.setIconSrc(iconLeft, iconRight);
        mFoucsView = (FoucsView) mContentView.findViewById(R.id.fouce_view);
        //??????????????????
        initListener();
    }

    /**
     * ???????????????view
     */
    private void initPreviewSurface() {
        mPreviewPresenter = new CameraPreviewPresenter(this);
        mPreviewPresenter.setVideoSaveDir(saveVideoDirPath);
        mPreviewPresenter.setRecordSeconds(this.duration / 1000);
        mTextureView = mContentView.findViewById(R.id.mTextureView);

        //????????????
        mPlayerSurface = mContentView.findViewById(R.id.mPlayerSurface);

    }

    /**
     * ?????????????????????view
     */
    private void initFilterView(){
        mFilterLayout = findViewById(R.id.layout_filter);
        mFilterLayout.setOnlyShowFilter();

        image_filter = mContentView.findViewById(R.id.image_filter);
        image_filter.setOnClickListener(this);

        image_beauty = mContentView.findViewById(R.id.image_beauty);
        image_beauty.setOnClickListener(this);

        //??????????????????
        line_beautySeekBar = mContentView.findViewById(R.id.line_beautySeekBar);
        sb_adjust = mContentView.findViewById(R.id.sb_adjust);
        sb_complexionLevel = mContentView.findViewById(R.id.sb_complexionLevel);

    }

    /**
     * ??????????????????
     */
    private void initListener(){
        //??????
        mFilterLayout.setOnFilterActionListener(onFilterChangeListener);

        //??????
        sb_adjust.setOnSeekBarChangeListener(beautyParamsChangeListener);
        sb_complexionLevel.setOnSeekBarChangeListener(beautyParamsChangeListener);

        //???????????????view
        mTextureView.addMultiClickListener(mMultiClickListener);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        //????????????
        mPlayerSurface.getHolder().addCallback(surfaceHolderCallback);

        //???????????????
        mSwitchCamera.setOnClickListener(this);

        //?????? ??????
        mCaptureLayout.setCaptureListener(new CaptureListener() {
            @Override
            public void takePictures() {
                currentTakeType = TYPE_PICTURE;
                line_topIcon.setVisibility(INVISIBLE);
                takePhoto();
            }

            @Override
            public void recordStart() {
                currentTakeType = TYPE_VIDEO;
                line_topIcon.setVisibility(INVISIBLE);
                takePhoto();
                mPreviewPresenter.startRecord();
                mCaptureLayout.setTextWithAnimation("????????????");
            }

            @Override
            public void recordShort(final long time) {
                currentTakeType = TYPE_SHORT;
                mCaptureLayout.setTextWithAnimation("??????????????????");
                //??????????????????
                mPreviewPresenter.stopRecord();
                //?????????????????????
                isRecordTooShort = true;
            }

            @Override
            public void recordEnd(long time) {
//                machine.stopRecord(false, time);
                currentTakeType = TYPE_VIDEO;
                mPreviewPresenter.stopRecord();
                showPicture(firstFrame,true);
                showLoadingDialog();

            }

            @Override
            public void recordZoom(float zoom) {

            }

            @Override
            public void recordError() {
                currentTakeType = TYPE_DEFAULT;
                if (errorListener != null) {
                    errorListener.AudioPermissionError();
                }
            }
        });

        //?????????????????????????????????
        mPreviewPresenter.setOnPreviewCaptureListener(captureListener);

        //?????? ??????
        mCaptureLayout.setTypeListener(new TypeListener() {
            @Override
            public void cancel() {

                if (currentTakeType == TYPE_VIDEO){
                    //??????????????????
                    stopVideo();
                    //??????????????????
                    if (!TextUtils.isEmpty(videoUrl)) FileUtil.deleteFile(videoUrl);
                }else{
                    mPreviewPresenter.onResume();
                }
                //??????????????????
                resetState();
                //                setTip("???????????????????????????");
                mCaptureLayout.setTextWithAnimation(mCaptureLayout.getDefaultStateTip());
            }

            @Override
            public void confirm() {
                confirmState();
            }
        });
        //??????
        mCaptureLayout.setLeftClickListener(new ClickListener() {
            @Override
            public void onClick() {
                if (leftClickListener != null) {
                    leftClickListener.onClick();
                }
            }
        });
        mCaptureLayout.setRightClickListener(new ClickListener() {
            @Override
            public void onClick() {
                if (rightClickListener != null) {
                    rightClickListener.onClick();
                }
            }
        });
    }

    //??????????????????
    private SeekBar.OnSeekBarChangeListener  beautyParamsChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float realProgress = progress / 100f;
            if (seekBar.getId() == R.id.sb_adjust){//??????
                mPreviewPresenter.getCameraParam().beauty.beautyIntensity = realProgress;
            }else if(seekBar.getId() == R.id.sb_complexionLevel){//??????
                mPreviewPresenter.getCameraParam().beauty.complexionIntensity = realProgress;
            }
            if (sb_adjust.getProgress() == 0 && sb_complexionLevel.getProgress() == 0){
                image_beauty.setImageResource(R.mipmap.editor_beauty_normal);
            }else{
                image_beauty.setImageResource(R.mipmap.editor_beauty_pressed);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    //?????????????????????????????????
    private OnPreviewCaptureListener captureListener = new OnPreviewCaptureListener() {
        @Override
        public void onPreviewCapture(String path, int type) {
            //????????????????????????????????????view????????????????????????
            if (TextUtils.isEmpty(path)){
                post(()->{
                    //???????????????????????????
                    resetState();
                });
                return;
            }
            if (type == OnPreviewCaptureListener.MediaTypePicture){//??????
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (currentTakeType == TYPE_VIDEO){
                    firstFramePath = path;
                    firstFrame = bitmap;
                }else{
                    showPicture(bitmap,true);
                    captureBitmap = bitmap;
                }
            }else{//??????
                videoUrl = path;
                if (isRecordTooShort){//????????????????????????????????????
                    post(()->{
                        //???????????????????????????
                        resetState();
                    });
                    //?????????????????????
                    FileUtil.deleteFile(path);
                    FileUtil.deleteFile(firstFramePath);
                    isRecordTooShort = false;
                }else{
                    //???????????????
                    post(()->{
                        mPreviewPresenter.doStopPreview();
                        mPlayerSurface.setVisibility(VISIBLE);
                    });
                }
            }
        }
    };

    //????????????
    private OnFilterActionListener onFilterChangeListener = new OnFilterActionListener() {
        @Override
        public void onAdjustChange(ImageFilterType type, float value) {

        }

        @Override
        public void onCloseFilter() {
            if (mCaptureLayout.getVisibility() == View.GONE){
                mCaptureLayout.setVisibility(View.VISIBLE);
            }
            if (mFilterLayout.getVisibility() == View.VISIBLE){
                mFilterLayout.setVisibility(View.GONE);
            }
        }

        @Override
        public void onColorFilterChanged(ResourceData resourceData) {
            if (!resourceData.name.equals("none")) {
                String folderPath = FilterHelper.getFilterDirectory(mContext) + File.separator + resourceData.unzipFolder;
                DynamicColor color = null;
                try {
                    color = ResourceJsonCodec.decodeFilterData(folderPath);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mPreviewPresenter.changeDynamicFilter(color);
            } else {
                mPreviewPresenter.changeDynamicFilter(null);
            }
        }
    };

    /**
     * ?????????????????????
     */
    private DVTextureView.OnMultiClickListener mMultiClickListener = new DVTextureView.OnMultiClickListener() {

        @Override
        public void onSurfaceSingleClick(final float x, final float y) {
            // ??????
            //????????????????????????????????????????????????????????????
            if (mFilterLayout.getVisibility() == View.VISIBLE || line_beautySeekBar.getVisibility() == View.VISIBLE){
                hideBeautySeekBar();
                hideFilterLayout();
                if (mCaptureLayout.getVisibility() == View.GONE){
                    mCaptureLayout.setVisibility(View.VISIBLE);
                }
            }else{
                //?????????????????????
                setFocusViewWidthAnimation(x, y);
            }

        }

        @Override
        public void onSurfaceDoubleClick(float x, float y) {
            // ??????
        }

    };
    // ---------------------------- TextureView SurfaceTexture?????? ---------------------------------
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mPreviewPresenter.onSurfaceCreated(surface);
            mPreviewPresenter.onSurfaceChanged(width, height);

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            mPreviewPresenter.onSurfaceChanged(width, height);

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mPreviewPresenter.onSurfaceDestroyed();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    // ---------------------------- ????????????????????? SurfaceView?????? ---------------------------------
    SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            //???????????????????????????????????????????????????????????????
            playVideo(firstFrame,videoUrl);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    };


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * ???????????????
     */
    private void showLoadingDialog(){
        if (loadDialog == null){
            loadDialog = new TipLoadDialog(getContext());
            loadDialog.setCancelable(false);
            loadDialog.setCanceledOnTouchOutside(false);
            loadDialog.setFullScreenFlags();
        }
        loadDialog.setMsgAndType("???????????????...",TipLoadDialog.ICON_TYPE_LOADING).show();
    }

    /**
     * ???????????????
     */
    private void dismissLoadingDialog(){
        if (loadDialog != null && loadDialog.isShowing())
        loadDialog.dismiss();
    }

    /**
     * ???????????????????????????
     * @param visibility
     */
    public void setFlashLightVisibility(int visibility ){
        if (mFlashLamp.getVisibility() != visibility){
            mFlashLamp.setVisibility(visibility);
        }
    }
    /**
     * ???????????????????????????
     * @param enable
     */
    public void setFlashLightEnable(boolean enable){
        flashLightEnable = enable;
        if (!enable){
            setFlashLightVisibility(View.GONE);
        }
    }

    /**
     * ????????????
     */
    private void takePhoto(){
        mPreviewPresenter.takePicture();
    }


    //????????????????????????
    private void setFocusViewWidthAnimation(float x, float y) {
        handlerFoucs(x,y);
        mPreviewPresenter.handleFocus(getContext(),x,y, new FocusCallback() {
            @Override
            public void focusSuccess() {
                mFoucsView.setVisibility(INVISIBLE);
            }
        });
    }

    /**
     * ??????????????????
     */
    public boolean handlerFoucs(float x, float y) {
        if (y > mCaptureLayout.getTop()) {
            return false;
        }
        mFoucsView.setVisibility(VISIBLE);
        if (x < mFoucsView.getWidth() / 2) {
            x = mFoucsView.getWidth() / 2;
        }
        if (x > layout_width - mFoucsView.getWidth() / 2) {
            x = layout_width - mFoucsView.getWidth() / 2;
        }
        if (y < mFoucsView.getWidth() / 2) {
            y = mFoucsView.getWidth() / 2;
        }
        if (y > mCaptureLayout.getTop() - mFoucsView.getWidth() / 2) {
            y = mCaptureLayout.getTop() - mFoucsView.getWidth() / 2;
        }
        mFoucsView.setX(x - mFoucsView.getWidth() / 2);
        mFoucsView.setY(y - mFoucsView.getHeight() / 2);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mFoucsView, "scaleX", 1, 0.6f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mFoucsView, "scaleY", 1, 0.6f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(mFoucsView, "alpha", 1f, 0.4f, 1f, 0.4f, 1f, 0.4f, 1f);
        AnimatorSet animSet = new AnimatorSet();
        animSet.play(scaleX).with(scaleY).before(alpha);
        animSet.setDuration(400);
        animSet.start();
        return true;
    }

    private void updateVideoViewSize(float videoWidth, float videoHeight) {
        if (videoWidth > videoHeight) {
            LayoutParams videoViewParam;
            int height = (int) ((videoHeight / videoWidth) * getWidth());
            videoViewParam = new LayoutParams(LayoutParams.MATCH_PARENT, height);
            videoViewParam.gravity = Gravity.CENTER;
            mTextureView.setLayoutParams(videoViewParam);
        }
    }


    public void setSaveVideoPath(String path) {
        saveVideoDirPath = path;
    }


    public void setCameraListener(JCameraListener jCameraListener) {
        this.jCameraListener = jCameraListener;
    }


    private ErrorListener errorListener;

    //??????Camera????????????
    public void setErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    //??????CaptureButton???????????????????????????
    public void setFeatures(int state) {
        this.mCaptureLayout.setButtonFeatures(state);
    }


    /**
     * ?????????????????????
     */
    public void resetState() {
        if (currentTakeType == TYPE_DEFAULT){
            return;
        }

        defaultState();

    }

    /**
     * ?????????????????????
     */
    private void defaultState(){
//        if (mTextureView.getVisibility() == INVISIBLE){
//            mTextureView.setVisibility(VISIBLE);
//        }
        if (mPhoto.getVisibility() == VISIBLE){
            mPhoto.setVisibility(INVISIBLE);
        }
        if (line_topIcon.getVisibility() == INVISIBLE){
            line_topIcon.setVisibility(VISIBLE);
        }
        mCaptureLayout.resetCaptureLayout();
        //????????????
        stopVideo();
        if (currentTakeType == TYPE_VIDEO){
            mPreviewPresenter.doStartPreview();
        }
        //????????????
        currentTakeType = TYPE_DEFAULT;
        captureBitmap = null;
        videoUrl = null;
        firstFrame = null;

    }

    /**
     * ????????????????????????
     */
    public void confirmState() {
        switch (currentTakeType) {
            case TYPE_VIDEO:
                stopVideo();    //????????????
                if (jCameraListener != null) {
                    jCameraListener.recordSuccess(videoUrl, firstFrame);
                }
                break;
            case TYPE_PICTURE:
                mPhoto.setVisibility(INVISIBLE);
                if (jCameraListener != null) {
                    jCameraListener.captureSuccess(captureBitmap);
                }
                break;
        }
        mCaptureLayout.resetCaptureLayout();
    }

    /**
     * ???????????????
     * @param bitmap
     * @param isVertical
     */
    public void showPicture(final Bitmap bitmap,final boolean isVertical) {
        if (Looper.myLooper() == Looper.getMainLooper()){
            realShowPhoto(bitmap,isVertical);
        }else{
            post(new Runnable() {
                @Override
                public void run() {
                    realShowPhoto(bitmap,isVertical);
                }
            });
        }


    }

    /**
     * ??????????????????
     * @param bitmap
     * @param isVertical
     */
    private void realShowPhoto(final Bitmap bitmap,final boolean isVertical){
        try {
            if (isVertical) {
                mPhoto.setScaleType(ImageView.ScaleType.FIT_XY);
            } else {
                mPhoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            captureBitmap = bitmap;
            mPhoto.setImageBitmap(bitmap);
            mPhoto.setVisibility(VISIBLE);
//            mCaptureLayout.startAlphaAnimation();
            mCaptureLayout.startTypeBtnAnimator();

        }catch (Exception e){
            e.printStackTrace();
        }
    }


    /**
     * ??????????????????
     * @param firstFrame
     * @param url
     */
    public void playVideo(Bitmap firstFrame, final String url) {
        videoUrl = url;
        DVCameraView.this.firstFrame = firstFrame;
        try {

            if (mMediaPlayer == null) {
                mMediaPlayer = new MediaPlayer();
            } else {
                mMediaPlayer.reset();
            }

            mMediaPlayer.setDataSource(url);

            mMediaPlayer.setSurface(mPlayerSurface.getHolder().getSurface());

            mMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer
                    .OnVideoSizeChangedListener() {
                @Override
                public void
                onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    updateVideoViewSize(mMediaPlayer.getVideoWidth(), mMediaPlayer
                            .getVideoHeight());
                }
            });

            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {

//                    mPlayerSurface.setVisibility(VISIBLE);
                    mMediaPlayer.start();
                    postDelayed(()->{
                        mPhoto.setVisibility(INVISIBLE);
                        FileUtil.deleteFile(firstFramePath);
                    },100);
//                    mCaptureLayout.setTextWithAnimation("????????????");
                    dismissLoadingDialog();

                }
            });
            mMediaPlayer.setLooping(true);
            mMediaPlayer.prepareAsync();

        } catch (Exception e) {
            resetState();
            dismissLoadingDialog();
            mPhoto.setVisibility(INVISIBLE);
            mCaptureLayout.setTextWithAnimation("????????????");
            e.printStackTrace();
        }
    }

    /**
     * ??????????????????
     */
    public void stopVideo() {

        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mPlayerSurface.getVisibility() == View.VISIBLE){
            mPlayerSurface.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * ??????????????????
     * @param tip
     */
    public void setTip(String tip) {
        mCaptureLayout.setTip(tip);
        mCaptureLayout.showTip();
    }

    

    public void setLeftClickListener(ClickListener clickListener) {
        this.leftClickListener = clickListener;
    }

    public void setRightClickListener(ClickListener clickListener) {
        this.rightClickListener = clickListener;
    }

    /**
     * ?????????????????????
     */
    private void setFlashRes() {
        if (flashStatus){
            mFlashLamp.setImageResource(R.drawable.ic_dv_flash_on);
            mPreviewPresenter.setFlashLight(true);
        }else{
            mFlashLamp.setImageResource(R.drawable.ic_dv_flash_off);
            mPreviewPresenter.setFlashLight(false);
        }
    }

    @Override
    public void onDetachedFromWindow(){
        super.onDetachedFromWindow();

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.image_beauty){//??????
            //?????????????????????????????????
            if (line_beautySeekBar.getVisibility() == View.VISIBLE){
                line_beautySeekBar.setVisibility(View.GONE);
            }else{
                line_beautySeekBar.setVisibility(View.VISIBLE);
            }
            hideFilterLayout();
        }else if (v.getId() == R.id.image_flash){//??????
            if (mPreviewPresenter.isFront()){
                Toast.makeText(getContext(),"????????????????????????????????????",Toast.LENGTH_SHORT).show();
                return;
            }
            flashStatus = !flashStatus;
            setFlashRes();
            hideFilterLayout();
            hideBeautySeekBar();
        }else if (v.getId() == R.id.image_switch){//?????????????????????
            mPreviewPresenter.switchCamera();
            hideFilterLayout();
            hideBeautySeekBar();
            //?????????????????????????????????
            if (flashLightEnable){
                if (mPreviewPresenter.isFront()){
                    setFlashLightVisibility(View.GONE);
                }else{
                    setFlashLightVisibility(View.VISIBLE);
                }
            }
        }else if(v.getId() == R.id.image_filter){//??????????????????
            if (mCaptureLayout.getVisibility() == View.VISIBLE){
                mCaptureLayout.setVisibility(View.GONE);
            }
            if (mFilterLayout.getVisibility() == View.GONE){
                mFilterLayout.setVisibility(View.VISIBLE);
            }
            hideBeautySeekBar();
        }else if(v.getId() == R.id.video_preview){//??????????????????
            if (mCaptureLayout.getVisibility() == View.GONE){
                mCaptureLayout.setVisibility(View.VISIBLE);
            }
            if (mFilterLayout.getVisibility() == View.VISIBLE){
                mFilterLayout.setVisibility(View.GONE);
            }
            hideBeautySeekBar();
        }
    }

    /**
     * ??????????????????
     */
    private void hideBeautySeekBar(){
        if (line_beautySeekBar.getVisibility() == View.VISIBLE){
            line_beautySeekBar.setVisibility(View.GONE);
        }
    }

    /**
     * ??????????????????
     */
    private void hideFilterLayout(){
        if (mFilterLayout.getVisibility() == View.VISIBLE){
            mFilterLayout.setVisibility(View.GONE);
        }
    }


    // ---------------------------- ??????????????????activity???????????? ---------------------------------
    public void onCreate(Activity activity) {
        mPreviewPresenter.onCreate(activity);

    }

    public void onStart() {
        mPreviewPresenter.onStart();
    }

    //????????????onResume
    public void onResume() {
        LogUtil.i("CameraView onResume");
        resetState(); //????????????
        mPreviewPresenter.onResume();
    }

    //????????????onPause
    public void onPause() {
        LogUtil.i("CameraView onPause");
        stopVideo();
        mPreviewPresenter.onPause();
    }

    public void onStop() {
        mPreviewPresenter.onStop();
    }

    public void onDestroy() {
        mPreviewPresenter.onDestroy();
    }
}
