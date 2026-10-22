cmake_minimum_required(VERSION 3.22.1)

project("batitongtv_security")

add_library(
    batitongtv_security
    SHARED
    security_bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    batitongtv_security
    ${log-lib}
)
