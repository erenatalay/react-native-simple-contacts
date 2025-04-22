import Foundation
import Contacts

@objc(ContactsModule)
class ContactsModule: NSObject {
    
    // Store contactStore as a property to avoid creating it multiple times
    private let contactStore = CNContactStore()
    
    @objc
    func getContacts(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // İlk olarak izin durumunu kendi checkPermission metodumuzla kontrol edelim
        checkPermission({ (permissionStatus) in
            // İzin durumuna göre işlem yapalım
            if permissionStatus as? String == "granted" {
                // Tam erişim - normal akış
                self.fetchAllContactsAfterPermissionCheck(resolve: resolve, reject: reject)
            } else if permissionStatus as? String == "limited" {
                // Kısıtlı erişim - seçilen kişileri getir
                self.fetchLimitedContactsAfterPermissionCheck(resolve: resolve, reject: reject)
            } else {
                // İzin yok
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
    
    // İzin kontrolünden sonra tüm kişileri getir
    private func fetchAllContactsAfterPermissionCheck(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // Background thread'de çalıştır
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
    
    // İzin kontrolünden sonra kısıtlı kişileri getir
    private func fetchLimitedContactsAfterPermissionCheck(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // Background thread'de çalıştır
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
    
    // Helper method to fetch all contacts (for full access)
    private func fetchAllContacts(keysToFetch: [CNKeyDescriptor], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            let request = CNContactFetchRequest(keysToFetch: keysToFetch)
            var results = [[String: Any]]()
            
            // Batch process contacts
            try self.contactStore.enumerateContacts(with: request) { (contact, stopPointer) in
                results.append(self.contactToDict(contact: contact))
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
    
    // Helper method to fetch only selected contacts (for limited access)
    private func fetchLimitedContacts(keysToFetch: [CNKeyDescriptor], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            var results = [[String: Any]]()
            
            // iOS sürüm kontrolü düzeltildi
            if #available(iOS 15.0, *) {
                // Fix: Use the instance method on the contactStore instead of the type
                let containerIdentifier = try contactStore.defaultContainerIdentifier()
                let predicate = CNContact.predicateForContactsInContainer(withIdentifier: containerIdentifier)
                
                // Error handling eklendi
                do {
                    let contacts = try self.contactStore.unifiedContacts(matching: predicate, keysToFetch: keysToFetch)
                    
                    for contact in contacts {
                        results.append(self.contactToDict(contact: contact))
                    }
                    
                    // Return to main thread to resolve
                    DispatchQueue.main.async {
                        resolve(results)
                    }
                } catch {
                    // Erişim hatası durumunda boş sonuç dönebiliriz
                    print("Limited access contact fetch error: \(error.localizedDescription)")
                    DispatchQueue.main.async {
                        resolve(results) // Boş dizi dön
                    }
                }
            } else {
                // For older iOS versions, we need to try to get contacts but respect limitations
                let request = CNContactFetchRequest(keysToFetch: keysToFetch)
                
                // Error handling eklendi
                do {
                    try self.contactStore.enumerateContacts(with: request) { (contact, stopPointer) in
                        results.append(self.contactToDict(contact: contact))
                    }
                    
                    // Return to main thread to resolve
                    DispatchQueue.main.async {
                        resolve(results)
                    }
                } catch {
                    print("Limited access contact fetch error for old iOS: \(error.localizedDescription)")
                    DispatchQueue.main.async {
                        resolve(results) // Boş dizi dön
                    }
                }
            }
        } catch {
            DispatchQueue.main.async {
                // Tüm hatalar için başarısız dön, ancak boş liste döndürmeyi deneyebilirsin
                reject("fetch_failed", "Failed to fetch contacts: \(error.localizedDescription)", error)
            }
        }
    }
    
    // Helper to convert a CNContact to dictionary
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
        
        // Email addresses
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
    
    // Raw değeri kontrol edelim - debug için
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
        // Her durumda limited testi yapalım
        testLimitedAccess(resolve: resolve, reject: reject)
    case .denied:
        print("Status: Denied")
        resolve("denied")
    @unknown default:
        print("Status: Unknown (\(rawStatus))")
        // Bilinmeyen bir durum için de limited testi yapalım
        testLimitedAccess(resolve: resolve, reject: reject)
    }
}

// Gerçekten limited erişim olup olmadığını test et
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
            
            // Container ID alabilir miyiz kontrol et
            do {
                let containerID = try self.contactStore.defaultContainerIdentifier()
                print("Container ID obtained: \(containerID)")
                
                // Kişilere gerçekten erişebildiğimizi test et
                do {
                    let predicate = CNContact.predicateForContactsInContainer(withIdentifier: containerID)
                    print("Attempting to access contacts with predicate")
                    let contacts = try self.contactStore.unifiedContacts(matching: predicate, keysToFetch: tempKeys)
                    print("Successfully accessed \(contacts.count) contacts")
                    
                    // Başarılı erişim varsa limited erişim var demektir
                    DispatchQueue.main.async {
                        print("Returning limited access permission")
                        resolve("limited")
                    }
                } catch {
                    // Kişilere erişemiyorsak denied olarak değerlendir
                    print("Failed to access contacts: \(error.localizedDescription)")
                    DispatchQueue.main.async {
                        resolve("denied")
                    }
                }
            } catch {
                // Container ID alamıyorsak denied olarak değerlendir
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
        // Önce mevcut izin durumunu kontrol et
        checkPermission({ (permissionStatus) in
            // Eğer izin zaten reddedilmişse, direkt denied dön
            if permissionStatus as? String == "denied" {
                resolve("denied")
                return
            }
            
            // İzin reddedilmemişse, izin isteğine devam et
            self.contactStore.requestAccess(for: .contacts) { (granted, error) in
                if let error = error {
                    DispatchQueue.main.async {
                        reject("permission_error", "Error requesting contacts permission: \(error.localizedDescription)", error)
                    }
                    return
                }
                
                // İzin alındıktan sonra gerçek durumu kontrol et
                DispatchQueue.global(qos: .userInitiated).async {
                    // Güncel izin durumunu al
                    let authStatus = CNContactStore.authorizationStatus(for: .contacts)
                    
                    // Basit durumları hemen değerlendir
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
                    
                    // restricted durumu için pratik bir test yapalım
                    do {
                        let tempKeys = [CNContactGivenNameKey as CNKeyDescriptor]
                        
                        // Önce container ID alabilir miyiz kontrol et
                        var containerID: String
                        do {
                            containerID = try self.contactStore.defaultContainerIdentifier()
                        } catch {
                            DispatchQueue.main.async {
                                resolve("denied")
                            }
                            return
                        }
                        
                        // Kişilere gerçekten erişebildiğimizi test et
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