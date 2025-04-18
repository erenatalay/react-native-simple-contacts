declare module 'react-native-simple-contact' {
  export const requestPermission: () => Promise<ContactsPermissionStatus>;
  export const checkPermission: () => Promise<ContactsPermissionStatus>;
  export const getContacts: () => Promise<Contact[]>;
  export type Contact = Contact;
  export enum ContactsPermission {
    undetermined = 'undetermined',
    denied = 'denied',
    granted = 'granted',
    limited = 'limited',
  }
  export type ContactsPermissionStatus = ContactsPermissionStatus;
}
