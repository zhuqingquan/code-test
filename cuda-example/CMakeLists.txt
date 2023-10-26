cmake_minimum_required(VERSION 3.8)
project(CUDA_EXAMPLES)

find_package(CUDA REQUIRED)

message(STATUS "cuda version: " ${CUDA_VERSION_STRING})
message(STATUS "cuda info include_dir: " ${CUDA_INCLUDE_DIRS})
message(STATUS "cuda info libraries: " ${CUDA_LIBRARIES})

include_directories(${CUDA_INCLUDE_DIRS})

cuda_add_executable(vectorAdd vectorAdd.cu)
target_link_libraries(vectorAdd ${CUDA_LIBRARIES})
