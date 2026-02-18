package com.wmods.wppenhacer.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public class ContactHelper {

    public static String getContactName(Context context, String jid) {
        if (jid == null) return null;
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        String phoneNumber = jid.replace("@s.whatsapp.net", "").replace("@g.us", "");
        if (phoneNumber.contains("@")) phoneNumber = phoneNumber.split("@")[0];

        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
