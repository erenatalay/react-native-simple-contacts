package com.simplecontacts

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.util.Log
import android.net.Uri
import android.os.AsyncTask

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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ContactsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(), PermissionListener {
    private val reactContext: ReactApplicationContext = reactContext
    private var permissionPromise: Promise? = null
    private val TAG = "ContactsModule"
    private val executor = Executors.newFixedThreadPool(2)
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val BATCH_SIZE = 500
    }

    override fun getName(): String {
        return "ContactsModule"
    }

    @ReactMethod
    fun getContacts(promise: Promise) {
        if (hasPermission()) {
            fetchContactsOptimized(promise)
        } else {
            promise.reject("permission_denied", "Contacts permission not granted")
        }
    }
    
    private fun fetchContactsOptimized(promise: Promise) {
        val startTime = System.currentTimeMillis()
        
        val contentResolver: ContentResolver = reactContext.contentResolver
        val contacts: WritableArray = Arguments.createArray()
        
        try {
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            
            var cursor: Cursor? = null
            val contactsMap = mutableMapOf<String, WritableMap>()
            
            try {
                val selection = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1"
                cursor = contentResolver.query(
                    uri,
                    projection,
                    selection,
                    null,
                    "${ContactsContract.Contacts.SORT_KEY_PRIMARY} ASC"
                )
                
                cursor?.let {
                    val totalContacts = it.count
                    Log.d(TAG, "Toplam $totalContacts kişi bulundu")
                    var processedCount = 0
                
                    while (it.moveToNext()) {
                        val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        val contact = createBasicContactMap(it)
                        contactsMap[contactId] = contact
                        processedCount++
                
                        if (processedCount % (BATCH_SIZE/5) == 0) {
                            Log.d(TAG, "$processedCount/$totalContacts kişi işlendi")
                        }
                    }
                            
                    val batchSize = 200 
                    val contactIds = contactsMap.keys.toList()
                        
                    contactIds.chunked(batchSize).forEach { batch ->
                        fetchStructuredNamesOptimized(batch, contactsMap, contentResolver)
                        fetchPhoneNumbersOptimized(batch, contactsMap, contentResolver)
                        fetchEmailsOptimized(batch, contactsMap, contentResolver)
                    }
                            

                    for (contact in contactsMap.values) {
                        contacts.pushMap(contact)
                    }
                            
                    val endTime = System.currentTimeMillis()
                    val duration = (endTime - startTime) / 1000.0
                    Log.d(TAG, "Rehber çekme tamamlandı: ${contacts.size()} kişi, $duration saniye sürdü")
                        

                    promise.resolve(contacts)
                } ?: run {
                    promise.reject("cursor_error", "Cursor is null")
                }
            } finally {
                cursor?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rehber çekme hatası: ${e.message}", e)
            promise.reject("fetch_error", "Could not fetch contacts: ${e.message}")
        }
    }

    private fun fetchStructuredNamesOptimized(contactIds: List<String>, contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactIds.isEmpty()) return
                        
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.CONTACT_ID} IN (${contactIds.joinToString(",")})"
        val selectionArgs = arrayOf(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    CommonDataKinds.StructuredName.FAMILY_NAME,
                    CommonDataKinds.StructuredName.GIVEN_NAME,
                    CommonDataKinds.StructuredName.MIDDLE_NAME,
                    CommonDataKinds.StructuredName.PREFIX,
                    CommonDataKinds.StructuredName.SUFFIX
                ),
                selection,
                selectionArgs,
                null
            )
            
            cursor?.let {
                val contactIdIndex = it.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                val familyNameIndex = it.getColumnIndexOrThrow(CommonDataKinds.StructuredName.FAMILY_NAME)
                val givenNameIndex = it.getColumnIndexOrThrow(CommonDataKinds.StructuredName.GIVEN_NAME)
                val middleNameIndex = it.getColumnIndexOrThrow(CommonDataKinds.StructuredName.MIDDLE_NAME)
                val prefixIndex = it.getColumnIndexOrThrow(CommonDataKinds.StructuredName.PREFIX)
                val suffixIndex = it.getColumnIndexOrThrow(CommonDataKinds.StructuredName.SUFFIX)
                
                while (it.moveToNext()) {
                    val contactId = it.getString(contactIdIndex)
                    val contact = contactsMap[contactId] ?: continue
                    
                    contact.putString("familyName", it.getString(familyNameIndex) ?: "")
                    contact.putString("givenName", it.getString(givenNameIndex) ?: "")
                    contact.putString("middleName", it.getString(middleNameIndex) ?: "")
                    contact.putString("prefix", it.getString(prefixIndex) ?: "")
                    contact.putString("suffix", it.getString(suffixIndex) ?: "")
                }
            }
        } finally {
            cursor?.close()
        }

    private fun fetchPhoneNumbersOptimized(contactIds: List<String>, contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactIds.isEmpty()) return
        

        val inClause = contactIds.joinToString(",")
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($inClause)"
        
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                selection,
                null,
                null
            )
            
            cursor?.let {
                val contactIdIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
      
                val phoneNumbersMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
                
                while (it.moveToNext()) {
                    val contactId = it.getString(contactIdIndex)
                    val number = it.getString(numberIndex) ?: ""
                    val type = it.getInt(typeIndex)
                    
                    val label = when (type) {
                        CommonDataKinds.Phone.TYPE_HOME -> "home"
                        CommonDataKinds.Phone.TYPE_WORK -> "work"
                        CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
                        else -> "other"
                    }
                    
                    if (!phoneNumbersMap.containsKey(contactId)) {
                        phoneNumbersMap[contactId] = mutableListOf()
                    }
                    phoneNumbersMap[contactId]?.add(Pair(label, number))
                }
                
        
                for ((contactId, phones) in phoneNumbersMap) {
                    val contact = contactsMap[contactId] ?: continue
                    val phoneNumbers = Arguments.createArray()
                    
                    for ((label, number) in phones) {
                        val phoneNumber = Arguments.createMap()
                        phoneNumber.putString("label", label)
                        phoneNumber.putString("number", number)
                        phoneNumbers.pushMap(phoneNumber)
                    }
                    
                    contact.putArray("phoneNumbers", phoneNumbers)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    

    private fun fetchEmailsOptimized(contactIds: List<String>, contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactIds.isEmpty()) return
                    

        val inClause = contactIds.joinToString(",")
        val selection = "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} IN ($inClause)"
        
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE
                ),
                selection,
                null,
                null
            )
            
            cursor?.let {
                val contactIdIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val addressIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val typeIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE)
                

                val emailMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
                
                while (it.moveToNext()) {
                    val contactId = it.getString(contactIdIndex)
                    val email = it.getString(addressIndex) ?: ""
                    val type = it.getInt(typeIndex)
                    
                    val label = when (type) {
                        CommonDataKinds.Email.TYPE_HOME -> "home"
                        CommonDataKinds.Email.TYPE_WORK -> "work"
                        else -> "other"
                    }
                    
                    if (!emailMap.containsKey(contactId)) {
                        emailMap[contactId] = mutableListOf()
                    }
                    emailMap[contactId]?.add(Pair(label, email))
                }
                
                for ((contactId, emails) in emailMap) {
                    val contact = contactsMap[contactId] ?: continue
                    val emailAddresses = Arguments.createArray()
                    
                    for ((label, email) in emails) {
                        val emailAddress = Arguments.createMap()
                        emailAddress.putString("label", label)
                        emailAddress.putString("email", email)
                        emailAddresses.pushMap(emailAddress)
                    }
                    
                    contact.putArray("emailAddresses", emailAddresses)
                }
            }
        } finally {
            cursor?.close()
        }
    }
                
    private fun createBasicContactMap(cursor: Cursor): WritableMap {
        val contact = Arguments.createMap()
        
        val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
        val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
        val photoUri = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI))
        val thumbnailUri = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI))
        val starred = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
        
        contact.putString("recordID", contactId)
        contact.putString("lookupKey", lookupKey ?: "")
        contact.putString("displayName", displayName ?: "")
        contact.putBoolean("hasThumbnail", photoUri != null || thumbnailUri != null)
        contact.putString("thumbnailPath", thumbnailUri ?: "")
        contact.putString("photoUri", photoUri ?: "")
        contact.putBoolean("isStarred", starred)
        
        contact.putArray("phoneNumbers", Arguments.createArray())
        contact.putArray("emailAddresses", Arguments.createArray())
        
        contact.putString("familyName", "")
        contact.putString("givenName", "")
        contact.putString("middleName", "")
        contact.putString("jobTitle", "")
        contact.putString("company", "")
        contact.putString("department", "")
        contact.putString("note", "")
        contact.putString("prefix", "")
        contact.putString("suffix", "")
        
        contact.putMap("birthday", Arguments.createMap())
        
        return contact
    }
                    
    private fun enrichContactDetails(contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactsMap.isEmpty()) return
                    
        val contactIds = contactsMap.keys.toList()
        val batchSize = 100 
                    
        for (i in contactIds.indices step batchSize) {
            val endIndex = minOf(i + batchSize, contactIds.size)
            val batch = contactIds.subList(i, endIndex)
            fetchStructuredNames(batch, contactsMap, contentResolver)
        }
                    
        for (i in contactIds.indices step batchSize) {
            val endIndex = minOf(i + batchSize, contactIds.size)
            val batch = contactIds.subList(i, endIndex)
            fetchPhoneNumbers(batch, contactsMap, contentResolver)
        }
                        
        for (i in contactIds.indices step batchSize) {
            val endIndex = minOf(i + batchSize, contactIds.size)
            val batch = contactIds.subList(i, endIndex)
            fetchEmails(batch, contactsMap, contentResolver)
        }
    }
                    
    private fun fetchStructuredNames(contactIds: List<String>, contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactIds.isEmpty()) return
                    
        val selection = StringBuilder("${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.CONTACT_ID} IN (")
        selection.append(contactIds.joinToString(separator = ",") { "?" })
        selection.append(")")
                            
        val selectionArgs = arrayOf(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) + contactIds.toTypedArray()
                    
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                null,
                selection.toString(),
                selectionArgs,
                null
            )
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID))
                    val contact = contactsMap[contactId] ?: continue
                    
                    val familyName = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.FAMILY_NAME)) ?: ""
                    val givenName = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.GIVEN_NAME)) ?: ""
                    val middleName = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.MIDDLE_NAME)) ?: ""
                    val prefix = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.PREFIX)) ?: ""
                    val suffix = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.SUFFIX)) ?: ""
                    
                    contact.putString("familyName", familyName)
                    contact.putString("givenName", givenName)
                    contact.putString("middleName", middleName)
                    contact.putString("prefix", prefix)
                    contact.putString("suffix", suffix)
                }
            }
        } finally {
            cursor?.close()
        }
    }
                            
    private fun fetchPhoneNumbers(contactIds: List<String>, contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactIds.isEmpty()) return
        
        val selection = StringBuilder("${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (")
        selection.append(contactIds.joinToString(separator = ",") { "?" })
        selection.append(")")
        
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                selection.toString(),
                contactIds.toTypedArray(),
                null
            )
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                    val contact = contactsMap[contactId] ?: continue
                    
                    val phoneNumbers = contact.getArray("phoneNumbers")
                    val phoneNumber = Arguments.createMap()
                    
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                    val label: String

                    when (type) {
                        CommonDataKinds.Phone.TYPE_HOME -> label = "home"
                        CommonDataKinds.Phone.TYPE_WORK -> label = "work"
                        CommonDataKinds.Phone.TYPE_MOBILE -> label = "mobile"
                        else -> label = "other"
                    }
                    
                    phoneNumber.putString("label", label)
                    phoneNumber.putString("number", number)
                            
                    val updatedPhoneNumbers = Arguments.createArray()
                    if (phoneNumbers != null) {
                        for (i in 0 until phoneNumbers.size()) {
                            phoneNumbers.getMap(i)?.let { updatedPhoneNumbers.pushMap(it) }
                        }
                    }
                    updatedPhoneNumbers.pushMap(phoneNumber)
                    
                    contact.putArray("phoneNumbers", updatedPhoneNumbers)
                }
            }
        } finally {
            cursor?.close()
        }
    }
                    
    private fun fetchEmails(contactIds: List<String>, contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactIds.isEmpty()) return
                    
        val selection = StringBuilder("${ContactsContract.CommonDataKinds.Email.CONTACT_ID} IN (")
        selection.append(contactIds.joinToString(separator = ",") { "?" })
        selection.append(")")
                            
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                null,
                selection.toString(),
                contactIds.toTypedArray(),
                null
            )
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID))
                    val contact = contactsMap[contactId] ?: continue
                    
                    val emailAddresses = contact.getArray("emailAddresses")
                    val emailAddress = Arguments.createMap()
                    
                    val email = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE))
                    val label: String

                    when (type) {
                        CommonDataKinds.Email.TYPE_HOME -> label = "home"
                        CommonDataKinds.Email.TYPE_WORK -> label = "work"
                        else -> label = "other"
                    }
                    
                    emailAddress.putString("label", label)
                    emailAddress.putString("email", email)
                    
                    val updatedEmailAddresses = Arguments.createArray()
                    if (emailAddresses != null) {
                        for (i in 0 until emailAddresses.size()) {
                            emailAddresses.getMap(i)?.let { updatedEmailAddresses.pushMap(it) }
                        }
                    }
                    updatedEmailAddresses.pushMap(emailAddress)
                    
                    contact.putArray("emailAddresses", updatedEmailAddresses)
                }
            }
        } finally {
            cursor?.close()
        }
    }

    @ReactMethod
    fun checkPermission(promise: Promise) {
        try {
            val permissionStatus = when {
                PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                    reactContext,
                    Manifest.permission.READ_CONTACTS
                ) -> "granted"
                ActivityCompat.shouldShowRequestPermissionRationale(
                    reactContext.currentActivity!!,
                    Manifest.permission.READ_CONTACTS
                ) -> "denied"
                else -> "undetermined"  
            }
            promise.resolve(permissionStatus)
        } catch (e: Exception) {
            promise.reject("check_permission_error", e.message)
        }
    }

    @ReactMethod
    fun requestPermission(promise: Promise) {
        this.permissionPromise = promise
        
        if (hasPermission()) {
            promise.resolve("granted")  
            return
        }
        
        val activity = reactContext.currentActivity
        if (activity == null) {
            promise.reject("activity_error", "Activity is null")
            return
        }
        
        if (activity is PermissionAwareActivity) {
            try {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    PERMISSION_REQUEST_CODE,
                    this
                )
            } catch (e: Exception) {
                promise.reject("request_permission_error", e.message)
            }
        } else {
            promise.reject("activity_error", "Activity is not PermissionAwareActivity")
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
                permissionPromise!!.resolve("granted")  
            } else {
                permissionPromise!!.resolve("denied")
            }
            permissionPromise = null
            return true
        }
        return false
    }
}
