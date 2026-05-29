#!/bin/sh
#
# Gradle start up script for UN*X (official format)
#

# Resolve APP_HOME
app_path=$0
while [ -h "$app_path" ] ; do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in
      /*)   app_path=$link ;;
      *)    app_path=${app_path%/*}/$link ;;
    esac
done
APP_HOME=$( cd "${app_path%/*}/.." && pwd -P ) 2>/dev/null || APP_HOME=$(cd "$(dirname "$0")" && pwd)

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# JVM options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

warn() { echo "$*" >&2; }
die()  { echo; echo "$*" >&2; echo; exit 1; }

# OS detection
cygwin=false; darwin=false; msys=false; nonstop=false
case "$(uname)" in
  CYGWIN*)  cygwin=true  ;;
  Darwin*)  darwin=true  ;;
  MSYS*|MINGW*) msys=true ;;
  NONSTOP*) nonstop=true ;;
esac

CLASSPATH="${APP_HOME}/gradle/wrapper/gradle-wrapper.jar"

# Find java
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
    JAVACMD="java"
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and 'java' was not found in PATH."
fi

# Max FDs
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    MAX_FD=$(ulimit -H -n 2>/dev/null || echo "")
    case $MAX_FD in
      ''|soft) ;;
      *) ulimit -n "$MAX_FD" 2>/dev/null || warn "Could not set max file descriptors" ;;
    esac
fi

eval set -- \
  $DEFAULT_JVM_OPTS \
  $JAVA_OPTS \
  $GRADLE_OPTS \
  "\"-Dorg.gradle.appname=$APP_BASE_NAME\"" \
  -classpath "\"$CLASSPATH\"" \
  org.gradle.wrapper.GradleWrapperMain \
  '"$@"'

exec "$JAVACMD" "$@"
