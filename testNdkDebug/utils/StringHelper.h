/*
 * @Author: zhuqingquan
 * @Date: 2021-11-10 12:54:22
 * @FilePath: utils/StringHelper.h
 */
#include <stdlib.h>
#include <string>
/*
#ifndef BUILD_UTILS
#define UTILS_API
#elif
#endif
*/
std::string UnicodeToUTF8(const std::wstring& unicodeString);