package com.larvalabs.svgandroid;

import org.xml.sax.InputSource;

/**
 * Created by James on 02/05/2014.
 */
public class PathBuilder extends PaintBuilder<PathBuilder> {

    public SVGPaths build() {
        if (!hasData()) {
            throw new IllegalStateException("SVG input not specified. Call one of the readFrom...() methods first.");
        }

        try {
            final PathHandler handler = new PathHandler();
            applyPaintSettings(handler);
            final InputSource source = openData();
            return com.larvalabs.svgandroid.SVGParser.parse(source, handler);
        }
        finally {
            closeData();
        }
    }
}
