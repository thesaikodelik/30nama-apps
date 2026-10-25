#!/usr/bin/env bash
# Builds an LGPL libmpv (with FFmpeg and its other dependencies) for Android
# arm64-v8a, then copies the libraries and headers into the Quest app.
#
# Runs on Linux or WSL, needs no root: the few build tools it needs (meson,
# ninja, pkgconf) are fetched and built into the work directory. Every source
# is pinned by version and SHA-256 (git sources by commit).
#
#   quest/tools/build-libmpv.sh            # build what is missing, then copy
#   quest/tools/build-libmpv.sh --clean    # rebuild everything from scratch
#
# Work directory: $NAMA_MPV_WORK, default ~/nama-mpv. Keep it on the Linux
# file system, not under /mnt: builds there are many times slower.
#
# Adapted from mpv-android's buildscripts (MIT), with -Dgpl=false and an
# FFmpeg without --enable-gpl / --enable-nonfree. See LIBMPV.md.

set -euo pipefail

## Pinned versions ------------------------------------------------------------

NDK_VERSION=r28c                   # 28.2.13676358, same as the app's ndkVersion
NDK_SHA256=dfb20d396df28ca02a8c708314b814a4d961dc9074f9a161932746f815aa552f
API=34                             # Horizon OS is Android 14; equals minSdk

MESON_VERSION=1.12.1
MESON_SHA256=ab0a6ca09f8ef70c564c8241fb5a23957886a0b53fb58412b5e07eaf07dba743
NINJA_VERSION=1.13.2
NINJA_SHA256=5749cbc4e668273514150a80e387a957f933c6ed3f5f11e03fb30955e2bbead6
PKGCONF_VERSION=2.5.1
PKGCONF_SHA256=cd05c9589b9f86ecf044c10a2269822bc9eb001eced2582cfffd658b0a50c243
GPERF_VERSION=3.3                # fontconfig generates a hash table with it
GPERF_SHA256=fd87e0aba7e43ae054837afd6cd4db03a3f2693deb3619085e6ed9d8d9604ad8

MBEDTLS_VERSION=3.6.7
MBEDTLS_SHA256=a7e8bcbec0e6f761b4af24f25677626b35f762f68eef79c08677a363212d11f6
DAV1D_VERSION=1.5.4
DAV1D_SHA256=686616b7c69eb88d44459391ab25cac13b6647a3b288835c5784e71c1514a5c5
LIBXML2_VERSION=2.15.4
LIBXML2_SHA256=a569ee9c59eabb197a0d5047046d757ee14c86fb2d4a734555b633cb5584ad25
FFMPEG_VERSION=8.1.3
FFMPEG_SHA256=7138d28c96d9d3e3af4ee3d8cad72741f8ffb40da90c1112235dea3ecd3178a3
FREETYPE_VERSION=2.15.0
FREETYPE_SHA256=93cd47eb719c325107f2d3cf087d06299e944c3b48a40b55537302304dea1dd5
FRIBIDI_VERSION=1.0.17
FRIBIDI_SHA256=6949dcde27d41cebad1fd741fcafc36d55a1020d2d872d4a6eb3914caabbada2
HARFBUZZ_VERSION=14.6.0
HARFBUZZ_SHA256=d07a007327277708a2a73ae437887cdbaf282937f6d03ca5467723e9099af586
UNIBREAK_VERSION=8.0
UNIBREAK_SHA256=9c4fad6e517338a098373acc9f35579ae2c325e6446666fb9ac2666ba15ceba4
FONTCONFIG_VERSION=2.18.3
FONTCONFIG_SHA256=9ae01e1d53acdef56010c5451cd34aa41d325b2faccd8606448d8fa01b2496b3
LIBASS_VERSION=0.17.5
LIBASS_SHA256=2dca25c0e0c837ddf00b52011b3f82cac1e4ddd3ad018227806b0c2288864acc
LIBPLACEBO_VERSION=v7.360.1
LIBPLACEBO_COMMIT=cee9b076f2c63104ccfd497fa79c39a867293ec4
MPV_VERSION=0.41.0
MPV_SHA256=ee21092a5ee427353392360929dc64645c54479aefdb5babc5cfbb5fad626209

## Paths -----------------------------------------------------------------------

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QUEST="$(cd "$HERE/.." && pwd)"
WORK="${NAMA_MPV_WORK:-$HOME/nama-mpv}"
DL="$WORK/downloads"
SRC="$WORK/src"
TOOLS="$WORK/tools"
PREFIX="$WORK/prefix/arm64"
JNILIBS="$QUEST/app/src/main/jniLibs/arm64-v8a"
INCLUDE="$QUEST/app/src/main/cpp/include/mpv"
JOBS="$(nproc 2>/dev/null || echo 4)"

if [[ "${1:-}" == "--clean" ]]; then
	rm -rf "$SRC" "$WORK/prefix" "$WORK/stamps"
fi
mkdir -p "$DL" "$SRC" "$TOOLS/bin" "$WORK/stamps"

msg() { printf '\e[1;34m==> %s\e[m\n' "$*" >&2; }
die() { printf '\e[1;31m%s\e[m\n' "$*" >&2; exit 1; }

# fetch <file> <url> <sha256>: download once, refuse anything that does not match.
# When bumping a version, set its hash to PIN once to have the new one printed.
fetch() {
	local file="$DL/$1" url="$2" want="$3"
	if [[ ! -f "$file" ]]; then
		msg "Downloading $1"
		curl -fL --retry 3 -o "$file.part" "$url"
		mv "$file.part" "$file"
	fi
	local got
	got="$(sha256sum "$file" | cut -d' ' -f1)"
	if [[ "$want" == PIN ]]; then
		echo "PIN $1 $got" >&2
	elif [[ "$got" != "$want" ]]; then
		die "SHA-256 mismatch for $1: expected $want, got $got"
	fi
}

# unpack <archive> <dir>: a fresh source tree, top-level folder stripped
unpack() {
	rm -rf "$SRC/$2"
	mkdir -p "$SRC/$2"
	case "$1" in
		*.zip) die "unpack: zip not supported" ;;
		*) tar -xf "$DL/$1" -C "$SRC/$2" --strip-components=1 ;;
	esac
}

done_stamp() { [[ -f "$WORK/stamps/$1" ]]; }
mark_done() { touch "$WORK/stamps/$1"; }

## Host tools ------------------------------------------------------------------

for t in curl tar xz bzip2 unzip make gcc python3 perl sha256sum git; do
	command -v "$t" >/dev/null || die "Missing host tool: $t"
done

export PATH="$TOOLS/bin:$PATH"

if ! done_stamp tools; then
	fetch "ninja-$NINJA_VERSION.zip" \
		"https://github.com/ninja-build/ninja/releases/download/v$NINJA_VERSION/ninja-linux.zip" "$NINJA_SHA256"
	unzip -o -q "$DL/ninja-$NINJA_VERSION.zip" -d "$TOOLS/bin"

	fetch "meson-$MESON_VERSION.tar.gz" \
		"https://github.com/mesonbuild/meson/releases/download/$MESON_VERSION/meson-$MESON_VERSION.tar.gz" "$MESON_SHA256"
	rm -rf "$TOOLS/meson" && mkdir -p "$TOOLS/meson"
	tar -xf "$DL/meson-$MESON_VERSION.tar.gz" -C "$TOOLS/meson" --strip-components=1
	printf '#!/bin/sh\nexec python3 "%s" "$@"\n' "$TOOLS/meson/meson.py" >"$TOOLS/bin/meson"
	chmod +x "$TOOLS/bin/meson"

	fetch "pkgconf-$PKGCONF_VERSION.tar.xz" \
		"https://distfiles.ariadne.space/pkgconf/pkgconf-$PKGCONF_VERSION.tar.xz" "$PKGCONF_SHA256"
	unpack "pkgconf-$PKGCONF_VERSION.tar.xz" pkgconf
	(
		cd "$SRC/pkgconf"
		# static, so the tool runs without its library on the loader path
		meson setup _build --prefix="$TOOLS" -Dtests=disabled --buildtype=release --default-library=static
		ninja -C _build install
	)
	ln -sf pkgconf "$TOOLS/bin/pkg-config"

	fetch "gperf-$GPERF_VERSION.tar.gz" "https://ftp.gnu.org/gnu/gperf/gperf-$GPERF_VERSION.tar.gz" "$GPERF_SHA256"
	unpack "gperf-$GPERF_VERSION.tar.gz" gperf
	(
		cd "$SRC/gperf"
		./configure --prefix="$TOOLS" >/dev/null
		make -j"$JOBS" >/dev/null
		make install >/dev/null
	)
	mark_done tools
fi

pkg-config --version >/dev/null || die "pkg-config does not run"

## NDK and the cross environment -----------------------------------------------

NDK="$WORK/android-ndk-$NDK_VERSION"
if [[ ! -d "$NDK" ]]; then
	fetch "android-ndk-$NDK_VERSION-linux.zip" \
		"https://dl.google.com/android/repository/android-ndk-$NDK_VERSION-linux.zip" "$NDK_SHA256"
	msg "Unpacking NDK"
	unzip -q "$DL/android-ndk-$NDK_VERSION-linux.zip" -d "$WORK"
fi
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN/bin:$PATH"

TRIPLE=aarch64-linux-android
export CC="$TRIPLE$API-clang"
export CXX="$TRIPLE$API-clang++"
export AR=llvm-ar RANLIB=llvm-ranlib NM=llvm-nm STRIP=llvm-strip
# 16 KB pages: required by newer Android, harmless on 4 KB devices
LDFLAGS_COMMON="-Wl,-O1,--icf=safe -Wl,-z,max-page-size=16384"
export LDFLAGS="$LDFLAGS_COMMON"
unset CFLAGS CXXFLAGS CPPFLAGS PKG_CONFIG_PATH
export PKG_CONFIG_SYSROOT_DIR="$PREFIX"
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"

# Compiler wrappers that strip the work directory from __FILE__ and debug info.
# FFmpeg records its configure line in the library, so the flag can't go there.
for w in cc:clang cxx:clang++; do
	cat >"$TOOLS/bin/nama-${w%%:*}" <<WRAPPER
#!/bin/sh
exec $TRIPLE$API-${w#*:} -ffile-prefix-map=$WORK/= "\$@"
WRAPPER
	chmod +x "$TOOLS/bin/nama-${w%%:*}"
done

# Flat prefix: /usr/local inside DESTDIR lands at the prefix root
if [[ ! -d "$PREFIX" ]]; then
	mkdir -p "$PREFIX"
	ln -s . "$PREFIX/usr"
	ln -s . "$PREFIX/local"
fi

CROSS="$PREFIX/crossfile.txt"
cat >"$CROSS" <<CROSSFILE
[built-in options]
buildtype = 'release'
default_library = 'static'
wrap_mode = 'nodownload'
prefix = '/usr/local'
# keeps the work directory's path (and so the user name) out of the binaries
c_args = ['-ffile-prefix-map=$WORK/=']
cpp_args = ['-ffile-prefix-map=$WORK/=']
c_link_args = ['-Wl,-z,max-page-size=16384']
cpp_link_args = ['-Wl,-z,max-page-size=16384']
[binaries]
c = '$CC'
cpp = '$CXX'
ar = 'llvm-ar'
nm = 'llvm-nm'
strip = 'llvm-strip'
pkg-config = 'pkg-config'
[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
CROSSFILE

# meson_build <dir> [options...]: configure, build and install into the prefix
meson_build() {
	local dir="$1"; shift
	(
		cd "$SRC/$dir"
		unset CC CXX LDFLAGS # meson takes these from the cross file
		# relative: mpv embeds its configure line, which must not carry a home path
		meson setup _build --cross-file ../../prefix/arm64/crossfile.txt "$@"
		ninja -C _build -j"$JOBS"
		DESTDIR="$PREFIX" ninja -C _build install
	)
}

## Libraries -------------------------------------------------------------------

build_mbedtls() {
	fetch "mbedtls-$MBEDTLS_VERSION.tar.bz2" \
		"https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-$MBEDTLS_VERSION/mbedtls-$MBEDTLS_VERSION.tar.bz2" "$MBEDTLS_SHA256"
	unpack "mbedtls-$MBEDTLS_VERSION.tar.bz2" mbedtls
	(
		cd "$SRC/mbedtls"
		# since 3.6.6 the default is /dev/random, which can block for no reason
		./scripts/config.py set MBEDTLS_PLATFORM_DEV_RANDOM '"/dev/urandom"'
		make -j"$JOBS" no_test CFLAGS="-O2 -fPIC"
		make DESTDIR="$PREFIX" install
	)
}

build_dav1d() {
	fetch "dav1d-$DAV1D_VERSION.tar.xz" \
		"https://downloads.videolan.org/pub/videolan/dav1d/$DAV1D_VERSION/dav1d-$DAV1D_VERSION.tar.xz" "$DAV1D_SHA256"
	unpack "dav1d-$DAV1D_VERSION.tar.xz" dav1d
	meson_build dav1d -Denable_tests=false -Denable_tools=false -Db_lto=true -Dstack_alignment=16
}

build_libxml2() {
	fetch "libxml2-$LIBXML2_VERSION.tar.gz" \
		"https://gitlab.gnome.org/GNOME/libxml2/-/archive/v$LIBXML2_VERSION/libxml2-v$LIBXML2_VERSION.tar.gz" "$LIBXML2_SHA256"
	unpack "libxml2-$LIBXML2_VERSION.tar.gz" libxml2
	meson_build libxml2 -Dminimum=true -D{push,reader,sax1,iso8859x,pattern}=enabled
}

build_ffmpeg() {
	fetch "ffmpeg-$FFMPEG_VERSION.tar.xz" \
		"https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz" "$FFMPEG_SHA256"
	unpack "ffmpeg-$FFMPEG_VERSION.tar.xz" ffmpeg
	(
		cd "$SRC/ffmpeg"
		mkdir -p _build && cd _build
		local args=(
			--target-os=android --enable-cross-compile
			--cross-prefix=$TRIPLE- --cc=nama-cc --cxx=nama-cxx
			--nm=llvm-nm --ar=llvm-ar --ranlib=llvm-ranlib --strip=llvm-strip
			--pkg-config=pkg-config
			--arch=aarch64 --cpu=armv8-a
			# relative: FFmpeg embeds this line (avcodec_configuration), no home paths in it
			--extra-cflags="-I../../../prefix/arm64/include" --extra-ldflags="-L../../../prefix/arm64/lib $LDFLAGS_COMMON"
			--prefix=/usr/local

			# LGPL: no --enable-gpl, no --enable-nonfree. version3 is needed for
			# mbedTLS (Apache-2.0), which makes FFmpeg LGPL v3 or later.
			--enable-version3
			--enable-jni --enable-mediacodec --enable-mbedtls --enable-libdav1d --enable-libxml2
			--disable-vulkan
			--enable-shared --disable-static --enable-pic

			--disable-doc --disable-programs --disable-debug
			# nothing in the player encodes, muxes or grabs devices
			--disable-muxers --disable-encoders --disable-devices
			# kept for mpv's screenshot and dump-cache commands
			--enable-encoder=mjpeg,png --enable-muxer=mov,matroska,mpegts
		)
		../configure "${args[@]}"
		grep -q 'LGPL version 3' config.h || die "FFmpeg is not configured as LGPL v3"
		make -j"$JOBS"
		make DESTDIR="$PREFIX" install
	)
}

build_freetype2() {
	fetch "freetype-$FREETYPE_VERSION.tar.xz" \
		"https://download.savannah.gnu.org/releases/freetype/freetype-$FREETYPE_VERSION.tar.xz" "$FREETYPE_SHA256"
	unpack "freetype-$FREETYPE_VERSION.tar.xz" freetype2
	meson_build freetype2 -Dbrotli=disabled -Dbzip2=disabled -Dharfbuzz=disabled -Dpng=disabled -Dzlib=internal
}

build_fribidi() {
	fetch "fribidi-$FRIBIDI_VERSION.tar.xz" \
		"https://github.com/fribidi/fribidi/releases/download/v$FRIBIDI_VERSION/fribidi-$FRIBIDI_VERSION.tar.xz" "$FRIBIDI_SHA256"
	unpack "fribidi-$FRIBIDI_VERSION.tar.xz" fribidi
	meson_build fribidi -Dtests=false -Ddocs=false -Dbin=false
}

build_harfbuzz() {
	fetch "harfbuzz-$HARFBUZZ_VERSION.tar.xz" \
		"https://github.com/harfbuzz/harfbuzz/releases/download/$HARFBUZZ_VERSION/harfbuzz-$HARFBUZZ_VERSION.tar.xz" "$HARFBUZZ_SHA256"
	unpack "harfbuzz-$HARFBUZZ_VERSION.tar.xz" harfbuzz
	meson_build harfbuzz -Dtests=disabled -Ddocs=disabled -Dutilities=disabled \
		-D{raster,vector,gpu,subset}=disabled -D{glib,gobject,cairo,icu}=disabled -Dfreetype=disabled
}

build_unibreak() {
	fetch "libunibreak-$UNIBREAK_VERSION.tar.gz" \
		"https://github.com/adah1972/libunibreak/releases/download/libunibreak_${UNIBREAK_VERSION//./_}/libunibreak-$UNIBREAK_VERSION.tar.gz" "$UNIBREAK_SHA256"
	unpack "libunibreak-$UNIBREAK_VERSION.tar.gz" unibreak
	(
		cd "$SRC/unibreak"
		mkdir -p _build && cd _build
		../configure --host=$TRIPLE --with-pic --enable-static --disable-shared --prefix=/usr/local
		make -j"$JOBS"
		make DESTDIR="$PREFIX" install
	)
}

build_fontconfig() {
	fetch "fontconfig-$FONTCONFIG_VERSION.tar.gz" \
		"https://gitlab.freedesktop.org/fontconfig/fontconfig/-/archive/$FONTCONFIG_VERSION/fontconfig-$FONTCONFIG_VERSION.tar.gz" "$FONTCONFIG_SHA256"
	unpack "fontconfig-$FONTCONFIG_VERSION.tar.gz" fontconfig
	meson_build fontconfig -D{tests,doc,tools,nls,cache-build}=disabled -Dxml-backend=libxml2
}

build_libass() {
	fetch "libass-$LIBASS_VERSION.tar.xz" \
		"https://github.com/libass/libass/releases/download/$LIBASS_VERSION/libass-$LIBASS_VERSION.tar.xz" "$LIBASS_SHA256"
	unpack "libass-$LIBASS_VERSION.tar.xz" libass
	(
		cd "$SRC/libass"
		mkdir -p _build && cd _build
		../configure --host=$TRIPLE --with-pic --enable-asm --prefix=/usr/local \
			--enable-static --disable-shared --enable-libunibreak --enable-fontconfig
		make -j"$JOBS"
		make DESTDIR="$PREFIX" install
	)
}

build_libplacebo() {
	local dir="$SRC/libplacebo"
	rm -rf "$dir"
	git clone -q --depth=1 --recurse-submodules --shallow-submodules -b "$LIBPLACEBO_VERSION" \
		https://code.videolan.org/videolan/libplacebo.git "$dir"
	local got
	got="$(git -C "$dir" rev-parse HEAD)"
	if [[ "$LIBPLACEBO_COMMIT" == PIN ]]; then
		echo "PIN libplacebo $got" >&2
	elif [[ "$got" != "$LIBPLACEBO_COMMIT" ]]; then
		die "libplacebo $LIBPLACEBO_VERSION is $got, expected $LIBPLACEBO_COMMIT"
	fi
	meson_build libplacebo -Dvulkan=disabled -Dopengl=enabled -Dglslang=disabled -Dshaderc=disabled \
		-Dlcms=disabled -Dd3d11=disabled -Ddovi=enabled -Dlibdovi=disabled -Ddemos=false -Dtests=false
	# libplacebo is C++ inside; a static link into libmpv needs the C++ runtime.
	# Not "-lstdc++" because of https://github.com/mesonbuild/meson/issues/11300
	sed -i '/^Libs:/ s|$| -lc++|' "$PREFIX/lib/pkgconfig/libplacebo.pc"
}

build_mpv() {
	fetch "mpv-$MPV_VERSION.tar.gz" \
		"https://github.com/mpv-player/mpv/archive/refs/tags/v$MPV_VERSION.tar.gz" "$MPV_SHA256"
	unpack "mpv-$MPV_VERSION.tar.gz" mpv
	meson_build mpv --default-library shared \
		-Dgpl=false \
		-Dlibmpv=true -Dcplayer=false -Dbuild-date=false -Dtests=false -Dfuzzers=false \
		-D{manpage,html,pdf}-build=disabled \
		-Dlua=disabled -Djavascript=disabled -Dvulkan=disabled \
		-D{libarchive,uchardet,rubberband,zimg,lcms2,libbluray,dvdnav,cdda,sdl2-audio,sdl2-video}=disabled \
		-Dandroid-media-ndk=enabled -Degl-android=enabled -Dgl=enabled \
		-D{audiotrack,aaudio,opensles}=enabled
	grep -q '#define HAVE_GPL 0' "$SRC/mpv/_build/config.h" || die "mpv is configured as GPL"
}

for lib in mbedtls dav1d libxml2 ffmpeg freetype2 fribidi harfbuzz unibreak fontconfig libass libplacebo mpv; do
	if ! done_stamp "$lib"; then
		msg "Building $lib"
		"build_$lib"
		mark_done "$lib"
	fi
done

## Copy into the app -----------------------------------------------------------

msg "Copying libraries to $JNILIBS"
mkdir -p "$JNILIBS" "$INCLUDE"
rm -f "$JNILIBS"/*.so
libs=(libmpv.so libavcodec.so libavdevice.so libavfilter.so libavformat.so libavutil.so libswresample.so libswscale.so)
for l in "${libs[@]}"; do
	[[ -f "$PREFIX/lib/$l" ]] || continue
	# stripped copies; the prefix keeps the symbols for debugging
	llvm-strip --strip-unneeded -o "$JNILIBS/$l" "$PREFIX/lib/$l"
done
llvm-strip --strip-unneeded -o "$JNILIBS/libc++_shared.so" "$TOOLCHAIN/sysroot/usr/lib/$TRIPLE/libc++_shared.so"
cp "$PREFIX"/include/mpv/*.h "$INCLUDE/"

# Every library libmpv pulls in must be one we ship or one Android has
system_libs="libc.so libm.so libdl.so liblog.so libandroid.so libEGL.so libGLESv2.so libGLESv3.so libmediandk.so libOpenSLES.so libaaudio.so libz.so"
for so in "$JNILIBS"/*.so; do
	for need in $(llvm-readelf -d "$so" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p'); do
		[[ -f "$JNILIBS/$need" ]] || [[ " $system_libs " == *" $need "* ]] || die "$(basename "$so") needs $need, which is not shipped"
	done
done

# Release files must not name the person who built them (AGENTS.md)
for so in "$JNILIBS"/*.so; do
	if grep -qaF -e "$HOME" -e "$WORK" "$so" || { [[ -n "${USER:-}" ]] && grep -qaw -e "$USER" "$so"; }; then
		die "$(basename "$so") contains a local path or the user name"
	fi
done

msg "License check"
grep -o 'LGPL version 3 or later' "$SRC/ffmpeg/_build/config.h" | head -1 | sed 's/^/FFmpeg: /'
grep -q '#define HAVE_GPL 0' "$SRC/mpv/_build/config.h" && echo "mpv: HAVE_GPL 0 (-Dgpl=false)"

msg "SHA-256"
(cd "$JNILIBS" && for so in *.so; do printf '%s  %8s KB  %s\n' "$(sha256sum "$so" | cut -d' ' -f1)" "$(( $(stat -c %s "$so") / 1024 ))" "$so"; done)
