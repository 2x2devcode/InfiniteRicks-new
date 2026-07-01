cmake_minimum_required(VERSION 3.22.1)
project("rickpin")

add_library(rickpin SHARED pin_config.cpp)

find_library(log-lib log)

target_link_libraries(rickpin ${log-lib})
