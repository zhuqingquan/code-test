cmake_minimum_required(VERSION 3.1)

project(utilsJNI)

message("$ENV{JAVA_HOME}/include")
include_directories($ENV{JAVA_HOME}/include 
                    $ENV{JAVA_HOME}/include/linux)

set(Source StringHelperJNI.cc)

add_library(${PROJECT_NAME} SHARED ${Source})