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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.HandlerThread;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.testing.TestableLooper;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Unit tests for ImsEmergencyRegistrationStateHelper.
 */
@RunWith(AndroidJUnit4.class)
public class ImsEmergencyRegistrationStateHelperTest {
    private static final String TAG = "ImsEmergencyRegistrationStateHelperTest";

    private static final int SLOT_0 = 0;
    private static final int SUB_1 = 1;

    @Mock private ImsMmTelManager mMmTelManager;

    private Context mContext;
    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;
    private ImsEmergencyRegistrationStateHelper mImsEmergencyRegistrationHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext() {
            @Override
            public String getSystemServiceName(Class<?> serviceClass) {
                if (serviceClass == ImsManager.class) {
                    return Context.TELEPHONY_IMS_SERVICE;
                }
                return super.getSystemServiceName(serviceClass);
            }
        };

        mHandlerThread = new HandlerThread(
                ImsEmergencyRegistrationStateHelperTest.class.getSimpleName());
        mHandlerThread.start();
        try {
            mLooper = new TestableLooper(mHandlerThread.getLooper());
        } catch (Exception e) {
            loge("Unable to create looper from handler.");
        }
        mImsEmergencyRegistrationHelper = new ImsEmergencyRegistrationStateHelper(
                mContext, SLOT_0, SUB_1, mHandlerThread.getLooper());

        ImsManager imsManager = mContext.getSystemService(ImsManager.class);
        when(imsManager.getImsMmTelManager(eq(SUB_1))).thenReturn(mMmTelManager);
    }

    @After
    public void tearDown() throws Exception {
        if (mImsEmergencyRegistrationHelper != null) {
            mImsEmergencyRegistrationHelper.destroy();
            mImsEmergencyRegistrationHelper = null;
        }
        mMmTelManager = null;

        if (mLooper != null) {
            mLooper.destroy();
            mLooper = null;
        }
    }

    @Test
    @SmallTest
    public void testStart() throws ImsException {
        mImsEmergencyRegistrationHelper.start();

        verify(mMmTelManager).registerImsStateCallback(
                any(Executor.class), any(ImsStateCallback.class));
        assertFalse(mImsEmergencyRegistrationHelper.isImsEmergencyRegistered());
    }

    @Test
    @SmallTest
    public void testNotifyImsStateCallbackOnAvailable() throws ImsException {
        ImsStateCallback callback = setUpImsStateCallback();
        callback.onAvailable();
        processAllMessages();

        verify(mMmTelManager).registerImsEmergencyRegistrationCallback(
                any(Executor.class), any(RegistrationManager.RegistrationCallback.class));
        assertFalse(mImsEmergencyRegistrationHelper.isImsEmergencyRegistered());
    }

    @Test
    @SmallTest
    public void testNotifyImsRegistrationCallbackOnRegistered() throws ImsException {
        RegistrationManager.RegistrationCallback callback = setUpImsEmergencyRegistrationCallback();

        assertFalse(mImsEmergencyRegistrationHelper.isImsEmergencyRegistered());

        callback.onRegistered(getImsEmergencyRegistrationAttributes());
        processAllMessages();

        assertTrue(mImsEmergencyRegistrationHelper.isImsEmergencyRegistered());
    }

    @Test
    @SmallTest
    public void testNotifyImsRegistrationCallbackOnRegisteredUnregistered() throws ImsException {
        RegistrationManager.RegistrationCallback callback = setUpImsEmergencyRegistrationCallback();

        assertFalse(mImsEmergencyRegistrationHelper.isImsEmergencyRegistered());

        callback.onRegistered(getImsEmergencyRegistrationAttributes());
        processAllMessages();

        callback.onUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_CS_RETRY_REQUIRED, 0, null), 0, 0);
        processAllMessages();

        assertFalse(mImsEmergencyRegistrationHelper.isImsEmergencyRegistered());
    }

    private ImsStateCallback setUpImsStateCallback() throws ImsException {
        mImsEmergencyRegistrationHelper.start();

        ArgumentCaptor<ImsStateCallback> callbackCaptor =
                ArgumentCaptor.forClass(ImsStateCallback.class);

        verify(mMmTelManager).registerImsStateCallback(
                any(Executor.class), callbackCaptor.capture());

        ImsStateCallback imsStateCallback = callbackCaptor.getValue();
        assertNotNull(imsStateCallback);
        return imsStateCallback;
    }

    private RegistrationManager.RegistrationCallback setUpImsEmergencyRegistrationCallback()
            throws ImsException {
        ImsStateCallback imsStateCallback = setUpImsStateCallback();
        imsStateCallback.onAvailable();
        processAllMessages();

        ArgumentCaptor<RegistrationManager.RegistrationCallback> callbackCaptor =
                ArgumentCaptor.forClass(RegistrationManager.RegistrationCallback.class);

        verify(mMmTelManager).registerImsEmergencyRegistrationCallback(
                any(Executor.class), callbackCaptor.capture());

        RegistrationManager.RegistrationCallback registrationCallback = callbackCaptor.getValue();
        assertNotNull(registrationCallback);
        return registrationCallback;
    }

    private static ImsRegistrationAttributes getImsEmergencyRegistrationAttributes() {
        return new ImsRegistrationAttributes.Builder(ImsRegistrationImplBase.REGISTRATION_TECH_LTE)
                .setFlagRegistrationTypeEmergency()
                .build();
    }

    private void processAllMessages() {
        while (!mLooper.getLooper().getQueue().isIdle()) {
            mLooper.processAllMessages();
        }
    }

    private static void loge(String str) {
        Log.e(TAG, str);
    }
}
