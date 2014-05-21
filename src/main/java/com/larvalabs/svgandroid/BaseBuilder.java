package com.larvalabs.svgandroid;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Created by James on 02/05/2014.
 */
public abstract class BaseBuilder<T extends BaseBuilder> {
    private InputStream data;
    private boolean closeInputStream = true;

    /**
     * Parse SVG data from an input stream.
     *
     * @param svgData the input stream, with SVG XML data in UTF-8 character encoding.
     * @return the parsed SVG.
     */
    public T readFromInputStream(InputStream svgData) {
        this.data = svgData;
        return (T) this;
    }

    /**
     * Parse SVG data from a string.
     *
     * @param svgData the string containing SVG XML data.
     */
    public T readFromString(String svgData) {
        this.data = new ByteArrayInputStream(svgData.getBytes());
        return (T) this;
    }

    /**
     * Parse SVG data from an Android application resource.
     *
     * @param resources the Android context resources.
     * @param resId     the ID of the raw resource SVG.
     */
    public T readFromResource(Resources resources, int resId) {
        this.data = resources.openRawResource(resId);
        return (T) this;
    }

    /**
     * Parse SVG data from an Android application asset.
     *
     * @param assetMngr the Android asset manager.
     * @param svgPath   the path to the SVG file in the application's assets.
     * @throws java.io.IOException if there was a problem reading the file.
     */
    public T readFromAsset(AssetManager assetMngr, String svgPath) throws IOException {
        this.data = assetMngr.open(svgPath);
        return (T) this;
    }

    /**
     * Whether or not to close the input stream after reading (ie. after calling build).<br>
     * <em>(default is true)</em>
     */
    public T setCloseInputStreamWhenDone(boolean closeInputStream) {
        this.closeInputStream = closeInputStream;
        return (T) this;
    }

    protected boolean hasData() {
        return data != null;
    }

    protected InputSource openData() {
        // SVGZ support (based on https://github.com/josefpavlik/svg-android/commit/fc0522b2e1):
        if (!data.markSupported()) {
            data = new BufferedInputStream(data); // decorate stream so we can use mark/reset
        }
        try {
            data.mark(4);
            byte[] magic = new byte[2];
            int r = data.read(magic, 0, 2);
            int magicInt = (magic[0] + ((magic[1]) << 8)) & 0xffff;
            data.reset();
            if (r == 2 && magicInt == GZIPInputStream.GZIP_MAGIC) {
                // Log.d(SVGParser.TAG, "SVG is gzipped");
                GZIPInputStream gin = new GZIPInputStream(data);
                data = gin;
            }
        }
        catch (IOException ioe) {
            throw new SVGParseException(ioe);
        }

        return new InputSource(data);
    }

    protected void closeData() {
        if (closeInputStream) {
            try {
                data.close();
            }
            catch (IOException e) {
                Log.e(SVGParser.TAG, "Error closing SVG input stream.", e);
            }
        }
    }
}
