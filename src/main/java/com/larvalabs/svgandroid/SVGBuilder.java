package com.larvalabs.svgandroid;

import org.xml.sax.InputSource;

/**
 * Builder for reading SVGs. Specify input, specify any parsing options (optional), then call {@link #build()} to parse
 * and return a {@link com.larvalabs.svgandroid.SVG}.
 *
 * @since 24/12/2012
 */
public class SVGBuilder extends PaintBuilder<SVGBuilder> {

    /**
     * Loads, reads, parses the SVG (or SVGZ).
     *
     * @return the parsed SVG.
     * @throws com.larvalabs.svgandroid.SVGParseException if there is an error while parsing.
     */
    public com.larvalabs.svgandroid.SVG build() throws SVGParseException {
        if (!hasData()) {
            throw new IllegalStateException("SVG input not specified. Call one of the readFrom...() methods first.");
        }

        try {
            final SVGHandler handler = new SVGHandler();
            applyPaintSettings(handler);

            final InputSource source = openData();
            final com.larvalabs.svgandroid.SVG svg = com.larvalabs.svgandroid.SVGParser.parse(source, handler);
            return svg;

        }
        finally {
            closeData();
        }
    }
}
