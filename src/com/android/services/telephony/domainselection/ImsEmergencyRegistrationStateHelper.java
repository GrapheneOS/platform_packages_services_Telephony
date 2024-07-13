/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.services.telephony.domainselection;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.RegistrationManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

/**
 * A class to listen to the IMS emergency registration state.
 */
public class ImsEmergencyRegistrationStateHelper {
    private static final String TAG = ImsEmergencyRegistrationStateHelper.class.getSimpleName();

    protected static final long MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS = 2 * 1000; // 2 seconds

    private final Context mContext;
    private final int mSlotId;
    private final int mSubId;
    private final Handler mHandler;

    private ImsMmTelManager mMmTelManager;
    private ImsStateCallback mImsStateCallback;
    private RegistrationManager.RegistrationCallback mRegistrationCallback;
    private boolean mImsEmergencyRegistered;

    public ImsEmergencyRegistrationStateHelper(@NonNull Context context,
            int slotId, int subId, @NonNull Looper looper) {
        mContext = context;
        mSlotId = slotId;
        mSubId = subId;
        mHandler = new Handler(looper);
    }

    /**
     * Destroys this instance.
     */
    public void destroy() {
        stopListeningForImsEmergencyRegistrationState();
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Returns the Handler instance.
     */
    @VisibleForTesting
    public @NonNull Handler getHandler() {
        return mHandler;
    }

    /**
     * Returns {@code true} if IMS is registered, {@code false} otherwise.
     */
    public boolean isImsEmergencyRegistered() {
        return mImsEmergencyRegistered;
    }

    /**
     * Starts listening for IMS emergency registration state.
     */
    public void start() {
        startListeningForImsEmergencyRegistrationState();
    }

    /**
     * Starts listening to monitor the IMS states -
     * connection state, IMS emergency registration state.
     */
    private void startListeningForImsEmergencyRegistrationState() {
        if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
            return;
        }

        ImsManager imsMngr = mContext.getSystemService(ImsManager.class);
        mMmTelManager = imsMngr.getImsMmTelManager(mSubId);
        mImsEmergencyRegistered = false;
        registerImsStateCallback();
    }

    /**
     * Stops listening to monitor the IMS states -
     * connection state, IMS emergency registration state.
     */
    private void stopListeningForImsEmergencyRegistrationState() {
        if (mMmTelManager != null) {
            unregisterImsEmergencyRegistrationCallback();
            unregisterImsStateCallback();
            mMmTelManager = null;
        }
    }

    private void registerImsStateCallback() {
        if (mImsStateCallback != null) {
            loge("ImsStateCallback is already registered for sub-" + mSubId);
            return;
        }

        // Listens to the IMS connection state change.
        mImsStateCallback = new ImsStateCallback() {
            @Override
            public void onUnavailable(@DisconnectedReason int reason) {
                unregisterImsEmergencyRegistrationCallback();
            }

            @Override
            public void onAvailable() {
                registerImsEmergencyRegistrationCallback();
            }

            @Override
            public void onError() {
                mImsStateCallback = null;
                mHandler.postDelayed(
                        ImsEmergencyRegistrationStateHelper.this::registerImsStateCallback,
                        MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS);
            }
        };

        try {
            mMmTelManager.registerImsStateCallback(mHandler::post, mImsStateCallback);
        } catch (ImsException e) {
            loge("Exception when registering ImsStateCallback: " + e);
            mImsStateCallback = null;
        }
    }

    private void unregisterImsStateCallback() {
        if (mImsStateCallback != null) {
            try {
                mMmTelManager.unregisterImsStateCallback(mImsStateCallback);
            }  catch (Exception ignored) {
                // Ignore the runtime exception while unregistering callback.
                logd("Exception when unregistering ImsStateCallback: " + ignored);
            }
            mImsStateCallback = null;
        }
    }

    private void registerImsEmergencyRegistrationCallback() {
        if (mRegistrationCallback != null) {
            logd("RegistrationCallback is already registered for sub-" + mSubId);
            return;
        }

        // Listens to the IMS emergency registration state change.
        mRegistrationCallback = new RegistrationManager.RegistrationCallback() {
            @Override
            public void onRegistered(@NonNull ImsRegistrationAttributes attributes) {
                mImsEmergencyRegistered = true;
            }

            @Override
            public void onUnregistered(@NonNull ImsReasonInfo info) {
                mImsEmergencyRegistered = false;
            }
        };

        try {
            mMmTelManager.registerImsEmergencyRegistrationCallback(mHandler::post,
                    mRegistrationCallback);
        } catch (ImsException e) {
            loge("Exception when registering RegistrationCallback: " + e);
            mRegistrationCallback = null;
        }
    }

    private void unregisterImsEmergencyRegistrationCallback() {
        if (mRegistrationCallback != null) {
            try {
                mMmTelManager.unregisterImsEmergencyRegistrationCallback(mRegistrationCallback);
            }  catch (Exception ignored) {
                // Ignore the runtime exception while unregistering callback.
                logd("Exception when unregistering RegistrationCallback: " + ignored);
            }
            mRegistrationCallback = null;
        }
    }

    private void logd(String s) {
        Log.d(TAG, "[" + mSlotId + "|" + mSubId + "] " + s);
    }

    private void loge(String s) {
        Log.e(TAG, "[" + mSlotId + "|" + mSubId + "] " + s);
    }
}
