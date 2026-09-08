cmake_minimum_required(VERSION 3.22)
project(driverhook C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Populated by the GitHub Actions workflow.
add_subdirectory(deps/libadrenotools)
# Pin ByteHook to v1.0.10 in CI. Newer releases add a ShadowHook dependency.
add_subdirectory(deps/bhook/bytehook/src/main/cpp bytehook_build)

# ByteHook exports C-only compile flags and its own version-script linker
# flags to consumers. Neither belongs on our C++ driverhook target.
set_property(TARGET bytehook PROPERTY INTERFACE_COMPILE_OPTIONS "")
set_property(TARGET bytehook PROPERTY INTERFACE_LINK_OPTIONS "")

add_library(driverhook SHARED driverhook.cpp probe.cpp)
target_include_directories(driverhook PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(driverhook
    adrenotools
    bytehook
    log
    android
    dl)

# 16 KiB ELF max-page-size compatibility for newer Android devices.
target_link_options(driverhook PRIVATE "-Wl,-z,max-page-size=16384")
