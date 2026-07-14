FROM debian:trixie-slim AS build

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential cmake git pkg-config python3 file ccache \
    autoconf automake libtool \
    libasound2-dev libx11-dev libxrandr-dev libxi-dev \
    libgl1-mesa-dev libglu1-mesa-dev libxcursor-dev libxinerama-dev \
    libgmp-dev libssl-dev uuid-dev zlib1g-dev binutils-dev libiberty-dev \
    libyaml-cpp-dev libtinyxml2-dev libjsoncons-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /quasar
COPY . .

# Initialize submodules if any (this might fail if no internet, but let's assume they are there or fetched)
# RUN git submodule update --init --recursive

# Build quasar
RUN --mount=type=cache,target=/root/.cache/ccache \
    mkdir -p build && cd build && \
    cmake .. -DCMAKE_BUILD_TYPE=Release \
              -DCMAKE_VERBOSE_MAKEFILE=ON \
              -DCMAKE_C_COMPILER_LAUNCHER=ccache \
              -DCMAKE_CXX_COMPILER_LAUNCHER=ccache && \
    make -j$(nproc) quasar_opcua quasar_named quasar_coretypes quasar_scripting open62541

# Compile the standalone server (from nunki/quasar_server)
COPY quasar_server /quasar/quasar_server
RUN mkdir -p /quasar/quasar_server/build && cd /quasar/quasar_server/build && \
    cmake .. -DCMAKE_BUILD_TYPE=Release -DQUASAR_ROOT=/quasar && \
    make -j$(nproc) && \
    cp quasar_server /quasar/build/quasar_server

# Run stage
FROM debian:trixie-slim
RUN apt-get update && apt-get install -y \
    libssl3 libuuid1 zlib1g \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /quasar/build/quasar_server .
COPY --from=build /quasar/build/lib/libquasar_opcua.so /usr/lib/
COPY --from=build /quasar/build/lib/libquasar_named.so /usr/lib/
COPY --from=build /quasar/build/lib/libquasar_coretypes.so /usr/lib/
COPY --from=build /quasar/build/lib/libquasar_scripting.so /usr/lib/

EXPOSE 4840
ENTRYPOINT ["./quasar_server"]
