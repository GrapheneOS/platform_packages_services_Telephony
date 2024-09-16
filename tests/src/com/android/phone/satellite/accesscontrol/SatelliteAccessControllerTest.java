/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.phone.satellite.accesscontrol;

import static android.location.LocationManager.MODE_CHANGED_ACTION;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_PROVISIONED;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_LOCATION_DISABLED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_LOCATION_NOT_AVAILABLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_MODEM_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.ALLOWED_STATE_CACHE_VALID_DURATION_NANOS;
import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.EVENT_COUNTRY_CODE_CHANGED;
import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.CMD_IS_SATELLITE_COMMUNICATION_ALLOWED;
import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.DEFAULT_DELAY_MINUTES_BEFORE_VALIDATING_POSSIBLE_CHANGE_IN_ALLOWED_REGION;
import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.DEFAULT_THROTTLE_INTERVAL_FOR_LOCATION_QUERY_MINUTES;
import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.EVENT_CONFIG_DATA_UPDATED;
import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.GOOGLE_US_SAN_SAT_S2_FILE_NAME;
import static com.android.phone.satellite.accesscontrol.SatelliteAccessController.DEFAULT_MAX_RETRY_COUNT_FOR_VALIDATING_POSSIBLE_CHANGE_IN_ALLOWED_REGION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.DropBoxManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ResultReceiver;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.SatelliteManager;
import android.testing.TestableLooper;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyCountryDetector;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.satellite.SatelliteConfig;
import com.android.internal.telephony.satellite.SatelliteConfigParser;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.satellite.SatelliteModemInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Unit test for {@link SatelliteAccessController} */
@RunWith(AndroidJUnit4.class)
public class SatelliteAccessControllerTest {
    private static final String TAG = "SatelliteAccessControllerTest";
    private static final String[] TEST_SATELLITE_COUNTRY_CODES = {"US", "CA", "UK"};
    private static final String[] TEST_SATELLITE_COUNTRY_CODES_EMPTY = {""};
    private static final String TEST_SATELLITE_COUNTRY_CODE_US = "US";
    private static final String TEST_SATELLITE_COUNTRY_CODE_KR = "KR";
    private static final String TEST_SATELLITE_COUNTRY_CODE_JP = "JP";

    private static final String TEST_SATELLITE_S2_FILE = "sat_s2_file.dat";
    private static final boolean TEST_SATELLITE_ALLOW = true;
    private static final boolean TEST_SATELLITE_NOT_ALLOW = false;
    private static final int TEST_LOCATION_FRESH_DURATION_SECONDS = 10;
    private static final long TEST_LOCATION_FRESH_DURATION_NANOS =
            TimeUnit.SECONDS.toNanos(TEST_LOCATION_FRESH_DURATION_SECONDS);
    private static final long TEST_LOCATION_QUERY_THROTTLE_INTERVAL_NANOS =
            TimeUnit.MINUTES.toNanos(10);  // DEFAULT_THROTTLE_INTERVAL_FOR_LOCATION_QUERY_MINUTES
    private static final long TIMEOUT = 500;
    private static final List<String> EMPTY_STRING_LIST = new ArrayList<>();
    private static final List<String> LOCATION_PROVIDERS =
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER);
    private static final int SUB_ID = 0;

    @Mock
    private LocationManager mMockLocationManager;
    @Mock
    private TelecomManager mMockTelecomManager;
    @Mock
    private TelephonyCountryDetector mMockCountryDetector;
    @Mock
    private SatelliteController mMockSatelliteController;
    @Mock
    private SatelliteModemInterface mMockSatelliteModemInterface;
    @Mock
    private DropBoxManager mMockDropBoxManager;
    @Mock
    private Context mMockContext;
    @Mock
    private Phone mMockPhone;
    @Mock
    private Phone mMockPhone2;
    @Mock
    private FeatureFlags mMockFeatureFlags;
    @Mock
    private Resources mMockResources;
    @Mock
    private SatelliteOnDeviceAccessController mMockSatelliteOnDeviceAccessController;
    @Mock
    Location mMockLocation0;
    @Mock
    Location mMockLocation1;
    @Mock
    File mMockSatS2File;
    @Mock
    SharedPreferences mMockSharedPreferences;
    @Mock
    private SharedPreferences.Editor mMockSharedPreferencesEditor;
    @Mock
    private Map<SatelliteOnDeviceAccessController.LocationToken, Boolean>
            mMockCachedAccessRestrictionMap;
    @Mock
    private Intent mMockLocationIntent;
    @Mock
    private Set<ResultReceiver> mMockSatelliteAllowResultReceivers;
    @Mock
    private ResultReceiver mMockSatelliteSupportedResultReceiver;

    private Looper mLooper;
    private TestableLooper mTestableLooper;
    private Phone[] mPhones;
    private TestSatelliteAccessController mSatelliteAccessControllerUT;

    @Captor
    private ArgumentCaptor<CancellationSignal> mLocationRequestCancellationSignalCaptor;
    @Captor
    private ArgumentCaptor<Consumer<Location>> mLocationRequestConsumerCaptor;
    @Captor
    private ArgumentCaptor<Handler> mConfigUpdateHandlerCaptor;
    @Captor
    private ArgumentCaptor<Integer> mConfigUpdateIntCaptor;
    @Captor
    private ArgumentCaptor<Object> mConfigUpdateObjectCaptor;
    @Captor
    private ArgumentCaptor<Handler> mCountryDetectorHandlerCaptor;
    @Captor
    private ArgumentCaptor<Integer> mCountryDetectorIntCaptor;
    @Captor
    private ArgumentCaptor<Object> mCountryDetectorObjCaptor;
    @Captor
    private ArgumentCaptor<BroadcastReceiver> mLocationBroadcastReceiverCaptor;
    @Captor
    private ArgumentCaptor<IntentFilter> mIntentFilterCaptor;
    @Captor
    private ArgumentCaptor<LocationRequest> mLocationRequestCaptor;
    @Captor
    private ArgumentCaptor<String> mLocationProviderStringCaptor;
    @Captor
    private ArgumentCaptor<Integer> mResultCodeIntCaptor;
    @Captor
    private ArgumentCaptor<Bundle> mResultDataBundleCaptor;

    private boolean mQueriedSatelliteAllowed = false;
    private int mQueriedSatelliteAllowedResultCode = SATELLITE_RESULT_SUCCESS;
    private Semaphore mSatelliteAllowedSemaphore = new Semaphore(0);
    private ResultReceiver mSatelliteAllowedReceiver = new ResultReceiver(null) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mQueriedSatelliteAllowedResultCode = resultCode;
            if (resultCode == SATELLITE_RESULT_SUCCESS) {
                if (resultData.containsKey(KEY_SATELLITE_COMMUNICATION_ALLOWED)) {
                    mQueriedSatelliteAllowed = resultData.getBoolean(
                            KEY_SATELLITE_COMMUNICATION_ALLOWED);
                } else {
                    logd("KEY_SATELLITE_COMMUNICATION_ALLOWED does not exist.");
                    mQueriedSatelliteAllowed = false;
                }
            } else {
                logd("mSatelliteAllowedReceiver: resultCode=" + resultCode);
                mQueriedSatelliteAllowed = false;
            }
            try {
                mSatelliteAllowedSemaphore.release();
            } catch (Exception ex) {
                fail("mSatelliteAllowedReceiver: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    @Before
    public void setUp() throws Exception {
        logd("setUp");
        MockitoAnnotations.initMocks(this);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        HandlerThread handlerThread = new HandlerThread("SatelliteAccessControllerTest");
        handlerThread.start();
        mLooper = handlerThread.getLooper();
        mTestableLooper = new TestableLooper(mLooper);
        when(mMockContext.getSystemServiceName(LocationManager.class)).thenReturn(
                Context.LOCATION_SERVICE);
        when(mMockContext.getSystemServiceName(TelecomManager.class)).thenReturn(
                Context.TELECOM_SERVICE);
        when(mMockContext.getSystemServiceName(DropBoxManager.class)).thenReturn(
                Context.DROPBOX_SERVICE);
        when(mMockContext.getSystemService(LocationManager.class)).thenReturn(
                mMockLocationManager);
        when(mMockContext.getSystemService(TelecomManager.class)).thenReturn(
                mMockTelecomManager);
        when(mMockContext.getSystemService(DropBoxManager.class)).thenReturn(
                mMockDropBoxManager);
        mPhones = new Phone[]{mMockPhone, mMockPhone2};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        replaceInstance(SatelliteController.class, "sInstance", null,
                mMockSatelliteController);
        replaceInstance(SatelliteModemInterface.class, "sInstance", null,
                mMockSatelliteModemInterface);
        replaceInstance(TelephonyCountryDetector.class, "sInstance", null,
                mMockCountryDetector);
        when(mMockSatelliteController.getSatellitePhone()).thenReturn(mMockPhone);
        when(mMockPhone.getSubId()).thenReturn(SubscriptionManager.getDefaultSubscriptionId());
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES);
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_oem_enabled_satellite_access_allow))
                .thenReturn(TEST_SATELLITE_ALLOW);
        when(mMockResources.getString(
                com.android.internal.R.string.config_oem_enabled_satellite_s2cell_file))
                .thenReturn(TEST_SATELLITE_S2_FILE);
        when(mMockResources.getInteger(com.android.internal.R.integer
                .config_oem_enabled_satellite_location_fresh_duration))
                .thenReturn(TEST_LOCATION_FRESH_DURATION_SECONDS);
        when(mMockResources.getInteger(com.android.internal.R.integer
                .config_satellite_delay_minutes_before_retry_validating_possible_change_in_allowed_region))
                .thenReturn(
                        DEFAULT_DELAY_MINUTES_BEFORE_VALIDATING_POSSIBLE_CHANGE_IN_ALLOWED_REGION);
        when(mMockResources.getInteger(com.android.internal.R.integer
                .config_satellite_max_retry_count_for_validating_possible_change_in_allowed_region))
                .thenReturn(
                        DEFAULT_MAX_RETRY_COUNT_FOR_VALIDATING_POSSIBLE_CHANGE_IN_ALLOWED_REGION);
        when(mMockResources.getInteger(com.android.internal.R.integer
                .config_satellite_location_query_throttle_interval_minutes))
                .thenReturn(DEFAULT_THROTTLE_INTERVAL_FOR_LOCATION_QUERY_MINUTES);

        when(mMockLocationManager.getProviders(true)).thenReturn(LOCATION_PROVIDERS);
        when(mMockLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                .thenReturn(mMockLocation0);
        when(mMockLocationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER))
                .thenReturn(mMockLocation1);
        when(mMockLocation0.getLatitude()).thenReturn(0.0);
        when(mMockLocation0.getLongitude()).thenReturn(0.0);
        when(mMockLocation1.getLatitude()).thenReturn(1.0);
        when(mMockLocation1.getLongitude()).thenReturn(1.0);
        when(mMockSatelliteOnDeviceAccessController.isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class))).thenReturn(true);

        when(mMockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(
                mMockSharedPreferences);
        when(mMockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(mMockSharedPreferences.getStringSet(anyString(), any()))
                .thenReturn(Set.of(TEST_SATELLITE_COUNTRY_CODES));
        doReturn(mMockSharedPreferencesEditor).when(mMockSharedPreferences).edit();
        doReturn(mMockSharedPreferencesEditor).when(mMockSharedPreferencesEditor)
                .putBoolean(anyString(), anyBoolean());
        doReturn(mMockSharedPreferencesEditor).when(mMockSharedPreferencesEditor)
                .putStringSet(anyString(), any());
        doReturn(mMockSharedPreferencesEditor).when(mMockSharedPreferencesEditor)
                .putLong(anyString(), anyLong());
        doNothing().when(mMockSharedPreferencesEditor).apply();

        when(mMockFeatureFlags.satellitePersistentLogging()).thenReturn(true);
        when(mMockFeatureFlags.geofenceEnhancementForBetterUx()).thenReturn(true);
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        mSatelliteAccessControllerUT = new TestSatelliteAccessController(mMockContext,
                mMockFeatureFlags, mLooper, mMockLocationManager, mMockTelecomManager,
                mMockSatelliteOnDeviceAccessController, mMockSatS2File);
        mTestableLooper.processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        if (mTestableLooper != null) {
            mTestableLooper.destroy();
            mTestableLooper = null;
        }

        if (mLooper != null) {
            mLooper.quit();
            mLooper = null;
        }
    }

    @Test
    public void testGetInstance() {
        SatelliteAccessController inst1 =
                SatelliteAccessController.getOrCreateInstance(mMockContext, mMockFeatureFlags);
        SatelliteAccessController inst2 =
                SatelliteAccessController.getOrCreateInstance(mMockContext, mMockFeatureFlags);
        assertEquals(inst1, inst2);
    }

    @Test
    public void testIsSatelliteAccessAllowedForLocation() {
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        // Test disallowList case
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_oem_enabled_satellite_access_allow))
                .thenReturn(TEST_SATELLITE_NOT_ALLOW);

        // configuration is EMPTY then we return true with any network country code.
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES_EMPTY);
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        assertTrue(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_US)));
        assertTrue(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_JP)));

        // configuration is ["US", "CA", "UK"]
        // - if network country code is ["US"] or ["US","KR"] or [EMPTY] return false;
        // - if network country code is ["KR"] return true;
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES);
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        assertFalse(mSatelliteAccessControllerUT.isSatelliteAccessAllowedForLocation(List.of()));
        assertFalse(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_US)));
        assertFalse(mSatelliteAccessControllerUT.isSatelliteAccessAllowedForLocation(
                        List.of(TEST_SATELLITE_COUNTRY_CODE_US, TEST_SATELLITE_COUNTRY_CODE_KR)));
        assertTrue(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_KR)));

        // Test allowList case
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_oem_enabled_satellite_access_allow))
                .thenReturn(TEST_SATELLITE_ALLOW);

        // configuration is [EMPTY] then return false in case of any network country code
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES_EMPTY);
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        assertFalse(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_US)));
        assertFalse(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_JP)));

        // configuration is ["US", "CA", "UK"]
        // - if network country code is [EMPTY] or ["US","KR"] or [KR] return false;
        // - if network country code is ["US"] return true;
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES);
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        assertFalse(mSatelliteAccessControllerUT.isSatelliteAccessAllowedForLocation(List.of()));
        assertFalse(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_KR)));
        assertFalse(mSatelliteAccessControllerUT.isSatelliteAccessAllowedForLocation(
                List.of(TEST_SATELLITE_COUNTRY_CODE_US, TEST_SATELLITE_COUNTRY_CODE_KR)));
        assertTrue(mSatelliteAccessControllerUT
                .isSatelliteAccessAllowedForLocation(List.of(TEST_SATELLITE_COUNTRY_CODE_US)));
    }

    @Test
    public void testIsRegionDisallowed() throws Exception {
        // setup to make the return value of mQueriedSatelliteAllowed 'true'
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_oem_enabled_satellite_access_allow))
                .thenReturn(TEST_SATELLITE_ALLOW);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockLocationManager).isLocationEnabled();
        when(mMockSatelliteOnDeviceAccessController.isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class))).thenReturn(true);
        replaceInstance(SatelliteAccessController.class, "mCachedAccessRestrictionMap",
                mSatelliteAccessControllerUT, mMockCachedAccessRestrictionMap);
        doReturn(true).when(mMockCachedAccessRestrictionMap).containsKey(any());
        doReturn(true).when(mMockCachedAccessRestrictionMap).get(any());

        // get allowed country codes EMPTY from resources
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES_EMPTY);

        // allow case that network country codes [US] with [EMPTY] configuration
        // location will not be compared and mQueriedSatelliteAllowed will be set false
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_US));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(0)).containsKey(any());
        assertFalse(mQueriedSatelliteAllowed);

        // allow case that network country codes [EMPTY] with [EMPTY] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(List.of());
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // get allowed country codes [US, CA, UK] from resources
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES);

        // allow case that network country codes [US, CA, UK] with [US, CA, UK] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODES));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // allow case that network country codes [US] with [US, CA, UK] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_US));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // allow case that network country codes [US, KR] with [US, CA, UK] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(
                List.of(TEST_SATELLITE_COUNTRY_CODE_US, TEST_SATELLITE_COUNTRY_CODE_KR));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // allow case that network country codes [US] with [EMPTY] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(List.of());
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // allow case that network country codes [KR, JP] with [US, CA, UK] configuration
        // location will not be compared and mQueriedSatelliteAllowed will be set false
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(
                List.of(TEST_SATELLITE_COUNTRY_CODE_KR, TEST_SATELLITE_COUNTRY_CODE_JP));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(0)).containsKey(any());
        assertFalse(mQueriedSatelliteAllowed);

        // allow case that network country codes [KR] with [US, CA, UK] configuration
        // location will not be compared and mQueriedSatelliteAllowed will be set false
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_KR));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(0)).containsKey(any());
        assertFalse(mQueriedSatelliteAllowed);


        // set disallowed list case
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_oem_enabled_satellite_access_allow))
                .thenReturn(TEST_SATELLITE_NOT_ALLOW);
        // get disallowed country codes list [EMPTY] from resources
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES_EMPTY);

        // disallow case that network country codes [US] with [EMPTY] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_US));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // get disallowed country codes list ["US", "CA", "UK"] from resources
        when(mMockResources.getStringArray(
                com.android.internal.R.array.config_oem_enabled_satellite_country_codes))
                .thenReturn(TEST_SATELLITE_COUNTRY_CODES);

        // disallow case that network country codes [EMPTY] with [US, CA, UK] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODES_EMPTY));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // disallow case that network country codes [US, JP] with [US, CA, UK] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(
                List.of(TEST_SATELLITE_COUNTRY_CODE_US, TEST_SATELLITE_COUNTRY_CODE_JP));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // disallow case that network country codes [JP] with [US, CA, UK] configuration
        // location will be compared and mQueriedSatelliteAllowed will be set true
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_JP));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(1)).containsKey(any());
        assertTrue(mQueriedSatelliteAllowed);

        // disallow case that network country codes [US] with [US, CA, UK] configuration
        // location will not be compared and mQueriedSatelliteAllowed will be set false
        clearInvocations(mMockCachedAccessRestrictionMap);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_US));
        mSatelliteAccessControllerUT.loadOverlayConfigs(mMockContext);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockCachedAccessRestrictionMap, times(0)).containsKey(any());
        assertFalse(mQueriedSatelliteAllowed);
    }

    @Test
    public void testRequestIsSatelliteCommunicationAllowedForCurrentLocation() throws Exception {
        // OEM-enabled satellite is not supported
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(
                mSatelliteAllowedSemaphore, 1));
        assertEquals(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, mQueriedSatelliteAllowedResultCode);

        // OEM-enabled satellite is supported
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        // Satellite is not supported
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_SUCCESS);
        clearAllInvocations();
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(
                mSatelliteAllowedSemaphore, 1));
        assertEquals(SATELLITE_RESULT_NOT_SUPPORTED, mQueriedSatelliteAllowedResultCode);
        assertFalse(mQueriedSatelliteAllowed);

        // Failed to query whether satellite is supported or not
        setUpResponseForRequestIsSatelliteSupported(false, SATELLITE_RESULT_MODEM_ERROR);
        clearAllInvocations();
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(
                mSatelliteAllowedSemaphore, 1));
        assertEquals(SATELLITE_RESULT_MODEM_ERROR, mQueriedSatelliteAllowedResultCode);

        // Network country codes are not available. TelecomManager.isInEmergencyCall() returns true.
        // On-device access controller will be used. Last known location is available and fresh.
        clearAllInvocations();
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(EMPTY_STRING_LIST);
        when(mMockTelecomManager.isInEmergencyCall()).thenReturn(true);
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;
        when(mMockLocation0.getElapsedRealtimeNanos()).thenReturn(2L);
        when(mMockLocation1.getElapsedRealtimeNanos()).thenReturn(0L);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        assertTrue(
                mSatelliteAccessControllerUT.isKeepOnDeviceAccessControllerResourcesTimerStarted());
        verify(mMockSatelliteOnDeviceAccessController).isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class));
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(
                mSatelliteAllowedSemaphore, 1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteAllowedResultCode);
        assertTrue(mQueriedSatelliteAllowed);

        // Move time forward and verify resources are cleaned up
        clearAllInvocations();
        mTestableLooper.moveTimeForward(mSatelliteAccessControllerUT
                .getKeepOnDeviceAccessControllerResourcesTimeoutMillis());
        mTestableLooper.processAllMessages();
        assertFalse(
                mSatelliteAccessControllerUT.isKeepOnDeviceAccessControllerResourcesTimerStarted());
        assertTrue(mSatelliteAccessControllerUT.isSatelliteOnDeviceAccessControllerReset());
        verify(mMockSatelliteOnDeviceAccessController).close();

        // Restore SatelliteOnDeviceAccessController for next verification
        mSatelliteAccessControllerUT.setSatelliteOnDeviceAccessController(
                mMockSatelliteOnDeviceAccessController);

        // Network country codes are not available. TelecomManager.isInEmergencyCall() returns
        // false. Phone0 is in ECM. On-device access controller will be used. Last known location is
        // not fresh.
        clearAllInvocations();
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(EMPTY_STRING_LIST);
        when(mMockTelecomManager.isInEmergencyCall()).thenReturn(false);
        when(mMockPhone.isInEcm()).thenReturn(true);
        when(mMockPhone.getContext()).thenReturn(mMockContext);
        when(mMockPhone2.getContext()).thenReturn(mMockContext);
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;
        when(mMockLocation0.getElapsedRealtimeNanos()).thenReturn(0L);
        when(mMockLocation1.getElapsedRealtimeNanos()).thenReturn(0L);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        assertFalse(
                mSatelliteAccessControllerUT.isKeepOnDeviceAccessControllerResourcesTimerStarted());
        verify(mMockLocationManager).getCurrentLocation(eq(LocationManager.FUSED_PROVIDER),
                any(LocationRequest.class), mLocationRequestCancellationSignalCaptor.capture(),
                any(Executor.class), mLocationRequestConsumerCaptor.capture());
        assertTrue(mSatelliteAccessControllerUT.isWaitForCurrentLocationTimerStarted());
        sendLocationRequestResult(mMockLocation0);
        assertFalse(mSatelliteAccessControllerUT.isWaitForCurrentLocationTimerStarted());
        // The LocationToken should be already in the cache
        verify(mMockSatelliteOnDeviceAccessController, never()).isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class));
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(
                mSatelliteAllowedSemaphore, 1));
        assertEquals(SATELLITE_RESULT_SUCCESS, mQueriedSatelliteAllowedResultCode);
        assertTrue(mQueriedSatelliteAllowed);

        // Timed out to wait for current location. No cached allowed state.
        clearAllInvocations();
        mSatelliteAccessControllerUT.setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                "cache_clear_and_not_allowed");
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(EMPTY_STRING_LIST);
        when(mMockTelecomManager.isInEmergencyCall()).thenReturn(false);
        when(mMockPhone.isInEcm()).thenReturn(true);
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;
        when(mMockLocation0.getElapsedRealtimeNanos()).thenReturn(0L);
        when(mMockLocation1.getElapsedRealtimeNanos()).thenReturn(0L);
        when(mMockCountryDetector.getCachedLocationCountryIsoInfo()).thenReturn(new Pair<>("", 0L));
        when(mMockCountryDetector.getCachedNetworkCountryIsoInfo()).thenReturn(new HashMap<>());
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        assertFalse(
                mSatelliteAccessControllerUT.isKeepOnDeviceAccessControllerResourcesTimerStarted());
        verify(mMockLocationManager).getCurrentLocation(anyString(), any(LocationRequest.class),
                any(CancellationSignal.class), any(Executor.class), any(Consumer.class));
        assertTrue(mSatelliteAccessControllerUT.isWaitForCurrentLocationTimerStarted());
        // Timed out
        mTestableLooper.moveTimeForward(
                mSatelliteAccessControllerUT.getWaitForCurrentLocationTimeoutMillis());
        mTestableLooper.processAllMessages();
        assertFalse(mSatelliteAccessControllerUT.isWaitForCurrentLocationTimerStarted());
        verify(mMockSatelliteOnDeviceAccessController, never()).isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class));
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(
                mSatelliteAllowedSemaphore, 1));
        assertEquals(SATELLITE_RESULT_LOCATION_NOT_AVAILABLE, mQueriedSatelliteAllowedResultCode);

        // Network country codes are not available. TelecomManager.isInEmergencyCall() returns
        // false. No phone is in ECM. Last known location is not fresh. Cached country codes should
        // be used for verifying satellite allow. No cached country codes are available.
        clearAllInvocations();
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(EMPTY_STRING_LIST);
        when(mMockCountryDetector.getCachedLocationCountryIsoInfo()).thenReturn(new Pair<>("", 0L));
        when(mMockCountryDetector.getCachedNetworkCountryIsoInfo()).thenReturn(new HashMap<>());
        when(mMockTelecomManager.isInEmergencyCall()).thenReturn(false);
        when(mMockPhone.isInEcm()).thenReturn(false);
        when(mMockPhone2.isInEcm()).thenReturn(false);
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;
        when(mMockLocation0.getElapsedRealtimeNanos()).thenReturn(0L);
        when(mMockLocation1.getElapsedRealtimeNanos()).thenReturn(0L);
        doReturn(false).when(mMockLocationManager).isLocationEnabled();
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockLocationManager, never()).getCurrentLocation(anyString(),
                any(LocationRequest.class), any(CancellationSignal.class), any(Executor.class),
                any(Consumer.class));
        verify(mMockSatelliteOnDeviceAccessController, never()).isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class));
        assertTrue(waitForRequestIsSatelliteAllowedForCurrentLocationResult(
                mSatelliteAllowedSemaphore, 1));
        assertEquals(SATELLITE_RESULT_LOCATION_DISABLED, mQueriedSatelliteAllowedResultCode);
        assertFalse(mQueriedSatelliteAllowed);
    }

    @Test
    public void testAllowLocationQueryForSatelliteAllowedCheck() {
        mSatelliteAccessControllerUT.mLatestSatelliteCommunicationAllowedSetTime = 1;

        mSatelliteAccessControllerUT.setIsSatelliteAllowedRegionPossiblyChanged(false);
        // cash is invalid
        mSatelliteAccessControllerUT.elapsedRealtimeNanos =
                ALLOWED_STATE_CACHE_VALID_DURATION_NANOS + 10;
        assertTrue(mSatelliteAccessControllerUT.allowLocationQueryForSatelliteAllowedCheck());

        // cash is valid
        mSatelliteAccessControllerUT.elapsedRealtimeNanos =
                ALLOWED_STATE_CACHE_VALID_DURATION_NANOS - 10;
        assertFalse(mSatelliteAccessControllerUT.allowLocationQueryForSatelliteAllowedCheck());

        mSatelliteAccessControllerUT.setIsSatelliteAllowedRegionPossiblyChanged(true);
        // cash is invalid
        mSatelliteAccessControllerUT.elapsedRealtimeNanos =
                ALLOWED_STATE_CACHE_VALID_DURATION_NANOS + 10;
        assertTrue(mSatelliteAccessControllerUT.allowLocationQueryForSatelliteAllowedCheck());

        // cash is valid and throttled
        mSatelliteAccessControllerUT.elapsedRealtimeNanos =
                ALLOWED_STATE_CACHE_VALID_DURATION_NANOS - 10;

        // cash is valid and never queried before
        mSatelliteAccessControllerUT.mLastLocationQueryForPossibleChangeInAllowedRegionTimeNanos =
                0;
        assertTrue(mSatelliteAccessControllerUT.allowLocationQueryForSatelliteAllowedCheck());

        // cash is valid and throttled
        mSatelliteAccessControllerUT.mLastLocationQueryForPossibleChangeInAllowedRegionTimeNanos =
                mSatelliteAccessControllerUT.elapsedRealtimeNanos
                        - TEST_LOCATION_QUERY_THROTTLE_INTERVAL_NANOS + 100;
        assertFalse(mSatelliteAccessControllerUT.allowLocationQueryForSatelliteAllowedCheck());

        // cash is valid and not throttled
        mSatelliteAccessControllerUT.mLastLocationQueryForPossibleChangeInAllowedRegionTimeNanos =
                mSatelliteAccessControllerUT.elapsedRealtimeNanos
                        - TEST_LOCATION_QUERY_THROTTLE_INTERVAL_NANOS - 100;
        assertTrue(mSatelliteAccessControllerUT.allowLocationQueryForSatelliteAllowedCheck());
    }

    @Test
    public void testValidatePossibleChangeInSatelliteAllowedRegion() throws Exception {
        // OEM-enabled satellite is supported
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        verify(mMockCountryDetector).registerForCountryCodeChanged(
                mCountryDetectorHandlerCaptor.capture(), mCountryDetectorIntCaptor.capture(),
                mCountryDetectorObjCaptor.capture());

        assertSame(mCountryDetectorHandlerCaptor.getValue(), mSatelliteAccessControllerUT);
        assertSame(mCountryDetectorIntCaptor.getValue(), EVENT_COUNTRY_CODE_CHANGED);
        assertNull(mCountryDetectorObjCaptor.getValue());

        // Normal case that invokes
        // mMockSatelliteOnDeviceAccessController.isSatCommunicationAllowedAtLocation
        clearInvocations(mMockSatelliteOnDeviceAccessController);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        doReturn(true).when(mMockLocationManager).isLocationEnabled();
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS;
        sendCommandValidateCountryCodeChangeEvent(mMockContext);
        verify(mMockSatelliteOnDeviceAccessController,
                times(1)).isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class));

        // Case that isCommunicationAllowedCacheValid is true
        clearInvocations(mMockSatelliteOnDeviceAccessController);
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;
        sendCommandValidateCountryCodeChangeEvent(mMockContext);
        verify(mMockSatelliteOnDeviceAccessController, never()).isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class));

        // Case that mLatestCacheEnforcedValidateTimeNanos is over
        // ALLOWED_STATE_CACHE_VALIDATE_INTERVAL_NANOS (1hours)
        clearInvocations(mMockSatelliteOnDeviceAccessController);
        mSatelliteAccessControllerUT.elapsedRealtimeNanos =
                mSatelliteAccessControllerUT.elapsedRealtimeNanos
                        + TEST_LOCATION_QUERY_THROTTLE_INTERVAL_NANOS + 1;
        when(mMockLocation0.getElapsedRealtimeNanos())
                .thenReturn(mSatelliteAccessControllerUT.elapsedRealtimeNanos + 1L);
        when(mMockLocation1.getElapsedRealtimeNanos())
                .thenReturn(mSatelliteAccessControllerUT.elapsedRealtimeNanos + 1L);
        when(mMockLocation0.getLatitude()).thenReturn(2.0);
        when(mMockLocation0.getLongitude()).thenReturn(2.0);
        when(mMockLocation1.getLatitude()).thenReturn(3.0);
        when(mMockLocation1.getLongitude()).thenReturn(3.0);
        when(mMockSatelliteOnDeviceAccessController.isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class))).thenReturn(false);
        sendCommandValidateCountryCodeChangeEvent(mMockContext);
        verify(mMockSatelliteOnDeviceAccessController,
                times(1)).isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class));
    }

    @Test
    public void testRetryValidatePossibleChangeInSatelliteAllowedRegion() throws Exception {
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);

        verify(mMockCountryDetector).registerForCountryCodeChanged(
                mCountryDetectorHandlerCaptor.capture(), mCountryDetectorIntCaptor.capture(),
                mCountryDetectorObjCaptor.capture());

        assertSame(mCountryDetectorHandlerCaptor.getValue(), mSatelliteAccessControllerUT);
        assertSame(mCountryDetectorIntCaptor.getValue(), EVENT_COUNTRY_CODE_CHANGED);
        assertNull(mCountryDetectorObjCaptor.getValue());

        assertTrue(mSatelliteAccessControllerUT
                .getRetryCountPossibleChangeInSatelliteAllowedRegion() == 0);

        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_LOCATION_NOT_AVAILABLE);
        sendCommandValidateCountryCodeChangeEvent(mMockContext);

        assertTrue(mSatelliteAccessControllerUT
                .getRetryCountPossibleChangeInSatelliteAllowedRegion() == 1);

        mSatelliteAccessControllerUT.setRetryCountPossibleChangeInSatelliteAllowedRegion(
                DEFAULT_MAX_RETRY_COUNT_FOR_VALIDATING_POSSIBLE_CHANGE_IN_ALLOWED_REGION);
        sendSatelliteCommunicationAllowedEvent();
        assertTrue(mSatelliteAccessControllerUT
                .getRetryCountPossibleChangeInSatelliteAllowedRegion() == 0);

        mSatelliteAccessControllerUT.setRetryCountPossibleChangeInSatelliteAllowedRegion(2);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        sendSatelliteCommunicationAllowedEvent();
        assertTrue(mSatelliteAccessControllerUT
                .getRetryCountPossibleChangeInSatelliteAllowedRegion() == 0);
    }

    @Test
    public void testUpdateSatelliteConfigData() throws Exception {
        verify(mMockSatelliteController).registerForConfigUpdateChanged(
                mConfigUpdateHandlerCaptor.capture(), mConfigUpdateIntCaptor.capture(),
                mConfigUpdateObjectCaptor.capture());

        assertSame(mConfigUpdateHandlerCaptor.getValue(), mSatelliteAccessControllerUT);
        assertSame(mConfigUpdateIntCaptor.getValue(), EVENT_CONFIG_DATA_UPDATED);
        assertSame(mConfigUpdateObjectCaptor.getValue(), mMockContext);

        replaceInstance(SatelliteAccessController.class, "mCachedAccessRestrictionMap",
                mSatelliteAccessControllerUT, mMockCachedAccessRestrictionMap);

        // These APIs are executed during loadRemoteConfigs
        verify(mMockSharedPreferences, times(1)).getStringSet(anyString(), any());
        verify(mMockSharedPreferences, times(1)).getBoolean(anyString(), anyBoolean());

        // satelliteConfig is null
        SatelliteConfigParser spyConfigParser =
                spy(new SatelliteConfigParser("test".getBytes()));
        doReturn(spyConfigParser).when(mMockSatelliteController).getSatelliteConfigParser();
        assertNull(spyConfigParser.getConfig());

        sendConfigUpdateChangedEvent(mMockContext);
        verify(mMockSharedPreferences, never()).edit();
        verify(mMockCachedAccessRestrictionMap, never()).clear();

        // satelliteConfig has invalid country codes
        SatelliteConfig mockConfig = mock(SatelliteConfig.class);
        doReturn(List.of("USA", "JAP")).when(mockConfig).getDeviceSatelliteCountryCodes();
        doReturn(mockConfig).when(mMockSatelliteController).getSatelliteConfig();
        doReturn(false).when(mockConfig).isSatelliteDataForAllowedRegion();

        sendConfigUpdateChangedEvent(mMockContext);
        verify(mMockSharedPreferences, never()).edit();
        verify(mMockCachedAccessRestrictionMap, never()).clear();

        // satelliteConfig does not have is_allow_access_control data
        doReturn(List.of(TEST_SATELLITE_COUNTRY_CODES))
                .when(mockConfig).getDeviceSatelliteCountryCodes();
        doReturn(null).when(mockConfig).isSatelliteDataForAllowedRegion();

        sendConfigUpdateChangedEvent(mMockContext);
        verify(mMockSharedPreferences, never()).edit();
        verify(mMockCachedAccessRestrictionMap, never()).clear();

        // satelliteConfig doesn't have S2CellFile
        File mockFile = mock(File.class);
        doReturn(false).when(mockFile).exists();
        doReturn(List.of(TEST_SATELLITE_COUNTRY_CODES))
                .when(mockConfig).getDeviceSatelliteCountryCodes();
        doReturn(true).when(mockConfig).isSatelliteDataForAllowedRegion();
        doReturn(mockFile).when(mockConfig).getSatelliteS2CellFile(mMockContext);

        sendConfigUpdateChangedEvent(mMockContext);
        verify(mMockSharedPreferences, never()).edit();
        verify(mMockCachedAccessRestrictionMap, never()).clear();

        // satelliteConfig has valid data
        doReturn(mockConfig).when(mMockSatelliteController).getSatelliteConfig();
        File testS2File = mSatelliteAccessControllerUT
                .getTestSatelliteS2File(GOOGLE_US_SAN_SAT_S2_FILE_NAME);
        doReturn(List.of(TEST_SATELLITE_COUNTRY_CODES))
                .when(mockConfig).getDeviceSatelliteCountryCodes();
        doReturn(true).when(mockConfig).isSatelliteDataForAllowedRegion();
        doReturn(testS2File).when(mockConfig).getSatelliteS2CellFile(mMockContext);

        sendConfigUpdateChangedEvent(mMockContext);
        verify(mMockSharedPreferences, times(2)).edit();
        verify(mMockCachedAccessRestrictionMap, times(1)).clear();
    }

    @Test
    public void testLocationModeChanged() throws Exception {
        // setup for querying GPS not to reset mIsSatelliteAllowedRegionPossiblyChanged false.
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_oem_enabled_satellite_access_allow))
                .thenReturn(TEST_SATELLITE_ALLOW);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        when(mMockSatelliteOnDeviceAccessController.isSatCommunicationAllowedAtLocation(
                any(SatelliteOnDeviceAccessController.LocationToken.class))).thenReturn(true);
        replaceInstance(SatelliteAccessController.class, "mCachedAccessRestrictionMap",
                mSatelliteAccessControllerUT, mMockCachedAccessRestrictionMap);
        doReturn(false).when(mMockCachedAccessRestrictionMap).containsKey(any());
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;

        // Captor and Verify if the mockReceiver and mocContext is registered well
        verify(mMockContext).registerReceiver(mLocationBroadcastReceiverCaptor.capture(),
                mIntentFilterCaptor.capture());
        assertSame(mSatelliteAccessControllerUT.getLocationBroadcastReceiver(),
                mLocationBroadcastReceiverCaptor.getValue());
        assertSame(MODE_CHANGED_ACTION, mIntentFilterCaptor.getValue().getAction(0));

        // When the intent action is not MODE_CHANGED_ACTION,
        // verify if the location manager never invoke isLocationEnabled()
        doReturn("").when(mMockLocationIntent).getAction();
        mSatelliteAccessControllerUT.setIsSatelliteAllowedRegionPossiblyChanged(false);
        mSatelliteAccessControllerUT.getLocationBroadcastReceiver()
                .onReceive(mMockContext, mMockLocationIntent);
        verify(mMockLocationManager, never()).isLocationEnabled();

        // When the intent action is MODE_CHANGED_ACTION and isLocationEnabled() is true,
        // verify if mIsSatelliteAllowedRegionPossiblyChanged is true
        doReturn(MODE_CHANGED_ACTION).when(mMockLocationIntent).getAction();
        doReturn(true).when(mMockLocationManager).isLocationEnabled();
        clearInvocations(mMockLocationManager);
        mSatelliteAccessControllerUT.setIsSatelliteAllowedRegionPossiblyChanged(false);
        mSatelliteAccessControllerUT.getLocationBroadcastReceiver()
                .onReceive(mMockContext, mMockLocationIntent);
        verify(mMockLocationManager, times(1)).isLocationEnabled();
        mTestableLooper.processAllMessages();
        assertEquals(true, mSatelliteAccessControllerUT.isSatelliteAllowedRegionPossiblyChanged());

        // When the intent action is MODE_CHANGED_ACTION and isLocationEnabled() is false,
        // verify if mIsSatelliteAllowedRegionPossiblyChanged is false
        doReturn(false).when(mMockLocationManager).isLocationEnabled();
        clearInvocations(mMockLocationManager);
        mSatelliteAccessControllerUT.setIsSatelliteAllowedRegionPossiblyChanged(false);
        mSatelliteAccessControllerUT.getLocationBroadcastReceiver()
                .onReceive(mMockContext, mMockLocationIntent);
        verify(mMockLocationManager, times(1)).isLocationEnabled();
        mTestableLooper.processAllMessages();
        assertEquals(false, mSatelliteAccessControllerUT.isSatelliteAllowedRegionPossiblyChanged());
    }

    @Test
    public void testCheckSatelliteAccessRestrictionUsingGPS() {
        // In emergency case,
        // verify if the location manager get FUSED provider and ignore location settings
        doReturn(true).when(mMockTelecomManager).isInEmergencyCall();
        mSatelliteAccessControllerUT.setLocationRequestCancellationSignalAsNull();
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;
        mSatelliteAccessControllerUT.checkSatelliteAccessRestrictionUsingGPS();

        verify(mMockLocationManager, times(1))
                .getCurrentLocation(mLocationProviderStringCaptor.capture(),
                        mLocationRequestCaptor.capture(), any(), any(), any());
        assertEquals(LocationManager.FUSED_PROVIDER, mLocationProviderStringCaptor.getValue());
        assertTrue(mLocationRequestCaptor.getValue().isLocationSettingsIgnored());

        // In non-emergency case,
        // verify if the location manager get FUSED provider and not ignore location settings
        clearInvocations(mMockLocationManager);
        doReturn(false).when(mMockTelecomManager).isInEmergencyCall();
        doReturn(false).when(mMockPhone).isInEcm();
        doReturn(false).when(mMockPhone2).isInEcm();
        doReturn(false).when(mMockSatelliteController).isInEmergencyMode();
        doReturn(true).when(mMockLocationManager).isLocationEnabled();
        mSatelliteAccessControllerUT.setLocationRequestCancellationSignalAsNull();
        mSatelliteAccessControllerUT.checkSatelliteAccessRestrictionUsingGPS();

        verify(mMockLocationManager, times(1))
                .getCurrentLocation(mLocationProviderStringCaptor.capture(),
                        mLocationRequestCaptor.capture(), any(), any(), any());
        assertEquals(LocationManager.FUSED_PROVIDER, mLocationProviderStringCaptor.getValue());
        assertFalse(mLocationRequestCaptor.getValue().isLocationSettingsIgnored());
    }

    @Test
    public void testHandleIsSatelliteSupportedResult() throws Exception {
        // Setup for this test case
        Iterator<ResultReceiver> mockIterator = mock(Iterator.class);
        doReturn(mockIterator).when(mMockSatelliteAllowResultReceivers).iterator();
        doReturn(true, false).when(mockIterator).hasNext();
        doReturn(mMockSatelliteSupportedResultReceiver).when(mockIterator).next();

        replaceInstance(SatelliteAccessController.class, "mSatelliteAllowResultReceivers",
                mSatelliteAccessControllerUT, mMockSatelliteAllowResultReceivers);
        doNothing().when(mMockSatelliteAllowResultReceivers).clear();

        // case that resultCode is not SATELLITE_RESULT_SUCCESS
        int resultCode = SATELLITE_RESULT_ERROR;
        Bundle bundle = new Bundle();
        doReturn(true, false).when(mockIterator).hasNext();
        clearInvocations(mMockSatelliteSupportedResultReceiver);
        mSatelliteAccessControllerUT.handleIsSatelliteSupportedResult(resultCode, bundle);
        verify(mMockSatelliteSupportedResultReceiver)
                .send(mResultCodeIntCaptor.capture(), any());
        assertEquals(Integer.valueOf(SATELLITE_RESULT_ERROR), mResultCodeIntCaptor.getValue());

        // case no KEY_SATELLITE_SUPPORTED in the bundle data.
        // verify that the resultCode is delivered as it were
        resultCode = SATELLITE_RESULT_SUCCESS;
        bundle.putBoolean(KEY_SATELLITE_PROVISIONED, false);
        doReturn(true, false).when(mockIterator).hasNext();
        clearInvocations(mMockSatelliteSupportedResultReceiver);
        mSatelliteAccessControllerUT.handleIsSatelliteSupportedResult(resultCode, bundle);
        verify(mMockSatelliteSupportedResultReceiver)
                .send(mResultCodeIntCaptor.capture(), any());
        assertEquals(Integer.valueOf(SATELLITE_RESULT_SUCCESS), mResultCodeIntCaptor.getValue());

        // case KEY_SATELLITE_SUPPORTED is false
        // verify SATELLITE_RESULT_NOT_SUPPORTED is captured
        bundle.putBoolean(KEY_SATELLITE_SUPPORTED, false);
        doReturn(true, false).when(mockIterator).hasNext();
        clearInvocations(mMockSatelliteSupportedResultReceiver);
        mSatelliteAccessControllerUT.handleIsSatelliteSupportedResult(resultCode, bundle);
        verify(mMockSatelliteSupportedResultReceiver)
                .send(mResultCodeIntCaptor.capture(), mResultDataBundleCaptor.capture());
        assertEquals(Integer.valueOf(SATELLITE_RESULT_NOT_SUPPORTED),
                mResultCodeIntCaptor.getValue());
        assertEquals(false,
                mResultDataBundleCaptor.getValue().getBoolean(KEY_SATELLITE_COMMUNICATION_ALLOWED));

        // case KEY_SATELLITE_SUPPORTED is success and region is not allowed
        // verify SATELLITE_RESULT_SUCCESS is captured
        bundle.putBoolean(KEY_SATELLITE_SUPPORTED, true);
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_KR));
        doReturn(true, false).when(mockIterator).hasNext();
        clearInvocations(mMockSatelliteSupportedResultReceiver);
        mSatelliteAccessControllerUT.handleIsSatelliteSupportedResult(resultCode, bundle);
        verify(mMockSatelliteSupportedResultReceiver)
                .send(mResultCodeIntCaptor.capture(), mResultDataBundleCaptor.capture());
        assertEquals(Integer.valueOf(SATELLITE_RESULT_SUCCESS),
                mResultCodeIntCaptor.getValue());
        assertEquals(false,
                mResultDataBundleCaptor.getValue().getBoolean(KEY_SATELLITE_COMMUNICATION_ALLOWED));

        // case KEY_SATELLITE_SUPPORTED is success and locationManager is disabled
        // verify SATELLITE_RESULT_LOCATION_DISABLED is captured
        when(mMockCountryDetector.getCurrentNetworkCountryIso())
                .thenReturn(List.of(TEST_SATELLITE_COUNTRY_CODE_US));
        doReturn(false).when(mMockLocationManager).isLocationEnabled();
        doReturn(true, false).when(mockIterator).hasNext();
        clearInvocations(mMockSatelliteSupportedResultReceiver);
        mSatelliteAccessControllerUT.handleIsSatelliteSupportedResult(resultCode, bundle);
        verify(mMockSatelliteSupportedResultReceiver)
                .send(mResultCodeIntCaptor.capture(), mResultDataBundleCaptor.capture());
        assertEquals(Integer.valueOf(SATELLITE_RESULT_LOCATION_DISABLED),
                mResultCodeIntCaptor.getValue());
        assertEquals(false,
                mResultDataBundleCaptor.getValue().getBoolean(KEY_SATELLITE_COMMUNICATION_ALLOWED));
    }

    @Test
    public void testRequestIsCommunicationAllowedForCurrentLocationWithEnablingSatellite() {
        // Set non-emergency case
        when(mMockFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        setUpResponseForRequestIsSatelliteSupported(true, SATELLITE_RESULT_SUCCESS);
        setUpResponseForRequestIsSatelliteProvisioned(true, SATELLITE_RESULT_SUCCESS);
        when(mMockCountryDetector.getCurrentNetworkCountryIso()).thenReturn(EMPTY_STRING_LIST);
        doReturn(false).when(mMockTelecomManager).isInEmergencyCall();
        doReturn(false).when(mMockPhone).isInEcm();
        doReturn(false).when(mMockPhone2).isInEcm();
        doReturn(false).when(mMockSatelliteController).isInEmergencyMode();
        doReturn(true).when(mMockLocationManager).isLocationEnabled();
        mSatelliteAccessControllerUT.setLocationRequestCancellationSignalAsNull();
        mSatelliteAccessControllerUT.elapsedRealtimeNanos = TEST_LOCATION_FRESH_DURATION_NANOS + 1;

        // Invoking requestIsCommunicationAllowedForCurrentLocation(resultReceiver, "false");
        // verify that mLocationManager.isLocationEnabled() is invoked
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, false);
        mTestableLooper.processAllMessages();
        verify(mMockLocationManager, times(1)).isLocationEnabled();
        verify(mMockLocationManager, times(1)).getCurrentLocation(anyString(),
                any(LocationRequest.class), any(CancellationSignal.class), any(Executor.class),
                any(Consumer.class));

        // Invoking requestIsCommunicationAllowedForCurrentLocation(resultReceiver, "true");
        // verify that mLocationManager.isLocationEnabled() is not invoked
        clearInvocations(mMockLocationManager);
        mSatelliteAccessControllerUT.requestIsCommunicationAllowedForCurrentLocation(
                mSatelliteAllowedReceiver, true);
        mTestableLooper.processAllMessages();
        verify(mMockLocationManager, times(1)).isLocationEnabled();
        verify(mMockLocationManager, never()).getCurrentLocation(anyString(),
                any(LocationRequest.class), any(CancellationSignal.class), any(Executor.class),
                any(Consumer.class));
    }

    private void sendSatelliteCommunicationAllowedEvent() {
        Pair<Integer, ResultReceiver> requestPair =
                new Pair<>(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                        mSatelliteAccessControllerUT.getResultReceiverCurrentLocation());
        Message msg = mSatelliteAccessControllerUT.obtainMessage(
                CMD_IS_SATELLITE_COMMUNICATION_ALLOWED);
        msg.obj = requestPair;
        msg.sendToTarget();
        mTestableLooper.processAllMessages();
    }


    private void sendConfigUpdateChangedEvent(Context context) {
        Message msg = mSatelliteAccessControllerUT.obtainMessage(EVENT_CONFIG_DATA_UPDATED);
        msg.obj = new AsyncResult(context, SATELLITE_RESULT_SUCCESS, null);
        msg.sendToTarget();
        mTestableLooper.processAllMessages();
    }

    private void sendCommandValidateCountryCodeChangeEvent(Context context) {
        Message msg = mSatelliteAccessControllerUT.obtainMessage(EVENT_COUNTRY_CODE_CHANGED);
        msg.obj = new AsyncResult(context, SATELLITE_RESULT_SUCCESS, null);
        msg.sendToTarget();
        mTestableLooper.processAllMessages();
    }

    private void clearAllInvocations() {
        clearInvocations(mMockSatelliteController);
        clearInvocations(mMockSatelliteOnDeviceAccessController);
        clearInvocations(mMockLocationManager);
        clearInvocations(mMockCountryDetector);
    }

    private void verifyCountryDetectorApisCalled() {
        verify(mMockCountryDetector).getCurrentNetworkCountryIso();
        verify(mMockCountryDetector).getCachedLocationCountryIsoInfo();
        verify(mMockCountryDetector).getCachedLocationCountryIsoInfo();
    }

    private boolean waitForRequestIsSatelliteAllowedForCurrentLocationResult(Semaphore semaphore,
            int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!semaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    logd("Timeout to receive "
                            + "requestIsCommunicationAllowedForCurrentLocation()"
                            + " callback");
                    return false;
                }
            } catch (Exception ex) {
                logd("waitForRequestIsSatelliteSupportedResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private void sendLocationRequestResult(Location location) {
        mLocationRequestConsumerCaptor.getValue().accept(location);
        mTestableLooper.processAllMessages();
    }

    private void setUpResponseForRequestIsSatelliteSupported(
            boolean isSatelliteSupported, @SatelliteManager.SatelliteResult int error) {
        doAnswer(invocation -> {
            ResultReceiver resultReceiver = invocation.getArgument(0);
            if (error == SATELLITE_RESULT_SUCCESS) {
                Bundle bundle = new Bundle();
                bundle.putBoolean(SatelliteManager.KEY_SATELLITE_SUPPORTED, isSatelliteSupported);
                resultReceiver.send(error, bundle);
            } else {
                resultReceiver.send(error, Bundle.EMPTY);
            }
            return null;
        }).when(mMockSatelliteController).requestIsSatelliteSupported(any(ResultReceiver.class));
    }

    private void setUpResponseForRequestIsSatelliteProvisioned(
            boolean isSatelliteProvisioned, @SatelliteManager.SatelliteResult int error) {
        doAnswer(invocation -> {
            ResultReceiver resultReceiver = invocation.getArgument(0);
            if (error == SATELLITE_RESULT_SUCCESS) {
                Bundle bundle = new Bundle();
                bundle.putBoolean(KEY_SATELLITE_PROVISIONED,
                        isSatelliteProvisioned);
                resultReceiver.send(error, bundle);
            } else {
                resultReceiver.send(error, Bundle.EMPTY);
            }
            return null;
        }).when(mMockSatelliteController).requestIsSatelliteProvisioned(any(ResultReceiver.class));
    }

    @SafeVarargs
    private static <E> List<E> listOf(E... values) {
        return Arrays.asList(values);
    }

    private static void logd(String message) {
        Log.d(TAG, message);
    }

    private static void replaceInstance(final Class c,
            final String instanceName, final Object obj, final Object newValue) throws Exception {
        Field field = c.getDeclaredField(instanceName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }

    private static class TestSatelliteAccessController extends SatelliteAccessController {
        public long elapsedRealtimeNanos = 0;

        /**
         * Create a SatelliteAccessController instance.
         *
         * @param context                           The context associated with the
         *                                          {@link SatelliteAccessController} instance.
         * @param featureFlags                      The FeatureFlags that are supported.
         * @param looper                            The Looper to run the SatelliteAccessController
         *                                          on.
         * @param locationManager                   The LocationManager for querying current
         *                                          location of the
         *                                          device.
         * @param satelliteOnDeviceAccessController The on-device satellite access controller
         *                                          instance.
         */
        protected TestSatelliteAccessController(Context context, FeatureFlags featureFlags,
                Looper looper, LocationManager locationManager, TelecomManager telecomManager,
                SatelliteOnDeviceAccessController satelliteOnDeviceAccessController,
                File s2CellFile) {
            super(context, featureFlags, looper, locationManager, telecomManager,
                    satelliteOnDeviceAccessController, s2CellFile);
        }

        @Override
        protected long getElapsedRealtimeNanos() {
            return elapsedRealtimeNanos;
        }

        public boolean isKeepOnDeviceAccessControllerResourcesTimerStarted() {
            return hasMessages(EVENT_KEEP_ON_DEVICE_ACCESS_CONTROLLER_RESOURCES_TIMEOUT);
        }

        public boolean isSatelliteOnDeviceAccessControllerReset() {
            synchronized (mLock) {
                return (mSatelliteOnDeviceAccessController == null);
            }
        }

        public void setSatelliteOnDeviceAccessController(
                @Nullable SatelliteOnDeviceAccessController accessController) {
            synchronized (mLock) {
                mSatelliteOnDeviceAccessController = accessController;
            }
        }

        public long getKeepOnDeviceAccessControllerResourcesTimeoutMillis() {
            return KEEP_ON_DEVICE_ACCESS_CONTROLLER_RESOURCES_TIMEOUT_MILLIS;
        }

        public long getWaitForCurrentLocationTimeoutMillis() {
            return WAIT_FOR_CURRENT_LOCATION_TIMEOUT_MILLIS;
        }

        public boolean isWaitForCurrentLocationTimerStarted() {
            return hasMessages(EVENT_WAIT_FOR_CURRENT_LOCATION_TIMEOUT);
        }

        public int getRetryCountPossibleChangeInSatelliteAllowedRegion() {
            return mRetryCountForValidatingPossibleChangeInAllowedRegion;
        }

        public void setRetryCountPossibleChangeInSatelliteAllowedRegion(int retryCount) {
            mRetryCountForValidatingPossibleChangeInAllowedRegion = retryCount;
        }

        public ResultReceiver getResultReceiverCurrentLocation() {
            return mHandlerForSatelliteAllowedResult;
        }

        public BroadcastReceiver getLocationBroadcastReceiver() {
            return mLocationModeChangedBroadcastReceiver;
        }

        public void setLocationRequestCancellationSignalAsNull() {
            synchronized (mLock) {
                mLocationRequestCancellationSignal = null;
            }
        }
    }
}
