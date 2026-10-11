#!/usr/bin/env bash
# Готовит фон меню для мода: бесшовная петля, 60 кадров/с (интерполяция движения),
# масштаб до 720p/1080p, JPEG-кадры + video.json → menu-video.zip
set -euo pipefail
SRC=${1:-release/menu-video.mp4}
OUT=${2:-dist/menu-video.zip}
FPS=60
WORK=$(mktemp -d)
SH=$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of default=nw=1:nk=1 "$SRC")
DUR=$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$SRC")
if [ "$SH" -ge 1000 ]; then W=1920; H=1080; else W=1280; H=720; fi
X=1.0                                        # длина перехода конец→начало, с
OFF=$(awk "BEGIN{printf \"%.3f\", $DUR - 2*$X}")
ffmpeg -v error -y -i "$SRC" -filter_complex "\
[0:v]trim=start=$X,setpts=PTS-STARTPTS,fps=30[main];\
[0:v]trim=end=$X,setpts=PTS-STARTPTS,fps=30[head];\
[main][head]xfade=transition=fade:duration=$X:offset=$OFF,\
minterpolate=fps=$FPS:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1:scd=fdiff:scd_threshold=4,\
scale=$W:$H:flags=lanczos,unsharp=5:5:0.6:5:5:0.0,format=yuvj420p[v]" \
  -map "[v]" -q:v 3 "$WORK/f%04d.jpg"
N=$(ls "$WORK" | grep -c '\.jpg$')
printf '{"fps":%d,"frames":%d,"w":%d,"h":%d}\n' $FPS "$N" $W $H > "$WORK/video.json"
mkdir -p "$(dirname "$OUT")"; OUT=$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")
rm -f "$OUT"
(cd "$WORK" && zip -q -0 -r "$OUT" .)
echo "menu video: $N frames ${W}x${H} @${FPS}, $(du -h "$OUT" | cut -f1)"
