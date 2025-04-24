import Foundation
import Contacts
import React

@objc(ContactsModule)
class ContactsModule: NSObject {
    
    private let contactStore = CNContactStore()
    
    @objc
    func getContacts(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        checkPermission({ (permissionStatus) in
            if permissionStatus as? String == "granted" || permissionStatus as? String == "limited" {
                self.fetchAllContactsAfterPermissionCheck(resolve: resolve, reject: reject)
            } else {
                DispatchQueue.main.async {
                    reject("permission_denied", "No permission to access contacts: \(permissionStatus as? String ?? "unknown")", nil)
                }
            }
        }, reject: { (code, message, error) in
            DispatchQueue.main.async {
                reject(code, message, error)
            }
        })
    }
    
    private func fetchAllContactsAfterPermissionCheck(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
            guard let self = self else {
                DispatchQueue.main.async {
                    reject("error", "Self is nil", nil)
                }
                return
            }
            
            autoreleasepool {
                let keysToFetch: [CNKeyDescriptor] = [
                    CNContactIdentifierKey as CNKeyDescriptor,
                    CNContactGivenNameKey as CNKeyDescriptor,
                    CNContactFamilyNameKey as CNKeyDescriptor,
                    CNContactPhoneNumbersKey as CNKeyDescriptor,
                    CNContactEmailAddressesKey as CNKeyDescriptor,
                    CNContactOrganizationNameKey as CNKeyDescriptor
                ]
                
                self.fetchAllContacts(keysToFetch: keysToFetch, resolve: resolve, reject: reject)
            }
        }
    }
    
    private func fetchAllContacts(keysToFetch: [CNKeyDescriptor], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // Create a concurrent processing queue for parallel contact processing
        let processingQueue = DispatchQueue(label: "com.simplecontacts.processing", attributes: .concurrent)
        // Sequential result queue to prevent race conditions when appending results
        let resultQueue = DispatchQueue(label: "com.simplecontacts.results", qos: .default)
        // Semaphore limits concurrent operations to prevent excessive resource consumption
        // Value of 2 means at most 2 batch processing operations can run concurrently
        let semaphore = DispatchSemaphore(value: 2)
        @discardableResult
        func safeReject(_ message: String, error: Error? = nil) -> Bool {
            DispatchQueue.main.async {
                reject("fetch_failed", message, error)
            }
            return false
        }
        
        do {
            let request = CNContactFetchRequest(keysToFetch: keysToFetch)
            request.sortOrder = .givenName
            // Process contacts in batches to optimize memory usage
            let batchSize = 50
            var results = [[String: Any]]()
            var totalContacts = 0
            
            // DispatchGroup ensures we wait for all batches to complete before returning results
            let group = DispatchGroup()
            var contactBuffer = [CNContact]()
            
            // Start with a reasonable initial capacity to avoid frequent reallocations
            // The array will dynamically grow as needed
            let initialCapacity = 100
            results.reserveCapacity(initialCapacity)
            
            try self.contactStore.enumerateContacts(with: request) { (contact, stopPointer) in
                do {
                    // Add contact to buffer for batch processing
                    contactBuffer.append(contact)
                    totalContacts += 1
                    
                    if contactBuffer.count >= batchSize {
                        // When buffer reaches batch size, process the batch
                        let contactBatch = contactBuffer
                        contactBuffer.removeAll(keepingCapacity: true)
                        
                        // Enter the group before async processing
                        group.enter()
                        // Wait on semaphore to limit concurrent operations
                        semaphore.wait()
                        
                        processingQueue.async {
                            // Use autoreleasepool to release memory for each batch
                            autoreleasepool {
                                do {
                                    // Convert contacts to lightweight dictionaries
                                    let processedBatch = self.processContactBatchLight(contacts: contactBatch)
                                    
                                    resultQueue.async {
                                        // Add processed contacts to results array
                                        results.append(contentsOf: processedBatch)
                                        
                                        // Dynamic capacity management:
                                        // If total contacts exceed current capacity, increase it
                                        // using exponential growth strategy (2x) or direct sizing
                                        if totalContacts > results.capacity {
                                            let newCapacity = max(results.capacity * 2, totalContacts + batchSize)
                                            results.reserveCapacity(newCapacity)
                                        }
                                        
                                        // Signal semaphore to allow another batch to be processed
                                        semaphore.signal()
                                        // Leave the group for this batch
                                        group.leave()
                                    }
                                } catch {
                                    // Release semaphore and group in case of error
                                    semaphore.signal()
                                    group.leave()
                                    _ = safeReject("Error processing contact batch: \(error.localizedDescription)", error: error)
                                }
                            }
                        }
                        
                        // Cancel previous memory-intensive operations periodically
                        if totalContacts % 50 == 0 {
                            autoreleasepool {
                                NSObject.cancelPreviousPerformRequests(withTarget: self)
                            }
                        }
                    }
                } catch {
                    stopPointer.pointee = true
                    _ = safeReject("Error processing contact: \(error.localizedDescription)", error: error)
                }
            }
            
            // Process any remaining contacts in the buffer
            if !contactBuffer.isEmpty {
                group.enter()
                semaphore.wait()
                
                processingQueue.async {
                    autoreleasepool {
                        do {
                            let processedBatch = self.processContactBatchLight(contacts: contactBuffer)
                            resultQueue.async {
                                results.append(contentsOf: processedBatch)
                                
                                // Dynamic capacity management for final batch
                                if totalContacts > results.capacity {
                                    let newCapacity = max(results.capacity * 2, totalContacts + batchSize)
                                    results.reserveCapacity(newCapacity)
                                }
                                
                                semaphore.signal()
                                group.leave()
                            }
                        } catch {
                            semaphore.signal()
                            group.leave()
                            _ = safeReject("Error processing final contact batch: \(error.localizedDescription)", error: error)
                        }
                    }
                }
            }
            
            // Wait for all async operations to complete
            group.wait()
            
            // Copy results in a thread-safe manner to prevent race conditions
            var finalResults = [[String: Any]]()
            resultQueue.sync {
                finalResults = results
            }
            
            // Return results on the main thread as required by React Native
            DispatchQueue.main.async {
                if Thread.isMainThread {
                    resolve(finalResults)
                } else {
                    DispatchQueue.main.async {
                        resolve(finalResults)
                    }
                }
            }
        } catch {
            _ = safeReject("Failed to fetch contacts: \(error.localizedDescription)", error: error)
        }
    }

    private func processContactBatch(contacts: [CNContact]) -> [[String: Any]] {
        var results = [[String: Any]]()
        results.reserveCapacity(contacts.count)
        
        for contact in contacts {
            let contactDict = self.contactToDict(contact: contact)
            results.append(contactDict)
        }
        return results
    }
        
    private func processContactBatchLight(contacts: [CNContact]) -> [[String: Any]] {
        var results = [[String: Any]]()
        results.reserveCapacity(contacts.count)
        
        for contact in contacts {
            autoreleasepool {
                if let contactDict = self.contactToLightDictSafe(contact: contact) {
                    results.append(contactDict)
                }
            }
        }
        return results
    }
    
    private func contactToLightDictSafe(contact: CNContact) -> [String: Any]? {
        var result: [String: Any] = [:]
        
        result["recordID"] = contact.identifier
        result["givenName"] = contact.givenName
        result["familyName"] = contact.familyName
        
        let fullName = [contact.givenName, contact.familyName].filter { !$0.isEmpty }.joined(separator: " ")
        result["displayName"] = fullName.isEmpty ? "No Name" : fullName
        
        var phoneNumbers: [[String: String]] = []
        if !contact.phoneNumbers.isEmpty {
            let firstPhone = contact.phoneNumbers[0]
            let label = CNLabeledValue<CNPhoneNumber>.localizedString(forLabel: firstPhone.label ?? "")
            let number = firstPhone.value.stringValue
            phoneNumbers.append(["label": label, "number": number])
        }
        result["phoneNumbers"] = phoneNumbers
        
        var emailAddresses: [[String: String]] = []
        if !contact.emailAddresses.isEmpty {
            let firstEmail = contact.emailAddresses[0]
            let label = firstEmail.label != nil ? CNLabeledValue<NSString>.localizedString(forLabel: firstEmail.label!) : "other"
            let email = firstEmail.value as String
            emailAddresses.append(["label": label, "email": email])
        }
        result["emailAddresses"] = emailAddresses
        
        return result
    }

    private func contactToLightDict(contact: CNContact) -> [String: Any] {
        return contactToLightDictSafe(contact: contact) ?? [:]
    }
    
    private func contactToDict(contact: CNContact) -> [String: Any] {
        var result = [String: Any]()
        
        result["recordID"] = contact.identifier
        
        let firstName = contact.givenName
        let lastName = contact.familyName
        let fullName = [firstName, lastName].filter { !$0.isEmpty }.joined(separator: " ")
        result["displayName"] = fullName.isEmpty ? "No Name" : fullName
        
        result["givenName"] = firstName
        result["familyName"] = lastName
        
        result["company"] = contact.organizationName
        
        var phoneNumbers = [[String: String]]()
        for phone in contact.phoneNumbers {
            let label = phone.label != nil ? CNLabeledValue<CNPhoneNumber>.localizedString(forLabel: phone.label!) : "other"
            phoneNumbers.append([
                "label": label,
                "number": phone.value.stringValue
            ])
        }
        result["phoneNumbers"] = phoneNumbers
        
        var emailAddresses = [[String: String]]()
        for email in contact.emailAddresses {
            let label = email.label != nil ? CNLabeledValue<NSString>.localizedString(forLabel: email.label!) : "other"
            emailAddresses.append([
                "label": label,
                "email": email.value as String
            ])
        }
        result["emailAddresses"] = emailAddresses
        
        return result
    }
    
    @objc
    func checkPermission(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let authStatus = CNContactStore.authorizationStatus(for: .contacts)
        
        if #available(iOS 18.0, *) {
            if authStatus.rawValue == 3 {
                resolve("limited")
                return
            }
        }
        
        switch authStatus {
        case .authorized:
            resolve("granted")
            return
        case .denied:
            resolve("denied")
            return
        case .notDetermined:
            resolve("undetermined")
            return
        case .restricted:
            if containerAccessAvailable() {
                resolve("limited")
                return
            }
            resolve("denied")
            return
        @unknown default:
            if #available(iOS 18.0, *) {
                if authStatus.rawValue == 3 {
                    resolve("limited")
                    return
                }
            }
            
            if self.containerAccessAvailable() {
                resolve("limited")
            } else {
                resolve("denied")
            }
            return
        }
    }

    private func containerAccessAvailable() -> Bool {
        do {
            let _ = try contactStore.defaultContainerIdentifier()
            return true
        } catch {
            return false
        }
    }

    @objc
    func requestPermission(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        contactStore.requestAccess(for: .contacts) { (granted, error) in
            if let error = error {
                DispatchQueue.main.async {
                    resolve("denied")
                }
                return
            }
            
            DispatchQueue.main.async {
                let authStatus = CNContactStore.authorizationStatus(for: .contacts)
                
                switch authStatus {
                case .authorized:
                    resolve("granted")
                    
                case .denied:
                    resolve("denied")
                    
                case .restricted:
                    resolve("denied")
                    
                case .notDetermined:
                    resolve("undetermined")
                    
                @unknown default:
                    if #available(iOS 18.0, *) {
                        if authStatus.rawValue == 3 {
                            resolve("limited")
                            return
                        }
                    }
                    
                    if self.containerAccessAvailable() {
                        resolve("limited")
                    } else {
                        resolve("denied")
                    }
                }
            }
        }
    }
  
    @objc
    static func requiresMainQueueSetup() -> Bool {
        return false
    }
}