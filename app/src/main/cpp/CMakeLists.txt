cmake_minimum_required(VERSION 3.22.1)

project("slm")

# Add include directories for headers
include_directories(llama)

# Create the native library
add_library(native-lib SHARED
        native-lib.cpp)

# Find Android system libraries
find_library(log-lib log)
find_library(android-lib android)

# Import pre-built llama libraries
set(LLAMA_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
        IMPORTED_LOCATION ${LLAMA_LIB_DIR}/libllama.so)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
        IMPORTED_LOCATION ${LLAMA_LIB_DIR}/libggml.so)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES
        IMPORTED_LOCATION ${LLAMA_LIB_DIR}/libggml-base.so)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES
        IMPORTED_LOCATION ${LLAMA_LIB_DIR}/libggml-cpu.so)

# Link all libraries
target_link_libraries(native-lib
        ${log-lib}
        ${android-lib}
        llama
        ggml
        ggml-base
        ggml-cpu)