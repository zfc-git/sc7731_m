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

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.Call.Details;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallEventListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incalluibind.ObjectFactory;

import java.lang.ref.WeakReference;
import com.android.ims.ImsManager;
import com.google.common.base.Preconditions;
import com.sprd.incallui.InCallUIHdVoicePlugin;

import static android.Manifest.permission.READ_PHONE_STATE;
import android.content.pm.PackageManager;

/**
 * Presenter for the Call Card Fragment.
 * <p>
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener, IncomingCallListener, InCallDetailsListener,
        InCallEventListener, CallList.CallUpdateListener {

    public interface EmergencyCallListener {
        public void onCallUpdated(BaseFragment fragment, boolean isEmergency);
    }

    private static final String TAG = CallCardPresenter.class.getSimpleName();
    private static final long CALL_TIME_UPDATE_INTERVAL_MS = 1000;

    private final EmergencyCallListener mEmergencyCallListener = ObjectFactory.newEmergencyCallListener();

    private Call mPrimary;
    private Call mSecondary;
    private ContactCacheEntry mPrimaryContactInfo;
    private ContactCacheEntry mSecondaryContactInfo;
    private CallTimer mCallTimer;
    private Context mContext;
    private boolean mSpinnerShowing = false;
    private boolean mIsConferenceState = false; //SPRD:Add for bug548426
    // SPRD: FDN in dialer feature.
    private static final int INVALID_SUBSCRIPTION_ID = -1;

    public static class ContactLookupCallback implements ContactInfoCacheCallback {
        private final WeakReference<CallCardPresenter> mCallCardPresenter;
        private final boolean mIsPrimary;

        public ContactLookupCallback(CallCardPresenter callCardPresenter, boolean isPrimary) {
            mCallCardPresenter = new WeakReference<CallCardPresenter>(callCardPresenter);
            mIsPrimary = isPrimary;
        }

        @Override
        public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
            CallCardPresenter presenter = mCallCardPresenter.get();
            if (presenter != null) {
                presenter.onContactInfoComplete(callId, entry, mIsPrimary);
            }
        }

        @Override
        public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
            CallCardPresenter presenter = mCallCardPresenter.get();
            if (presenter != null) {
                presenter.onImageLoadComplete(callId, entry);
            }
        }

    }

    public CallCardPresenter() {
        // create the call timer
        mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                updateCallTime();
            }
        });
    }

    public void init(Context context, Call call) {
        mContext = Preconditions.checkNotNull(context);

        // Call may be null if disconnect happened already.
        if (call != null) {
            mPrimary = call;
            CallList.getInstance().addCallUpdateListener(call.getId(), this);

            // start processing lookups right away.
            if (!call.isConferenceCall()) {
                startContactInfoSearch(call, true, call.getState() == Call.State.INCOMING);
            } else {
                updateContactEntry(null, true);
            }
        }

        onStateChange(null, InCallPresenter.getInstance().getInCallState(), CallList.getInstance());
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        // Contact search may have completed before ui is ready.
        if (mPrimaryContactInfo != null) {
            updatePrimaryDisplayInfo();
        }

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addInCallEventListener(this);
    }

    @Override
    public void onUiUnready(CallCardUi ui) {
        super.onUiUnready(ui);

        // stop getting call state changes
        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallEventListener(this);
        if (mPrimary != null) {
            CallList.getInstance().removeCallUpdateListener(mPrimary.getId(), this);
        }

        mPrimary = null;
        mPrimaryContactInfo = null;
        mSecondaryContactInfo = null;
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        Log.d(this, "onStateChange() " + newState);
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        Call primary = null;
        Call secondary = null;

        if (newState == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (newState == InCallState.PENDING_OUTGOING || newState == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();
            if (primary == null) {
                primary = callList.getPendingOutgoingCall();
            }

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null, true);
        } else if (newState == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null, false);
            secondary = getCallToDisplay(callList, primary, true);
        }

        Log.d(this, "Primary call: " + primary);
        Log.d(this, "Secondary call: " + secondary);

        /** SPRD: Support STK case 27.22.4.13.1/5 SET UP CALL. bug508777. @{ */
//        final boolean primaryChanged = !(Call.areSame(mPrimary, primary) &&
//                Call.areSameNumber(mPrimary, primary));
        boolean primaryNumberChanged = false;
        if (mPrimary != null) {
            ContactCacheEntry primaryCache = ContactInfoCache.getInstance(mContext).getInfo(mPrimary.getId());
            if (primaryCache != null && !TextUtils.isEmpty(mPrimary.getNumber())
                    && !TextUtils.isEmpty(primaryCache.number)
                    && !TextUtils.equals(primaryCache.number.replace(" ",""),
                    mPrimary.getNumber().replace(" ",""))) {
                primaryCache.number = mPrimary.getNumber();
                primaryNumberChanged = true;
                Log.d(this, "Primary call number changed: " + primary);
            }
        }
        final boolean primaryChanged = !(Call.areSame(mPrimary, primary) &&
                Call.areSameNumber(mPrimary, primary)) || primaryNumberChanged;
        /** @} */

        final boolean secondaryChanged = !(Call.areSame(mSecondary, secondary) &&
                Call.areSameNumber(mSecondary, secondary));


        mSecondary = secondary;
        Call previousPrimary = mPrimary;
        mPrimary = primary;
        /* SPRD: Add for bug548426 @{*/
        boolean conferenceStateChanged = false;
        if (mPrimary != null) {
            conferenceStateChanged = (mIsConferenceState != mPrimary.isGenericConferenceCall());
            mIsConferenceState = mPrimary.isGenericConferenceCall();
        }
        /* @} */
        // Refresh primary call information if either:
        // 1. Primary call changed.
        // 2. The call's ability to manage conference has changed.
        if (mPrimary != null && (primaryChanged ||
                ui.isManageConferenceVisible() != shouldShowConferenceList() ||//SPRD:add for VoLTE
                ui.isManageConferenceVisible() != shouldShowManageConference() || conferenceStateChanged)) {//SPRD:Modify for bug548426
            // primary call has changed
            if (previousPrimary != null) {
                CallList.getInstance().removeCallUpdateListener(previousPrimary.getId(), this);
            }
            CallList.getInstance().addCallUpdateListener(mPrimary.getId(), this);

            mPrimaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, mPrimary,
                    mPrimary.getState() == Call.State.INCOMING);
            updatePrimaryDisplayInfo();
            maybeStartSearch(mPrimary, true);
            mPrimary.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }

        if (previousPrimary != null && mPrimary == null) {
            CallList.getInstance().removeCallUpdateListener(previousPrimary.getId(), this);
        }

        if (mSecondary == null) {
            // Secondary call may have ended.  Update the ui.
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo();
        } else if (secondaryChanged) {
            // secondary call has changed
            mSecondaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, mSecondary,
                    mSecondary.getState() == Call.State.INCOMING);
            updateSecondaryDisplayInfo();
            maybeStartSearch(mSecondary, false);
            mSecondary.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }

        // Start/stop timers.
        //SPRD:add isPrimaryCallIsConference for bug532301
        if (isPrimaryCallActive() || isPrimaryCallIsConference()) {
            Log.d(this, "Starting the calltime timer");
            mCallTimer.start(CALL_TIME_UPDATE_INTERVAL_MS);
        } else {
            Log.d(this, "Canceling the calltime timer");
            mCallTimer.cancel();
            ui.setPrimaryCallElapsedTime(false, 0);
        }

        // Set the call state
        int callState = Call.State.IDLE;
        int subId = -1; // SPRD: show main/vice card for bug473889
        if (mPrimary != null) {
            callState = mPrimary.getState();
            updatePrimaryCallState();
        } else {
            getUi().setCallState(
                    subId, // SPRD: modify for bug 473889
                    callState,
                    VideoProfile.STATE_AUDIO_ONLY,
                    Call.SessionModificationState.NO_REQUEST,
                    new DisconnectCause(DisconnectCause.UNKNOWN),
                    null,
                    null,
                    null,
                    false /* isWifi */,
                    false /* isConference */);
            getUi().showHdAudioIndicator(false);
        }

        maybeShowManageConferenceCallButton();

        // Hide the end call button instantly if we're receiving an incoming call.
        getUi().setEndCallButtonEnabled(shouldShowEndCallButton(mPrimary, callState),
                callState != Call.State.INCOMING /* animate */);

        maybeSendAccessibilityEvent(oldState, newState);
    }

    @Override
    public void onDetailsChanged(Call call, Details details) {
        updatePrimaryCallState();

        if (call.can(Details.CAPABILITY_MANAGE_CONFERENCE) !=
                Details.can(details.getCallCapabilities(), Details.CAPABILITY_MANAGE_CONFERENCE)) {
            maybeShowManageConferenceCallButton();
        }
    }

    @Override
    public void onCallChanged(Call call) {
        // No-op; specific call updates handled elsewhere.
    }

    /**
     * Handles a change to the session modification state for a call.  Triggers showing the progress
     * spinner, as well as updating the call state label.
     *
     * @param sessionModificationState The new session modification state.
     */
    @Override
    public void onSessionModificationStateChange(int sessionModificationState) {
        Log.d(this, "onSessionModificationStateChange : sessionModificationState = " +
                sessionModificationState);

        if (mPrimary == null) {
            return;
        }
        maybeShowProgressSpinner(mPrimary.getState(), sessionModificationState);
        getUi().setEndCallButtonEnabled(sessionModificationState !=
                        Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST,
                true /* shouldAnimate */);
        updatePrimaryCallState();
    }

    private String getSubscriptionNumber() {
        // If it's an emergency call, and they're not populating the callback number,
        // then try to fall back to the phone sub info (to hopefully get the SIM's
        // number directly from the telephony layer).
        PhoneAccountHandle accountHandle = mPrimary.getAccountHandle();
        if (accountHandle != null) {
            TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
            PhoneAccount account = mgr.getPhoneAccount(accountHandle);
            if (account != null) {
                return getNumberFromHandle(account.getSubscriptionAddress());
            }
        }
        return null;
    }

    private void updatePrimaryCallState() {
        if (getUi() != null && mPrimary != null) {
            /* SPRD: show main/vice card for bug473889 @{ */
            int subId = -1;
            if (mPrimary.getAccountHandle() != null) {
                TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
                PhoneAccount account = mgr.getPhoneAccount(mPrimary.getAccountHandle());
                TelephonyManager telephonyManager = (TelephonyManager) mContext
                        .getSystemService(Context.TELEPHONY_SERVICE);
                subId = telephonyManager.getSubIdViaPhoneAccount(account);
            }
            /* @} */
            getUi().setCallState(
                    subId, // SPRD: modify for bug473889
                    mPrimary.getState(),
                    mPrimary.getVideoState(),
                    mPrimary.getSessionModificationState(),
                    mPrimary.getDisconnectCause(),
                    getConnectionLabel(),
                    getCallStateIcon(),
                    getGatewayNumber(),
                    mPrimary.hasProperty(Details.PROPERTY_WIFI),
                    mPrimary.isConferenceCall());

            //SPRD: Add incallui Hd Voice plugin
//            boolean showHdAudioIndicator =
//                    isPrimaryCallActive() && mPrimary.hasProperty(Details.PROPERTY_HIGH_DEF_AUDIO);
//            getUi().showHdAudioIndicator(showHdAudioIndicator);
            if (isPrimaryCallActive()) {
                getUi().showHdAudioIndicatorPlugin(mPrimary, mContext);
            }
            setCallbackNumber();
        }
    }

    /**
     * Only show the conference call button if we can manage the conference.
     */
    private void maybeShowManageConferenceCallButton() {
        getUi().showManageConferenceCallButton(shouldShowManageConference());
        /*SPRD: add for VoLTE{@*/
        if(mContext != null && mContext.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED && ImsManager.isVolteEnabledByPlatform(mContext)){
            InCallPresenter.getInstance().showConferenceListFragment(shouldShowConferenceList());
        }
        /*@}*/
    }

    /**
     * Determines if a pending session modification exists for the current call.  If so, the
     * progress spinner is shown, and the call state is updated.
     *
     * @param callState The call state.
     * @param sessionModificationState The session modification state.
     */
    private void maybeShowProgressSpinner(int callState, int sessionModificationState) {
        final boolean show = sessionModificationState ==
                Call.SessionModificationState.WAITING_FOR_RESPONSE
                && callState == Call.State.ACTIVE;
        if (show != mSpinnerShowing) {
            getUi().setProgressSpinnerVisible(show);
            mSpinnerShowing = show;
        }
    }

    /**
     * Determines if the manage conference button should be visible, based on the current primary
     * call.
     *
     * @return {@code True} if the manage conference button should be visible.
     */
    private boolean shouldShowManageConference() {
        if (mPrimary == null) {
            return false;
        }

        return mPrimary.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE)
                && !mPrimary.isVideoCall(mContext);
    }

    private void setCallbackNumber() {
        String callbackNumber = null;

        // Show the emergency callback number if either:
        // 1. This is an emergency call.
        // 2. The phone is in Emergency Callback Mode, which means we should show the callback
        //    number.
        boolean isEmergencyCall = PhoneNumberUtils.isEmergencyNumber(
                getNumberFromHandle(mPrimary.getHandle()));
        boolean showCallbackNumber = mPrimary.hasProperty(Details.PROPERTY_EMERGENCY_CALLBACK_MODE);

        if (isEmergencyCall || showCallbackNumber) {
            callbackNumber = getSubscriptionNumber();
        } else {
            StatusHints statusHints = mPrimary.getTelecommCall().getDetails().getStatusHints();
            if (statusHints != null) {
                Bundle extras = statusHints.getExtras();
                if (extras != null) {
                    callbackNumber = extras.getString(TelecomManager.EXTRA_CALL_BACK_NUMBER);
                }
            }
        }

        TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
        String simNumber = mgr.getLine1Number(mPrimary.getAccountHandle());
        if (!showCallbackNumber && PhoneNumberUtils.compare(callbackNumber, simNumber)) {
            Log.d(this, "Numbers are the same (and callback number is not being forced to show);" +
                    " not showing the callback number");
            callbackNumber = null;
        }

        getUi().setCallbackNumber(callbackNumber, isEmergencyCall || showCallbackNumber);
    }

    public void updateCallTime() {
        final CallCardUi ui = getUi();

        if (ui == null) {
            mCallTimer.cancel();
        /* SPRD: add for bug532301 @{*/
        } else if (isPrimaryCallIsConference()) {
            if(mPrimary != null){
                Call conference = mPrimary;
                if(mPrimary.getParentId() != null){
                    conference = CallList.getInstance().getCallById(mPrimary.getParentId());
                }
                long callStart = Long.MAX_VALUE;
                if(mPrimary.getGroupState() == Call.State.ACTIVE){
                    callStart = conference.getConnectRealTimeMillis();
                }
                if(conference.getChildCallIds().size() > 0){
                    Call c;
                    for(String id : conference.getChildCallIds()){
                        c = CallList.getInstance().getCallById(id);
                        if(c != null && c.getGroupState() == Call.State.ACTIVE
                                && c.getConnectRealTimeMillis() < callStart){
                            callStart = c.getConnectRealTimeMillis();
                        }
                    }
                }
                final long duration = SystemClock.elapsedRealtime() - callStart;
                if(callStart != Long.MAX_VALUE && duration > 0){
                    ui.setPrimaryCallElapsedTime(true, duration);
                } else {
                    ui.setPrimaryCallElapsedTime(false, 0);
                }
            } else {
                ui.setPrimaryCallElapsedTime(false, 0);
            }
        /* @} */
        } else if (!isPrimaryCallActive()) {
            ui.setPrimaryCallElapsedTime(false, 0);
            mCallTimer.cancel();
        } else {
            /* SPRD: Use cpu time instead of system time when user dialing @{
            * @orig
            final long callStart = mPrimary.getConnectTimeMillis();
            final long duration = System.currentTimeMillis() - callStart;
            ui.setPrimaryCallElapsedTime(true, duration); */
            final long callStart = mPrimary.getConnectRealTimeMillis();
            final long duration = SystemClock.elapsedRealtime() - callStart;
            ui.setPrimaryCallElapsedTime(true, duration);
        /* @} */
        }
    }

    public void onCallStateButtonTouched() {
        Intent broadcastIntent = ObjectFactory.getCallStateButtonBroadcastIntent(mContext);
        if (broadcastIntent != null) {
            Log.d(this, "Sending call state button broadcast: ", broadcastIntent);
            mContext.sendBroadcast(broadcastIntent, Manifest.permission.READ_PHONE_STATE);
        }
    }

    /**
     * Handles click on the contact photo by toggling fullscreen mode if the current call is a video
     * call.
     */
    public void onContactPhotoClick() {
        if (mPrimary != null && mPrimary.isVideoCall(mContext)) {
            InCallPresenter.getInstance().toggleFullscreenMode();
        }
    }

    private void maybeStartSearch(Call call, boolean isPrimary) {
        // no need to start search for conference calls which show generic info.
        if (call != null && !call.isConferenceCall()) {
            startContactInfoSearch(call, isPrimary, call.getState() == Call.State.INCOMING);
        }
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch(final Call call, final boolean isPrimary,
            boolean isIncoming) {
        final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);

        cache.findInfo(call, isIncoming, new ContactLookupCallback(this, isPrimary));
    }

    private void onContactInfoComplete(String callId, ContactCacheEntry entry, boolean isPrimary) {
        updateContactEntry(entry, isPrimary);
        if (entry.name != null) {
            Log.d(TAG, "Contact found: " + entry);
        }
        if (entry.contactUri != null) {
            CallerInfoUtils.sendViewNotification(mContext, entry.contactUri);
        }
    }

    private void onImageLoadComplete(String callId, ContactCacheEntry entry) {
        if (getUi() == null) {
            return;
        }

        if (entry.photo != null) {
            if (mPrimary != null && callId.equals(mPrimary.getId())) {
                getUi().setPrimaryImage(entry.photo);
            }
        }
    }

    private void updateContactEntry(ContactCacheEntry entry, boolean isPrimary) {
        if (isPrimary) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo();
        } else {
            mSecondaryContactInfo = entry;
            updateSecondaryDisplayInfo();
        }
    }

    /**
     * Get the highest priority call to display.
     * Goes through the calls and chooses which to return based on priority of which type of call
     * to display to the user. Callers can use the "ignore" feature to get the second best call
     * by passing a previously found primary call as ignore.
     *
     * @param ignore A call to ignore if found.
     */
    private Call getCallToDisplay(CallList callList, Call ignore, boolean skipDisconnected) {

        // Active calls come second.  An active call always gets precedent.
        Call retval = callList.getActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Disconnected calls get primary position if there are no active calls
        // to let user know quickly what call has disconnected. Disconnected
        // calls are very short lived.
        if (!skipDisconnected) {
            retval = callList.getDisconnectingCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
            retval = callList.getDisconnectedCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
        }

        // Then we go to background call (calls on hold)
        retval = callList.getBackgroundCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Lastly, we go to a second background call.
        retval = callList.getSecondBackgroundCall();

        return retval;
    }

    private void updatePrimaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            // TODO: May also occur if search result comes back after ui is destroyed. Look into
            // removing that case completely.
            Log.d(TAG, "updatePrimaryDisplayInfo called but ui is null!");
            return;
        }
        if (mPrimary == null) {
            // Clear the primary display info.
            ui.setPrimary(INVALID_SUBSCRIPTION_ID, null, null, false, null, null, false);
            return;
        }
        /* SPRD: FDN in dialer feature. @{ */
        int subId = INVALID_SUBSCRIPTION_ID;
        if (mPrimary.getAccountHandle() != null) {
            TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
            PhoneAccount account = mgr.getPhoneAccount(mPrimary.getAccountHandle());
            TelephonyManager telephonyManager = (TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE);
            subId = telephonyManager.getSubIdViaPhoneAccount(account);
        }
        /* @} */

       if (mPrimary.isConferenceCall()) {
            Log.d(TAG, "Update primary display info for conference call.");

            /*SPRD: add for VoLTE{@*/
            String number = null;
            if ((mContext != null
                    && mContext.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED //SPRD:Add for bug532464
                    && ImsManager.isVolteEnabledByPlatform(mContext) 
                    && mPrimary.isGenericConferenceCall())) {
                number = mPrimary.getNumber();
            }
            /* @} */
            ui.setPrimary(
                    subId,
                    number /* number */,
                    getConferenceString(mPrimary),
                    false /* nameIsNumber */,
                    null /* label */,
                    getConferencePhoto(mPrimary),
                    false /* isSipCall */);
        }
       /*SPRD: add for VoLTE{@*/
        else if ((mContext != null
                && mContext.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED //SPRD:Add for bug532464
                && ImsManager.isVolteEnabledByPlatform(mContext) 
                && mPrimary.isGenericConferenceCall())) {
           if(mPrimaryContactInfo != null){
               String name = getNameForCall(mPrimaryContactInfo);
               String number = getNumberForCall(mPrimaryContactInfo);
               boolean nameIsNumber = name != null && name.equals(mPrimaryContactInfo.number);
               ui.setPrimary(
                       subId,
                       mPrimary.getNumber(),
                       getGenericConferenceString(),
                       false,
                       mPrimaryContactInfo.label,
                       mPrimaryContactInfo.photo,
                       mPrimaryContactInfo.isSipCall);
           } else {
               ui.setPrimary(
                       subId,
                       mPrimary.getNumber(),
                       getGenericConferenceString(),
                       false /* nameIsNumber */,
                       null /* label */,
                       getConferencePhoto(mPrimary),
                       false /* isSipCall */);
           }
       }
       /* @} */
       else if (mPrimaryContactInfo != null) {
            Log.d(TAG, "Update primary display info for " + mPrimaryContactInfo);

            String name = getNameForCall(mPrimaryContactInfo);
            String number = getNumberForCall(mPrimaryContactInfo);
            boolean nameIsNumber = name != null && name.equals(mPrimaryContactInfo.number);
            ui.setPrimary(
                    subId,
                    number,
                    name,
                    nameIsNumber,
                    mPrimaryContactInfo.label,
                    mPrimaryContactInfo.photo,
                    mPrimaryContactInfo.isSipCall);
        } else {
            // Clear the primary display info.
            ui.setPrimary(INVALID_SUBSCRIPTION_ID, null, null, false, null, null, false);
        }

        if (mEmergencyCallListener != null) {
            boolean isEmergencyCall = PhoneNumberUtils.isEmergencyNumber(
                    getNumberFromHandle(mPrimary.getHandle()));
            mEmergencyCallListener.onCallUpdated((BaseFragment) ui, isEmergencyCall);
        }
    }

    private void updateSecondaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mSecondary == null) {
            // Clear the secondary display info.
            ui.setSecondary(false, null, false, null, null, false /* isConference */,
                    false /* isVideoCall */);
            return;
        }

        if (mSecondary.isConferenceCall()) {
            ui.setSecondary(
                    true /* show */,
                    getConferenceString(mSecondary),
                    false /* nameIsNumber */,
                    null /* label */,
                    getCallProviderLabel(mSecondary),
                    true /* isConference */,
                    mSecondary.isVideoCall(mContext));
        } else if (mSecondaryContactInfo != null) {
            Log.d(TAG, "updateSecondaryDisplayInfo() " + mSecondaryContactInfo);
            String name = getNameForCall(mSecondaryContactInfo);
            boolean nameIsNumber = name != null && name.equals(mSecondaryContactInfo.number);
            ui.setSecondary(
                    true /* show */,
                    name,
                    nameIsNumber,
                    mSecondaryContactInfo.label,
                    getCallProviderLabel(mSecondary),
                    false /* isConference */,
                    mSecondary.isVideoCall(mContext));
        } else {
            // Clear the secondary display info.
            ui.setSecondary(false, null, false, null, null, false /* isConference */,
                    false /* isVideoCall */);
        }
    }


    /**
     * Gets the phone account to display for a call.
     */
    private PhoneAccount getAccountForCall(Call call) {
        PhoneAccountHandle accountHandle = call.getAccountHandle();
        if (accountHandle == null) {
            return null;
        }
        return InCallPresenter.getInstance().getTelecomManager().getPhoneAccount(accountHandle);
    }

    /**
     * Returns the gateway number for any existing outgoing call.
     */
    private String getGatewayNumber() {
        if (hasOutgoingGatewayCall()) {
            return getNumberFromHandle(mPrimary.getGatewayInfo().getGatewayAddress());
        }
        return null;
    }

    /**
     * Return the string label to represent the call provider
     */
    private String getCallProviderLabel(Call call) {
        PhoneAccount account = getAccountForCall(call);
        TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
        /* SPRD: show main/vice card for bug473889 @{ */
        // if (account != null && !TextUtils.isEmpty(account.getLabel())
        // && mgr.getCallCapablePhoneAccounts().size() > 1) {
        if (account != null && !TextUtils.isEmpty(account.getLabel())) {
        /* @} */
            return account.getLabel().toString();
        }
        return null;
    }

    /**
     * Returns the label (line of text above the number/name) for any given call.
     * For example, "calling via [Account/Google Voice]" for outgoing calls.
     */
    private String getConnectionLabel() {
        StatusHints statusHints = mPrimary.getTelecommCall().getDetails().getStatusHints();
        if (statusHints != null && !TextUtils.isEmpty(statusHints.getLabel())) {
            return statusHints.getLabel().toString();
        }

        if (hasOutgoingGatewayCall() && getUi() != null) {
            // Return the label for the gateway app on outgoing calls.
            final PackageManager pm = mContext.getPackageManager();
            try {
                ApplicationInfo info = pm.getApplicationInfo(
                        mPrimary.getGatewayInfo().getGatewayProviderPackageName(), 0);
                return pm.getApplicationLabel(info).toString();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(this, "Gateway Application Not Found.", e);
                return null;
            }
        }
        return getCallProviderLabel(mPrimary);
    }

    private Drawable getCallStateIcon() {
        // Return connection icon if one exists.
        StatusHints statusHints = mPrimary.getTelecommCall().getDetails().getStatusHints();
        if (statusHints != null && statusHints.getIcon() != null) {
            Drawable icon = statusHints.getIcon().loadDrawable(mContext);
            if (icon != null) {
                return icon;
            }
        }

        return null;
    }

    private boolean hasOutgoingGatewayCall() {
        // We only display the gateway information while STATE_DIALING so return false for any other
        // call state.
        // TODO: mPrimary can be null because this is called from updatePrimaryDisplayInfo which
        // is also called after a contact search completes (call is not present yet).  Split the
        // UI update so it can receive independent updates.
        if (mPrimary == null) {
            return false;
        }
        return Call.State.isDialing(mPrimary.getState()) && mPrimary.getGatewayInfo() != null &&
                !mPrimary.getGatewayInfo().isEmpty();
    }

    /**
     * Gets the name to display for the call.
     */
    private static String getNameForCall(ContactCacheEntry contactInfo) {
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.number;
        }
        return contactInfo.name;
    }

    /**
     * Gets the number to display for a call.
     */
    private static String getNumberForCall(ContactCacheEntry contactInfo) {
        // If the name is empty, we use the number for the name...so dont show a second
        // number in the number field
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.location;
        }
        return contactInfo.number;
    }

    public void secondaryInfoClicked() {
        if (mSecondary == null) {
            Log.w(this, "Secondary info clicked but no secondary call.");
            return;
        }

        Log.i(this, "Swapping call to foreground: " + mSecondary);
        TelecomAdapter.getInstance().unholdCall(mSecondary.getId());
    }

    public void endCallClicked() {
        if (mPrimary == null) {
            return;
        }

        Log.i(this, "Disconnecting call: " + mPrimary);
        final String callId = mPrimary.getId();
        mPrimary.setState(Call.State.DISCONNECTING);
        CallList.getInstance().onUpdate(mPrimary);
        TelecomAdapter.getInstance().disconnectCall(callId);
    }

    private String getNumberFromHandle(Uri handle) {
        return handle == null ? "" : handle.getSchemeSpecificPart();
    }

    /**
     * Handles a change to the fullscreen mode of the in-call UI.
     *
     * @param isFullscreenMode {@code True} if the in-call UI is entering full screen mode.
     */
    @Override
    public void onFullscreenModeChanged(boolean isFullscreenMode) {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }
        ui.setCallCardVisible(!isFullscreenMode);
    }

    private boolean isPrimaryCallActive() {
        return mPrimary != null && mPrimary.getState() == Call.State.ACTIVE;
    }

    private String getConferenceString(Call call) {
        boolean isGenericConference = call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE);
        Log.v(this, "getConferenceString: " + isGenericConference);

        final int resId = isGenericConference
                ? R.string.card_title_in_call : R.string.card_title_conf_call;
        return mContext.getResources().getString(resId);
    }

    private Drawable getConferencePhoto(Call call) {
        boolean isGenericConference = call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE);
        Log.v(this, "getConferencePhoto: " + isGenericConference);

        final int resId = isGenericConference
                ? R.drawable.img_phone : R.drawable.img_conference;
        Drawable photo = mContext.getResources().getDrawable(resId);
        photo.setAutoMirrored(true);
        return photo;
    }

    private boolean shouldShowEndCallButton(Call primary, int callState) {
        if (primary == null) {
            return false;
        }
        if ((!Call.State.isConnectingOrConnected(callState)
                && callState != Call.State.DISCONNECTING) || callState == Call.State.INCOMING) {
            return false;
        }
        if (mPrimary.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            return false;
        }
        return true;
    }

    private void maybeSendAccessibilityEvent(InCallState oldState, InCallState newState) {
        if (mContext == null) {
            return;
        }
        final AccessibilityManager am = (AccessibilityManager) mContext.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        if (!am.isEnabled()) {
            return;
        }
        if ((oldState != InCallState.OUTGOING && newState == InCallState.OUTGOING)
                || (oldState != InCallState.INCOMING && newState == InCallState.INCOMING)) {
            if (getUi() != null) {
                getUi().sendAccessibilityAnnouncement();
            }
        }
    }

    /*SPRD: add for VoLTE{@*/
    private boolean shouldShowConferenceList() {
        if (mPrimary == null) {
            return false;
        }

        return CallList.getInstance().hasValidGroupCall() && !mPrimary.isVideoCall(mContext)
                && (mPrimary.isConferenceCall() || mPrimary.isGenericConferenceCall());// SPRD: fix the bug535441 ;
    }

    private String getGenericConferenceString() {
        return mContext.getResources().getString(R.string.card_title_conf_call);
    }

    private boolean isPrimaryCallIsConference() {
        return mPrimary != null && (mPrimary.getGroupState() == Call.State.ACTIVE
                || mPrimary.getGroupState() == Call.State.DIALING
                || mPrimary.getGroupState() == Call.State.CONFERENCED
                || mPrimary.getGroupState() == Call.State.REDIALING
                || mPrimary.getGroupState() == Call.State.CONNECTING)
                && (mPrimary.getParentId() != null || mPrimary.getChildCallIds().size()>0);
    }

    /*@}*/

    public interface CallCardUi extends Ui {
        void setVisible(boolean on);
        void setCallCardVisible(boolean visible);
        void setPrimary(String number, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isSipCall);
        // SPRD: FDN in dialer feature.
        void setPrimary(int subId, String number, String name, boolean nameIsNumber, String label,
                        Drawable photo, boolean isSipCall);
        void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
                String providerLabel, boolean isConference, boolean isVideoCall);
        void setCallState(int state, int videoState, int sessionModificationState,
                DisconnectCause disconnectCause, String connectionLabel,
                Drawable connectionIcon, String gatewayNumber, boolean isWifi,
                boolean isConference);
        // SPRD: modify for bug 473889
        void setCallState(int subId, int state, int videoState, int sessionModificationState,
                DisconnectCause disconnectCause, String connectionLabel,
                Drawable connectionIcon, String gatewayNumber, boolean isWifi,
                boolean isConference);
        void setPrimaryCallElapsedTime(boolean show, long duration);
        void setPrimaryName(String name, boolean nameIsNumber);
        void setPrimaryImage(Drawable image);
        void setPrimaryPhoneNumber(String phoneNumber);
        void setPrimaryLabel(String label);
        void setEndCallButtonEnabled(boolean enabled, boolean animate);
        void setCallbackNumber(String number, boolean isEmergencyCalls);
        void setProgressSpinnerVisible(boolean visible);
        void showHdAudioIndicator(boolean visible);
        void showManageConferenceCallButton(boolean visible);
        boolean isManageConferenceVisible();
        void animateForNewOutgoingCall();
        void sendAccessibilityAnnouncement();
        void showHdAudioIndicatorPlugin(Call call, Context context); //SPRD: Add incallui Hd Voice plugin
    }
}
