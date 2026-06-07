cmake_minimum_required(VERSION 3.22.1)
project(waenhancer)

# Ogg
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libs" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "Build testing" FORCE)
set(INSTALL_DOCS OFF CACHE BOOL "Install docs" FORCE)
set(INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "Install pkgconfig" FORCE)
set(INSTALL_CMAKE_PACKAGE_MODULE OFF CACHE BOOL "Install cmake pkg" FORCE)
add_subdirectory(ogg)

# Opus
set(OPUS_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "Build programs" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "Install pkgconfig" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "Install cmake pkg" FORCE)
add_subdirectory(opus)

# libopusenc
set(OPUSENC_SRC
    libopusenc/src/opusenc.c
    libopusenc/src/picture.c
    libopusenc/src/ogg_packer.c
    libopusenc/src/opus_header.c
    libopusenc/src/resample.c
    libopusenc/src/random.c
    libopusenc/src/unicode_support.c
)

add_library(opusenc STATIC ${OPUSENC_SRC})
target_include_directories(opusenc PUBLIC libopusenc/include)
target_include_directories(opusenc PRIVATE libopusenc/src)
target_compile_definitions(opusenc PRIVATE RANDOM_PREFIX=libopusenc OUTSIDE_SPEEX PACKAGE_NAME="\\"libopusenc\\"" PACKAGE_VERSION="\\"0.2.1\\"")
# link libopus and libogg
target_link_libraries(opusenc PUBLIC opus ogg)

# Our App's Native Library
add_library(
    audio_opus_converter
    SHARED
    AudioOpusConverter.cpp
)

target_link_libraries(
    audio_opus_converter
    opusenc
    log
)
