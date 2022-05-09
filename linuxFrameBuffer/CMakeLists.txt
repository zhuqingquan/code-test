cmake_minimum_required(VERSION 3.1)

project(testFrameBuffer)

set(SOURCE testFrameBuffer.cpp)

add_executable(${PROJECT_NAME} ${SOURCE})

set_target_properties(${PROJECT_NAME} PROPERTIES CXX_STANDARD 17)