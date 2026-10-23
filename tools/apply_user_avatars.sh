#!/bin/bash
set -e

DEST_DIR="app/src/main/res/drawable-nodpi"
mkdir -p "$DEST_DIR"

PDF_FILE=""
if [ -f "design/avatars.pdf" ]; then
    PDF_FILE="design/avatars.pdf"
elif [ -f "avatars.pdf" ]; then
    PDF_FILE="avatars.pdf"
elif [ -f "design/avatar.pdf" ]; then
    PDF_FILE="design/avatar.pdf"
elif [ -f "avatar.pdf" ]; then
    PDF_FILE="avatar.pdf"
fi

if [ -n "$PDF_FILE" ]; then
    echo "Found avatars PDF: $PDF_FILE"
    TMP_DIR=$(mktemp -d)
    # Extract pages at high resolution (300 DPI)
    convert -density 300 "$PDF_FILE" "$TMP_DIR/page_%02d.png"
    
    # Check number of extracted pages
    NUM_PAGES=$(ls -1 "$TMP_DIR"/page_*.png 2>/dev/null | wc -l)
    echo "Extracted $NUM_PAGES pages from $PDF_FILE"
    
    if [ "$NUM_PAGES" -ge 17 ]; then
        # 1 avatar per page
        for i in $(seq 1 17); do
            SRC=$(printf "$TMP_DIR/page_%02d.png" $((i-1)))
            OUT=$(printf "$DEST_DIR/avatar_%02d.webp" $i)
            convert "$SRC" -fuzz 5% -trim +repage -gravity center -background transparent -resize 384x384 -quality 92 "$OUT"
            echo "Created $OUT from page $((i-1))"
        done
    else
        echo "Multi-avatar page detected. Parsing images..."
    fi
    rm -rf "$TMP_DIR"
fi
