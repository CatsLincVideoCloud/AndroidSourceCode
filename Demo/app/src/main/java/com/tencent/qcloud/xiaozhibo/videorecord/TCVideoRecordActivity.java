package com.tencent.qcloud.xiaozhibo.videorecord;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.liteav.basic.log.TXCLog;
import com.tencent.qcloud.xiaozhibo.R;
import com.tencent.qcloud.xiaozhibo.common.activity.TCBaseActivity;
import com.tencent.qcloud.xiaozhibo.common.activity.TCVideoPreviewActivity;
import com.tencent.qcloud.xiaozhibo.common.utils.TCConstants;
import com.tencent.qcloud.xiaozhibo.common.utils.TCUtils;
import com.tencent.qcloud.xiaozhibo.common.widget.beautysetting.BeautyDialogFragment;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.ugc.TXRecordCommon;
import com.tencent.ugc.TXUGCRecord;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.view.View.GONE;

/**
 * UGC主播端录制界面
 */
public class TCVideoRecordActivity extends TCBaseActivity implements View.OnClickListener, BeautyDialogFragment.OnBeautyParamsChangeListener
            ,TXRecordCommon.ITXVideoRecordListener, View.OnTouchListener, GestureDetector.OnGestureListener,
            ScaleGestureDetector.OnScaleGestureListener, BeautyDialogFragment.OnDismissListener{

    private static final String TAG = "TCVideoRecordActivity";
    private static final String OUTPUT_DIR_NAME = "TXUGC";
    private boolean mRecording = false;
    private boolean mStartPreview = false;
    private boolean mFront = true;
    private TXUGCRecord mTXCameraRecord;
    private TXRecordCommon.TXRecordResult mTXRecordResult;

    private BeautyDialogFragment mBeautyDialogFragment;
    private BeautyDialogFragment.BeautyParams mBeautyParams = new BeautyDialogFragment.BeautyParams();

    private TXCloudVideoView mVideoView;
    private ImageView mIvConfirm;
    private TextView mProgressTime;
    private ProgressDialog mCompleteProgressDialog;
    private ImageView mIvTorch;
    private ImageView mIvMusic;
    private ImageView mIvBeauty;
    private ImageView mIvScale;
    private ComposeRecordBtn mComposeRecordBtn;
    private RelativeLayout mRlAspect;
    private RelativeLayout mRlAspectSelect;
    private ImageView mIvAspectSelectFirst;
    private ImageView mIvAspectSelectSecond;
    private ImageView mIvScaleMask;
    private boolean mAspectSelectShow = false;

    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusListener;
    private boolean mPause = false;
    private TCAudioControl mAudioCtrl;
    private int mCurrentAspectRatio;
    private int mFirstSelectScale;
    private int mSecondSelectScale;
    private RelativeLayout mRecordRelativeLayout = null;
    private FrameLayout mMaskLayout;
    private RecordProgressView mRecordProgressView;
    private ImageView mIvDeleteLastPart;
    private boolean isSelected = false; // 回删状态
    private long mLastClickTime;
    private boolean mIsTorchOpen = false; // 闪光灯的状态

    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private float mScaleFactor;
    private float mLastScaleFactor;

    private int mRecommendQuality = TXRecordCommon.VIDEO_QUALITY_MEDIUM;
    private int mMinDuration = 5 * 1000;
    private int mMaxDuration = 60 * 1000;
    private int mAspectRatio = TXRecordCommon.VIDEO_ASPECT_RATIO_9_16; // 视频比例
    private int mRecordResolution; // 录制分辨率
    private int mBiteRate; // 码率
    private int mFps; // 帧率
    private int mGop; // 关键帧间隔
    private String mBGMPath;
    private String mBGMPlayingPath;
    private int mBGMDuration;
    private ImageView mIvMusicMask;
    private RadioGroup mRadioGroup;
    private int mRecordSpeed = TXRecordCommon.RECORD_SPEED_NORMAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Disable Screen Rotation
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.activity_video_record);

        LinearLayout backLL = (LinearLayout) findViewById(R.id.back_ll);
        backLL.setOnClickListener(this);

        initViews();

        getData();

        mBeautyDialogFragment = new BeautyDialogFragment();
        mBeautyDialogFragment.setBeautyParamsListner(mBeautyParams, this);
        mBeautyDialogFragment.setmOnDismissListener(this);
//
//        mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
//
//        // 预览
//        if (mTXCameraRecord == null) {
//            mTXCameraRecord = TXUGCRecord.getInstance(TCVideoRecordActivity.this.getApplicationContext());
//        }
//        mVideoView = (TXCloudVideoView) findViewById(R.id.video_view);
//        mVideoView.enableHardwareDecode(true);
//
//        mProgressTime = (TextView) findViewById(R.id.progress_time);
    }

    private void getData() {
        Intent intent = getIntent();
        if (intent == null) {
            TXCLog.e(TAG, "intent is null");
            return;
        }

        mCurrentAspectRatio = mAspectRatio;
        setSelectAspect();

        mRecordProgressView.setMaxDuration(mMaxDuration);
        mRecordProgressView.setMinDuration(mMinDuration);
    }

    private void startCameraPreview() {
        if (mStartPreview) return;
        mStartPreview = true;

        mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
        mTXCameraRecord.setVideoRecordListener(this);
        // 推荐配置
        if (mRecommendQuality >= 0) {
            TXRecordCommon.TXUGCSimpleConfig simpleConfig = new TXRecordCommon.TXUGCSimpleConfig();
            simpleConfig.videoQuality = mRecommendQuality;
            simpleConfig.minDuration = mMinDuration;
            simpleConfig.maxDuration = mMaxDuration;
            simpleConfig.isFront = mFront;
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                simpleConfig.mHomeOriention = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
            } else {
                simpleConfig.mHomeOriention = TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT;
            }
            mTXCameraRecord.setRecordSpeed(mRecordSpeed);
            mTXCameraRecord.startCameraSimplePreview(simpleConfig, mVideoView);
            mTXCameraRecord.setAspectRatio(mCurrentAspectRatio);
        } else {
            // 自定义配置
            TXRecordCommon.TXUGCCustomConfig customConfig = new TXRecordCommon.TXUGCCustomConfig();
            customConfig.videoResolution = mRecordResolution;
            customConfig.minDuration = mMinDuration;
            customConfig.maxDuration = mMaxDuration;
            customConfig.videoBitrate = mBiteRate;
            customConfig.videoGop = mGop;
            customConfig.videoFps = mFps;
            customConfig.isFront = mFront;
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                customConfig.mHomeOriention = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
            } else {
                customConfig.mHomeOriention = TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT;
            }
            mTXCameraRecord.setRecordSpeed(mRecordSpeed);
            mTXCameraRecord.startCameraCustomPreview(customConfig, mVideoView);
            mTXCameraRecord.setAspectRatio(mCurrentAspectRatio);
        }

//        mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
//        mTXCameraRecord.setFaceScaleLevel(mBeautyParams.mFaceSlimLevel);
//        mTXCameraRecord.setEyeScaleLevel(mBeautyParams.mBigEyeLevel);
//        mTXCameraRecord.setFilter(mBeautyParams.mFilterBmp);
//        mTXCameraRecord.setGreenScreenFile(mBeautyParams.mGreenFile, true);
//        mTXCameraRecord.setMotionTmpl(mBeautyParams.mMotionTmplPath);
//        mTXCameraRecord.setFaceShortLevel(mBeautyParams.mFaceShortLevel);
//        mTXCameraRecord.setFaceVLevel(mBeautyParams.mFaceVLevel);
//        mTXCameraRecord.setChinLevel(mBeautyParams.mChinSlimLevel);
//        mTXCameraRecord.setNoseSlimLevel(mBeautyParams.mNoseScaleLevel);

        mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyProgress, mBeautyParams.mWhiteProgress, mBeautyParams.mRuddyProgress);
        mTXCameraRecord.setFaceScaleLevel(mBeautyParams.mFaceLiftProgress);
        mTXCameraRecord.setEyeScaleLevel(mBeautyParams.mBigEyeProgress);
        mTXCameraRecord.setFilter(TCUtils.getFilterBitmap(getResources(), mBeautyParams.mFilterIdx));
        mTXCameraRecord.setGreenScreenFile(TCUtils.getGreenFileName(mBeautyParams.mGreenIdx), true);
        mTXCameraRecord.setMotionTmpl(mBeautyParams.mMotionTmplPath);
    }

    private void initViews() {
        mMaskLayout = (FrameLayout) findViewById(R.id.mask);
        mMaskLayout.setOnTouchListener(this);

        mIvConfirm = (ImageView) findViewById(R.id.btn_confirm);
        mIvConfirm.setOnClickListener(this);
        mIvConfirm.setImageResource(R.drawable.ugc_confirm_disable);
        mIvConfirm.setEnabled(false);

//        mBeautyPannelView = (BeautySettingPannel) findViewById(R.id.beauty_pannel);
//        mBeautyPannelView.setBeautyParamsChangeListener(this);
//        mBeautyPannelView.disableExposure();

        mAudioCtrl = (TCAudioControl) findViewById(R.id.layoutAudioControl);
        mAudioCtrl.setOnItemClickListener(new TCVideoRecordActivity.OnItemClickListener() {

            @Override
            public void onBGMSelect(String path) {
                mBGMPath = path;
                mBGMDuration = mTXCameraRecord.setBGM(path);
            }
        });

        mAudioCtrl.setReturnListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAudioCtrl.mMusicSelectView.setVisibility(GONE);
                mAudioCtrl.setVisibility(GONE);
                mIvMusic.setImageResource(R.drawable.ugc_record_music);
                mRecordRelativeLayout.setVisibility(View.VISIBLE);
            }
        });

        mVideoView = (TXCloudVideoView) findViewById(R.id.video_view);
        mVideoView.enableHardwareDecode(true);

        mProgressTime = (TextView) findViewById(R.id.progress_time);
        mIvDeleteLastPart = (ImageView) findViewById(R.id.btn_delete_last_part);
        mIvDeleteLastPart.setOnClickListener(this);

        mIvScale = (ImageView) findViewById(R.id.iv_scale);
        mIvScaleMask = (ImageView) findViewById(R.id.iv_scale_mask);
        mIvAspectSelectFirst = (ImageView) findViewById(R.id.iv_scale_first);
        mIvAspectSelectSecond = (ImageView) findViewById(R.id.iv_scale_second);
        mRlAspect = (RelativeLayout) findViewById(R.id.layout_aspect);
        mRlAspectSelect = (RelativeLayout) findViewById(R.id.layout_aspect_select);

        mIvMusic = (ImageView) findViewById(R.id.btn_music_pannel);
        mIvMusicMask = (ImageView) findViewById(R.id.iv_music_mask);

        mIvBeauty = (ImageView) findViewById(R.id.btn_beauty);

        mRecordRelativeLayout = (RelativeLayout) findViewById(R.id.record_layout);
        mRecordProgressView = (RecordProgressView) findViewById(R.id.record_progress_view);

        mGestureDetector = new GestureDetector(this, this);
        mScaleGestureDetector = new ScaleGestureDetector(this, this);

        mCompleteProgressDialog = new ProgressDialog(this);
        mCompleteProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
        mCompleteProgressDialog.setCancelable(false);// 设置是否可以通过点击Back键取消
        mCompleteProgressDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条

        mIvTorch = (ImageView) findViewById(R.id.btn_torch);
        mIvTorch.setOnClickListener(this);

        if (mFront) {
            mIvTorch.setImageResource(R.drawable.ugc_torch_disable);
            mIvTorch.setEnabled(false);
        } else {
            mIvTorch.setImageResource(R.drawable.selector_torch_close);
            mIvTorch.setEnabled(true);
        }

        mComposeRecordBtn = (ComposeRecordBtn) findViewById(R.id.compose_record_btn);
        mRadioGroup = (RadioGroup) findViewById(R.id.rg_record_speed);
        ((RadioButton)findViewById(R.id.rb_normal)).setChecked(true);
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
                switch (checkedId) {
                    case R.id.rb_fast:
                        mRecordSpeed = TXRecordCommon.RECORD_SPEED_FAST;
                        break;
                    case R.id.rb_fastest:
                        mRecordSpeed = TXRecordCommon.RECORD_SPEED_FASTEST;
                        break;
                    case R.id.rb_normal:
                        mRecordSpeed = TXRecordCommon.RECORD_SPEED_NORMAL;
                        break;
                    case R.id.rb_slow:
                        mRecordSpeed = TXRecordCommon.RECORD_SPEED_SLOW;
                        break;
                    case R.id.rb_slowest:
                        mRecordSpeed = TXRecordCommon.RECORD_SPEED_SLOWEST;
                        break;
                }
                mTXCameraRecord.setRecordSpeed(mRecordSpeed);
            }
        });
    }

    public interface OnItemClickListener {
        void onBGMSelect(String path);
    }

    public interface OnSpeedItemClickListener {
        void onRecordSpeedItemSelected(int pos);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setSelectAspect();

        if (checkPermission()) {
            startCameraPreview();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopCameraPreview();
            mStartPreview = false;
        }
        if (mRecording && !mPause) {
            pauseRecord();
        }
        if (mTXCameraRecord != null) {
            mTXCameraRecord.pauseBGM();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mRecordProgressView != null) {
            mRecordProgressView.release();
        }

        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopBGM();
            mTXCameraRecord.stopCameraPreview();
            mTXCameraRecord.setVideoRecordListener(null);
            mTXCameraRecord.release();
            mTXCameraRecord = null;
            mStartPreview = false;
        }
        abandonAudioFocus();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mTXCameraRecord.stopCameraPreview();
        if (mRecording && !mPause) {
            pauseRecord();
        }

        if (mTXCameraRecord != null) {
            mTXCameraRecord.pauseBGM();
        }

        mStartPreview = false;
        startCameraPreview();
    }

    /**
     * BeautyDialogFragment消失的回调
     */
    @Override
    public void onDismiss() {
        mIvBeauty.setImageResource(R.drawable.ugc_record_beautiful_girl);
        mRecordRelativeLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.back_ll:
                back();
                break;
            case R.id.btn_beauty:
                Bundle args = new Bundle();
                args.putBoolean("hideMotionTable", true);
                try {
                    mBeautyDialogFragment.setArguments(args);
                    if (mBeautyDialogFragment.isAdded()) {
                        mBeautyDialogFragment.dismiss();

                        mIvBeauty.setImageResource(R.drawable.ugc_record_beautiful_girl);
                        mRecordRelativeLayout.setVisibility(View.VISIBLE);
                    }else {
                        mBeautyDialogFragment.show(getFragmentManager(), "");

                        mIvBeauty.setImageResource(R.drawable.ugc_record_beautiful_girl_hover);
                        mRecordRelativeLayout.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (mAudioCtrl.getVisibility() == View.VISIBLE) {
                    mAudioCtrl.setVisibility(GONE);
                    mIvMusic.setImageResource(R.drawable.ugc_record_music);
                }

                break;
            case R.id.btn_switch_camera:
                mFront = !mFront;
                mIsTorchOpen = false;
                if (mFront) {
                    mIvTorch.setImageResource(R.drawable.ugc_torch_disable);
                    mIvTorch.setEnabled(false);
                } else {
                    mIvTorch.setImageResource(R.drawable.selector_torch_close);
                    mIvTorch.setEnabled(true);
                }
                if (mTXCameraRecord != null) {
                    TXCLog.i(TAG, "switchCamera = " + mFront);
                    mTXCameraRecord.switchCamera(mFront);
                }
                break;
            case R.id.compose_record_btn:
                if (mAspectSelectShow) {
                    hideAspectSelectAnim();
                    mAspectSelectShow = !mAspectSelectShow;
                }

                switchRecord();
                break;
            case R.id.btn_music_pannel:
                mAudioCtrl.setPusher(mTXCameraRecord);

                mAudioCtrl.setVisibility(mAudioCtrl.getVisibility() == View.VISIBLE ? GONE : View.VISIBLE);
                mIvMusic.setImageResource(mAudioCtrl.getVisibility() == View.VISIBLE ? R.drawable.ugc_record_music_hover : R.drawable.ugc_record_music);
                mRecordRelativeLayout.setVisibility(mAudioCtrl.getVisibility() == View.VISIBLE ? GONE : View.VISIBLE);

                if (mBeautyDialogFragment.isAdded()) {
                    mBeautyDialogFragment.dismiss();

                    mIvBeauty.setImageResource(R.drawable.ugc_record_beautiful_girl);
                    mRecordRelativeLayout.setVisibility(View.VISIBLE);
                }
//                if (mBeautyPannelView.getVisibility() == View.VISIBLE) {
//                    mBeautyPannelView.setVisibility(GONE);
//                    mIvBeauty.setImageResource(R.drawable.ugc_record_beautiful_girl);
//                }
                break;
            case R.id.btn_confirm:
                mCompleteProgressDialog.show();
                stopRecord();
                break;
            case R.id.iv_scale:
                scaleDisplay();
                break;
            case R.id.iv_scale_first:
                selectAnotherAspect(mFirstSelectScale);
                break;
            case R.id.iv_scale_second:
                selectAnotherAspect(mSecondSelectScale);
                break;
            case R.id.btn_delete_last_part:
                deleteLastPart();
                break;
            case R.id.btn_torch:
                toggleTorch();
                break;
            default:
                break;
        }
    }

    private void setSelectAspect() {
        if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_9_16) {
            mIvScale.setImageResource(R.drawable.selector_aspect169);
            mFirstSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_1_1;
            mIvAspectSelectFirst.setImageResource(R.drawable.selector_aspect11);

            mSecondSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_3_4;
            mIvAspectSelectSecond.setImageResource(R.drawable.selector_aspect43);
        } else if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_1_1) {
            mIvScale.setImageResource(R.drawable.selector_aspect11);
            mFirstSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_3_4;
            mIvAspectSelectFirst.setImageResource(R.drawable.selector_aspect43);

            mSecondSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_9_16;
            mIvAspectSelectSecond.setImageResource(R.drawable.selector_aspect169);
        } else {
            mIvScale.setImageResource(R.drawable.selector_aspect43);
            mFirstSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_1_1;
            mIvAspectSelectFirst.setImageResource(R.drawable.selector_aspect11);

            mSecondSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_9_16;
            mIvAspectSelectSecond.setImageResource(R.drawable.selector_aspect169);
        }
    }

    private void toggleTorch() {
        if (mIsTorchOpen) {
            mTXCameraRecord.toggleTorch(false);
            mIvTorch.setImageResource(R.drawable.selector_torch_close);
        } else {
            mTXCameraRecord.toggleTorch(true);
            mIvTorch.setImageResource(R.drawable.selector_torch_open);
        }
        mIsTorchOpen = !mIsTorchOpen;
    }

    private void deleteLastPart() {
        if (mRecording && !mPause) {
            return;
        }
        if (!isSelected) {
            isSelected = true;
            mRecordProgressView.selectLast();
        } else {
            isSelected = false;
            mRecordProgressView.deleteLast();
            mTXCameraRecord.getPartsManager().deleteLastPart();
            int timeSecond = mTXCameraRecord.getPartsManager().getDuration() / 1000;
            mProgressTime.setText(String.format(Locale.CHINA, "00:%02d", timeSecond));
            if (timeSecond < mMinDuration / 1000) {
                mIvConfirm.setImageResource(R.drawable.ugc_confirm_disable);
                mIvConfirm.setEnabled(false);
            } else {
                mIvConfirm.setImageResource(R.drawable.selector_record_confirm);
                mIvConfirm.setEnabled(true);
            }

            if (mTXCameraRecord.getPartsManager().getPartsPathList().size() == 0) {
                mIvScaleMask.setVisibility(GONE);
                mIvMusicMask.setVisibility(GONE);
            }
        }
    }

    private void scaleDisplay() {
        if (!mAspectSelectShow) {
            showAspectSelectAnim();
        } else {
            hideAspectSelectAnim();
        }

        mAspectSelectShow = !mAspectSelectShow;
    }

    private void selectAnotherAspect(int targetScale) {
        if (mTXCameraRecord != null) {
            scaleDisplay();

            mCurrentAspectRatio = targetScale;

            if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_9_16) {
                mTXCameraRecord.setAspectRatio(TXRecordCommon.VIDEO_ASPECT_RATIO_9_16);

            } else if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_3_4) {
                mTXCameraRecord.setAspectRatio(TXRecordCommon.VIDEO_ASPECT_RATIO_3_4);

            } else if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_1_1) {
                mTXCameraRecord.setAspectRatio(TXRecordCommon.VIDEO_ASPECT_RATIO_1_1);
            }

            setSelectAspect();
        }
    }

    private void hideAspectSelectAnim() {
        ObjectAnimator showAnimator = ObjectAnimator.ofFloat(mRlAspectSelect, "translationX", 0f,
                2 * (getResources().getDimension(R.dimen.ugc_aspect_divider) + getResources().getDimension(R.dimen.ugc_aspect_width)));
        showAnimator.setDuration(80);
        showAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mRlAspectSelect.setVisibility(GONE);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        showAnimator.start();
    }

    private void showAspectSelectAnim() {
        ObjectAnimator showAnimator = ObjectAnimator.ofFloat(mRlAspectSelect, "translationX",
                2 * (getResources().getDimension(R.dimen.ugc_aspect_divider) + getResources().getDimension(R.dimen.ugc_aspect_width)), 0f);
        showAnimator.setDuration(80);
        showAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mRlAspectSelect.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {

            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        showAnimator.start();
    }

    private void switchRecord() {
        long currentClickTime = System.currentTimeMillis();
        if (currentClickTime - mLastClickTime < 200) {
            return;
        }
        if (mRecording) {
            if (mPause) {
                if (mTXCameraRecord.getPartsManager().getPartsPathList().size() == 0) {
                    startRecord();
                } else {
                    resumeRecord();
                }
            } else {
                pauseRecord();
            }
        } else {
            startRecord();
        }
        mLastClickTime = currentClickTime;
    }

    private void resumeRecord() {
        mComposeRecordBtn.startRecord();
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) {
            liveRecord.setBackgroundResource(R.drawable.video_stop);
        }
        mIvDeleteLastPart.setImageResource(R.drawable.ugc_delete_last_part_disable);
        mIvDeleteLastPart.setEnabled(false);
        mIvScaleMask.setVisibility(View.VISIBLE);

        mPause = false;
        isSelected = false;
        if (mTXCameraRecord != null) {
            mTXCameraRecord.resumeRecord();
            if (!TextUtils.isEmpty(mBGMPath)) {
                if (mBGMPlayingPath == null || !mBGMPath.equals(mBGMPlayingPath)) {
                    mTXCameraRecord.playBGMFromTime(0, mBGMDuration);
                    mBGMPlayingPath = mBGMPath;
                } else {
                    mTXCameraRecord.resumeBGM();
                }
            }
        }
        requestAudioFocus();

        mRadioGroup.setVisibility(GONE);
    }

    private void pauseRecord() {
        mComposeRecordBtn.pauseRecord();
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) {
            liveRecord.setBackgroundResource(R.drawable.start_record);
        }
        mPause = true;
        mIvDeleteLastPart.setImageResource(R.drawable.selector_delete_last_part);
        mIvDeleteLastPart.setEnabled(true);

        if (mTXCameraRecord != null) {
            if (!TextUtils.isEmpty(mBGMPlayingPath)) {
                mTXCameraRecord.pauseBGM();
            }
            mTXCameraRecord.pauseRecord();
        }
        abandonAudioFocus();

        mRadioGroup.setVisibility(View.VISIBLE);
    }

//    private void startCameraPreview() {
//        if (mStartPreview) return;
//        mStartPreview = true;
//
//        TXRecordCommon.TXUGCSimpleConfig param = new TXRecordCommon.TXUGCSimpleConfig();
//        param.videoQuality = TXRecordCommon.VIDEO_QUALITY_MEDIUM;
//        param.isFront = mFront;
//
//        mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
//        mTXCameraRecord.startCameraSimplePreview(param, mVideoView);
//        mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyProgress, mBeautyParams.mWhiteProgress, mBeautyParams.mRuddyProgress);
//        mTXCameraRecord.setFaceScaleLevel(mBeautyParams.mFaceLiftProgress);
//        mTXCameraRecord.setEyeScaleLevel(mBeautyParams.mBigEyeProgress);
//        mTXCameraRecord.setFilter(TCUtils.getFilterBitmap(getResources(), mBeautyParams.mFilterIdx));
//        mTXCameraRecord.setGreenScreenFile(TCUtils.getGreenFileName(mBeautyParams.mGreenIdx), true);
//        mTXCameraRecord.setMotionTmpl(mBeautyParams.mMotionTmplPath);
//    }

    private void stopRecord() {
        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopBGM();
            mTXCameraRecord.stopRecord();
        }
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.start_record);
        mRecording = false;
        mPause = false;
        abandonAudioFocus();

        mRadioGroup.setVisibility(View.VISIBLE);
    }

    private void startRecord() {
        mComposeRecordBtn.startRecord();
        mIvScaleMask.setVisibility(View.VISIBLE);
        mIvDeleteLastPart.setImageResource(R.drawable.ugc_delete_last_part_disable);
        mIvDeleteLastPart.setEnabled(false);
        if (mTXCameraRecord == null) {
            mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
        }

        String customVideoPath = getCustomVideoOutputPath();
        String customCoverPath = customVideoPath.replace(".mp4", ".jpg");

        int result = mTXCameraRecord.startRecord(customVideoPath, customCoverPath);
        if (result != 0) {
            Toast.makeText(TCVideoRecordActivity.this.getApplicationContext(), "录制失败，错误码：" + result, Toast.LENGTH_SHORT).show();
            mTXCameraRecord.setVideoRecordListener(null);
            mTXCameraRecord.stopRecord();
            return;
        }
        if (!TextUtils.isEmpty(mBGMPath)) {
            mTXCameraRecord.playBGMFromTime(0, mBGMDuration);
            mBGMPlayingPath = mBGMPath;
            TXCLog.i(TAG, "music duration = " + mTXCameraRecord.getMusicDuration(mBGMPath));
        }

        mAudioCtrl.setPusher(mTXCameraRecord);
        mRecording = true;
        mPause = false;
        ImageView liveRecord = (ImageView) findViewById(R.id.record);
        if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.video_stop);
        requestAudioFocus();

        mIvMusicMask.setVisibility(View.VISIBLE);
        mRadioGroup.setVisibility(GONE);
    }

    private String getCustomVideoOutputPath() {
        long currentTime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
        String time = sdf.format(new Date(currentTime));
        String outputDir = Environment.getExternalStorageDirectory() + File.separator + OUTPUT_DIR_NAME;
        File outputFolder = new File(outputDir);
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }
        String tempOutputPath = outputDir + File.separator + "TXUGC_" + time + ".mp4";
        return tempOutputPath;
    }

    private void startPreview() {
        if (mTXRecordResult != null && (mTXRecordResult.retCode == TXRecordCommon.RECORD_RESULT_OK
                || mTXRecordResult.retCode == TXRecordCommon.RECORD_RESULT_OK_REACHED_MAXDURATION
                || mTXRecordResult.retCode == TXRecordCommon.RECORD_RESULT_OK_LESS_THAN_MINDURATION)) {
            Intent intent = new Intent(getApplicationContext(), TCVideoPreviewActivity.class);
            intent.putExtra(TCConstants.VIDEO_RECORD_TYPE, TCConstants.VIDEO_RECORD_TYPE_UGC_RECORD);
            intent.putExtra(TCConstants.VIDEO_RECORD_RESULT, mTXRecordResult.retCode);
            intent.putExtra(TCConstants.VIDEO_RECORD_DESCMSG, mTXRecordResult.descMsg);
            intent.putExtra(TCConstants.VIDEO_RECORD_VIDEPATH, mTXRecordResult.videoPath);
            intent.putExtra(TCConstants.VIDEO_RECORD_COVERPATH, mTXRecordResult.coverPath);
            if(mRecommendQuality == TXRecordCommon.VIDEO_QUALITY_LOW){
                intent .putExtra(TCConstants.VIDEO_RECORD_RESOLUTION, TXRecordCommon.VIDEO_RESOLUTION_360_640);
            }else if(mRecommendQuality == TXRecordCommon.VIDEO_QUALITY_MEDIUM){
                intent .putExtra(TCConstants.VIDEO_RECORD_RESOLUTION, TXRecordCommon.VIDEO_RESOLUTION_540_960);
            }else if(mRecommendQuality == TXRecordCommon.VIDEO_QUALITY_HIGH){
                intent .putExtra(TCConstants.VIDEO_RECORD_RESOLUTION, TXRecordCommon.VIDEO_RESOLUTION_720_1280);
            }else{
                intent .putExtra(TCConstants.VIDEO_RECORD_RESOLUTION, mRecordResolution);
            }
            startActivity(intent);
            finish();
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray(new String[0]),
                        100);
                return false;
            }
        }

        return true;
    }

//    private void retryRecord() {
//        if (mRecording ) {
//            stopRecord();
//        }
//        View recordLayout = TCVideoRecordActivity.this.findViewById(R.id.record_layout);
//        View publishLayout = TCVideoRecordActivity.this.findViewById(R.id.publishLayout);
//        View controlLayout = TCVideoRecordActivity.this.findViewById(R.id.record_control);
//        if (recordLayout != null) {
//            recordLayout.setVisibility(View.VISIBLE);
//        }
//        if (publishLayout != null) {
//            publishLayout.setVisibility(View.GONE);
//        }
//        if (controlLayout != null) {
//            controlLayout.setVisibility(View.VISIBLE);
//        }
//
//        if (mRecordProgress != null) {
//            mRecordProgress.setProgress(0);
//        }
//
//        mLayoutPitu.setVisibility(View.GONE);
//        mPitu.setVisibility(View.VISIBLE);
//        mClosePitu.setVisibility(View.GONE);
//    }

    @Override
    public void onBeautyParamsChange(BeautyDialogFragment.BeautyParams params, int key) {
        switch (key){
            case BeautyDialogFragment.BEAUTYPARAM_BEAUTY:
            case BeautyDialogFragment.BEAUTYPARAM_WHITE:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setBeautyDepth(params.mBeautyStyle, params.mBeautyProgress, params.mWhiteProgress, params.mRuddyProgress);
                }
                break;
            case BeautyDialogFragment.BEAUTYPARAM_FACE_LIFT:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setFaceScaleLevel(params.mFaceLiftProgress);
                }
                break;
            case BeautyDialogFragment.BEAUTYPARAM_BIG_EYE:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setEyeScaleLevel(params.mBigEyeProgress);
                }
                break;
            case BeautyDialogFragment.BEAUTYPARAM_FILTER:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setFilter(TCUtils.getFilterBitmap(getResources(), params.mFilterIdx));
                }
                break;
            case BeautyDialogFragment.BEAUTYPARAM_MOTION_TMPL:
                if (mTXCameraRecord != null){
                    mTXCameraRecord.setMotionTmpl(params.mMotionTmplPath);
                }
                break;
            case BeautyDialogFragment.BEAUTYPARAM_GREEN:
                if (mTXCameraRecord != null){
                    mTXCameraRecord.setGreenScreenFile(TCUtils.getGreenFileName(params.mGreenIdx), true);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onRecordEvent(int event, Bundle param) {
        TXCLog.d(TAG, "onRecordEvent event id = " + event);
        if (event == TXRecordCommon.EVT_ID_PAUSE) {
            mRecordProgressView.clipComplete();
        } else if(event == TXRecordCommon.EVT_CAMERA_CANNOT_USE){
            Toast.makeText(this, "摄像头打开失败，请检查权限", Toast.LENGTH_SHORT).show();
        }else if(event == TXRecordCommon.EVT_MIC_CANNOT_USE){
            Toast.makeText(this, "麦克风打开失败，请检查权限", Toast.LENGTH_SHORT).show();
        }else if (event == TXRecordCommon.EVT_ID_RESUME) {

        }
    }

    @Override
    public void onRecordProgress(long milliSecond) {
        if (mRecordProgressView == null) {
            return;
        }
        mRecordProgressView.setProgress((int) milliSecond);
        float timeSecondFloat = milliSecond / 1000f;
        int timeSecond = Math.round(timeSecondFloat);
        mProgressTime.setText(String.format(Locale.CHINA, "00:%02d", timeSecond));
        if (timeSecondFloat < mMinDuration / 1000) {
            mIvConfirm.setImageResource(R.drawable.ugc_confirm_disable);
            mIvConfirm.setEnabled(false);
        } else {
            mIvConfirm.setImageResource(R.drawable.selector_record_confirm);
            mIvConfirm.setEnabled(true);
        }
    }

    @Override
    public void onRecordComplete(TXRecordCommon.TXRecordResult result) {
        mCompleteProgressDialog.dismiss();

        mTXRecordResult = result;
        TXCLog.i(TAG, "onRecordComplete, result retCode = " + result.retCode + ", descMsg = " + result.descMsg + ", videoPath + " + result.videoPath + ", coverPath = " + result.coverPath);
        if (mTXRecordResult.retCode < 0) {
            ImageView liveRecord = (ImageView) findViewById(R.id.record);
            if (liveRecord != null) liveRecord.setBackgroundResource(R.drawable.start_record);
            mRecording = false;

            int timeSecond = mTXCameraRecord.getPartsManager().getDuration() / 1000;
            mProgressTime.setText(String.format(Locale.CHINA, "00:%02d", timeSecond));
            Toast.makeText(TCVideoRecordActivity.this.getApplicationContext(), "录制失败，原因：" + mTXRecordResult.descMsg, Toast.LENGTH_SHORT).show();
        } else {
            if (mTXCameraRecord != null) {
                mTXCameraRecord.getPartsManager().deleteAllParts();
            }
            startPreview();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 100:
                for (int ret : grantResults) {
                    if (ret != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                TXRecordCommon.TXUGCSimpleConfig param = new TXRecordCommon.TXUGCSimpleConfig();
                param.videoQuality = TXRecordCommon.VIDEO_QUALITY_MEDIUM;
                param.isFront = mFront;
                mTXCameraRecord.startCameraSimplePreview(param,mVideoView);
                mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyProgress, mBeautyParams.mWhiteProgress, mBeautyParams.mRuddyProgress);
                mTXCameraRecord.setMotionTmpl(mBeautyParams.mMotionTmplPath);
                break;
            default:
                break;
        }
    }

    private void requestAudioFocus() {
        if (null == mAudioManager) {
            mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        }

        if (null == mOnAudioFocusListener) {
            mOnAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int focusChange) {
                    try {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            pauseRecord();
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pauseRecord();
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {

                        } else {
                            pauseRecord();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            };
        }
        try {
            mAudioManager.requestAudioFocus(mOnAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void abandonAudioFocus() {
        try {
            if (null != mAudioManager && null != mOnAudioFocusListener) {
                mAudioManager.abandonAudioFocus(mOnAudioFocusListener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (view == mMaskLayout) {
            if (motionEvent.getPointerCount() >= 2) {
                mScaleGestureDetector.onTouchEvent(motionEvent);
            } else if (motionEvent.getPointerCount() == 1) {
                mGestureDetector.onTouchEvent(motionEvent);
            }
        }
        return true;
    }

    // OnGestureListener回调start
    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        if (mAudioCtrl.isShown()) {
            mAudioCtrl.setVisibility(GONE);
            mIvMusic.setImageResource(R.drawable.ugc_record_music);
            mRecordRelativeLayout.setVisibility(View.VISIBLE);
        }
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }
    // OnGestureListener回调end

    // OnScaleGestureListener回调start
    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        int maxZoom = mTXCameraRecord.getMaxZoom();
        if (maxZoom == 0) {
            TXCLog.i(TAG, "camera not support zoom");
            return false;
        }

        float factorOffset = scaleGestureDetector.getScaleFactor() - mLastScaleFactor;

        mScaleFactor += factorOffset;
        mLastScaleFactor = scaleGestureDetector.getScaleFactor();
        if (mScaleFactor < 0) {
            mScaleFactor = 0;
        }
        if (mScaleFactor > 1) {
            mScaleFactor = 1;
        }

        int zoomValue = Math.round(mScaleFactor * maxZoom);
        mTXCameraRecord.setZoom(zoomValue);
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
        mLastScaleFactor = scaleGestureDetector.getScaleFactor();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

    }
    // OnScaleGestureListener回调end

    private void back(){
        if (!mRecording) {
            finish();
        }
        if (mPause) {
            if (mTXCameraRecord != null) {
                mTXCameraRecord.getPartsManager().deleteAllParts();
            }
            finish();
        } else {
            pauseRecord();
        }
    }

    @Override
    public void onBackPressed() {
        back();
    }
}
