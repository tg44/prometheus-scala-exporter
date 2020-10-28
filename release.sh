#!/bin/sh


git checkout master

rm -rf xyz

VERSION=`sbt 'inspect actual version' | grep "Setting: java.lang.String" | cut -d '=' -f2 | tr -d ' '`
rm -rf ~/.m2/repository/xyz/tg44/prometheus-scala-exporter_2.12/$VERSION
rm -rf ~/.m2/repository/xyz/tg44/prometheus-scala-exporter_2.13/$VERSION

sbt clean +publishM2

mkdir -p xyz/tg44/prometheus-scala-exporter_2.12/$VERSION/
cp -R ~/.m2/repository/xyz/tg44/prometheus-scala-exporter_2.12/$VERSION/ xyz/tg44/prometheus-scala-exporter_2.12/$VERSION/
mkdir -p xyz/tg44/prometheus-scala-exporter_2.13/$VERSION/
cp -R ~/.m2/repository/xyz/tg44/prometheus-scala-exporter_2.13/$VERSION/ xyz/tg44/prometheus-scala-exporter_2.13/$VERSION/

git checkout releases
git status
git add -A
git commit

