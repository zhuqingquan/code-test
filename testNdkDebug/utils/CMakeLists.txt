cmake_minimum_required(VERSION 3.1)

project(utils)

set(utils_src StringHelper.cpp)

add_library(${PROJECT_NAME} SHARED ${utils_src})