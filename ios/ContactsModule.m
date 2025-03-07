#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(ContactsModule, NSObject)

RCT_EXTERN_METHOD(getContacts: (RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(checkPermission: (RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(requestPermission: (RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

@end