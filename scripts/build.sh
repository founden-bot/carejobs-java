#!/usr/bin/env bash
# Builds and tests the library with the JDK alone (no Maven needed):
#
#   build/carejobs-<version>.jar           the library (a named module, Java 11 or newer)
#   build/carejobs-<version>-sources.jar   the sources
#   build/carejobs-<version>-javadoc.jar   the API documentation
#   build/carejobs-<version>.pom           a copy of pom.xml
#
# Usage: scripts/build.sh [--live]
#
# Runs the offline tests always, and the live tests against https://carejobs.now
# with --live. Needs JDK 17 or newer to run (for jar --date), and compiles for
# Java 11 with --release 11. The version is read from pom.xml and must match
# CareJobsClient.VERSION.
set -euo pipefail
cd "$(dirname "$0")/.."

version="$(sed -n 's:^  <version>\(.*\)</version>$:\1:p' pom.xml | head -1)"
code_version="$(sed -n 's/.*String VERSION = "\(.*\)";/\1/p' src/main/java/now/carejobs/CareJobsClient.java)"
if [ -z "$version" ] || [ "$version" != "$code_version" ]; then
  echo "build: pom.xml says $version but CareJobsClient.VERSION is $code_version" >&2
  exit 1
fi
stamp="2026-10-20T00:00:00Z" # fixed, so the jars do not depend on when they were built

rm -rf build
mkdir -p build/classes/META-INF build/test-classes build/javadoc build/meta

echo "compiling $version for Java 11"
javac --release 11 -Xlint:all -Werror -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java' | sort)
cp LICENSE build/classes/META-INF/LICENSE

echo "testing"
javac --release 11 -Xlint:all -encoding UTF-8 -cp build/classes -d build/test-classes $(find src/test/java -name '*.java' | sort)
java -cp build/classes:build/test-classes now.carejobs.OfflineTest src/test/resources/fixtures | tail -1
if [ "${1:-}" = "--live" ]; then
  java -cp build/classes:build/test-classes now.carejobs.LiveTest | tail -1
fi

cat > build/meta/MANIFEST.MF <<EOF
Implementation-Title: carejobs
Implementation-Version: $version
Implementation-Vendor: O-mega Enterprise, Inc.
Implementation-URL: https://github.com/founden-bot/carejobs-java
EOF

jar --create --date="$stamp" --file "build/carejobs-$version.jar" --manifest build/meta/MANIFEST.MF -C build/classes .

mkdir -p build/sources/META-INF
cp -R src/main/java/. build/sources/
cp LICENSE build/sources/META-INF/LICENSE
jar --create --date="$stamp" --file "build/carejobs-$version-sources.jar" -C build/sources .

javadoc --release 11 -quiet -Xdoclint:all -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 \
  -notimestamp -d build/javadoc -doctitle "CareJobs.Now Java client $version" -windowtitle "carejobs $version" \
  $(find src/main/java -name '*.java' | sort)
jar --create --date="$stamp" --file "build/carejobs-$version-javadoc.jar" -C build/javadoc .

cp pom.xml "build/carejobs-$version.pom"

echo
ls -l build/*.jar build/*.pom
