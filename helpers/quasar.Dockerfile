FROM debian:trixie-slim AS build

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential cmake git pkg-config python3 file \
    autoconf automake libtool \
    libasound2-dev libx11-dev libxrandr-dev libxi-dev \
    libgl1-mesa-dev libglu1-mesa-dev libxcursor-dev libxinerama-dev \
    libgmp-dev libssl-dev uuid-dev zlib1g-dev binutils-dev libiberty-dev \
    libyaml-cpp-dev libtinyxml2-dev libjsoncons-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /quasar
COPY quasar/ .

# Initialize submodules if any (this might fail if no internet, but let's assume they are there or fetched)
# RUN git submodule update --init --recursive

# Build quasar
RUN mkdir build && cd build && \
    cmake .. -DCMAKE_BUILD_TYPE=Release && \
    make -j$(nproc) quasar_opcua quasar_named quasar_coretypes open62541

# Compile the standalone server (from nunki/helpers)
COPY nunki/helpers/quasar_server.cpp /quasar/quasar_server.cpp
RUN g++ -O3 -std=c++20 /quasar/quasar_server.cpp -o /quasar/build/quasar_server \
    -I /quasar/cmake-projects/opcua/include \
    -I /quasar/cmake-projects/named/include \
    -I /quasar/cmake-projects/coretypes/include \
    -I /quasar/build/cmake-projects/third-party/open62541/src_generated \
    -I /quasar/cmake-projects/third-party/open62541/include \
    -I /quasar/cmake-projects/third-party/open62541/plugins/include \
    -I /quasar/cmake-projects/third-party/open62541/arch \
    -L /quasar/build/cmake-projects/opcua \
    -L /quasar/build/cmake-projects/named \
    -L /quasar/build/cmake-projects/coretypes \
    -L /quasar/build/cmake-projects/third-party/open62541 \
    -lquasar_opcua -lquasar_named -lquasar_coretypes -lopen62541 -lpthread

# Run stage
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y \
    libssl3 libuuid1 zlib1g \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /quasar/build/quasar_server .
COPY --from=build /quasar/build/cmake-projects/opcua/libquasar_opcua.so /usr/lib/
COPY --from=build /quasar/build/cmake-projects/named/libquasar_named.so /usr/lib/
COPY --from=build /quasar/build/cmake-projects/coretypes/libquasar_coretypes.so /usr/lib/
COPY --from=build /quasar/build/cmake-projects/third-party/open62541/libopen62541.so /usr/lib/

EXPOSE 4840
ENTRYPOINT ["./quasar_server"]
