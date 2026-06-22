cmake_minimum_required(VERSION 3.22.1)

project(asrm_whisper_jni LANGUAGES CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS_3RD_PARTY OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(SPIRV-Headers_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/cmake/spirv-headers
    CACHE PATH
    "Local SPIRV-Headers package config"
    FORCE
)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/vulkan-headers/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/spirv-headers/include)

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp
)

if (TARGET ggml-vulkan)
    target_compile_definitions(
        ggml-vulkan
        PRIVATE
            VULKAN_HPP_TYPESAFE_CONVERSION=1
    )
    target_include_directories(
        ggml-vulkan
        PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/vulkan-headers/include
            ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/spirv-headers/include
    )
endif()

add_library(asrm_whisper_jni SHARED
    asrm_whisper_jni.cpp
)

target_compile_features(asrm_whisper_jni PRIVATE cxx_std_17)

find_library(log-lib log)

target_link_libraries(asrm_whisper_jni
    whisper
    ${log-lib}
    dl
)
