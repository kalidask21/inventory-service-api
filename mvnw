#!/bin/sh
set -e
MVN_EXEC=$(find "$HOME/.m2/wrapper/dists" -name "mvn" -path "*/bin/mvn" 2>/dev/null | head -1)
if [ -z "$MVN_EXEC" ]; then
  WRAPPER_DIR="$(cd "$(dirname "$0")" && pwd)/.mvn/wrapper"
  PROPERTIES_FILE="$WRAPPER_DIR/maven-wrapper.properties"
  DISTRIBUTION_URL=$(grep "^distributionUrl" "$PROPERTIES_FILE" | sed 's/distributionUrl=//')
  DIST_NAME=$(basename "$DISTRIBUTION_URL" .zip)
  echo "Downloading Maven from $DISTRIBUTION_URL ..."
  mkdir -p "$HOME/.m2/wrapper/dists"
  TMP_ZIP="$HOME/.m2/wrapper/dists/${DIST_NAME}.zip"
  curl -fsSL "$DISTRIBUTION_URL" -o "$TMP_ZIP"
  unzip -qo "$TMP_ZIP" -d "$HOME/.m2/wrapper/dists"
  rm -f "$TMP_ZIP"
  MVN_EXEC=$(find "$HOME/.m2/wrapper/dists" -name "mvn" -path "*/bin/mvn" 2>/dev/null | head -1)
fi
exec "$MVN_EXEC" "$@"
