cmake_minimum_required(VERSION 3.18)
project(apexmapper_native C)

add_library(mouse_read SHARED mouse_read.c)
target_link_libraries(mouse_read log)
