package com.android.phone;

import android.annotation.Nullable;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.android.internal.app.ContactScopes;
import com.android.internal.app.RedirectedContentProvider;

// IccProvider is a legacy SIM phonebook provider, SimPhonebookProvider is the modern one
public class IccProviderStub extends RedirectedContentProvider {

    @Override
    public boolean onCreate() {
        TAG = "IccProviderStub";
        authorityOverride = ContactScopes.ICC_PROVIDER_AUTHORITY;
        super.onCreate();
        return true;
    }

    // copied from com.android.internal.telephony.IccProvider
    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[] {
        "name",
        "number",
        "emails",
        "anrs",
        "_id"
    };

    @Override
    public Cursor queryInner(Uri uri, @Nullable String[] projection, @Nullable String selection,
                             @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // IccProvider ignores the requested projection
        return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, 0);
    }
}
