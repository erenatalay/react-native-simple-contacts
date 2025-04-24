import Foundation
import Contacts
import React

@objc(ContactsModule)
class ContactsModule: NSObject {
    
    private let contactStore = CNContactStore()
    
    @objc
    func getContacts(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        checkPermission({ (permissionStatus) in
            if permissionStatus as? String == "granted" {
                self.fetchAllContactsAfterPermissionCheck(resolve: resolve, reject: reject)
            } else if permissionStatus as? String == "limited" {
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
            
            // Sadece ihtiyaç duyulan anahtar bilgileri içeren kısaltılmış liste
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
    
    private func fetchAllContacts(keysToFetch: [CNKeyDescriptor], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let startTime = CFAbsoluteTimeGetCurrent()
        
        let processingQueue = DispatchQueue(label: "com.simplecontacts.processing", attributes: .concurrent)
        let resultQueue = DispatchQueue(label: "com.simplecontacts.results", attributes: .concurrent)
        
        let semaphore = DispatchSemaphore(value: 4) // Control level of concurrency
        
        do {
            let request = CNContactFetchRequest(keysToFetch: keysToFetch)
            
            request.sortOrder = .givenName
            
            let batchSize = 200 
            var results = [[String: Any]]()
            var totalContacts = 0
            
            let group = DispatchGroup()
            
            var contactBuffer = [CNContact]()
            
            results.reserveCapacity(1000)
            
            try self.contactStore.enumerateContacts(with: request) { (contact, stopPointer) in
                contactBuffer.append(contact)
                totalContacts += 1
                
                if contactBuffer.count >= batchSize {
                    let contactBatch = contactBuffer
                    contactBuffer.removeAll(keepingCapacity: true)
                    
                    group.enter()
                    semaphore.wait() 
                    
                    processingQueue.async {
                        let processedBatch = self.processContactBatch(contacts: contactBatch)
                        
                        resultQueue.async {
                            results.append(contentsOf: processedBatch)
                            semaphore.signal() 
                            group.leave()
                        }
                    }
                }
                
                if totalContacts % 1000 == 0 {
                    let progress = totalContacts
                    DispatchQueue.main.async {
                    }
                }
            }
            
            if !contactBuffer.isEmpty {
                group.enter()
                semaphore.wait()
                
                processingQueue.async {
                    let processedBatch = self.processContactBatch(contacts: contactBuffer)
                    
                    resultQueue.async {
                        results.append(contentsOf: processedBatch)
                        semaphore.signal()
                        group.leave()
                    }
                }
            }
            
            group.wait()
            
            let endTime = CFAbsoluteTimeGetCurrent()
            let duration = endTime - startTime
            
            DispatchQueue.main.async {
                resolve(results)
            }
        } catch {
            let endTime = CFAbsoluteTimeGetCurrent()
            let duration = endTime - startTime
            
            DispatchQueue.main.async {
                reject("fetch_failed", "Failed to fetch contacts: \(error.localizedDescription)", error)
            }
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
    
    private func contactToLightDict(contact: CNContact) -> [String: Any] {
        var result: [String: Any] = [
            "recordID": contact.identifier,
            "givenName": contact.givenName,
            "familyName": contact.familyName
        ]
        
        let fullName = [contact.givenName, contact.familyName].filter { !$0.isEmpty }.joined(separator: " ")
        result["displayName"] = fullName.isEmpty ? "No Name" : fullName
        
        if let firstPhone = contact.phoneNumbers.first {
            result["phoneNumbers"] = [[
                "label": CNLabeledValue<CNPhoneNumber>.localizedString(forLabel: firstPhone.label ?? ""),
                "number": firstPhone.value.stringValue
            ]]
        } else {
            result["phoneNumbers"] = []
        }
        
        if let firstEmail = contact.emailAddresses.first {
            result["emailAddresses"] = [[
                "label": firstEmail.label != nil ? CNLabeledValue<NSString>.localizedString(forLabel: firstEmail.label!) : "other",
                "email": firstEmail.value as String
            ]]
        } else {
            result["emailAddresses"] = []
        }
        
        return result
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
            phoneNumbers.append([
                "label": CNLabeledValue<CNPhoneNumber>.localizedString(forLabel: phone.label ?? ""),
                "number": phone.value.stringValue
            ])
        }
        result["phoneNumbers"] = phoneNumbers
        
        var emailAddresses = [[String: String]]()
        for email in contact.emailAddresses {
            emailAddresses.append([
                "label": email.label != nil ? CNLabeledValue<NSString>.localizedString(forLabel: email.label!) : "other",
                "email": email.value as String
            ])
        }
        result["emailAddresses"] = emailAddresses
        
        return result
    }
    
    @objc
    func checkPermission(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let authStatus = CNContactStore.authorizationStatus(for: .contacts)
        
        
        if authStatus.rawValue == 3 {
            resolve("limited")
            return
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
        case .limited:
            resolve("limited")
            return
        @unknown default:
            if containerAccessAvailable() {
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
                    reject("permission_error", "Error requesting contacts permission: \(error.localizedDescription)", error)
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
                    
                default:
                    if authStatus.rawValue == 3 {
                        resolve("limited")
                        return
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