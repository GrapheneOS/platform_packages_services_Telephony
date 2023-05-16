package com.android.phone;

import android.annotation.Nullable;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.SimPhonebookContract;

import com.android.internal.app.RedirectedContentProvider;

import static com.android.phone.SimPhonebookProvider.ELEMENTARY_FILES;
import static com.android.phone.SimPhonebookProvider.ELEMENTARY_FILES_ALL_COLUMNS;
import static com.android.phone.SimPhonebookProvider.ELEMENTARY_FILES_ITEM;
import static com.android.phone.SimPhonebookProvider.SIM_RECORDS;
import static com.android.phone.SimPhonebookProvider.SIM_RECORDS_ALL_COLUMNS;
import static com.android.phone.SimPhonebookProvider.SIM_RECORDS_ITEM;

public class SimPhonebookProviderStub extends RedirectedContentProvider {
    @Override
    public boolean onCreate() {
        TAG = "SimPBProviderStub";
        authorityOverride = SimPhonebookContract.AUTHORITY;
        super.onCreate();
        return true;
    }

    @Override
    public Cursor queryInner(Uri uri, @Nullable String[] projection, @Nullable String selection,
         @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (projection == null) {
            switch (SimPhonebookProvider.URI_MATCHER.match(uri)) {
                case ELEMENTARY_FILES:
                case ELEMENTARY_FILES_ITEM:
                    projection = ELEMENTARY_FILES_ALL_COLUMNS;
                    break;
                case SIM_RECORDS:
                case SIM_RECORDS_ITEM:
                    projection = SIM_RECORDS_ALL_COLUMNS;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported Uri " + uri);
            }
        }

        return new MatrixCursor(projection, 0);
    }
}
