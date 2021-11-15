cmake_minimum_required(VERSION 3.1)

project(utilsJNI)

set(Source StringHelperJNI.cc)

add_library(${PROJECT_NAME} SHARED ${Source})