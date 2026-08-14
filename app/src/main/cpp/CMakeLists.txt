# Native JVM launcher: dlopen(libjli.so) -> JLI_Launch -> Minecraft.
#
# The launcher app itself must build against the NDK; the game JVM is
# loaded at runtime from the installed Java runtime's lib/jli/libjli.so,
# never linked here.

cmake_minimum_required(VERSION 3.22.1)

project(jvm_launcher CXX)

add_library(jvm_launcher SHARED
    jvm_launcher.cpp
)

target_compile_features(jvm_launcher PRIVATE cxx_std_17)

# android: android-specific headers; log: __android_log_print.
target_link_libraries(jvm_launcher
    android
    log
)
