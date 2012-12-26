#!/bin/bash

d=src/main/java/com/larvalabs/svgandroid
f=SVGParser
e=.java

git show orig-formatted:$d/$f$e  > $f-orig$e
git show mrn-formatted:$d/$f$e   > $f-mrn$e
git show josef-formatted:$d/$f$e > $f-josef$e
git show master:$d/$f$e          > $f-master$e

