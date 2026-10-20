#!/usr/bin/env bash
# Turns the files scripts/build.sh made into a Maven Central Portal bundle:
#
#   dist/carejobs-<version>-bundle.zip
#     now/carejobs/carejobs/<version>/
#       carejobs-<version>.jar, -sources.jar, -javadoc.jar, .pom
#       and for each: .asc (detached armored GPG signature), .md5, .sha1, .sha256, .sha512
#
# Usage: GPG_KEY=<fingerprint> scripts/bundle.sh < passphrase-file
#
# The passphrase of the signing key is read from standard input (one line).
# Set GNUPGHOME first if the key is not in the default keyring. Upload the zip
# at https://central.sonatype.com/publishing or with the Portal API.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${GPG_KEY:?set GPG_KEY to the fingerprint of the signing key}"
IFS= read -r passphrase || true
if [ -z "${passphrase:-}" ]; then
  echo "bundle: no passphrase on standard input" >&2
  exit 1
fi

version="$(sed -n 's:^  <version>\(.*\)</version>$:\1:p' pom.xml | head -1)"
dir="now/carejobs/carejobs/$version"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/$dir" dist

for suffix in ".jar" "-sources.jar" "-javadoc.jar" ".pom"; do
  file="carejobs-$version$suffix"
  test -f "build/$file" || { echo "bundle: build/$file is missing, run scripts/build.sh" >&2; exit 1; }
  cp "build/$file" "$stage/$dir/$file"
done

(
  cd "$stage/$dir"
  for file in *.jar *.pom; do
    printf '%s' "$passphrase" | gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 0 \
      --local-user "$GPG_KEY" --armor --detach-sign --output "$file.asc" "$file"
    gpg --batch --quiet --verify "$file.asc" "$file" 2>/dev/null || { echo "bundle: signature of $file does not verify" >&2; exit 1; }
    for algo in md5 sha1 sha256 sha512; do
      python3 -I -c "import hashlib,sys; print(hashlib.new(sys.argv[1], open(sys.argv[2],'rb').read()).hexdigest(), end='')" "$algo" "$file" > "$file.$algo"
    done
  done
)

rm -f "dist/carejobs-$version-bundle.zip"
(cd "$stage" && zip -q -r -X "$OLDPWD/dist/carejobs-$version-bundle.zip" io)
unzip -l "dist/carejobs-$version-bundle.zip"
