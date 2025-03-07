// index.d.ts
import { EmitterSubscription } from 'react-native';
import type { Contact } from './contacts.types';

declare module 'react-native-simple-contacts' {
  /**
   * Request permission to access contacts
   * @returns Promise resolving to a boolean indicating if permission was granted
   */
  export function requestPermission(): Promise<boolean>;

  /**
   * Check if the app has permission to access contacts
   * @returns Promise resolving to a boolean indicating if permission is granted
   */
  export function checkPermission(): Promise<boolean>;

  /**
   * Get all contacts from the device
   * @returns Promise resolving to an array of Contact objects
   */
  export function getContacts(): Promise<Contact[]>;

  export type Contact = Contact;
}
