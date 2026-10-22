package com.android.phone;

import android.telecom.PhoneAccountHandle;
import android.telephony.emergency.EmergencyNumber;

import java.lang.reflect.Constructor;
import java.util.List;

final class ShortcutViewTestUtils {
    private ShortcutViewTestUtils() {}

    static ShortcutViewUtils.PhoneInfo createPhoneInfo(int subId, String country,
            List<EmergencyNumber> numbers) {
        // Dexmaker cannot subclass PhoneInfo across class loaders. Use the real data object.
        try {
            Constructor<ShortcutViewUtils.PhoneInfo> constructor =
                    ShortcutViewUtils.PhoneInfo.class.getDeclaredConstructor(
                            PhoneAccountHandle.class, boolean.class, int.class, String.class,
                            List.class);
            constructor.setAccessible(true);
            return constructor.newInstance(null, true, subId, country, numbers);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
