cmake_minimum_required(VERSION 3.18)
project(twinio)
add_library(twinio SHARED iohook.c)
target_link_libraries(twinio log)
