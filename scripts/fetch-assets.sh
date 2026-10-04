#!/usr/bin/env bash
# Descarca bootstrap-ul Termux (ultimul) + proot + libtalloc in app/src/main/assets
set -euo pipefail
A=app/src/main/assets
mkdir -p "$A/proot"
W=$(mktemp -d)

gh api repos/termux/termux-packages/releases --paginate -q '.[].tag_name' > "$W/tags.txt"
grep '^bootstrap-' "$W/tags.txt" > "$W/boot.txt" || true
TAG=$(grep -E 'apt[.-]android-7' "$W/boot.txt" | awk 'NR==1' || true)
[ -n "$TAG" ] || TAG=$(awk 'NR==1' "$W/boot.txt")
echo "Bootstrap tag: $TAG"
curl -fL "https://github.com/termux/termux-packages/releases/download/${TAG//+/%2B}/bootstrap-aarch64.zip" \
  -o "$A/bootstrap-aarch64.zip"

REPO=https://packages-cf.termux.dev/apt/termux-main
curl -fsSL "$REPO/dists/stable/main/binary-aarch64/Packages" -o "$W/Packages"
deb_path() {
  awk -v p="$1" 'BEGIN{RS="";FS="\n"} {n=0;f=""; for(i=1;i<=NF;i++){ if($i=="Package: " p) n=1; if($i ~ /^Filename: /) f=substr($i,11)} if(n) print f}' "$W/Packages"
}
for pkg in proot libtalloc libandroid-shmem; do
  curl -fsSL "$REPO/$(deb_path $pkg)" -o "$W/$pkg.deb"
  mkdir -p "$W/$pkg" && (cd "$W/$pkg" && ar x ../$pkg.deb && tar xf data.tar.*)
done

mkdir -p "$A/proot/lib"
cp "$(find "$W/proot" -type f -name proot | awk 'NR==1')" "$A/proot/proot"
cp "$(find "$W/proot" -type f -path '*libexec/proot/loader' | awk 'NR==1')" "$A/proot/loader"
# toate bibliotecile .so din pachetele ajutatoare (urmeaza symlink-urile)
for pkg in libtalloc libandroid-shmem; do
  find "$W/$pkg" -path '*/usr/lib/*' -name '*.so*' | while read -r f; do
    cp -L "$f" "$A/proot/lib/$(basename "$f")"
  done
done

echo "== Dependente (NEEDED) proot =="
readelf -d "$A/proot/proot" | grep NEEDED || true
echo "== Biblioteci incluse =="
ls -la "$A" "$A/proot" "$A/proot/lib"
