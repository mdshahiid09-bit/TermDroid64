cmake_minimum_required(VERSION 3.22.1)
project("termdroid64")

add_library(termdroid64 SHARED
    pty_bridge.cpp)

find_library(log-lib log)

target_link_libraries(termdroid64
    ${log-lib})
