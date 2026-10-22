package com.android.phone;

import static android.telephony.emergency.EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE;
import static android.telephony.emergency.EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.espresso.action.ScrollToAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class EmergencyDialerShortcutTest {
    private static final int POLICE = EMERGENCY_SERVICE_CATEGORY_POLICE;
    private static final int AMBULANCE = EMERGENCY_SERVICE_CATEGORY_AMBULANCE;
    private static final int FIRE = EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE;
    private static final int GENERAL = POLICE | AMBULANCE | FIRE;
    private static final int UNSPECIFIED = EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;
    private static final int DATABASE = EMERGENCY_NUMBER_SOURCE_DATABASE;
    private static final int DEFAULT = EMERGENCY_NUMBER_SOURCE_DEFAULT;
    private static final int MODEM = EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG;
    private static final int NETWORK = EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING;
    private static final int SIM = EMERGENCY_NUMBER_SOURCE_SIM;
    private static final int EMERGENCY = EMERGENCY_CALL_ROUTING_EMERGENCY;
    private static final int UNKNOWN = EMERGENCY_CALL_ROUTING_UNKNOWN;

    @Rule
    public ActivityTestRule<EmergencyDialer> mRule =
            new ActivityTestRule<>(EmergencyDialer.class);

    @Test
    public void argentina_keepsFourthFireShortcut() throws Throwable {
        showShortcuts("ar", List.of(number("100", FIRE), number("101", POLICE),
                number("107", AMBULANCE), number("911", GENERAL)));

        assertNumbers("101", "911", "107", "100");
        assertReachable("100");
    }

    @Test
    public void russia_keepsFourthFireShortcut() throws Throwable {
        showShortcuts("ru", List.of(number("101", FIRE), number("102", POLICE),
                number("103", AMBULANCE), number("112", GENERAL)));

        assertNumbers("102", "112", "103", "101");
        assertReachable("101");
    }

    @Test
    public void russia_simNumbersWithoutCategories_shows104WithoutCategory() throws Throwable {
        showShortcuts("ru", simNumbers(russiaNumbers()));

        assertNumbers("102", "112", "103", "101", "104");
        assertUnlabelledShortcut("104");
    }

    @Test
    public void russia_network911WithAdditionalCategories_shows911And104() throws Throwable {
        showShortcuts("ru", networkNumbers(russiaNumbers()));

        assertNumbers("102", "112", "911", "103", "101", "104");
        assertUnlabelledShortcut("104");
    }

    @Test
    public void argentina_simNumbersWithoutCategories_shows112WithoutCategory() throws Throwable {
        showShortcuts("ar", simNumbers(argentinaNumbers()));

        assertNumbers("101", "911", "107", "100", "112");
        assertUnlabelledShortcut("112");
    }

    @Test
    public void argentina_network911WithAdditionalCategories_shows112WithoutCategory()
            throws Throwable {
        showShortcuts("ar", networkNumbers(argentinaNumbers()));

        assertNumbers("101", "911", "107", "100", "112");
        assertUnlabelledShortcut("112");
    }

    @Test
    public void multiplePoliceCategorySets_keepAmbulanceAndFireShortcuts() throws Throwable {
        showShortcuts("", List.of(number("101", POLICE),
                number("102", POLICE | EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD),
                number("103", POLICE | EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE),
                number("104", AMBULANCE), number("105", FIRE)));

        assertNumbers("101", "102", "103", "104", "105");
        assertReachable("104");
        assertReachable("105");
        onView(allOf(withId(R.id.phone_number_description),
                withText(R.string.fire_type_description)))
                .check(matches(isCompletelyDisplayed()));
    }

    @Test
    public void longList_scrollsToLastShortcutWithoutCoveringDialpadButton() throws Throwable {
        List<EmergencyNumber> numbers = new ArrayList<>();
        for (int categories = 1; categories < 32; categories++) {
            if ((categories & GENERAL) != 0) {
                numbers.add(number("9" + categories, categories));
            }
        }
        showShortcuts("", numbers);

        mRule.runOnUiThread(() -> assertEquals(numbers.size(), getButtons().getChildCount()));
        // The final entry serves fire, marine and mountain rescue.
        assertReachable("928");
        onView(withId(R.id.floating_action_button_dialpad))
                .check(matches(isCompletelyDisplayed()));
        mRule.runOnUiThread(() -> {
            ScrollView scroll = mRule.getActivity().findViewById(R.id.emergency_shortcuts_scroll);
            assertTrue(scroll.canScrollVertically(-1));
            Rect scrollBounds = new Rect();
            Rect dialpadBounds = new Rect();
            assertTrue(scroll.getGlobalVisibleRect(scrollBounds));
            assertTrue(mRule.getActivity().findViewById(R.id.dialpad_button_container)
                    .getGlobalVisibleRect(dialpadBounds));
            assertTrue(scrollBounds.bottom <= dialpadBounds.top);
        });
    }

    private void showShortcuts(String country, List<EmergencyNumber> numbers) throws Throwable {
        TelephonyManager telephony = mock(TelephonyManager.class);
        when(telephony.getEmergencyNumberList()).thenReturn(Map.of(1, numbers));
        Method getLists = ShortcutViewUtils.class.getDeclaredMethod(
                "getPromotedEmergencyNumberLists", TelephonyManager.class);
        getLists.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, List<EmergencyNumber>> lists =
                (Map<Integer, List<EmergencyNumber>>) getLists.invoke(null, telephony);

        mRule.runOnUiThread(() -> {
            EmergencyDialer activity = mRule.getActivity();
            ShortcutViewUtils.PhoneInfo phone = ShortcutViewTestUtils.createPhoneInfo(
                    1, country, lists.getOrDefault(1, List.of()));
            // A disabled config skips querying the device's phone and carrier state.
            ShortcutViewUtils.Config config = new ShortcutViewUtils.Config(
                    activity, null, EmergencyDialer.ENTRY_TYPE_UNKNOWN);

            // Supply phone data without depending on a SIM or changing device configuration.
            // Exercise the activity's real observer, adapter, button inflation and layout.
            try {
                Field phoneInfoField = ShortcutViewUtils.Config.class.getDeclaredField("mPhoneInfo");
                phoneInfoField.setAccessible(true);
                phoneInfoField.set(config, phone);
                Field configField = EmergencyDialer.class.getDeclaredField("mShortcutViewConfig");
                configField.setAccessible(true);
                configField.set(activity, config);
                Method setup = EmergencyDialer.class.getDeclaredMethod("setupEmergencyShortcutsView");
                setup.setAccessible(true);
                setup.invoke(activity);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void assertNumbers(String... expected) throws Throwable {
        mRule.runOnUiThread(() -> {
            ViewGroup buttons = getButtons();
            List<String> actual = new ArrayList<>();
            for (int i = 0; i < buttons.getChildCount(); i++) {
                actual.add(((EmergencyShortcutButton) buttons.getChildAt(i)).getPhoneNumber());
            }
            assertEquals(List.of(expected), actual);
            TextView title = mRule.getActivity().findViewById(R.id.emergency_number_title);
            assertEquals(mRule.getActivity().getString(R.string.numerous_emergency_numbers_title),
                    title.getText().toString());
        });
    }

    private void assertReachable(String number) {
        onView(allOf(isAssignableFrom(EmergencyShortcutButton.class),
                hasDescendant(allOf(withId(R.id.phone_number), withText(number)))))
                .perform(new ScrollToAction(100)).check(matches(isCompletelyDisplayed()));
    }

    private void assertUnlabelledShortcut(String number) throws Throwable {
        assertReachable(number);
        mRule.runOnUiThread(() -> {
            ViewGroup buttons = getButtons();
            for (int i = 0; i < buttons.getChildCount(); i++) {
                EmergencyShortcutButton button = (EmergencyShortcutButton) buttons.getChildAt(i);
                if (number.equals(button.getPhoneNumber())) {
                    TextView description = button.findViewById(R.id.phone_number_description);
                    assertEquals("", description.getText().toString());
                    assertEquals(View.GONE, description.getVisibility());
                    ImageView icon = button.findViewById(R.id.phone_type_icon);
                    assertNotNull(icon.getDrawable());
                    return;
                }
            }
            throw new AssertionError("Missing shortcut " + number);
        });
    }

    private ViewGroup getButtons() {
        return mRule.getActivity().findViewById(R.id.emergency_shortcut_buttons_container);
    }

    private static EmergencyNumber number(String number, int categories) {
        return number(number, "", categories,
                EMERGENCY_NUMBER_SOURCE_DATABASE, EMERGENCY_CALL_ROUTING_UNKNOWN);
    }

    private static List<EmergencyNumber> simNumbers(List<EmergencyNumber> database) {
        List<EmergencyNumber> numbers = new ArrayList<>(List.of(
                number("112", "ca", UNSPECIFIED, SIM | DEFAULT, UNKNOWN),
                number("911", "ca", UNSPECIFIED, SIM | DEFAULT, UNKNOWN),
                number("000", "ca", UNSPECIFIED, SIM, UNKNOWN),
                number("08", "ca", UNSPECIFIED, SIM, UNKNOWN),
                number("110", "ca", UNSPECIFIED, SIM, UNKNOWN),
                number("118", "ca", UNSPECIFIED, SIM, UNKNOWN),
                number("119", "ca", UNSPECIFIED, SIM, UNKNOWN),
                number("999", "ca", UNSPECIFIED, SIM, UNKNOWN)));
        numbers.addAll(database);
        return numbers;
    }

    private static List<EmergencyNumber> networkNumbers(List<EmergencyNumber> database) {
        List<EmergencyNumber> numbers = new ArrayList<>(List.of(
                number("911", "ca", GENERAL | EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD
                        | EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE, NETWORK | SIM | MODEM, UNKNOWN),
                number("112", "ca", UNSPECIFIED, SIM | MODEM, UNKNOWN)));
        numbers.addAll(database);
        numbers.add(number("112", "", UNSPECIFIED, MODEM, UNKNOWN));
        return numbers;
    }

    private static List<EmergencyNumber> russiaNumbers() {
        return List.of(
                number("101", "ru", FIRE, DATABASE, EMERGENCY),
                number("102", "ru", POLICE, DATABASE, EMERGENCY),
                number("103", "ru", AMBULANCE, DATABASE, EMERGENCY),
                number("104", "ru", UNSPECIFIED, DATABASE, EMERGENCY),
                number("112", "ru", GENERAL, DATABASE, EMERGENCY),
                number("121", "ru", UNSPECIFIED, DATABASE, EMERGENCY),
                number("123", "ru", UNSPECIFIED, DATABASE, EMERGENCY));
    }

    private static List<EmergencyNumber> argentinaNumbers() {
        return List.of(
                number("100", "ar", FIRE, DATABASE, UNKNOWN),
                number("101", "ar", POLICE, DATABASE, UNKNOWN),
                number("107", "ar", AMBULANCE, DATABASE, UNKNOWN),
                number("911", "ar", GENERAL, DATABASE, UNKNOWN));
    }

    private static EmergencyNumber number(String number, String country, int categories,
            int sources, int routing) {
        return new EmergencyNumber(number, country, "", categories, List.of(), sources, routing);
    }
}
