export interface EmailAddress {
  label: string;
  email: string;
}

export interface PhoneNumber {
  label: string;
  number: string;
}

export interface PostalAddress {
  label: string;
  street: string;
  city: string;
  state: string;
  postCode: string;
  country: string;
}

export interface Birthday {
  day: number;
  month: number;
  year: number;
}

export interface InstantMessageAddress {
  service: string;
  username: string;
}

export interface UrlAddress {
  label: string;
  url: string;
}

export interface Contact {
  recordID: string;
  backTitle: string;
  company: string | null;
  emailAddresses: EmailAddress[];
  displayName: string;
  familyName: string;
  givenName: string;
  middleName: string;
  jobTitle: string;
  phoneNumbers: PhoneNumber[];
  hasThumbnail: boolean;
  thumbnailPath: string;
  isStarred: boolean;
  postalAddresses: PostalAddress[];
  prefix: string;
  suffix: string;
  department: string;
  birthday: Birthday;
  imAddresses: InstantMessageAddress[];
  urlAddresses: UrlAddress[];
  note: string;
}

export enum ContactsPermission {
  undetermined = 'undetermined',
  denied = 'denied',
  granted = 'granted',
  limited = 'limited',
}
export type ContactsPermissionStatus = keyof typeof ContactsPermission;
export interface ContactsModuleInterface {
  getContacts(): Promise<Contact[]>;
  checkPermission(): Promise<ContactsPermissionStatus>;
  requestPermission(): Promise<ContactsPermissionStatus>;
}
