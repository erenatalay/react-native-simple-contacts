import { NativeModules, Platform } from 'react-native';
import type { Contact } from './contacts.types';

export * from './contacts.types';
const LINKING_ERROR =
  `The package 'react-native-simple-contacts' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const ContactsModule = NativeModules.ContactsModule
  ? NativeModules.ContactsModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export const requestPermission = async (): Promise<boolean> => {
  return ContactsModule.requestPermission();
};

export const checkPermission = async () => {
  const hasPermission = await ContactsModule.checkPermission();
  if (!hasPermission) {
    const granted = await requestPermission();
    return granted;
  }
  return true;
};

export const getContacts = async () => {
  const hasPermission = await checkPermission();
  if (hasPermission) {
    const contacts: Contact[] = await ContactsModule.getContacts();
    return contacts;
  }
  return [];
};
