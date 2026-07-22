#!/bin/bash
set -e
M2=$HOME/.m2/repository
FM="https://maven.minecraftforge.net"
MC="https://repo1.maven.org/maven2"

download() {
    local group=$1; local module=$2; local version=$3; local repo=$4; local classifier=${5:-}
    local groupPath=$(echo "$group" | tr '.' '/')
    local dir="$M2/$groupPath/$module/$version"
    mkdir -p "$dir"
    local baseName="$module-$version"
    [ -n "$classifier" ] && baseName="$baseName-$classifier"
    for ext in pom jar module; do
        local url="$repo/$groupPath/$module/$version/$baseName.$ext"
        local target="$dir/$baseName.$ext"
        if [ ! -f "$target" ]; then
            http=$(curl -sL --max-time 60 -o "$target" -w "%{http_code}" "$url" || true)
            if [ "$http" != "200" ]; then
                rm -f "$target"
            else
                echo "  OK: $baseName.$ext ($http, $(stat -c%s "$target" 2>/dev/null || echo 0) bytes)"
            fi
        else
            echo "  CACHED: $baseName.$ext"
        fi
    done
}

echo "=== Downloading ForgeGradle direct dependencies ==="
download net.minecraftforge.gradle ForgeGradle 6.0.24 $FM
download net.minecraftforge artifactural 3.0.18 $FM
download net.minecraftforge unsafe 0.2.0 $FM
download net.minecraftforge srgutils 0.5.10 $FM
download net.minecraftforge DiffPatch 2.0.12 $FM all
download net.minecraftforge JarJarMetadata 0.3.19 $FM
download net.minecraftforge JarJarSelector 0.3.19 $FM
download commons-io commons-io 2.11.0 $MC
download com.google.code.gson gson 2.10.1 $MC
download com.google.guava guava 31.1-jre $MC
download de.siegmar fastcsv 2.2.1 $MC
download org.apache.maven maven-artifact 3.9.1 $MC
download org.apache.httpcomponents httpclient 4.5.14 $MC
echo "=== Done ==="
