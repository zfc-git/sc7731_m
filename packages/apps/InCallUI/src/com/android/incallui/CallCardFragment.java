/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.incallui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Trace;
import android.os.Handler;
import android.os.Looper;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.phone.common.animation.AnimUtils;
import com.sprd.incallui.FdnListNameHelper;
import com.sprd.incallui.InCallUIHdVoicePlugin;
import com.sprd.incallui.InCallUIPlugin;
import com.sprd.incallui.CallerAddressHelper;
import com.sprd.incallui.InCallUITelcelHelper;
import com.sprd.incallui.PhoneRecorderHelper;

import java.util.List;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter, CallCardPresenter.CallCardUi>
        implements CallCardPresenter.CallCardUi {
    private static final String TAG = "CallCardFragment";

    /**
     * Internal class which represents the call state label which is to be applied.
     */
    private class CallStateLabel {
        private CharSequence mCallStateLabel;
        private boolean mIsAutoDismissing;

        public CallStateLabel(CharSequence callStateLabel, boolean isAutoDismissing) {
            mCallStateLabel = callStateLabel;
            mIsAutoDismissing = isAutoDismissing;
        }

        public CharSequence getCallStateLabel() {
            return mCallStateLabel;
        }

        /**
         * Determines if the call state label should auto-dismiss.
         *
         * @return {@code true} if the call state label should auto-dismiss.
         */
        public boolean isAutoDismissing() {
            return mIsAutoDismissing;
        }
    };

    /**
     * The duration of time (in milliseconds) a call state label should remain visible before
     * resetting to its previous value.
     */
    private static final long CALL_STATE_LABEL_RESET_DELAY_MS = 3000;
    /**
     * Amount of time to wait before sending an announcement via the accessibility manager.
     * When the call state changes to an outgoing or incoming state for the first time, the
     * UI can often be changing due to call updates or contact lookup. This allows the UI
     * to settle to a stable state to ensure that the correct information is announced.
     */
    private static final long ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS = 500;

    private AnimatorSet mAnimatorSet;
    private int mShrinkAnimationDuration;
    private int mFabNormalDiameter;
    private int mFabSmallDiameter;
    private boolean mIsLandscape;
    private boolean mIsDialpadShowing;

    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private View mCallStateButton;
    private ImageView mCallStateIcon;
    private ImageView mCallStateVideoCallIcon;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private ImageView mHdAudioIcon;
    private View mCallNumberAndLabel;
    private ImageView mPhoto;
    private TextView mElapsedTime;
    private Drawable mPrimaryPhotoDrawable;

    // Container view that houses the entire primary call card, including the call buttons
    private View mPrimaryCallCardContainer;
    // Container view that houses the primary call information
    private ViewGroup mPrimaryCallInfo;
    private View mCallButtonsContainer;

    // Secondary caller info
    private View mSecondaryCallInfo;
    private TextView mSecondaryCallName;
    private View mSecondaryCallProviderInfo;
    private TextView mSecondaryCallProviderLabel;
    private View mSecondaryCallConferenceCallIcon;
    private View mSecondaryCallVideoCallIcon;
    private View mProgressSpinner;

    private View mManageConferenceCallButton;

    // Dark number info bar
    private TextView mInCallMessageLabel;

    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private ImageButton mFloatingActionButton;
    private int mFloatingActionButtonVerticalOffset;

    private float mTranslationOffset;
    private Animation mPulseAnimation;

    private int mVideoAnimationDuration;
    // Whether or not the call card is currently in the process of an animation
    private boolean mIsAnimating;

    private MaterialPalette mCurrentThemeColors;

    private TextView mRecordingLabel; // SPRD: Add for recorder
    private final String DIALPAD_SHOWING ="DIALPAD_SHOWING";//bug497046
    private TextView mGeocodeView;  // SPRD: Porting Caller Address Feature
    /**
     * Call state label to set when an auto-dismissing call state label is dismissed.
     */
    private CharSequence mPostResetCallStateLabel;
    private boolean mCallStateLabelResetPending = false;
    private Handler mHandler;

    public float mSecondryCallInfoHeight;//SPRD:Add for bug531347

    @Override
    public CallCardPresenter.CallCardUi getUi() {
        return this;
    }

    @Override
    public CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler(Looper.getMainLooper());
        mShrinkAnimationDuration = getResources().getInteger(R.integer.shrink_animation_duration);
        mVideoAnimationDuration = getResources().getInteger(R.integer.video_animation_duration);
        mFloatingActionButtonVerticalOffset = getResources().getDimensionPixelOffset(
                R.dimen.floating_action_button_vertical_offset);
        mFabNormalDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_diameter);
        mFabSmallDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_small_diameter);

        /* SPRD: add for Bug497046 @{ */
        if (savedInstanceState != null) {
            mIsDialpadShowing = savedInstanceState.getBoolean(DIALPAD_SHOWING);
        }
        /* @} */
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        getPresenter().init(getActivity(), call);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onCreate");
        mTranslationOffset =
                getResources().getDimensionPixelSize(R.dimen.call_card_anim_translate_y_offset);
        final View view = inflater.inflate(R.layout.call_card_fragment, container, false);
        Trace.endSection();
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPulseAnimation =
                AnimationUtils.loadAnimation(view.getContext(), R.anim.call_status_pulse);

        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) view.findViewById(R.id.name);
        mNumberLabel = (TextView) view.findViewById(R.id.label);
        mSecondaryCallInfo = view.findViewById(R.id.secondary_call_info);
        mSecondaryCallProviderInfo = view.findViewById(R.id.secondary_call_provider_info);
        mPhoto = (ImageView) view.findViewById(R.id.photo);
        mPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().onContactPhotoClick();
            }
        });
        mCallStateIcon = (ImageView) view.findViewById(R.id.callStateIcon);
        mCallStateVideoCallIcon = (ImageView) view.findViewById(R.id.videoCallIcon);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);
        mHdAudioIcon = (ImageView) view.findViewById(R.id.hdAudioIcon);
        mCallNumberAndLabel = view.findViewById(R.id.labelAndNumber);
        mCallTypeLabel = (TextView) view.findViewById(R.id.callTypeLabel);
        mElapsedTime = (TextView) view.findViewById(R.id.elapsedTime);
        mPrimaryCallCardContainer = view.findViewById(R.id.primary_call_info_container);
        mPrimaryCallInfo = (ViewGroup) view.findViewById(R.id.primary_call_banner);
        mCallButtonsContainer = view.findViewById(R.id.callButtonFragment);
        mInCallMessageLabel = (TextView) view.findViewById(R.id.connectionServiceMessage);
        mProgressSpinner = view.findViewById(R.id.progressSpinner);

        mFloatingActionButtonContainer = view.findViewById(
                R.id.floating_end_call_action_button_container);
        mFloatingActionButton = (ImageButton) view.findViewById(
                R.id.floating_end_call_action_button);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });
        mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                mFloatingActionButtonContainer, mFloatingActionButton);

        mSecondaryCallInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().secondaryInfoClicked();
                updateFabPositionForSecondaryCallInfo();
            }
        });

        mCallStateButton = view.findViewById(R.id.callStateButton);
        mCallStateButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                getPresenter().onCallStateButtonTouched();
                return false;
            }
        });

        mManageConferenceCallButton = view.findViewById(R.id.manage_conference_call_button);
        mManageConferenceCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InCallActivity activity = (InCallActivity) getActivity();
                activity.showConferenceFragment(true);
            }
        });

        mPrimaryName.setElegantTextHeight(false);
        mCallStateLabel.setElegantTextHeight(false);
        // SPRD: Add for recorder && bug546555
        mRecordingLabel = (TextView) view.findViewById(R.id.recordinglabel);
        // Show mRecordingLabel when recorder state is active.
        if (mRecordingLabel != null && getActivity() != null
                && ((InCallActivity)getActivity()).getRecorderState()!= null
                && ((InCallActivity)getActivity()).getRecorderState().isActive()) {
            mRecordingLabel.setVisibility(View.VISIBLE);
        }
        /* @} */
        /* SPRD: Porting Caller Address Feature. */
        mGeocodeView = (TextView) view.findViewById(R.id.geocode);
        if (mGeocodeView != null) {
            mGeocodeView.setVisibility(CallerAddressHelper.getsInstance(getActivity())
                    .isSupportCallerAddress() ? View.VISIBLE : View.GONE);
        }
        /* @} */
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Hides or shows the progress spinner.
     *
     * @param visible {@code True} if the progress spinner should be visible.
     */
    @Override
    public void setProgressSpinnerVisible(boolean visible) {
        mProgressSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the visibility of the primary call card.
     * Ensures that when the primary call card is hidden, the video surface slides over to fill the
     * entire screen.
     *
     * @param visible {@code True} if the primary call card should be visible.
     */
    @Override
    public void setCallCardVisible(final boolean visible) {
        // When animating the hide/show of the views in a landscape layout, we need to take into
        // account whether we are in a left-to-right locale or a right-to-left locale and adjust
        // the animations accordingly.
        final boolean isLayoutRtl = InCallPresenter.isRtl();

        // Retrieve here since at fragment creation time the incoming video view is not inflated.
        final View videoView = getView().findViewById(R.id.incomingVideo);
        if (videoView == null) {
            return;
        }

        // Determine how much space there is below or to the side of the call card.
        final float spaceBesideCallCard = getSpaceBesideCallCard();

        // We need to translate the video surface, but we need to know its position after the layout
        // has occurred so use a {@code ViewTreeObserver}.
        final ViewTreeObserver observer = getView().getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // We don't want to continue getting called.
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }

                float videoViewTranslation = 0f;

                // Translate the call card to its pre-animation state.
                if (!mIsLandscape){
                    mPrimaryCallCardContainer.setTranslationY(visible ?
                            -mPrimaryCallCardContainer.getHeight() : 0);

                    if (visible) {
                        videoViewTranslation = videoView.getHeight() / 2 - spaceBesideCallCard / 2;
                    }
                }

                // Perform animation of video view.
                /* SPRD: fix bug 495983 @{ */
                /*ViewPropertyAnimator videoViewAnimator = videoView.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration);
                if (mIsLandscape) {
                    videoViewAnimator
                            .translationX(videoViewTranslation)
                            .start();
                } else {
                    videoViewAnimator
                            .translationY(videoViewTranslation)
                            .start();
                }
                videoViewAnimator.start();*/
                /* @} */

                // Animate the call card sliding.
                ViewPropertyAnimator callCardAnimator = mPrimaryCallCardContainer.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                if (!visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.GONE);
                                }
                            }

                            @Override
                            public void onAnimationStart(Animator animation) {
                                super.onAnimationStart(animation);
                                if (visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.VISIBLE);
                                }
                            }
                        });

                if (mIsLandscape) {
                    float translationX = mPrimaryCallCardContainer.getWidth();
                    translationX *= isLayoutRtl ? 1 : -1;
                    callCardAnimator
                            .translationX(visible ? 0 : translationX)
                            .start();
                } else {
                    callCardAnimator
                            .translationY(visible ? 0 : -mPrimaryCallCardContainer.getHeight())
                            .start();
                }

                return true;
            }
        });
        /* SPRD: modify for bug 518364 @ { */
        observer.dispatchOnPreDraw();
        /* @} */
    }

    /**
     * Determines the amount of space below the call card for portrait layouts), or beside the
     * call card for landscape layouts.
     *
     * @return The amount of space below or beside the call card.
     */
    public float getSpaceBesideCallCard() {
        if (mIsLandscape) {
            return getView().getWidth() - mPrimaryCallCardContainer.getWidth();
        } else {
            final int callCardHeight;
            // Retrieve the actual height of the call card, independent of whether or not the
            // outgoing call animation is in progress. The animation does not run in landscape mode
            // so this only needs to be done for portrait.
            if (mPrimaryCallCardContainer.getTag(R.id.view_tag_callcard_actual_height) != null) {
                callCardHeight = (int) mPrimaryCallCardContainer.getTag(
                        R.id.view_tag_callcard_actual_height);
            } else {
                callCardHeight = mPrimaryCallCardContainer.getHeight();
            }
            return getView().getHeight() - callCardHeight;
        }
    }

    @Override
    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText(null);
        } else {
            mPrimaryName.setText(nameIsNumber
                    ? PhoneNumberUtils.createTtsSpannable(name)
                    : name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    @Override
    public void setPrimaryImage(Drawable image) {
        if (image != null) {
            setDrawableToImageView(mPhoto, image);
        }
    }

    @Override
    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText(null);
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            mPhoneNumber.setText(PhoneNumberUtils.createTtsSpannable(number));
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    public void setPrimaryLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }

    }

    @Override
    public void setPrimary(int subId, String number, String name, boolean nameIsNumber,
                           String label, Drawable photo, boolean isSipCall) {
        Log.d(this, "Setting primary call");
        // SPRD: Porting Caller Address Feature.
        setCallerAddress(number, name, nameIsNumber);

        // set the name field.
        /* SPRD: FDN in dialer feature. @{
        * @orig
        * setPrimaryName(name, nameIsNumber); */
        boolean isSupportFdnListName = false;
        boolean isEmergencyCall = false;
        if (nameIsNumber) {
            isEmergencyCall = PhoneNumberUtils.isEmergencyNumber(name);
        } else {
            isEmergencyCall = PhoneNumberUtils.isEmergencyNumber(number);
        }
        if (getActivity() != null) {
            isSupportFdnListName = FdnListNameHelper.getInstance(
                    getActivity()).isSupportFdnListName(subId);
        }
        if (isSupportFdnListName && !isEmergencyCall) {
            FdnListNameHelper.getInstance(getActivity()).setFDNListName(
                    number, nameIsNumber, name, label, this, subId);
        } else {
            setPrimaryName(name, nameIsNumber);
        }

        if (!isSupportFdnListName) {
            if (TextUtils.isEmpty(number) && TextUtils.isEmpty(label)) {
                mCallNumberAndLabel.setVisibility(View.GONE);
                mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            } else {
                mCallNumberAndLabel.setVisibility(View.VISIBLE);
                mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            }
        }
        /* @} */

        /* SPRD: Porting Caller Address Feature. @{*/
        if (CallerAddressHelper.getsInstance(getActivity()).isSupportCallerAddress()
                && nameIsNumber) {
            mPhoneNumber.setVisibility(View.GONE);
        } else if(isSupportFdnListName && !isEmergencyCall && nameIsNumber) {
            setPrimaryPhoneNumber(name);
        } else {
            setPrimaryPhoneNumber(number);
        }
        /* @} */


        // Set the label (Mobile, Work, etc)
        setPrimaryLabel(label);

        showInternetCallLabel(isSipCall);

        setDrawableToImageView(mPhoto, photo);
    }

    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            String providerLabel, boolean isConference, boolean isVideoCall) {
        if (show != mSecondaryCallInfo.isShown()) {
            updateFabPositionForSecondaryCallInfo();
        }

        if (show) {
            boolean hasProvider = !TextUtils.isEmpty(providerLabel);
            showAndInitializeSecondaryCallInfo(hasProvider);

            mSecondaryCallConferenceCallIcon.setVisibility(isConference ? View.VISIBLE : View.GONE);
            mSecondaryCallVideoCallIcon.setVisibility(isVideoCall ? View.VISIBLE : View.GONE);

            mSecondaryCallName.setText(nameIsNumber
                    ? PhoneNumberUtils.createTtsSpannable(name)
                    : name);
            if (hasProvider) {
                mSecondaryCallProviderLabel.setText(providerLabel);
            }

            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mSecondaryCallName.setTextDirection(nameDirection);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public void setCallState(
            int subId, // SPRD: modify for bug 473889
            int state,
            int videoState,
            int sessionModificationState,
            DisconnectCause disconnectCause,
            String connectionLabel,
            Drawable callStateIcon,
            String gatewayNumber,
            boolean isWifi,
            boolean isConference) {
        boolean isGatewayCall = !TextUtils.isEmpty(gatewayNumber);
        // SPRD: modify for bug 473889
        CallStateLabel callStateLabel = getCallStateLabelFromState(subId, state, videoState,
                sessionModificationState, disconnectCause, connectionLabel, isGatewayCall, isWifi,
                isConference);

        Log.v(this, "setCallState " + callStateLabel.getCallStateLabel());
        Log.v(this, "AutoDismiss " + callStateLabel.isAutoDismissing());
        Log.v(this, "DisconnectCause " + disconnectCause.toString());
        Log.v(this, "gateway " + connectionLabel + gatewayNumber);

        if (TextUtils.equals(callStateLabel.getCallStateLabel(), mCallStateLabel.getText())) {
            // Nothing to do if the labels are the same
            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED) {
                mCallStateLabel.clearAnimation();
                mCallStateIcon.clearAnimation();
            }
            /* SPRD:bug523784 set vidoe icon gone when video to voice@{ */
            if (CallUtils.isVideoCall(videoState)
                    || (state == Call.State.ACTIVE && sessionModificationState == Call.SessionModificationState.WAITING_FOR_RESPONSE)) {
                mCallStateVideoCallIcon.setVisibility(View.VISIBLE);
            } else {
                mCallStateVideoCallIcon.setVisibility(View.GONE);
            }/* @} */
            return;
        }

        // Update the call state label and icon.
        setCallStateLabel(callStateLabel);
        if (!TextUtils.isEmpty(callStateLabel.getCallStateLabel())) {
            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED) {
                mCallStateLabel.clearAnimation();
            } else {
                mCallStateLabel.startAnimation(mPulseAnimation);
            }
        }

        if (callStateIcon != null) {
            mCallStateIcon.setVisibility(View.VISIBLE);
            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(1.0f);
            mCallStateIcon.setImageDrawable(callStateIcon);

            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED
                    || TextUtils.isEmpty(callStateLabel.getCallStateLabel())) {
                mCallStateIcon.clearAnimation();
            } else {
                mCallStateIcon.startAnimation(mPulseAnimation);
            }

            if (callStateIcon instanceof AnimationDrawable) {
                ((AnimationDrawable) callStateIcon).start();
            }
        } else {
            Animation callStateIconAnimation = mCallStateIcon.getAnimation();
            if (callStateIconAnimation != null) {
                callStateIconAnimation.cancel();
            }

            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(0.0f);
            mCallStateIcon.setVisibility(View.GONE);
        }

        if (CallUtils.isVideoCall(videoState)
                || (state == Call.State.ACTIVE && sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_RESPONSE)) {
            mCallStateVideoCallIcon.setVisibility(View.VISIBLE);
        } else {
            mCallStateVideoCallIcon.setVisibility(View.GONE);
        }
    }

    private void setCallStateLabel(CallStateLabel callStateLabel) {
        Log.v(this, "setCallStateLabel : label = " + callStateLabel.getCallStateLabel());

        if (callStateLabel.isAutoDismissing()) {
            mCallStateLabelResetPending = true;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.v(this, "restoringCallStateLabel : label = " +
                            mPostResetCallStateLabel);
                    changeCallStateLabel(mPostResetCallStateLabel);
                    mCallStateLabelResetPending = false;
                }
            }, CALL_STATE_LABEL_RESET_DELAY_MS);

            changeCallStateLabel(callStateLabel.getCallStateLabel());
        } else {
            // Keep track of the current call state label; used when resetting auto dismissing
            // call state labels.
            mPostResetCallStateLabel = callStateLabel.getCallStateLabel();

            if (!mCallStateLabelResetPending) {
                changeCallStateLabel(callStateLabel.getCallStateLabel());
            }
        }
    }

    private void changeCallStateLabel(CharSequence callStateLabel) {
        Log.v(this, "changeCallStateLabel : label = " + callStateLabel);
        if (!TextUtils.isEmpty(callStateLabel)) {
            /* SPRD: modify for bug 510339 @{ */
            mCallStateLabel.requestFocus();
            /* @} */
            mCallStateLabel.setText(callStateLabel);
            mCallStateLabel.setAlpha(1);
            mCallStateLabel.setVisibility(View.VISIBLE);
        } else {
            Animation callStateLabelAnimation = mCallStateLabel.getAnimation();
            if (callStateLabelAnimation != null) {
                callStateLabelAnimation.cancel();
            }
            mCallStateLabel.setText(null);
            mCallStateLabel.setAlpha(0);
            mCallStateLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setCallbackNumber(String callbackNumber, boolean isEmergencyCall) {
        if (mInCallMessageLabel == null) {
            return;
        }

        if (TextUtils.isEmpty(callbackNumber)) {
            mInCallMessageLabel.setVisibility(View.GONE);
            return;
        }

        // TODO: The new Locale-specific methods don't seem to be working. Revisit this.
        callbackNumber = PhoneNumberUtils.formatNumber(callbackNumber);

        int stringResourceId = isEmergencyCall ? R.string.card_title_callback_number_emergency
                : R.string.card_title_callback_number;

        String text = getString(stringResourceId, callbackNumber);
        mInCallMessageLabel.setText(text);

        mInCallMessageLabel.setVisibility(View.VISIBLE);
    }

    public boolean isAnimating() {
        return mIsAnimating;
    }

    private void showInternetCallLabel(boolean show) {
        if (show) {
            final String label = getView().getContext().getString(
                    R.string.incall_call_type_label_sip);
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(label);
        } else {
            mCallTypeLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimaryCallElapsedTime(boolean show, long duration) {
        if (show) {
            if (mElapsedTime.getVisibility() != View.VISIBLE) {
                AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
            }
            String callTimeElapsed = DateUtils.formatElapsedTime(duration / 1000);
            mElapsedTime.setText(callTimeElapsed);

            String durationDescription =
                    InCallDateUtils.formatDuration(getView().getContext(), duration);
            mElapsedTime.setContentDescription(
                    !TextUtils.isEmpty(durationDescription) ? durationDescription : null);
        } else {
            // hide() animation has no effect if it is already hidden.
            /* SPRD: duration time should not fadeout when hangup call for cmcc case feature. @{
             * @orig
             * AnimUtils.fadeOut(mElapsedTime, AnimUtils.DEFAULT_DURATION); */
            InCallUIPlugin.getInstance().setPrimaryCallElapsedTime(mElapsedTime);
            /* @} */
        }
    }

    private void setDrawableToImageView(ImageView view, Drawable photo) {
        if (photo == null) {
            photo = ContactInfoCache.getInstance(
                    view.getContext()).getDefaultContactPhotoDrawable();
        }

        if (mPrimaryPhotoDrawable == photo) {
            return;
        }
        mPrimaryPhotoDrawable = photo;

        final Drawable current = view.getDrawable();
        if (current == null) {
            view.setImageDrawable(photo);
            AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        } else {
            // Cross fading is buggy and not noticable due to the multiple calls to this method
            // that switch drawables in the middle of the cross-fade animations. Just set the
            // photo directly instead.
            view.setImageDrawable(photo);
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Gets the call state label based on the state of the call or cause of disconnect.
     *
     * Additional labels are applied as follows:
     *         1. All outgoing calls with display "Calling via [Provider]".
     *         2. Ongoing calls will display the name of the provider.
     *         3. Incoming calls will only display "Incoming via..." for accounts.
     *         4. Video calls, and session modification states (eg. requesting video).
     *         5. Incoming and active Wi-Fi calls will show label provided by hint.
     *
     * TODO: Move this to the CallCardPresenter.
     */
    // SPRD: modify for bug 473889
    private CallStateLabel getCallStateLabelFromState(int subId, int state, int videoState,
            int sessionModificationState, DisconnectCause disconnectCause, String label,
            boolean isGatewayCall, boolean isWifi, boolean isConference) {
        final Context context = getView().getContext();
        CharSequence callStateLabel = null;  // Label to display as part of the call banner

        boolean hasSuggestedLabel = label != null;
        boolean isAccount = hasSuggestedLabel && !isGatewayCall;
        boolean isAutoDismissing = false;

        // SPRD: show main/vice card
        TelephonyManager telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        switch  (state) {
            case Call.State.IDLE:
                // "Call state" is meaningless in this state.
                break;
            case Call.State.ACTIVE:
                // We normally don't show a "call state label" at all in this state
                // (but we can use the call state label to display the provider name).
                if ((isAccount || isWifi || isConference) && hasSuggestedLabel) {
                    /* SPRD: show main/vice card for bug473889 {@ */
                    callStateLabel = getSlotInfo(telephonyManager, subId) + label;
                    /* @} */
                } else if (sessionModificationState
                        == Call.SessionModificationState.REQUEST_REJECTED) {
                    callStateLabel = context.getString(R.string.card_title_video_call_rejected);
                    isAutoDismissing = true;
                } else if (sessionModificationState
                        == Call.SessionModificationState.REQUEST_FAILED) {
                    callStateLabel = context.getString(R.string.card_title_video_call_error);
                    isAutoDismissing = true;
                } else if (sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_RESPONSE) {
                    callStateLabel = context.getString(R.string.card_title_video_call_requesting);
                } else if (sessionModificationState
                        == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
                    callStateLabel = context.getString(R.string.card_title_video_call_requesting);
                } else if (CallUtils.isVideoCall(videoState)) {
                    callStateLabel = context.getString(R.string.card_title_video_call);
                }
                break;
            case Call.State.ONHOLD:
                callStateLabel = context.getString(R.string.card_title_on_hold);
                break;
            case Call.State.CONNECTING:
            case Call.State.DIALING:
                if (hasSuggestedLabel && !isWifi) {
                    /* SPRD: show main/vice card for bug474945 {@ */
                    // Do not show main/vice card in emergency call when phoneId is invalid.
                    int phoneId = telephonyManager.getPhoneId(subId);
                    if ( phoneId >= 0 && phoneId < telephonyManager.getPhoneCount()) {
                        callStateLabel = getSlotInfo(telephonyManager, subId)
                                + context.getString(R.string.calling_via_template, label);
                    } else {
                        callStateLabel = "";
                    }
                    // callStateLabel = context.getString(R.string.calling_via_template, label);
                    /* @} */
                } else {
                    callStateLabel = context.getString(R.string.card_title_dialing);
                }
                break;
            case Call.State.REDIALING:
                callStateLabel = context.getString(R.string.card_title_redialing);
                break;
            case Call.State.INCOMING:
            case Call.State.CALL_WAITING:
                if (isWifi && hasSuggestedLabel) {
                    callStateLabel = label;
                } else if (isAccount) {
                    // Modified for bug540856
                    callStateLabel = context.getString(R.string.incoming_via_template,
                            getSlotInfo(telephonyManager, subId) + label/* SPRD: show main/vice card */);
                } else if (VideoProfile.isTransmissionEnabled(videoState) ||
                        VideoProfile.isReceptionEnabled(videoState)) {
                    callStateLabel = context.getString(R.string.notification_incoming_video_call);
                } else {
                    callStateLabel = context.getString(R.string.card_title_incoming_call);
                }
                break;
            case Call.State.DISCONNECTING:
                // While in the DISCONNECTING state we display a "Hanging up"
                // message in order to make the UI feel more responsive.  (In
                // GSM it's normal to see a delay of a couple of seconds while
                // negotiating the disconnect with the network, so the "Hanging
                // up" state at least lets the user know that we're doing
                // something.  This state is currently not used with CDMA.)
                callStateLabel = context.getString(R.string.card_title_hanging_up);
                break;
            case Call.State.DISCONNECTED:
                callStateLabel = disconnectCause.getLabel();
                /* SPRD: Support telcel requirements in InCallUI. @{ */
                if (TextUtils.isEmpty(callStateLabel)) {
                    callStateLabel = context.getString(R.string.card_title_call_ended);
                } else if (getActivity() != null && InCallUITelcelHelper.getsInstance(
                        getActivity()).isVoiceClearCodeLabel(
                        getActivity(), callStateLabel.toString())) {
                    callStateLabel = context.getString(R.string.card_title_call_ended);
                }
                /* @} */
                break;
            case Call.State.CONFERENCED:
                callStateLabel = context.getString(R.string.card_title_conf_call);
                break;
            default:
                Log.wtf(this, "updateCallStateWidgets: unexpected call: " + state);
        }
        return new CallStateLabel(callStateLabel, isAutoDismissing);
    }

    private void showAndInitializeSecondaryCallInfo(boolean hasProvider) {
        mSecondaryCallInfo.setVisibility(View.VISIBLE);
        /* SPRD:Add for bug531347 @{ */
        final ViewTreeObserver observer =getView().getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mIsLandscape) {
                    mSecondryCallInfoHeight = mSecondaryCallInfo.getWidth();
                } else {
                    mSecondryCallInfoHeight = mSecondaryCallInfo.getHeight();
                }
                Log.d(this,"showAndInitializeSecondaryCallInfo->mSecondryCallInfoHeight = " + mSecondryCallInfoHeight);
                // Remove the listener so we don't continually re-layout.
                ViewTreeObserver observer = getView().getViewTreeObserver();
                if (observer.isAlive() && mSecondryCallInfoHeight > 0) {
                    observer.removeOnGlobalLayoutListener(this);
                }
            }
        });
        /* @ } */
        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccessible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
            mSecondaryCallConferenceCallIcon =
                    getView().findViewById(R.id.secondaryCallConferenceCallIcon);
            mSecondaryCallVideoCallIcon =
                    getView().findViewById(R.id.secondaryCallVideoCallIcon);
        }

        if (mSecondaryCallProviderLabel == null && hasProvider) {
            mSecondaryCallProviderInfo.setVisibility(View.VISIBLE);
            mSecondaryCallProviderLabel = (TextView) getView()
                    .findViewById(R.id.secondaryCallProviderLabel);
        }
    }

    public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT) {
            dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
            dispatchPopulateAccessibilityEvent(event, mPrimaryName);
            dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
            dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
            return;
        }
        dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
        dispatchPopulateAccessibilityEvent(event, mPrimaryName);
        dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
        dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallName);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallProviderLabel);

        return;
    }

    @Override
    public void sendAccessibilityAnnouncement() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getView() != null && getView().getParent() != null) {
                    AccessibilityEvent event = AccessibilityEvent.obtain(
                            AccessibilityEvent.TYPE_ANNOUNCEMENT);
                    dispatchPopulateAccessibilityEvent(event);
                    getView().getParent().requestSendAccessibilityEvent(getView(), event);
                }
            }
        }, ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS);
    }

    @Override
    public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
        if (enabled != mFloatingActionButton.isEnabled()) {
            if (animate) {
                if (enabled) {
                    mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
                } else {
                    mFloatingActionButtonController.scaleOut();
                }
            } else {
                if (enabled) {
                    mFloatingActionButtonContainer.setScaleX(1);
                    mFloatingActionButtonContainer.setScaleY(1);
                    mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                } else {
                    mFloatingActionButtonContainer.setVisibility(View.GONE);
                }
            }
            mFloatingActionButton.setEnabled(enabled);
            updateFabPosition();
        }
    }

    /**
     * Changes the visibility of the HD audio icon.
     *
     * @param visible {@code true} if the UI should show the HD audio icon.
     */
    @Override
    public void showHdAudioIndicator(boolean visible) {
       //SPRD: Add incallui Hd Voice plugin
       mHdAudioIcon.setBackground(InCallUIHdVoicePlugin.getInstance().getCallStateIcon(getView().getContext()));
       mHdAudioIcon.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Changes the visibility of the "manage conference call" button.
     *
     * @param visible Whether to set the button to be visible or not.
     */
    @Override
    public void showManageConferenceCallButton(boolean visible) {
        mManageConferenceCallButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Determines the current visibility of the manage conference button.
     *
     * @return {@code true} if the button is visible.
     */
    @Override
    public boolean isManageConferenceVisible() {
        return mManageConferenceCallButton.getVisibility() == View.VISIBLE;
    }

    /**
     * Get the overall InCallUI background colors and apply to call card.
     */
    public void updateColors() {
        MaterialPalette themeColors = InCallPresenter.getInstance().getThemeColors();

        // SPRD: Add for 525471, return when themeColors is null
        if ((mCurrentThemeColors != null && mCurrentThemeColors.equals(themeColors)) ||
                themeColors == null) {
            return;
        }

        if (getResources().getBoolean(R.bool.is_layout_landscape)) {
            final GradientDrawable drawable =
                    (GradientDrawable) mPrimaryCallCardContainer.getBackground();
            drawable.setColor(themeColors.mPrimaryColor);
        } else {
            mPrimaryCallCardContainer.setBackgroundColor(themeColors.mPrimaryColor);
        }
        mCallButtonsContainer.setBackgroundColor(themeColors.mPrimaryColor);

        mCurrentThemeColors = themeColors;
    }

    private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
        if (view == null) return;
        final List<CharSequence> eventText = event.getText();
        int size = eventText.size();
        view.dispatchPopulateAccessibilityEvent(event);
        // if no text added write null to keep relative position
        if (size == eventText.size()) {
            eventText.add(null);
        }
    }

    @Override
    public void animateForNewOutgoingCall() {
        final ViewGroup parent = (ViewGroup) mPrimaryCallCardContainer.getParent();

        final ViewTreeObserver observer = getView().getViewTreeObserver();

        mIsAnimating = true;

        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ViewTreeObserver observer = getView().getViewTreeObserver();
                if (!observer.isAlive()) {
                    return;
                }
                observer.removeOnGlobalLayoutListener(this);

                final LayoutIgnoringListener listener = new LayoutIgnoringListener();
                mPrimaryCallCardContainer.addOnLayoutChangeListener(listener);

                // Prepare the state of views before the slide animation
                final int originalHeight = mPrimaryCallCardContainer.getHeight();
                mPrimaryCallCardContainer.setTag(R.id.view_tag_callcard_actual_height,
                        originalHeight);
                mPrimaryCallCardContainer.setBottom(parent.getHeight());

                // Set up FAB.
                mFloatingActionButtonContainer.setVisibility(View.GONE);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());

                mCallButtonsContainer.setAlpha(0);
                mCallStateLabel.setAlpha(0);
                mPrimaryName.setAlpha(0);
                mCallTypeLabel.setAlpha(0);
                mCallNumberAndLabel.setAlpha(0);

                assignTranslateAnimation(mCallStateLabel, 1);
                assignTranslateAnimation(mCallStateIcon, 1);
                assignTranslateAnimation(mPrimaryName, 2);
                assignTranslateAnimation(mGeocodeView, 2); // SPRD: Porting Caller Address Feature
                assignTranslateAnimation(mCallNumberAndLabel, 3);
                assignTranslateAnimation(mCallTypeLabel, 4);
                assignTranslateAnimation(mCallButtonsContainer, 5);

                final Animator animator = getShrinkAnimator(parent.getHeight(), originalHeight);

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mPrimaryCallCardContainer.setTag(R.id.view_tag_callcard_actual_height,
                                null);
                        setViewStatePostAnimation(listener);
                        mIsAnimating = false;
                        InCallPresenter.getInstance().onShrinkAnimationComplete();
                        /* SPRD: Fix OOM issue @{ */
                        if (animator != null) {
                            animator.removeListener(this);
                        }
                        /* @} */
                    }
                });
                animator.start();
            }
        });
    }

    public void onDialpadVisibilityChange(boolean isShown) {
        mIsDialpadShowing = isShown;
        updateFabPosition();
    }

    private void updateFabPosition() {
        int offsetY = 0;
        if (!mIsDialpadShowing) {
            offsetY = mFloatingActionButtonVerticalOffset;
            /*SPRD:Fix bug 523787 @{ */
            if (mSecondaryCallInfo.isShown() && !mIsLandscape) {
                offsetY -= mSecondaryCallInfo.getHeight();
            }
            /* @} */
        }

        mFloatingActionButtonController.align(
                mIsLandscape ? FloatingActionButtonController.ALIGN_QUARTER_END
                        : FloatingActionButtonController.ALIGN_MIDDLE,
                0 /* offsetX */,
                offsetY,
                true);

        mFloatingActionButtonController.resize(
                mIsDialpadShowing ? mFabSmallDiameter : mFabNormalDiameter, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // If the previous launch animation is still running, cancel it so that we don't get
        // stuck in an intermediate animation state.
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.cancel();
        }

        mIsLandscape = getResources().getBoolean(R.bool.is_layout_landscape);

        final ViewGroup parent = ((ViewGroup) mPrimaryCallCardContainer.getParent());
        final ViewTreeObserver observer = parent.getViewTreeObserver();
        parent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewTreeObserver viewTreeObserver = observer;
                if (!viewTreeObserver.isAlive()) {
                    viewTreeObserver = parent.getViewTreeObserver();
                }
                viewTreeObserver.removeOnGlobalLayoutListener(this);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());
                updateFabPosition();
            }
        });

        updateColors();
    }

    /**
     * Adds a global layout listener to update the FAB's positioning on the next layout. This allows
     * us to position the FAB after the secondary call info's height has been calculated.
     */
    private void updateFabPositionForSecondaryCallInfo() {
        mSecondaryCallInfo.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer = mSecondaryCallInfo.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        observer.removeOnGlobalLayoutListener(this);

                        onDialpadVisibilityChange(mIsDialpadShowing);
                    }
                });
    }

    /**
     * Animator that performs the upwards shrinking animation of the blue call card scrim.
     * At the start of the animation, each child view is moved downwards by a pre-specified amount
     * and then translated upwards together with the scrim.
     */
    private Animator getShrinkAnimator(int startHeight, int endHeight) {
        final ObjectAnimator shrinkAnimator =
                ObjectAnimator.ofInt(mPrimaryCallCardContainer, "bottom", startHeight, endHeight);
        shrinkAnimator.setDuration(mShrinkAnimationDuration);
        shrinkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFloatingActionButton.setEnabled(true);
            }
        });
        shrinkAnimator.setInterpolator(AnimUtils.EASE_IN);
        return shrinkAnimator;
    }

    private void assignTranslateAnimation(View view, int offset) {
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.buildLayer();
        view.setTranslationY(mTranslationOffset * offset);
        view.animate().translationY(0).alpha(1).withLayer()
                .setDuration(mShrinkAnimationDuration).setInterpolator(AnimUtils.EASE_IN);
    }

    private void setViewStatePostAnimation(View view) {
        view.setTranslationY(0);
        view.setAlpha(1);
    }

    private void setViewStatePostAnimation(OnLayoutChangeListener layoutChangeListener) {
        setViewStatePostAnimation(mCallButtonsContainer);
        setViewStatePostAnimation(mCallStateLabel);
        setViewStatePostAnimation(mPrimaryName);
        setViewStatePostAnimation(mCallTypeLabel);
        setViewStatePostAnimation(mCallNumberAndLabel);
        setViewStatePostAnimation(mCallStateIcon);
        setViewStatePostAnimation(mGeocodeView); // SPRD: Porting Caller Address Feature

        mPrimaryCallCardContainer.removeOnLayoutChangeListener(layoutChangeListener);

        /*SPRD: don't show end button if state is incoming or disconnected. @{
          * @orig
        mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);*/
        final Call call = CallList.getInstance().getFirstCall();
        if (call != null) {
            int state = call.getState();
            if (!Call.State.isIncoming(state)
                    && Call.State.isConnectingOrConnected(state)) {
                mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
            } else if (mFloatingActionButton.isEnabled()) {
                mFloatingActionButton.setEnabled(false);
            }
        } else {
            mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
        }
        /* @} */
    }

    private final class LayoutIgnoringListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            v.setLeft(oldLeft);
            v.setRight(oldRight);
            v.setTop(oldTop);
            v.setBottom(oldBottom);
        }
    }

    //------------------- SPRD ------------------------

    /* SPRD:Add for recorder @{ */
    public void setRecordText(String str){
        mRecordingLabel.setText(str);
    }

    public TextView getRecordingLabel(){
        return mRecordingLabel;
    }

    public void setRecordingVisibility(int visibility){
        mRecordingLabel.setVisibility(visibility);
    }
    /* } */
    // SPRD: modify for bug 473889
    @Override
    public void setCallState(int state, int videoState, int sessionModificationState,
            DisconnectCause disconnectCause, String connectionLabel, Drawable connectionIcon,
            String gatewayNumber, boolean isWifi, boolean isConference) {
        // TODO Auto-generated method stub
    }

    /* SPRD: add for bug497046@{ */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(DIALPAD_SHOWING, mIsDialpadShowing);
    }/* @} */

    /* SPRD: Porting Caller Address Feature. @{ */
    private void setCallerAddress(String number, String name, boolean nameIsNumber) {
        if (mGeocodeView != null) {
            String newDescription = "";
            String oldDescription = mGeocodeView.getText().toString();
            if (nameIsNumber) {
                newDescription = CallerAddressHelper.getsInstance(getActivity())
                        .getCallerAddress(getActivity(), name);
            } else {
                newDescription = CallerAddressHelper.getsInstance(getActivity())
                        .getCallerAddress(getActivity(), number);
            }
            if (!TextUtils.isEmpty(newDescription) && !oldDescription.equals(newDescription)) {
                Log.d(this, "newDescription:" + newDescription);
                mGeocodeView.setText(newDescription);
            }
        }
    }
    /* @} */
    /* SPRD:add for bug531347@{ */
    public float getSecondaryCallInfo() {
        Log.d(this,"getSecondaryCallInfo->mSecondaryCallInfo = " + mSecondaryCallInfo + " mSecondryCallInfoHeight = " + mSecondryCallInfoHeight);
        if (mSecondaryCallInfo == null) {
            return 0.0f;
        } else {
            return mSecondryCallInfoHeight;
        }
    }
    /* @} */

    public String getSlotInfo(TelephonyManager tm, int subId) {
        int primaryCardId = tm.getPrimaryCard();
        int phoneId = tm.getPhoneId(subId);
        String card_slot = "";
        if (phoneId == primaryCardId) {
            card_slot = this.getString(R.string.main_card_slot);
        } else {
            card_slot = this.getString(R.string.vice_card_slot);
        }
        return card_slot;
    }

    //SPRD: Add incallui Hd Voice plugin
    @Override
    public void showHdAudioIndicatorPlugin(Call call, Context context) {
       InCallUIHdVoicePlugin.getInstance().showHdAudioIndicator(mHdAudioIcon, call, context);
    }

    /* SPRD: FDN in dialer feature. @{ */
    @Override
    public void setPrimary(String number, String name, boolean nameIsNumber, String label,
                           Drawable photo, boolean isSipCall) {

    }

    public void setCallNumberAndLabel(String number, String label) {
        if (TextUtils.isEmpty(number) && TextUtils.isEmpty(label)) {
            mCallNumberAndLabel.setVisibility(View.GONE);
            mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        } else {
            mCallNumberAndLabel.setVisibility(View.VISIBLE);
            mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        }
    }
    /* @} */
}
