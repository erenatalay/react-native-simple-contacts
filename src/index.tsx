import { NativeModules, Platform } from 'react-native';

import {
  ContactsPermission,
  type Contact,
  type ContactsPermissionStatus,
} from './contacts.types';

export * from './contacts.types';
const LINKING_ERROR =
  `The package 'react-native-simple-contact' doesn't seem to be linked. Make sure: \n\n` +
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

export const requestPermission =
  async (): Promise<ContactsPermissionStatus> => {
    return ContactsModule.requestPermission();
  };

export const checkPermission = async (): Promise<ContactsPermissionStatus> => {
  const permissionStatus = await ContactsModule.checkPermission();

  // İzin "granted" veya "limited" ise doğrudan döndür
  if (
    permissionStatus === ContactsPermission.granted ||
    permissionStatus === ContactsPermission.limited
  ) {
    return permissionStatus;
  }

  // İzin verilmemişse izin iste
  return await requestPermission();
};

export const getContacts = async () => {
  const permissionStatus = await checkPermission();

  // İzin "granted" veya "limited" ise kişileri getir
  if (
    permissionStatus === ContactsPermission.granted ||
    permissionStatus === ContactsPermission.limited
  ) {
    const contacts: Contact[] = await ContactsModule.getContacts();
    return contacts;
  }

  return [];
};
