import type { Contact } from './src';

declare module 'react-native-simple-contacts' {
  export const requestPermission: () => Promise<boolean>;
  export const checkPermission: () => Promise<boolean>;
  export const getContacts: () => Promise<Contact[]>;
  export type Contact = Contact;
}
