#include "StringHelperJNI.h"

jstring Java_utils_StringHelper_UnicodeToUTF8(JNIEnv* env, jobject* obj, jstring& unicodeString)
{
    return env->NewString((jchar*)L"s", 1); 
}