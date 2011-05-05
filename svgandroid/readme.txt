S V G   F O R   A N D R O I D
=============================

See NOTICE for license.

Requires the Android SDK and Apache Ant installed to compile.

Create a file in this directory called 'local.properties' with the single line:

sdk.dir=PATH/TO/ANDROID_SDK

(Replace "PATH/TO/ANDROID_SDK" with the actual path to your Android SDK install).

Build a jar file with:

ant jar

Or, to include svgandroid as a library project in your own Android project, see here:

http://developer.android.com/guide/developing/projects/projects-cmdline.html#ReferencingLibraryProject