import type { Contact } from './contacts.types';
export * from './contacts.types';
export declare const requestPermission: () => Promise<boolean>;
export declare const checkPermission: () => Promise<boolean>;
export declare const getContacts: () => Promise<Contact[]>;
