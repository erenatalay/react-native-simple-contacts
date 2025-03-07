import Foundation
import Contacts

@objc(ContactsModule)
class ContactsModule: NSObject {
    
    @objc
    func getContacts(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let contactStore = CNContactStore()
        let keysToFetch: [CNKeyDescriptor] = [
            CNContactIdentifierKey as CNKeyDescriptor,
            CNContactGivenNameKey as CNKeyDescriptor,
            CNContactFamilyNameKey as CNKeyDescriptor,
            CNContactMiddleNameKey as CNKeyDescriptor,
            CNContactEmailAddressesKey as CNKeyDescriptor,
            CNContactPhoneNumbersKey as CNKeyDescriptor,
            CNContactPostalAddressesKey as CNKeyDescriptor,
            CNContactOrganizationNameKey as CNKeyDescriptor,
            CNContactDepartmentNameKey as CNKeyDescriptor,
            CNContactJobTitleKey as CNKeyDescriptor,
            CNContactNoteKey as CNKeyDescriptor,
            CNContactImageDataAvailableKey as CNKeyDescriptor,
            CNContactThumbnailImageDataKey as CNKeyDescriptor,
            CNContactInstantMessageAddressesKey as CNKeyDescriptor,
            CNContactUrlAddressesKey as CNKeyDescriptor,
            CNContactBirthdayKey as CNKeyDescriptor,
            CNContactNamePrefixKey as CNKeyDescriptor,
            CNContactNameSuffixKey as CNKeyDescriptor
        ]
        
        contactStore.requestAccess(for: .contacts) { (granted, error) in
            if !granted {
                reject("permission_denied", "Permission to access contacts was denied", error)
                return
            }
            
            do {
                let containers = try contactStore.containers(matching: nil)
                var results = [[String: Any]]()
                
                for container in containers {
                    let fetchPredicate = CNContact.predicateForContactsInContainer(withIdentifier: container.identifier)
                    
                    do {
                        let containerResults = try contactStore.unifiedContacts(matching: fetchPredicate, keysToFetch: keysToFetch)
                        
                        for contact in containerResults {
                            var result = [String: Any]()
                            
                            result["recordID"] = contact.identifier
                            result["company"] = contact.organizationName
                            result["displayName"] = "\(contact.givenName) \(contact.familyName)"
                            result["familyName"] = contact.familyName
                            result["givenName"] = contact.givenName
                            result["middleName"] = contact.middleName
                            result["jobTitle"] = contact.jobTitle
                            result["hasThumbnail"] = contact.imageDataAvailable
                            result["prefix"] = contact.namePrefix
                            result["suffix"] = contact.nameSuffix
                            result["department"] = contact.departmentName
                            result["note"] = contact.note
                            
                            // Email addresses
                            var emailAddresses = [[String: String]]()
                            for email in contact.emailAddresses {
                                emailAddresses.append([
                                    "label": CNLabeledValue.localizedString(forLabel: email.label ?? ""),
                                    "email": email.value as String
                                ])
                            }
                            result["emailAddresses"] = emailAddresses
                            
                            // Phone numbers
                            var phoneNumbers = [[String: String]]()
                            for phone in contact.phoneNumbers {
                                phoneNumbers.append([
                                    "label": CNLabeledValue.localizedString(forLabel: phone.label ?? ""),
                                    "number": phone.value.stringValue
                                ])
                            }
                            result["phoneNumbers"] = phoneNumbers
                            
                            // Postal addresses
                            var postalAddresses = [[String: String]]()
                            for address in contact.postalAddresses {
                                postalAddresses.append([
                                    "label": CNLabeledValue.localizedString(forLabel: address.label ?? ""),
                                    "street": address.value.street,
                                    "city": address.value.city,
                                    "state": address.value.state,
                                    "postCode": address.value.postalCode,
                                    "country": address.value.country
                                ])
                            }
                            result["postalAddresses"] = postalAddresses
                            
                            // IM addresses
                            var imAddresses = [[String: String]]()
                            for im in contact.instantMessageAddresses {
                                imAddresses.append([
                                    "service": CNLabeledValue.localizedString(forLabel: im.label ?? ""),
                                    "username": im.value.username
                                ])
                            }
                            result["imAddresses"] = imAddresses
                            
                            // URL addresses
                            var urlAddresses = [[String: String]]()
                            for url in contact.urlAddresses {
                                urlAddresses.append([
                                    "label": CNLabeledValue.localizedString(forLabel: url.label ?? ""),
                                    "url": url.value as String
                                ])
                            }
                            result["urlAddresses"] = urlAddresses
                            
                            // Birthday
                            if let contactBirthday = contact.birthday {
                                result["birthday"] = [
                                    "year": contactBirthday.year ?? 0,
                                    "month": contactBirthday.month,
                                    "day": contactBirthday.day
                                ]
                            }
                            
                            results.append(result)
                        }
                    } catch {
                        reject("fetch_error", "Failed to fetch contacts for container: \(error.localizedDescription)", error)
                    }
                }
                
                resolve(results)
            } catch {
                reject("fetch_failed", "Failed to fetch contacts", error)
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
        let contactStore = CNContactStore()
        
        contactStore.requestAccess(for: .contacts) { (granted, error) in
            if let error = error {
                reject("permission_error", "Error requesting contacts permission: \(error.localizedDescription)", error)
                return
            }
            
            resolve(granted)
        }
    }
    
    @objc
    static func requiresMainQueueSetup() -> Bool {
        return false
    }
}
