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
        
        executor.execute {
            try {
                val contentResolver: ContentResolver = reactContext.contentResolver
                val contacts: WritableArray = Arguments.createArray()
                
                val projection = arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                )
                
                val contactsUri = ContactsContract.Contacts.CONTENT_URI
                var cursor: Cursor? = null
                
                try {
                    cursor = contentResolver.query(
                        contactsUri,
                        projection,
                        "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
                        null,
                        "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
                    )
                
                    if (cursor != null) {
                        val totalContacts = cursor.count
                        Log.d(TAG, "Toplam $totalContacts kişi bulundu")
                            
                        val contactsMap = mutableMapOf<String, WritableMap>()
                        var processedCount = 0
                        
                        while (cursor.moveToNext()) {
                            val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                            val contact = createBasicContactMap(cursor)
                            contactsMap[contactId] = contact
                            
                            processedCount++
                            
                            if (processedCount % BATCH_SIZE == 0) {
                                Log.d(TAG, "$processedCount/$totalContacts kişi işlendi")
                            }
                        }
                        
                        enrichContactDetails(contactsMap, contentResolver)
                        
                        for (contact in contactsMap.values) {
                            contacts.pushMap(contact)
                        }
                        
                        val endTime = System.currentTimeMillis()
                        val duration = (endTime - startTime) / 1000.0
                        Log.d(TAG, "Rehber çekme tamamlandı: ${contacts.size()} kişi, $duration saniye sürdü")
                        
                        reactContext.runOnUiQueueThread {
                            promise.resolve(contacts)
                        }
                    } else {
                        reactContext.runOnUiQueueThread {
                            promise.reject("cursor_error", "Cursor is null")
                        }
                    }
                } finally {
                    cursor?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rehber çekme hatası: ${e.message}", e)
                reactContext.runOnUiQueueThread {
                    promise.reject("fetch_error", "Could not fetch contacts: ${e.message}")
                }
            }
        }
    }
                
    private fun createBasicContactMap(cursor: Cursor): WritableMap {
        val contact = Arguments.createMap()
        
        val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
        
        contact.putString("recordID", contactId)
        contact.putString("displayName", displayName ?: "")
        
        if (displayName != null && displayName.isNotEmpty()) {
            val names = displayName.split(" ", limit = 2)
            contact.putString("givenName", names.getOrNull(0) ?: "")
            contact.putString("familyName", names.getOrNull(1) ?: "")
        } else {
            contact.putString("givenName", "")
            contact.putString("familyName", "")
        }
        
        contact.putArray("phoneNumbers", Arguments.createArray())
        
        return contact
    }
                    
    private fun enrichContactDetails(contactsMap: MutableMap<String, WritableMap>, contentResolver: ContentResolver) {
        if (contactsMap.isEmpty()) return
                    
        val contactIds = contactsMap.keys.toList()
        val batchSize = 100 
                    
        for (i in contactIds.indices step batchSize) {
            val endIndex = minOf(i + batchSize, contactIds.size)
            val batch = contactIds.subList(i, endIndex)
            fetchPhoneNumbers(batch, contactsMap, contentResolver)
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