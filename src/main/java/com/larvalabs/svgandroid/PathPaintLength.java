package com.larvalabs.svgandroid;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;

/**
 * Created by James on 02/05/2014.
 */
public class PathPaintLength {
    public final Path path;
    public final Paint paint;
    public final float length;

    PathPaintLength(Path path, Paint paint) {
        this.path = path;
        this.paint = paint;
        this.length = calcLength(path);
    }


    public static float calcLength(Path path) {
        PathMeasure measure = new PathMeasure(path, false);
        float length = 0.0f;
        do {
            length += measure.getLength();
        }
        while (measure.nextContour());

        return length;
    }
}
