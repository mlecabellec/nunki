cmake_minimum_required(VERSION 3.16)
project(quasar_server CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT QUASAR_ROOT)
    set(QUASAR_ROOT "/quasar")
endif()

add_executable(quasar_server quasar_server.cpp)

target_include_directories(quasar_server PRIVATE
    ${QUASAR_ROOT}/cmake-projects/opcua/include
    ${QUASAR_ROOT}/cmake-projects/named/include
    ${QUASAR_ROOT}/cmake-projects/coretypes/include
    ${QUASAR_ROOT}/cmake-projects/scripting/include
    ${QUASAR_ROOT}/cmake-projects/third-party/sol2/include
    ${QUASAR_ROOT}/cmake-projects/third-party/lua
    ${QUASAR_ROOT}/cmake-projects/third-party/asio/asio/include
    ${QUASAR_ROOT}/build/cmake-projects/third-party/open62541/src_generated
    ${QUASAR_ROOT}/cmake-projects/third-party/open62541/include
    ${QUASAR_ROOT}/cmake-projects/third-party/open62541/plugins/include
    ${QUASAR_ROOT}/cmake-projects/third-party/open62541/arch
)

target_link_directories(quasar_server PRIVATE
    ${QUASAR_ROOT}/build/lib
    ${QUASAR_ROOT}/build/bin
)

target_link_libraries(quasar_server PRIVATE
    quasar_opcua
    quasar_scripting
    quasar_named
    quasar_coretypes
    liblua
    open62541
    pthread
)
