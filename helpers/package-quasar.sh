#!/bin/bash
# Packaging script for Quasar
# Run this from the quasar directory after building

set -e

TYPE=$1
OUTPUT_DIR=${2:-"../dist-packages"}
mkdir -p "$OUTPUT_DIR"

VERSION="0.0.1"
RELEASE="1"

case "$TYPE" in
    "deb"|"rpm")
        echo "Building Quasar package using CPack ($TYPE)..."
        cd build-projects
        if [ "$TYPE" = "deb" ]; then
            cpack -G DEB
            cp *.deb "$OUTPUT_DIR/"
        else
            cpack -G RPM
            cp *.rpm "$OUTPUT_DIR/"
        fi
        cd -
        ;;
        
    "arch")
        echo "Building Quasar Arch Linux Package..."
        ARCH_DIR=$(mktemp -d)
        
        # Write PKGBUILD
        cat << EOF > "$ARCH_DIR/PKGBUILD"
pkgname=quasar
pkgver=$VERSION
pkgrel=$RELEASE
pkgdesc="Quasar Industrial Automation Solution"
arch=('x86_64')
url="https://github.com/mlecabellec/quasar"
license=('Proprietary')
depends=('openssl' 'zlib' 'gmp')

package() {
    mkdir -p "\$pkgdir/usr/bin"
    mkdir -p "\$pkgdir/usr/lib"
    # Copy compiled binaries from the build directory
    cp -r "\$srcdir/build-projects/bin/"* "\$pkgdir/usr/bin/" || true
    cp "\$srcdir/build-projects/cmake-projects/opcua/libquasar_opcua.so" "\$pkgdir/usr/lib/" || true
    cp "\$srcdir/build-projects/cmake-projects/named/libquasar_named.so" "\$pkgdir/usr/lib/" || true
    cp "\$srcdir/build-projects/cmake-projects/coretypes/libquasar_coretypes.so" "\$pkgdir/usr/lib/" || true
    cp "\$srcdir/build-projects/cmake-projects/third-party/open62541/libopen62541.so" "\$pkgdir/usr/lib/" || true
}
EOF

        # Copy the compiled build-projects into the PKGBUILD source directory
        mkdir -p "$ARCH_DIR/build-projects"
        cp -r build-projects/* "$ARCH_DIR/build-projects/"

        # makepkg cannot run as root
        if [ "$(id -u)" -eq 0 ]; then
            echo "Running as root, setting up temporary builduser for makepkg..."
            useradd -m -s /bin/bash builduser
            chown -R builduser:builduser "$ARCH_DIR"
            cd "$ARCH_DIR"
            sudo -u builduser makepkg -f
            cd -
            cp "$ARCH_DIR"/*.pkg.tar.zst "$OUTPUT_DIR/"
            userdel -r builduser
        else
            cd "$ARCH_DIR"
            makepkg -f
            cd -
            cp "$ARCH_DIR"/*.pkg.tar.zst "$OUTPUT_DIR/"
        fi
        
        rm -rf "$ARCH_DIR"
        echo "Quasar Arch package created successfully."
        ;;
        
    *)
        echo "Usage: $0 {deb|rpm|arch} [output_dir]"
        exit 1
        ;;
esac
