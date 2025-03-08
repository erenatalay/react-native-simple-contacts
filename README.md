
# react-native-simple-contacts

A simple React Native module to fetch contacts from the user's device with permission handling. This module provides basic functions to request and check permissions, and retrieve contact information, including names and phone numbers.

## Installation

### 1. Install the package

```bash
npm install react-native-simple-contacts
```

### 2. Link the package (for older versions of React Native)
If you're using React Native version 0.59 or earlier, you'll need to link the package manually:

```bash
react-native link react-native-simple-contacts
```

For newer versions of React Native, auto-linking should work.

### 3. Additional setup (iOS)
If you're working on iOS, you may need to add the following to your `Info.plist` file:

```xml
<key>NSContactsUsageDescription</key>
<string>We need access to your contacts to display them in the app.</string>
```

### 4. Additional setup (Android)
For Android, ensure that the correct permissions are set in your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

## Usage

### Importing the module

```tsx
import { requestPermission, checkPermission, getContacts, Contact } from "react-native-simple-contacts";
```

### Example Usage

Below is an example usage that demonstrates how to request permission, check if permission is granted, and then retrieve contacts:

```tsx
import React, { useEffect } from "react";
import { Button, Linking, SafeAreaView, StyleSheet, Text, View } from "react-native";
import { requestPermission, checkPermission, getContacts, Contact } from "react-native-simple-contacts";

const App = (): JSX.Element => {
  const [contacts, setContacts] = React.useState<Contact[]>([]);
  const [permission, setPermission] = React.useState<boolean>(false);

  const getContactsFetch = async () => {
    // Check permission
    const hasPermission = await checkPermission();
    setPermission(hasPermission);
    
    // Request permission if not granted
    if (!hasPermission) {
      const granted = await requestPermission();
      if (!granted) {
        console.log("Permission denied");
        return;
      }
    }

    // Fetch contacts if permission is granted
    try {
      const contacts = await getContacts();
      setContacts(contacts);
    } catch (error) {
      console.error("Error fetching contacts", error);
    }
  };

  const getPermission = async () => {
    if (!permission) {
      Linking.openSettings();
    }
  };

  useEffect(() => {
    getContactsFetch();
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      {!permission && <Button onPress={getPermission} title="Get Contacts" />}
      {permission &&
        contacts.map((contact, index) => (
          <View key={index}>
            <Text style={styles.text}>{contact?.displayName}</Text>
            <Text style={styles.text}>{contact.phoneNumbers?.[0]?.number}</Text>
          </View>
        ))}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 20,
  },
  text: {
    fontSize: 20,
    marginBottom: 20,
  },
});

export default App;
```

### Functions

1. **`requestPermission()`**  
   Requests permission from the user to access contacts.

   Returns: `Promise<boolean>`  
   - `true`: Permission granted.
   - `false`: Permission denied.

2. **`checkPermission()`**  
   Checks if the app has permission to access contacts.

   Returns: `Promise<boolean>`  
   - `true`: Permission granted.
   - `false`: Permission denied.

3. **`getContacts()`**  
   Fetches the contacts from the device.

   Returns: `Promise<Contact[]>`  
   - Returns an array of contacts with `displayName` and `phoneNumbers`.

### `Contact` Object Structure

The `Contact` object contains the following properties:

```ts
interface Contact {
  displayName: string;
  phoneNumbers: Array<{ number: string }>;
}
```

## Troubleshooting

1. **Permission Denied**  
   If the permission is denied, you can open the app settings for the user to manually enable the permission. You can use the `Linking.openSettings()` method to open the settings page of the app.

2. **Contacts Not Fetching**  
   If the contacts are not being fetched, make sure the permissions are correctly set in both iOS (`Info.plist`) and Android (`AndroidManifest.xml`).

3. **Missing Contact Details**  
   Ensure that the contacts on the device have phone numbers, as the `phoneNumbers` field might be empty for some contacts.

## License

MIT
