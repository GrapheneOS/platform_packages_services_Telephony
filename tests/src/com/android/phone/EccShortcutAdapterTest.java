package com.android.phone;

import static android.telephony.emergency.EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.telephony.emergency.EmergencyNumber;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class EccShortcutAdapterTest {
    private static final int POLICE = EMERGENCY_SERVICE_CATEGORY_POLICE;
    private static final int AMBULANCE = EMERGENCY_SERVICE_CATEGORY_AMBULANCE;
    private static final int FIRE = EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE;
    private static final int GENERAL = POLICE | AMBULANCE | FIRE;
    private static final int DATABASE = EMERGENCY_NUMBER_SOURCE_DATABASE;
    private static final int NETWORK = EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING;

    private Context mContext;
    private EccShortcutAdapter mAdapter;
    private final List<String> mRenderedShortcuts = new ArrayList<>();

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.US);
        mContext = context.createConfigurationContext(config);
        mAdapter = new EccShortcutAdapter(mContext) {
            @Override
            public View inflateView(View convertView, ViewGroup parent, CharSequence number,
                    CharSequence description, int iconRes) {
                mRenderedShortcuts.add(number + ": " + description);
                return null;
            }
        };
    }

    @Test
    public void canada_differentCategorySets_shows911Then112() {
        update("ca", List.of(
                number("112", GENERAL, DATABASE),
                number("911", GENERAL, DATABASE),
                number("911", GENERAL | EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD
                        | EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE, NETWORK)));

        assertEquals(List.of("911: Police, Ambulance, Fire", "112: Police, Ambulance, Fire"),
                renderShortcuts());
    }

    @Test
    public void canada_identicalCategorySets_showsOnly911() {
        update("ca", List.of(number("112", GENERAL, DATABASE),
                number("911", GENERAL, DATABASE)));

        assertEquals(List.of("911: Police, Ambulance, Fire"), renderShortcuts());
    }

    @Test
    public void spain_shows112WithAllItsServices() {
        update("es", List.of(number("080", FIRE, DATABASE),
                number("091", POLICE, DATABASE), number("112", GENERAL, DATABASE)));

        assertEquals(List.of("112: Police, Ambulance, Fire", "091: Police", "080: Fire"),
                renderShortcuts());
    }

    @Test
    public void uruguay_showsAll911ServicesAlongsideSpecializedNumbers() {
        update("uy", List.of(number("104", FIRE, DATABASE),
                number("105", AMBULANCE, DATABASE), number("911", GENERAL, DATABASE)));

        assertEquals(List.of("911: Police, Ambulance, Fire", "105: Ambulance", "104: Fire"),
                renderShortcuts());
    }

    @Test
    public void britain_primaryNumberHidesAlternatives() {
        update("gb", List.of(number("112", GENERAL, DATABASE),
                number("999", GENERAL, NETWORK)));

        assertEquals(List.of("999: Police, Ambulance, Fire"), renderShortcuts());
    }

    @Test
    public void fragmentedCategories_areCombinedBeforeRankingWithoutChangingInput() {
        EmergencyNumber fire = number("110", FIRE, DATABASE);
        EmergencyNumber police = number("110", POLICE, NETWORK);
        EmergencyNumber ambulance = number("119", AMBULANCE, DATABASE);
        List<EmergencyNumber> numbers = List.of(fire, ambulance, police);
        update("", numbers);

        assertEquals(List.of("110: Police, Fire", "119: Ambulance"), renderShortcuts());
        assertEquals(FIRE, fire.getEmergencyServiceCategoryBitmask());
        assertEquals(POLICE, police.getEmergencyServiceCategoryBitmask());
        assertEquals(List.of(fire, ambulance, police), numbers);
    }

    @Test
    public void unspecifiedCategory_isPreservedAlongsideSpecificCategories() {
        update("", List.of(number("112", GENERAL, DATABASE),
                number("911", GENERAL, DATABASE),
                number("112", EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED, NETWORK)));

        assertEquals(List.of("112: Police, Ambulance, Fire", "911: Police, Ambulance, Fire"),
                renderShortcuts());
    }

    @Test
    public void categoriesWithoutLabels_showNumbersWithoutDescriptions() {
        update("", List.of(number("104", EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED, DATABASE),
                number("105", EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD, DATABASE)));

        assertEquals(List.of("104: ", "105: "), renderShortcuts());
    }

    @Test
    public void equalRanks_preserveInputOrder() {
        update("", List.of(number("117", POLICE, NETWORK), number("112", POLICE, NETWORK)));

        assertEquals(List.of("117: Police"), renderShortcuts());
    }

    @Test
    public void missingPhone_clearsPreviousShortcuts() {
        update("ca", List.of(number("911", GENERAL, DATABASE)));
        assertTrue(mAdapter.hasShortcut("911"));

        mAdapter.updateCountryEccInfo(mContext, null);

        assertEquals(List.of(), renderShortcuts());
        assertFalse(mAdapter.hasShortcut("911"));
    }

    @Test
    public void emptyList_clearsPreviousShortcuts() {
        update("ca", List.of(number("911", GENERAL, DATABASE)));
        update("ca", List.of());

        assertEquals(List.of(), renderShortcuts());
        assertFalse(mAdapter.hasShortcut("911"));
    }

    private void update(String country, List<EmergencyNumber> numbers) {
        ShortcutViewUtils.PhoneInfo phone =
                ShortcutViewTestUtils.createPhoneInfo(1, country, numbers);
        mAdapter.updateCountryEccInfo(mContext, phone);
    }

    private List<String> renderShortcuts() {
        mRenderedShortcuts.clear();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            mAdapter.getView(i, null, null);
        }
        return List.copyOf(mRenderedShortcuts);
    }

    private static EmergencyNumber number(String number, int categories, int source) {
        return new EmergencyNumber(number, "", "", categories, List.of(), source,
                EMERGENCY_CALL_ROUTING_UNKNOWN);
    }
}
