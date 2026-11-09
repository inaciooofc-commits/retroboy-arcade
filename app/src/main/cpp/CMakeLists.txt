cmake_minimum_required(VERSION 3.22.1)
project(retroboy_core_probe LANGUAGES CXX)

add_library(retroboy_core_probe SHARED libretro_probe.cpp)
target_compile_features(retroboy_core_probe PRIVATE cxx_std_17)

find_library(log-lib log)
target_link_libraries(retroboy_core_probe PRIVATE ${log-lib} dl)
