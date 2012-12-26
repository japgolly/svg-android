package com.larvalabs.svgandroid;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.ColorFilter;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.InputSource;

import com.larvalabs.svgandroid.SVGParser.SVGHandler;

/**
 * Builder for reading SVGs. Specify input, specify any parsing options (optional), then call {@link #build()} to parse
 * and return a {@link SVG}.
 * 
 * @since 24/12/2012
 */
public class SVGBuilder {
	private InputStream data;
	private Integer searchColor = null;
	private Integer replaceColor = null;
	private ColorFilter colorFilter = null;
	private boolean whiteMode = false;
	private boolean closeInputStream = true;

	/**
	 * Parse SVG data from an input stream.
	 * 
	 * @param svgData the input stream, with SVG XML data in UTF-8 character encoding.
	 * @return the parsed SVG.
	 */
	public SVGBuilder getSVGFromInputStream(InputStream svgData) {
		this.data = svgData;
		return this;
	}

	/**
	 * Parse SVG data from a string.
	 * 
	 * @param svgData the string containing SVG XML data.
	 */
	public SVGBuilder getSVGFromString(String svgData) {
		this.data = new ByteArrayInputStream(svgData.getBytes());
		return this;
	}

	/**
	 * Parse SVG data from an Android application resource.
	 * 
	 * @param resources the Android context resources.
	 * @param resId the ID of the raw resource SVG.
	 */
	public SVGBuilder getSVGFromResource(Resources resources, int resId) {
		this.data = resources.openRawResource(resId);
		return this;
	}

	/**
	 * Parse SVG data from an Android application asset.
	 * 
	 * @param assetMngr the Android asset manager.
	 * @param svgPath the path to the SVG file in the application's assets.
	 * @throws IOException if there was a problem reading the file.
	 */
	public SVGBuilder getSVGFromAsset(AssetManager assetMngr, String svgPath) throws IOException {
		this.data = assetMngr.open(svgPath);
		return this;
	}

	public SVGBuilder disableColorSwap() {
		searchColor = replaceColor = null;
		return this;
	}

	public SVGBuilder setColorSwap(int searchColor, int replaceColor) {
		this.searchColor = searchColor;
		this.replaceColor = replaceColor;
		return this;
	}

	public SVGBuilder setWhiteMode(boolean whiteMode) {
		this.whiteMode = whiteMode;
		return this;
	}

	public SVGBuilder setColorFilter(ColorFilter colorFilter) {
		this.colorFilter = colorFilter;
		return this;
	}

	/**
	 * @param closeInputStream Whether or not to close the input stream after reading (ie. after calling
	 *            {@link #build()}.
	 */
	public SVGBuilder setCloseInputStream(boolean closeInputStream) {
		this.closeInputStream = closeInputStream;
		return this;
	}

	/**
	 * @return the parsed SVG.
	 * @throws SVGParseException if there is an error while parsing.
	 */
	public SVG build() throws SVGParseException {

		if (data == null) {
			throw new IllegalStateException("SVG input not specified. Call one of the getSVGFrom...() methods first.");
		}

		try {
			SVGHandler handler = new SVGHandler();
			handler.setColorSwap(searchColor, replaceColor);
			handler.setWhiteMode(whiteMode);
			if (colorFilter != null) {
				handler.strokePaint.setColorFilter(colorFilter);
				handler.fillPaint.setColorFilter(colorFilter);
			}

			return SVGParser.parse(new InputSource(data), handler);

		} finally {
			if (closeInputStream) {
				try {
					data.close();
				} catch (IOException e) {
					Log.e(SVGParser.TAG, "Error closing SVG input stream.", e);
				}
			}
		}
	}
}
