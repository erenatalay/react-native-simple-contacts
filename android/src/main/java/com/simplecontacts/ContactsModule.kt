package com.simplecontacts;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ContactsModule extends ReactContextBaseJavaModule implements PermissionListener {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private ReactApplicationContext reactContext;
    private Promise permissionPromise;

    public ContactsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "ContactsModule";
    }

    @ReactMethod
    public void getContacts(Promise promise) {
        if (hasPermission()) {
            try {
                ContentResolver contentResolver = reactContext.getContentResolver();
                WritableArray contacts = Arguments.createArray();
                
                // Contact IDs Map to avoid duplicates
                Map<String, WritableMap> contactsMap = new HashMap<>();
                
                // Get contacts
                Cursor cursor = contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        null,
                        null,
                        null,
                        ContactsContract.Contacts.DISPLAY_NAME + " ASC"
                );
                
                if (cursor != null && cursor.getCount() > 0) {
                    while (cursor.moveToNext()) {
                        String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                        
                        if (!contactsMap.containsKey(contactId)) {
                            WritableMap contact = Arguments.createMap();
                            
                            // Basic info
                            contact.putString("recordID", contactId);
                            contact.putString("backTitle", "");
                            
                            String displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                            contact.putString("displayName", displayName != null ? displayName : "");
                            
                            // Initialize arrays
                            contact.putArray("emailAddresses", Arguments.createArray());
                            contact.putArray("phoneNumbers", Arguments.createArray());
                            contact.putArray("postalAddresses", Arguments.createArray());
                            contact.putArray("imAddresses", Arguments.createArray());
                            contact.putArray("urlAddresses", Arguments.createArray());
                            
                            // Default values for required fields
                            contact.putString("familyName", "");
                            contact.putString("givenName", "");
                            contact.putString("middleName", "");
                            contact.putString("jobTitle", "");
                            contact.putString("company", "");
                            contact.putBoolean("hasThumbnail", false);
                            contact.putString("thumbnailPath", "");
                            contact.putBoolean("isStarred", false);
                            contact.putString("prefix", "");
                            contact.putString("suffix", "");
                            contact.putString("department", "");
                            contact.putString("note", "");
                            
                            // Birthday (empty)
                            WritableMap birthday = Arguments.createMap();
                            contact.putMap("birthday", birthday);
                            
                            contactsMap.put(contactId, contact);
                        }
                    }
                    cursor.close();
                }
                
                // Get more details for each contact
                for (String contactId : contactsMap.keySet()) {
                    WritableMap contact = contactsMap.get(contactId);
                    
                    // Names
                    Cursor nameCursor = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            new String[]{contactId, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE},
                            null
                    );
                    
                    if (nameCursor != null && nameCursor.moveToFirst()) {
                        String familyName = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME));
                        String givenName = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME));
                        String middleName = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME));
                        String prefix = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX));
                        String suffix = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX));
                        
                        contact.putString("familyName", familyName != null ? familyName : "");
                        contact.putString("givenName", givenName != null ? givenName : "");
                        contact.putString("middleName", middleName != null ? middleName : "");
                        contact.putString("prefix", prefix != null ? prefix : "");
                        contact.putString("suffix", suffix != null ? suffix : "");
                        nameCursor.close();
                    }
                    
                    // Organization
                    Cursor orgCursor = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            new String[]{contactId, CommonDataKinds.Organization.CONTENT_ITEM_TYPE},
                            null
                    );
                    
                    if (orgCursor != null && orgCursor.moveToFirst()) {
                        String company = orgCursor.getString(orgCursor.getColumnIndex(CommonDataKinds.Organization.COMPANY));
                        String department = orgCursor.getString(orgCursor.getColumnIndex(CommonDataKinds.Organization.DEPARTMENT));
                        String title = orgCursor.getString(orgCursor.getColumnIndex(CommonDataKinds.Organization.TITLE));
                        
                        contact.putString("company", company != null ? company : "");
                        contact.putString("department", department != null ? department : "");
                        contact.putString("jobTitle", title != null ? title : "");
                        orgCursor.close();
                    }
                    
                    // Phone Numbers
                    Cursor phoneCursor = contentResolver.query(
                            CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{contactId},
                            null
                    );
                    
                    WritableArray phoneNumbers = Arguments.createArray();
                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            WritableMap phoneNumber = Arguments.createMap();
                            String number = phoneCursor.getString(phoneCursor.getColumnIndex(CommonDataKinds.Phone.NUMBER));
                            int type = phoneCursor.getInt(phoneCursor.getColumnIndex(CommonDataKinds.Phone.TYPE));
                            String label;
                            
                            switch (type) {
                                case CommonDataKinds.Phone.TYPE_HOME:
                                    label = "home";
                                    break;
                                case CommonDataKinds.Phone.TYPE_WORK:
                                    label = "work";
                                    break;
                                case CommonDataKinds.Phone.TYPE_MOBILE:
                                    label = "mobile";
                                    break;
                                default:
                                    label = "other";
                            }
                            
                            phoneNumber.putString("label", label);
                            phoneNumber.putString("number", number);
                            phoneNumbers.pushMap(phoneNumber);
                        }
                        phoneCursor.close();
                    }
                    contact.putArray("phoneNumbers", phoneNumbers);
                    
                    // Email Addresses
                    Cursor emailCursor = contentResolver.query(
                            CommonDataKinds.Email.CONTENT_URI,
                            null,
                            CommonDataKinds.Email.CONTACT_ID + " = ?",
                            new String[]{contactId},
                            null
                    );
                    
                    WritableArray emailAddresses = Arguments.createArray();
                    if (emailCursor != null) {
                        while (emailCursor.moveToNext()) {
                            WritableMap emailAddress = Arguments.createMap();
                            String email = emailCursor.getString(emailCursor.getColumnIndex(CommonDataKinds.Email.ADDRESS));
                            int type = emailCursor.getInt(emailCursor.getColumnIndex(CommonDataKinds.Email.TYPE));
                            String label;
                            
                            switch (type) {
                                case CommonDataKinds.Email.TYPE_HOME:
                                    label = "home";
                                    break;
                                case CommonDataKinds.Email.TYPE_WORK:
                                    label = "work";
                                    break;
                                default:
                                    label = "other";
                            }
                            
                            emailAddress.putString("label", label);
                            emailAddress.putString("email", email);
                            emailAddresses.pushMap(emailAddress);
                        }
                        emailCursor.close();
                    }
                    contact.putArray("emailAddresses", emailAddresses);
                    
                    // Postal Addresses
                    Cursor addressCursor = contentResolver.query(
                            CommonDataKinds.StructuredPostal.CONTENT_URI,
                            null,
                            CommonDataKinds.StructuredPostal.CONTACT_ID + " = ?",
                            new String[]{contactId},
                            null
                    );
                    
                    WritableArray postalAddresses = Arguments.createArray();
                    if (addressCursor != null) {
                        while (addressCursor.moveToNext()) {
                            WritableMap postalAddress = Arguments.createMap();
                            String street = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.STREET));
                            String city = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.CITY));
                            String state = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.REGION));
                            String postCode = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.POSTCODE));
                            String country = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.COUNTRY));
                            int type = addressCursor.getInt(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.TYPE));
                            String label;
                            
                            switch (type) {
                                case CommonDataKinds.StructuredPostal.TYPE_HOME:
                                    label = "home";
                                    break;
                                case CommonDataKinds.StructuredPostal.TYPE_WORK:
                                    label = "work";
                                    break;
                                default:
                                    label = "other";
                            }
                            
                            postalAddress.putString("label", label);
                            postalAddress.putString("street", street != null ? street : "");
                            postalAddress.putString("city", city != null ? city : "");
                            postalAddress.putString("state", state != null ? state : "");
                            postalAddress.putString("postCode", postCode != null ? postCode : "");
                            postalAddress.putString("country", country != null ? country : "");
                            postalAddresses.pushMap(postalAddress);
                        }
                        addressCursor.close();
                    }
                    contact.putArray("postalAddresses", postalAddresses);
                    
                    // Note
                    Cursor noteCursor = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            new String[]{contactId, CommonDataKinds.Note.CONTENT_ITEM_TYPE},
                            null
                    );
                    
                    if (noteCursor != null && noteCursor.moveToFirst()) {
                        String note = noteCursor.getString(noteCursor.getColumnIndex(CommonDataKinds.Note.NOTE));
                        contact.putString("note", note != null ? note : "");
                        noteCursor.close();
                    }
                    
                    // IM addresses
                    Cursor imCursor = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            new String[]{contactId, CommonDataKinds.Im.CONTENT_ITEM_TYPE},
                            null
                    );
                    
                    WritableArray imAddresses = Arguments.createArray();
                    if (imCursor != null) {
                        while (imCursor.moveToNext()) {
                            WritableMap imAddress = Arguments.createMap();
                            String username = imCursor.getString(imCursor.getColumnIndex(CommonDataKinds.Im.DATA));
                            int protocolType = imCursor.getInt(imCursor.getColumnIndex(CommonDataKinds.Im.PROTOCOL));
                            String service;
                            
                            switch (protocolType) {
                                case CommonDataKinds.Im.PROTOCOL_AIM:
                                    service = "AIM";
                                    break;
                                case CommonDataKinds.Im.PROTOCOL_MSN:
                                    service = "MSN";
                                    break;
                                case CommonDataKinds.Im.PROTOCOL_YAHOO:
                                    service = "Yahoo";
                                    break;
                                case CommonDataKinds.Im.PROTOCOL_SKYPE:
                                    service = "Skype";
                                    break;
                                case CommonDataKinds.Im.PROTOCOL_QQ:
                                    service = "QQ";
                                    break;
                                case CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK:
                                    service = "Google Talk";
                                    break;
                                case CommonDataKinds.Im.PROTOCOL_ICQ:
                                    service = "ICQ";
                                    break;
                                case CommonDataKinds.Im.PROTOCOL_JABBER:
                                    service = "Jabber";
                                    break;
                                default:
                                    service = "Other";
                            }
                            
                            imAddress.putString("service", service);
                            imAddress.putString("username", username != null ? username : "");
                            imAddresses.pushMap(imAddress);
                        }
                        imCursor.close();
                    }
                    contact.putArray("imAddresses", imAddresses);
                    
                    // Birthday
                    Cursor bdayCursor = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            new String[]{contactId, CommonDataKinds.Event.CONTENT_ITEM_TYPE},
                            null
                    );
                    
                    WritableMap birthday = Arguments.createMap();
                    if (bdayCursor != null) {
                        while (bdayCursor.moveToNext()) {
                            int type = bdayCursor.getInt(bdayCursor.getColumnIndex(CommonDataKinds.Event.TYPE));
                            if (type == CommonDataKinds.Event.TYPE_BIRTHDAY) {
                                String startDate = bdayCursor.getString(bdayCursor.getColumnIndex(CommonDataKinds.Event.START_DATE));
                                if (startDate != null) {
                                    String[] parts = startDate.split("-");
                                    if (parts.length >= 3) {
                                        try {
                                            int year = Integer.parseInt(parts[0]);
                                            int month = Integer.parseInt(parts[1]);
                                            int day = Integer.parseInt(parts[2]);
                                            
                                            birthday.putInt("year", year);
                                            birthday.putInt("month", month);
                                            birthday.putInt("day", day);
                                        } catch (NumberFormatException e) {
                                            // Ignore parsing errors
                                        }
                                    }
                                }
                                break;
                            }
                        }
                        bdayCursor.close();
                    }
                    contact.putMap("birthday", birthday);
                    
                    // Add to contacts array
                    contacts.pushMap(contact);
                }
                
                promise.resolve(contacts);
            } catch (Exception e) {
                promise.reject("fetch_error", "Could not fetch contacts: " + e.getMessage());
            }
        } else {
            promise.reject("permission_denied", "Contacts permission not granted");
        }
    }

    @ReactMethod
    public void checkPermission(Promise promise) {
        promise.resolve(hasPermission());
    }

    @ReactMethod
    public void requestPermission(Promise promise) {
        this.permissionPromise = promise;
        
        if (hasPermission()) {
            promise.resolve(true);
            return;
        }
        
        if (getCurrentActivity() != null) {
            try {
                ((PermissionAwareActivity) getCurrentActivity()).requestPermissions(
                        new String[]{Manifest.permission.READ_CONTACTS},
                        PERMISSION_REQUEST_CODE,
                        this
                );
            } catch (Exception e) {
                promise.reject("permission_error", "Error requesting permission: " + e.getMessage());
            }
        } else {
            promise.reject("activity_null", "Activity is null");
        }
    }

    private boolean hasPermission() {
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                reactContext,
                Manifest.permission.READ_CONTACTS
        );
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE && permissionPromise != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionPromise.resolve(true);
            } else {
                permissionPromise.resolve(false);
            }
            permissionPromise = null;
            return true;
        }
        return false;
    }
}