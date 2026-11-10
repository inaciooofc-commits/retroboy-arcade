cmake_minimum_required(VERSION 3.22.1)
project(retroboy_core_probe LANGUAGES CXX)

add_library(retroboy_core_probe SHARED
    libretro_probe.cpp
    libretro_runtime.cpp
)
target_compile_features(retroboy_core_probe PRIVATE cxx_std_17)
target_include_directories(retroboy_core_probe PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)

find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(retroboy_core_probe PRIVATE ${log-lib} ${android-lib} dl)
