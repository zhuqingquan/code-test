#include <stdio.h>
#include <string>
#include <jni.h>

#undef JNIEXPORT
#define JNIEXPORT __attribute__((visibility("default")))

#ifdef __cplusplus
extern "C" {
#endif 

jstring Java_utils_StringHelper_UnicodeToUTF8(JNIEnv* env, jobject* jobj, jstring& unicodeString);

#ifdef __cplusplus
}
#endif