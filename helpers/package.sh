#!/bin/bash
# Packaging script for Nunki
# Supports: debian (deb), fedora (rpm), arch (pkg.tar.zst)

set -e

TYPE=$1
OUTPUT_DIR=${2:-"./dist-packages"}
mkdir -p "$OUTPUT_DIR"

VERSION="0.0.1"
RELEASE="1"
JAR_PATH="target/nunki-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: $JAR_PATH not found! Run 'mvn clean package -DskipTests' first."
    exit 1
fi

# Create wrapper script content
create_wrapper() {
    cat << 'EOF' > "$1"
#!/bin/bash
# Wrapper script for Nunki Java Application
exec java -jar /usr/share/nunki/nunki.jar "$@"
EOF
    chmod +x "$1"
}

# Create systemd service content
create_service() {
    cat << 'EOF' > "$1"
[Unit]
Description=Nunki Application
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/nunki
Restart=always
User=nunki
Group=nunki

[Install]
WantedBy=multi-user.target
EOF
}

case "$TYPE" in
    "deb")
        echo "Building Debian Package..."
        PKG_DIR=$(mktemp -d)
        
        # Directory structure
        mkdir -p "$PKG_DIR/DEBIAN"
        mkdir -p "$PKG_DIR/usr/share/nunki"
        mkdir -p "$PKG_DIR/usr/bin"
        mkdir -p "$PKG_DIR/lib/systemd/system"
        
        # Copy artifacts
        cp "$JAR_PATH" "$PKG_DIR/usr/share/nunki/nunki.jar"
        create_wrapper "$PKG_DIR/usr/bin/nunki"
        create_service "$PKG_DIR/lib/systemd/system/nunki.service"
        
        # Control file
        cat << EOF > "$PKG_DIR/DEBIAN/control"
Package: nunki
Version: $VERSION-$RELEASE
Section: utils
Priority: optional
Architecture: all
Depends: openjdk-17-jre | openjdk-21-jre | default-jre
Maintainer: mlecabellec <mickael.lecabellec@gmail.com>
Description: Nunki Application and OPC UA client
EOF

        # Post-install script
        cat << 'EOF' > "$PKG_DIR/DEBIAN/postinst"
#!/bin/sh
set -e
if ! id nunki >/dev/null 2>&1; then
    useradd --system --user-group --shell /usr/sbin/nologin nunki
fi
if [ -d /run/systemd/system ]; then
    systemctl daemon-reload
fi
exit 0
EOF
        chmod +x "$PKG_DIR/DEBIAN/postinst"
        
        # Build
        dpkg-deb --build "$PKG_DIR" "$OUTPUT_DIR/nunki_${VERSION}-${RELEASE}_all.deb"
        rm -rf "$PKG_DIR"
        echo "Debian package created successfully."
        ;;
        
    "rpm")
        echo "Building Fedora/RPM Package..."
        RPM_TOP_DIR=$(mktemp -d)
        
        # Directory structure
        mkdir -p "$RPM_TOP_DIR"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
        
        # Create helper files in SOURCES
        cp "$JAR_PATH" "$RPM_TOP_DIR/SOURCES/nunki.jar"
        create_wrapper "$RPM_TOP_DIR/SOURCES/nunki-wrapper"
        create_service "$RPM_TOP_DIR/SOURCES/nunki.service"
        
        # Spec file
        cat << EOF > "$RPM_TOP_DIR/SPECS/nunki.spec"
Name:           nunki
Version:        $VERSION
Release:        $RELEASE%{?dist}
Summary:        Nunki Application and OPC UA client
License:        Proprietary
URL:            https://github.com/mlecabellec/nunki
BuildArch:      noarch
Requires:       java-21-openjdk-headless

%description
Nunki Application and OPC UA client.

%prep
# No preparation needed

%install
mkdir -p %{buildroot}/usr/share/nunki
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/lib/systemd/system
cp %{_sourcedir}/nunki.jar %{buildroot}/usr/share/nunki/nunki.jar
cp %{_sourcedir}/nunki-wrapper %{buildroot}/usr/bin/nunki
cp %{_sourcedir}/nunki.service %{buildroot}/lib/systemd/system/nunki.service

%post
if ! getent passwd nunki >/dev/null; then
    useradd -r -s /sbin/nologin nunki
fi
systemctl daemon-reload

%files
/usr/share/nunki/nunki.jar
%attr(0755, root, root) /usr/bin/nunki
/lib/systemd/system/nunki.service

%changelog
* Sun May 31 2026 mlecabellec - 0.0.1
- Initial release
EOF

        # Build
        rpmbuild -bb --define "_topdir $RPM_TOP_DIR" "$RPM_TOP_DIR/SPECS/nunki.spec"
        cp "$RPM_TOP_DIR"/RPMS/noarch/*.rpm "$OUTPUT_DIR/"
        rm -rf "$RPM_TOP_DIR"
        echo "RPM package created successfully."
        ;;
        
    "arch")
        echo "Building Arch Linux Package..."
        ARCH_DIR=$(mktemp -d)
        
        # Copy helper files
        cp "$JAR_PATH" "$ARCH_DIR/nunki.jar"
        create_wrapper "$ARCH_DIR/nunki-wrapper"
        create_service "$ARCH_DIR/nunki.service"
        
        # Write PKGBUILD
        cat << EOF > "$ARCH_DIR/PKGBUILD"
pkgname=nunki
pkgver=$VERSION
pkgrel=$RELEASE
pkgdesc="Nunki Application and OPC UA client"
arch=('any')
url="https://github.com/mlecabellec/nunki"
license=('custom')
depends=('java-runtime>=17')
source=("nunki.jar" "nunki-wrapper" "nunki.service")
sha256sums=('SKIP' 'SKIP' 'SKIP')

package() {
    install -Dm644 "\$srcdir/nunki.jar" "\$pkgdir/usr/share/nunki/nunki.jar"
    install -Dm755 "\$srcdir/nunki-wrapper" "\$pkgdir/usr/bin/nunki"
    install -Dm644 "\$srcdir/nunki.service" "\$pkgdir/usr/lib/systemd/system/nunki.service"
}
EOF

        # makepkg cannot run as root
        if [ "$(id -u)" -eq 0 ]; then
            echo "Running as root, setting up temporary builduser for makepkg..."
            useradd -m -s /bin/bash builduser
            chown -R builduser:builduser "$ARCH_DIR"
            # Allow builduser to run makepkg
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
        echo "Arch package created successfully."
        ;;
        
    *)
        echo "Usage: $0 {deb|rpm|arch} [output_dir]"
        exit 1
        ;;
esac
