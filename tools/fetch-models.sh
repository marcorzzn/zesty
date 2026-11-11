#!/usr/bin/env sh
# Scarica i modelli di scontorno usati da Zesty e ne verifica l'impronta SHA-256.
# I modelli non sono in git (pesano ~51 MB); senza di loro l'app usa il motore classico.
set -e
DIR="$(dirname "$0")/../app/src/main/assets/models"
mkdir -p "$DIR"
fetch() { # url, file, sha256
  if [ -f "$DIR/$2" ] && echo "$3  $DIR/$2" | sha256sum -c --status; then echo "ok  $2"; return; fi
  curl -sSL -o "$DIR/$2.part" "$1"
  echo "$3  $DIR/$2.part" | sha256sum -c --status || { echo "impronta errata: $2"; rm -f "$DIR/$2.part"; exit 1; }
  mv "$DIR/$2.part" "$DIR/$2"; echo "scaricato  $2"
}
fetch https://huggingface.co/xrds/isnet-general-onnx-int8/resolve/main/onnx/model_quantized.onnx isnet_general.onnx 3b21a6706dc8d6e4ba9f5b31ebc6940f6c785b58862e27bb25daa9dd4424b87f
fetch https://huggingface.co/Xenova/modnet/resolve/main/onnx/model_quantized.onnx modnet_portrait.onnx 92e49898c3e05a6d7a944fc67a8cb87c4aad754ffb6ebd949528c7d1105fee3a
