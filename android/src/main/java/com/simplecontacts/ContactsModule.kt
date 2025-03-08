package com.simplecontacts

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener

import java.util.ArrayList
import java.util.HashMap
import java.util.Map

class ContactsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(), PermissionListener {
    private val reactContext: ReactApplicationContext = reactContext
    private var permissionPromise: Promise? = null
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }

    override fun getName(): String {
        return "ContactsModule"
    }

    @ReactMethod
    fun getContacts(promise: Promise) {
        if (hasPermission()) {
            try {
                val contentResolver: ContentResolver = reactContext.contentResolver
                val contacts: WritableArray = Arguments.createArray()
                
                // Contact IDs Map to avoid duplicates
                val contactsMap: MutableMap<String, WritableMap> = HashMap()
                
                // Get contacts
                val cursor: Cursor? = contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        null,
                        null,
                        null,
                        ContactsContract.Contacts.DISPLAY_NAME + " ASC"
                )
                
                if (cursor != null && cursor.count > 0) {
                    while (cursor.moveToNext()) {
                        val contactId: String = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                        
                        if (!contactsMap.containsKey(contactId)) {
                            val contact: WritableMap = Arguments.createMap()
                            
                            // Basic info
                            contact.putString("recordID", contactId)
                            contact.putString("backTitle", "")
                            
                            val displayName: String? = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                            contact.putString("displayName", displayName ?: "")
                            
                            // Initialize arrays
                            contact.putArray("emailAddresses", Arguments.createArray())
                            contact.putArray("phoneNumbers", Arguments.createArray())
                            contact.putArray("postalAddresses", Arguments.createArray())
                            contact.putArray("imAddresses", Arguments.createArray())
                            contact.putArray("urlAddresses", Arguments.createArray())
                            
                            // Default values for required fields
                            contact.putString("familyName", "")
                            contact.putString("givenName", "")
                            contact.putString("middleName", "")
                            contact.putString("jobTitle", "")
                            contact.putString("company", "")
                            contact.putBoolean("hasThumbnail", false)
                            contact.putString("thumbnailPath", "")
                            contact.putBoolean("isStarred", false)
                            contact.putString("prefix", "")
                            contact.putString("suffix", "")
                            contact.putString("department", "")
                            contact.putString("note", "")
                            
                            // Birthday (empty)
                            val birthday: WritableMap = Arguments.createMap()
                            contact.putMap("birthday", birthday)
                            
                            contactsMap[contactId] = contact
                        }
                    }
                    cursor.close()
                }
                
                // Get more details for each contact
                for (contactId in contactsMap.keys) {
                    val contact: WritableMap? = contactsMap[contactId]
                    
                    // Names
                    val nameCursor: Cursor? = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            arrayOf(contactId, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                            null
                    )
                    
                    if (nameCursor != null && nameCursor.moveToFirst()) {
                        val familyName: String? = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME))
                        val givenName: String? = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME))
                        val middleName: String? = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME))
                        val prefix: String? = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX))
                        val suffix: String? = nameCursor.getString(nameCursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX))
                        
                        contact?.putString("familyName", familyName ?: "")
                        contact?.putString("givenName", givenName ?: "")
                        contact?.putString("middleName", middleName ?: "")
                        contact?.putString("prefix", prefix ?: "")
                        contact?.putString("suffix", suffix ?: "")
                        nameCursor.close()
                    }
                    
                    // Organization
                    val orgCursor: Cursor? = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            arrayOf(contactId, CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
                            null
                    )
                    
                    if (orgCursor != null && orgCursor.moveToFirst()) {
                        val company: String? = orgCursor.getString(orgCursor.getColumnIndex(CommonDataKinds.Organization.COMPANY))
                        val department: String? = orgCursor.getString(orgCursor.getColumnIndex(CommonDataKinds.Organization.DEPARTMENT))
                        val title: String? = orgCursor.getString(orgCursor.getColumnIndex(CommonDataKinds.Organization.TITLE))
                        
                        contact?.putString("company", company ?: "")
                        contact?.putString("department", department ?: "")
                        contact?.putString("jobTitle", title ?: "")
                        orgCursor.close()
                    }
                    
                    // Phone Numbers
                    val phoneCursor: Cursor? = contentResolver.query(
                            CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(contactId),
                            null
                    )
                    
                    val phoneNumbers: WritableArray = Arguments.createArray()
                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            val phoneNumber: WritableMap = Arguments.createMap()
                            val number: String = phoneCursor.getString(phoneCursor.getColumnIndex(CommonDataKinds.Phone.NUMBER))
                            val type: Int = phoneCursor.getInt(phoneCursor.getColumnIndex(CommonDataKinds.Phone.TYPE))
                            val label: String
                            
                            when (type) {
                                CommonDataKinds.Phone.TYPE_HOME -> label = "home"
                                CommonDataKinds.Phone.TYPE_WORK -> label = "work"
                                CommonDataKinds.Phone.TYPE_MOBILE -> label = "mobile"
                                else -> label = "other"
                            }
                            
                            phoneNumber.putString("label", label)
                            phoneNumber.putString("number", number)
                            phoneNumbers.pushMap(phoneNumber)
                        }
                        phoneCursor.close()
                    }
                    contact?.putArray("phoneNumbers", phoneNumbers)
                    
                    // Email Addresses
                    val emailCursor: Cursor? = contentResolver.query(
                            CommonDataKinds.Email.CONTENT_URI,
                            null,
                            CommonDataKinds.Email.CONTACT_ID + " = ?",
                            arrayOf(contactId),
                            null
                    )
                    
                    val emailAddresses: WritableArray = Arguments.createArray()
                    if (emailCursor != null) {
                        while (emailCursor.moveToNext()) {
                            val emailAddress: WritableMap = Arguments.createMap()
                            val email: String = emailCursor.getString(emailCursor.getColumnIndex(CommonDataKinds.Email.ADDRESS))
                            val type: Int = emailCursor.getInt(emailCursor.getColumnIndex(CommonDataKinds.Email.TYPE))
                            val label: String
                            
                            when (type) {
                                CommonDataKinds.Email.TYPE_HOME -> label = "home"
                                CommonDataKinds.Email.TYPE_WORK -> label = "work"
                                else -> label = "other"
                            }
                            
                            emailAddress.putString("label", label)
                            emailAddress.putString("email", email)
                            emailAddresses.pushMap(emailAddress)
                        }
                        emailCursor.close()
                    }
                    contact?.putArray("emailAddresses", emailAddresses)
                    
                    // Postal Addresses
                    val addressCursor: Cursor? = contentResolver.query(
                            CommonDataKinds.StructuredPostal.CONTENT_URI,
                            null,
                            CommonDataKinds.StructuredPostal.CONTACT_ID + " = ?",
                            arrayOf(contactId),
                            null
                    )
                    
                    val postalAddresses: WritableArray = Arguments.createArray()
                    if (addressCursor != null) {
                        while (addressCursor.moveToNext()) {
                            val postalAddress: WritableMap = Arguments.createMap()
                            val street: String? = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.STREET))
                            val city: String? = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.CITY))
                            val state: String? = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.REGION))
                            val postCode: String? = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.POSTCODE))
                            val country: String? = addressCursor.getString(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.COUNTRY))
                            val type: Int = addressCursor.getInt(addressCursor.getColumnIndex(CommonDataKinds.StructuredPostal.TYPE))
                            val label: String
                            
                            when (type) {
                                CommonDataKinds.StructuredPostal.TYPE_HOME -> label = "home"
                                CommonDataKinds.StructuredPostal.TYPE_WORK -> label = "work"
                                else -> label = "other"
                            }
                            
                            postalAddress.putString("label", label)
                            postalAddress.putString("street", street ?: "")
                            postalAddress.putString("city", city ?: "")
                            postalAddress.putString("state", state ?: "")
                            postalAddress.putString("postCode", postCode ?: "")
                            postalAddress.putString("country", country ?: "")
                            postalAddresses.pushMap(postalAddress)
                        }
                        addressCursor.close()
                    }
                    contact?.putArray("postalAddresses", postalAddresses)
                    
                    // Note
                    val noteCursor: Cursor? = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            arrayOf(contactId, CommonDataKinds.Note.CONTENT_ITEM_TYPE),
                            null
                    )
                    
                    if (noteCursor != null && noteCursor.moveToFirst()) {
                        val note: String? = noteCursor.getString(noteCursor.getColumnIndex(CommonDataKinds.Note.NOTE))
                        contact?.putString("note", note ?: "")
                        noteCursor.close()
                    }
                    
                    // IM addresses
                    val imCursor: Cursor? = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            arrayOf(contactId, CommonDataKinds.Im.CONTENT_ITEM_TYPE),
                            null
                    )
                    
                    val imAddresses: WritableArray = Arguments.createArray()
                    if (imCursor != null) {
                        while (imCursor.moveToNext()) {
                            val imAddress: WritableMap = Arguments.createMap()
                            val username: String? = imCursor.getString(imCursor.getColumnIndex(CommonDataKinds.Im.DATA))
                            val protocolType: Int = imCursor.getInt(imCursor.getColumnIndex(CommonDataKinds.Im.PROTOCOL))
                            val service: String
                            
                            when (protocolType) {
                                CommonDataKinds.Im.PROTOCOL_AIM -> service = "AIM"
                                CommonDataKinds.Im.PROTOCOL_MSN -> service = "MSN"
                                CommonDataKinds.Im.PROTOCOL_YAHOO -> service = "Yahoo"
                                CommonDataKinds.Im.PROTOCOL_SKYPE -> service = "Skype"
                                CommonDataKinds.Im.PROTOCOL_QQ -> service = "QQ"
                                CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK -> service = "Google Talk"
                                CommonDataKinds.Im.PROTOCOL_ICQ -> service = "ICQ"
                                CommonDataKinds.Im.PROTOCOL_JABBER -> service = "Jabber"
                                else -> service = "Other"
                            }
                            
                            imAddress.putString("service", service)
                            imAddress.putString("username", username ?: "")
                            imAddresses.pushMap(imAddress)
                        }
                        imCursor.close()
                    }
                    contact?.putArray("imAddresses", imAddresses)
                    
                    // Birthday
                    val bdayCursor: Cursor? = contentResolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                                    ContactsContract.Data.MIMETYPE + " = ?",
                            arrayOf(contactId, CommonDataKinds.Event.CONTENT_ITEM_TYPE),
                            null
                    )
                    
                    val birthday: WritableMap = Arguments.createMap()
                    if (bdayCursor != null) {
                        while (bdayCursor.moveToNext()) {
                            val type: Int = bdayCursor.getInt(bdayCursor.getColumnIndex(CommonDataKinds.Event.TYPE))
                            if (type == CommonDataKinds.Event.TYPE_BIRTHDAY) {
                                val startDate: String? = bdayCursor.getString(bdayCursor.getColumnIndex(CommonDataKinds.Event.START_DATE))
                                if (startDate != null) {
                                    val parts: Array<String> = startDate.split("-").toTypedArray()
                                    if (parts.size >= 3) {
                                        try {
                                            val year: Int = parts[0].toInt()
                                            val month: Int = parts[1].toInt()
                                            val day: Int = parts[2].toInt()
                                            
                                            birthday.putInt("year", year)
                                            birthday.putInt("month", month)
                                            birthday.putInt("day", day)
                                        } catch (e: NumberFormatException) {
                                            // Ignore parsing errors
                                        }
                                    }
                                }
                                break
                            }
                        }
                        bdayCursor.close()
                    }
                    contact?.putMap("birthday", birthday)
                    
                    // Add to contacts array
                    contacts.pushMap(contact)
                }
                
                promise.resolve(contacts)
            } catch (e: Exception) {
                promise.reject("fetch_error", "Could not fetch contacts: " + e.message)
            }
        } else {
            promise.reject("permission_denied", "Contacts permission not granted")
        }
    }

    @ReactMethod
    fun checkPermission(promise: Promise) {
        promise.resolve(hasPermission())
    }

    @ReactMethod
    fun requestPermission(promise: Promise) {
        this.permissionPromise = promise
        
        if (hasPermission()) {
            promise.resolve(true)
            return
        }
        
        if (currentActivity != null) {
            try {
                (currentActivity as PermissionAwareActivity).requestPermissions(
                        arrayOf(Manifest.permission.READ_CONTACTS),
                        PERMISSION_REQUEST_CODE,
                        this
                )
            } catch (e: Exception) {
                promise.reject("permission_error", "Error requesting permission: " + e.message)
            }
        } else {
            promise.reject("activity_null", "Activity is null")
        }
    }

    private fun hasPermission(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                reactContext,
                Manifest.permission.READ_CONTACTS
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE && permissionPromise != null) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionPromise!!.resolve(true)
            } else {
                permissionPromise!!.resolve(false)
            }
            permissionPromise = null
            return true
        }
        return false
    }
}