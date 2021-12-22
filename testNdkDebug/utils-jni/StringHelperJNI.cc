#include "StringHelperJNI.h"
#include "StringHelper.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__((visibility("default")))

#ifdef __cplusplus
extern "C" {
#endif 

jstring Java_utils_StringHelper_UnicodeToUTF8(JNIEnv* env, jobject* obj, jstring& unicodeString)
{
    std::string result = UnicodeToUTF8(L"你好");
    return env->NewString((jchar*)L"s", 1); 
}

#ifdef __cplusplus
}
#endif