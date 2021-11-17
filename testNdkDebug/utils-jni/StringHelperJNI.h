#include <stdio.h>
#include <string>
#include <jni.h>

jstring Java_utils_StringHelper_UnicodeToUTF8(JNIEnv* env, jobject* jobj, jstring& unicodeString);