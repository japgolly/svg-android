This is forked from the awesome but unmaintained:
http://code.google.com/p/svg-android/

Changes
=======
* Mavenised.
* Added SVGBuilder to allow easy specification of SVG parsing & rendering options.
* ColorFilters can now be applied.
* This library now works with Robolectric.
* SVG viewBox attribute is now handled.
* Numbers with exponents are handled.
* Performance enhancements.
* Has most community patches applied. Great work to
  [josefpavlik](https://github.com/josefpavlik/svg-android) and
  [mrn](https://github.com/mrn/svg-android)

Maven
=====
Add this to your Android project's pom.xml:
```xml
<dependency>
  <groupId>com.github.japgolly.android</groupId>
  <artifactId>svg-android</artifactId>
	<version>2.0.1</version>
</dependency>
```

Usage
=====

Firstly, store your SVGs in `res/raw` or `assets`.

```java
// Load and parse a SVG
SVG svg = new SVGBuilder()
            .readFromResource(getResources(), R.raw.someSvgResource) // if svg in res/raw
            .readFromAsset(getAssets(), "somePicture.svg")           // if svg in assets
            // .setWhiteMode(true) // draw fills in white, doesn't draw strokes
            // .setColorSwap(0xFF008800, 0xFF33AAFF) // swap a single colour
            // .setColorFilter(filter) // run through a colour filter
            // .set[Stroke|Fill]ColorFilter(filter) // apply a colour filter to only the stroke or fill
            .build();

// Draw onto a canvas
canvas.drawPicture(svg.getPicture());

// Turn into a drawable
Drawable drawable = svg.createDrawable();
// drawable.draw(canvas);
// imageView.setImageDrawable(drawable);
```
