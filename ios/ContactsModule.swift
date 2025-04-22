import Foundation
import Contacts

@objc(ContactsModule)
class ContactsModule: NSObject {
    
    // Store contactStore as a property to avoid creating it multiple times
    private let contactStore = CNContactStore()
    
    @objc
    func getContacts(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        checkPermission({ (permissionStatus) in
            if permissionStatus as? String == "granted" {
                self.fetchAllContactsAfterPermissionCheck(resolve: resolve, reject: reject)
            } else if permissionStatus as? String == "limited" {
                self.fetchLimitedContactsAfterPermissionCheck(resolve: resolve, reject: reject)
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
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else {
                DispatchQueue.main.async {
                    reject("error", "Self is nil", nil)
                }
                return
            }
            
            // Sadece ihtiyaç duyulan alanları getir
            let keysToFetch: [CNKeyDescriptor] = [
                CNContactIdentifierKey as CNKeyDescriptor,
                CNContactGivenNameKey as CNKeyDescriptor,
                CNContactFamilyNameKey as CNKeyDescriptor,
                CNContactPhoneNumbersKey as CNKeyDescriptor,
                CNContactEmailAddressesKey as CNKeyDescriptor
            ]
            
            self.fetchAllContacts(keysToFetch: keysToFetch, resolve: resolve, reject: reject)
        }
    }
    
    private func fetchLimitedContactsAfterPermissionCheck(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else {
                DispatchQueue.main.async {
                    reject("error", "Self is nil", nil)
                }
                return
            }
            
            // Sadece ihtiyaç duyulan alanları getir
            let keysToFetch: [CNKeyDescriptor] = [
                CNContactIdentifierKey as CNKeyDescriptor,
                CNContactGivenNameKey as CNKeyDescriptor,
                CNContactFamilyNameKey as CNKeyDescriptor,
                CNContactPhoneNumbersKey as CNKeyDescriptor,
                CNContactEmailAddressesKey as CNKeyDescriptor
            ]
            
            self.fetchLimitedContacts(keysToFetch: keysToFetch, resolve: resolve, reject: reject)
        }
    }
    
    private func fetchAllContacts(keysToFetch: [CNKeyDescriptor], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            let request = CNContactFetchRequest(keysToFetch: keysToFetch)
            var results = [[String: Any]]()
            
            try self.contactStore.enumerateContacts(with: request) { (contact, stopPointer) in
                results.append(self.contactToDict(contact: contact))
            }
            
            DispatchQueue.main.async {
                resolve(results)
            }
        } catch {
            DispatchQueue.main.async {
                reject("fetch_failed", "Failed to fetch contacts: \(error.localizedDescription)", error)
            }
        }
    }
    
    private func fetchLimitedContacts(keysToFetch: [CNKeyDescriptor], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            var results = [[String: Any]]()
            
            if #available(iOS 15.0, *) {
                let containerIdentifier = try contactStore.defaultContainerIdentifier()
                let predicate = CNContact.predicateForContactsInContainer(withIdentifier: containerIdentifier)
                
                do {
                    let contacts = try self.contactStore.unifiedContacts(matching: predicate, keysToFetch: keysToFetch)
                    
                    for contact in contacts {
                        results.append(self.contactToDict(contact: contact))
                    }
                    
                    DispatchQueue.main.async {
                        resolve(results)
                    }
                } catch {
                    print("Limited access contact fetch error: \(error.localizedDescription)")
                    DispatchQueue.main.async {
                        resolve(results) 
                    }
                }
            } else {
                let request = CNContactFetchRequest(keysToFetch: keysToFetch)
                
                do {
                    try self.contactStore.enumerateContacts(with: request) { (contact, stopPointer) in
                        results.append(self.contactToDict(contact: contact))
                    }
                    
                    DispatchQueue.main.async {
                        resolve(results)
                    }
                } catch {
                    print("Limited access contact fetch error for old iOS: \(error.localizedDescription)")
                    DispatchQueue.main.async {
                        resolve(results) 
                    }
                }
            }
        } catch {
            DispatchQueue.main.async {
                reject("fetch_failed", "Failed to fetch contacts: \(error.localizedDescription)", error)
            }
        }
    }
    
    private func contactToDict(contact: CNContact) -> [String: Any] {
        var result = [String: Any]()
        
        // Essential info only
        result["recordID"] = contact.identifier
        
        // Better display name handling
        let firstName = contact.givenName
        let lastName = contact.familyName
        let fullName = [firstName, lastName].filter { !$0.isEmpty }.joined(separator: " ")
        result["displayName"] = fullName.isEmpty ? "No Name" : fullName
        
        result["givenName"] = firstName
        result["familyName"] = lastName
        
        // Phone numbers
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
    
    let rawStatus = authStatus.rawValue
    print("Raw status value: \(rawStatus)")
    
    switch authStatus {
    case .authorized:
        print("Status: Authorized")
        resolve("granted")
    case .notDetermined:
        print("Status: Not Determined")
        resolve("undetermined")
    case .restricted:
        print("Status: Restricted")
        testLimitedAccess(resolve: resolve, reject: reject)
    case .denied:
        print("Status: Denied")
        resolve("denied")
    @unknown default:
        print("Status: Unknown (\(rawStatus))")
        testLimitedAccess(resolve: resolve, reject: reject)
    }
}

private func testLimitedAccess(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    print("Testing limited access...")
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
        guard let self = self else { 
            print("Self is nil in testLimitedAccess")
            DispatchQueue.main.async {
                resolve("undefined")
            }
            return 
        }
        
        do {
            let tempKeys = [CNContactGivenNameKey as CNKeyDescriptor]
            print("Attempting to get container ID")
            
            do {
                let containerID = try self.contactStore.defaultContainerIdentifier()
                print("Container ID obtained: \(containerID)")
                
                do {
                    let predicate = CNContact.predicateForContactsInContainer(withIdentifier: containerID)
                    print("Attempting to access contacts with predicate")
                    let contacts = try self.contactStore.unifiedContacts(matching: predicate, keysToFetch: tempKeys)
                    print("Successfully accessed \(contacts.count) contacts")
                    
                    DispatchQueue.main.async {
                        print("Returning limited access permission")
                        resolve("limited")
                    }
                } catch {
                    print("Failed to access contacts: \(error.localizedDescription)")
                    DispatchQueue.main.async {
                        resolve("denied")
                    }
                }
            } catch {
                print("Failed to get container ID: \(error.localizedDescription)")
                DispatchQueue.main.async {
                    resolve("denied")
                }
            }
        } catch {
            print("General error in testLimitedAccess: \(error.localizedDescription)")
            DispatchQueue.main.async {
                resolve("denied")
            }
        }
    }
}

    @objc
    func requestPermission(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        checkPermission({ (permissionStatus) in
            if permissionStatus as? String == "denied" {
                resolve("denied")
                return
            }
            
            self.contactStore.requestAccess(for: .contacts) { (granted, error) in
                if let error = error {
                    DispatchQueue.main.async {
                        reject("permission_error", "Error requesting contacts permission: \(error.localizedDescription)", error)
                    }
                    return
                }
                
                DispatchQueue.global(qos: .userInitiated).async {
                    let authStatus = CNContactStore.authorizationStatus(for: .contacts)
                    
                    if authStatus == .authorized {
                        DispatchQueue.main.async {
                            resolve("granted")
                        }
                        return
                    } else if authStatus == .denied {
                        DispatchQueue.main.async {
                            resolve("denied")
                        }
                        return
                    } else if authStatus == .notDetermined {
                        DispatchQueue.main.async {
                            resolve("undetermined")
                        }
                        return
                    }
                    
                    do {
                        let tempKeys = [CNContactGivenNameKey as CNKeyDescriptor]
                        
                        var containerID: String
                        do {
                            containerID = try self.contactStore.defaultContainerIdentifier()
                        } catch {
                            DispatchQueue.main.async {
                                resolve("denied")
                            }
                            return
                        }
                        
                        do {
                            let predicate = CNContact.predicateForContactsInContainer(withIdentifier: containerID)
                            let _ = try self.contactStore.unifiedContacts(matching: predicate, keysToFetch: tempKeys)
                            
                            DispatchQueue.main.async {
                                resolve("limited")
                            }
                        } catch {
                            DispatchQueue.main.async {
                                resolve("denied")
                            }
                        }
                    } catch {
                        DispatchQueue.main.async {
                            resolve("denied")
                        }
                    }
                }
            }
        }, reject: reject)
    }
    
    @objc
    static func requiresMainQueueSetup() -> Bool {
        return false
    }
}