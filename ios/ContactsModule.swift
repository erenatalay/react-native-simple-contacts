import Foundation
import Contacts

@objc(ContactsModule)
class ContactsModule: NSObject {
    
    // Store contactStore as a property to avoid creating it multiple times
    private let contactStore = CNContactStore()
    
    @objc
    func getContacts(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // Move to background thread to avoid UI blocking
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            
            // Only fetch the keys we actually need
            let keysToFetch: [CNKeyDescriptor] = [
                CNContactIdentifierKey as CNKeyDescriptor,
                CNContactGivenNameKey as CNKeyDescriptor,
                CNContactFamilyNameKey as CNKeyDescriptor,
                CNContactPhoneNumbersKey as CNKeyDescriptor,
                CNContactEmailAddressesKey as CNKeyDescriptor
            ]
            
            self.contactStore.requestAccess(for: .contacts) { (granted, error) in
                if !granted {
                    DispatchQueue.main.async {
                        reject("permission_denied", "Permission to access contacts was denied", error)
                    }
                    return
                }
                
                do {
                    let request = CNContactFetchRequest(keysToFetch: keysToFetch)
                    var results = [[String: Any]]()
                    
                    // Batch process contacts
                    try self.contactStore.enumerateContacts(with: request) { (contact, stopPointer) in
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
                        
                        // Email addresses
                        var emailAddresses = [[String: String]]()
                        for email in contact.emailAddresses {
                            emailAddresses.append([
                                "label": email.label != nil ? CNLabeledValue<NSString>.localizedString(forLabel: email.label!) : "other",
                                "email": email.value as String
                            ])
                        }
                        result["emailAddresses"] = emailAddresses
                        
                        results.append(result)
                    }
                    
                    // Return to main thread to resolve
                    DispatchQueue.main.async {
                        resolve(results)
                    }
                } catch {
                    DispatchQueue.main.async {
                        reject("fetch_failed", "Failed to fetch contacts: \(error.localizedDescription)", error)
                    }
                }
            }
        }
    }
    
    @objc
    func checkPermission(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let authStatus = CNContactStore.authorizationStatus(for: .contacts)
        
        switch authStatus {
        case .authorized:
            resolve(true)
        case .denied, .restricted, .notDetermined:
            resolve(false)
        @unknown default:
            resolve(false)
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
                resolve(granted)
            }
        }
    }
    
    @objc
    static func requiresMainQueueSetup() -> Bool {
        return false
    }
}