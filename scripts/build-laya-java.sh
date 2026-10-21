#!/usr/bin/env bash
# Build laya-java (not on Maven Central) at the pinned commit and vendor it into backend/lib
# as a file-based Maven repository. Needs JDK 17+. Run once; the jar is committed.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/laya-common.sh
VERSION=0.1.0-${LAYA_COMMIT:0:7}

laya_checkout
(cd "$LAYA_SRC/laya-java" && ./gradlew -q -PlayaJavaVersion="$VERSION" :laya-java:jar)
JAR=$(ls "$LAYA_SRC"/laya-java/laya-java/build/libs/laya-java-"$VERSION".jar)
./mvnw -q install:install-file -Dfile="$JAR" -DgroupId=com.convaiinnovations -DartifactId=laya-java \
  -Dversion="$VERSION" -Dpackaging=jar -DgeneratePom=true -DlocalRepositoryPath=backend/lib
echo "vendored $JAR -> backend/lib"
