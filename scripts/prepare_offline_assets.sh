#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AS="$ROOT/app/src/main/assets"
DATA="$AS/data"
QURAN_PAGES="$AS/quran-pages"
RAW="$ROOT/app/src/main/res/raw"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Build-time sources. They are downloaded only by GitHub Actions; the final APK is local-only.
DATA_URL="https://codeload.github.com/mohammed-2-5/islamic-library-data/zip/refs/heads/master"
QURAN_IMAGES_URL="https://codeload.github.com/tarekeldeeb/madina_images/zip/refs/heads/w1024"
ADHAN_URL="https://upload.wikimedia.org/wikipedia/commons/e/e7/Adhan.ogg"

rm -rf "$DATA" "$QURAN_PAGES"
mkdir -p "$DATA/azkar" "$DATA/hadith" "$DATA/forties" "$DATA/names_of_allah" \
  "$DATA/tafseer" "$DATA/prophet_stories/quizzes" "$DATA/quran/chapters/ar" \
  "$QURAN_PAGES" "$RAW" "$AS/audio"

fetch() {
  local url="$1" dest="$2" label="$3"
  echo "Downloading: $label"
  curl -fL --retry 8 --retry-delay 2 --retry-all-errors \
    --connect-timeout 30 --max-time 900 -sS "$url" -o "$dest.part"
  test -s "$dest.part"
  mv "$dest.part" "$dest"
}

# 1) One archive for all Islamic JSON content; avoids sparse-checkout and per-file CDN 404s.
echo '1/3 — Download Islamic data archive'
fetch "$DATA_URL" "$TMP/islamic-data.zip" 'Islamic data archive'
unzip -q "$TMP/islamic-data.zip" -d "$TMP/islamic-data-root"
DATA_ROOT="$(find "$TMP/islamic-data-root" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
test -n "$DATA_ROOT"

# Required Quran metadata and Arabic chapters.
cp -a "$DATA_ROOT/azkar/." "$DATA/azkar/"
cp "$DATA_ROOT/hadith/bukhari.json" "$DATA/hadith/"
cp "$DATA_ROOT/hadith/muslim.json" "$DATA/hadith/"
cp "$DATA_ROOT/forties/nawawi40.json" "$DATA/forties/"
cp "$DATA_ROOT/forties/qudsi40.json" "$DATA/forties/"
cp "$DATA_ROOT/forties/shahwaliullah40.json" "$DATA/forties/"
cp "$DATA_ROOT/names_of_allah/names_of_allah.json" "$DATA/names_of_allah/"
cp "$DATA_ROOT/tafseer/muyassar.json" "$DATA/tafseer/"
cp "$DATA_ROOT/prophet_stories/index.json" "$DATA/prophet_stories/"
cp "$DATA_ROOT/prophet_stories/"*.json "$DATA/prophet_stories/"
cp -a "$DATA_ROOT/prophet_stories/quizzes/." "$DATA/prophet_stories/quizzes/"
cp "$DATA_ROOT/quran/chapters/ar/"*.json "$DATA/quran/chapters/ar/"
mkdir -p "$DATA/quran"
for f in qcf_v2_pages.json mushaf_pages.json qcf_surah_starts.json quran_segments.json quran_symbols.json quran_duas.json hizb_quarters.json; do
  cp "$DATA_ROOT/quran/$f" "$DATA/quran/$f"
done

# 2) Compact 1024px Madinah Mushaf PNGs, pages 001..604.
echo '2/3 — Download 604 Madinah Mushaf PNG pages'
fetch "$QURAN_IMAGES_URL" "$TMP/madina-images.zip" 'Madinah Mushaf page bundle'
unzip -q "$TMP/madina-images.zip" -d "$TMP/madina-images-root"
IMAGE_ROOT="$(find "$TMP/madina-images-root" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
test -n "$IMAGE_ROOT"
find "$IMAGE_ROOT" -type f -name 'w1024_page*.png' -print0 | while IFS= read -r -d '' f; do
  page="$(basename "$f" | sed -E 's/^w1024_page([0-9]{3})\.png$/\1.png/')"
  test "$page" != "$(basename "$f")"
  cp "$f" "$QURAN_PAGES/$page"
done

test "$(find "$QURAN_PAGES" -maxdepth 1 -type f -name '*.png' | wc -l)" -eq 604

# 3) Licensed/cleared local adhan source. Keep a copy for Native Android and one for WebView fallback.
echo '3/3 — Download local adhan audio'
fetch "$ADHAN_URL" "$RAW/adhan.ogg" 'local adhan'
cp "$RAW/adhan.ogg" "$AS/adhan.ogg"
test -s "$RAW/adhan.ogg"
test -s "$AS/adhan.ogg"
cp "$ROOT/app/src/main/res/raw/notification.wav" "$AS/audio/notification.wav"

cat > "$AS/OFFLINE_CONTENT.txt" <<'EOT'
YAWMY v1.0.8 — offline content bundle

Bundled inside the APK at build time:
- 604 Madinah Mushaf PNG pages
- Arabic Quran chapters and page maps
- Sahih al-Bukhari
- Sahih Muslim
- Three 40-hadith collections
- Azkar and dua JSON collections
- Tafseer Muyassar
- Prophet stories and quizzes
- 99 Names of Allah
- Native local adhan + notification tone

Runtime rule:
- The WebView content uses only local app assets.
- No Quran/hadith/azkar/story content is fetched from the internet at runtime.
EOT

cat > "$AS/ATTRIBUTIONS_OFFLINE.txt" <<'EOT'
Mushaf pages:
tarekeldeeb/madina_images — w1024 branch
https://github.com/tarekeldeeb/madina_images

Islamic datasets:
mohammed-2-5/islamic-library-data
https://github.com/mohammed-2-5/islamic-library-data

Adhan:
Adhan.ogg by Aishatu98 — Wikimedia Commons — CC0 1.0
https://commons.wikimedia.org/wiki/File:Adhan.ogg
EOT

echo '--- OFFLINE BUNDLE SUMMARY ---'
echo "Mushaf PNG pages: $(find "$QURAN_PAGES" -maxdepth 1 -type f -name '*.png' | wc -l)"
echo "Quran Arabic chapters: $(find "$DATA/quran/chapters/ar" -maxdepth 1 -type f -name '*.json' | wc -l)"
echo "Azkar JSON: $(find "$DATA/azkar" -maxdepth 1 -type f -name '*.json' | wc -l)"
echo "Prophet story JSON: $(find "$DATA/prophet_stories" -maxdepth 1 -type f -name '*.json' | wc -l)"
echo "Offline source size:"
du -sh "$AS"
AS_BYTES="$(du -sb "$AS" | cut -f1)"
if [ "$AS_BYTES" -lt 30000000 ]; then
  echo "ERROR: offline bundle is unexpectedly small (<30 MB)."
  exit 1
fi

echo 'Offline assets prepared successfully.'
