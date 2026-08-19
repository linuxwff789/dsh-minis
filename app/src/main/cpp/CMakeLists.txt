cmake_minimum_required(VERSION 3.22.1)
project(minis_native C)

add_library(pty_bridge SHARED pty_bridge.c)
find_library(log-lib log)
target_link_libraries(pty_bridge ${log-lib})
