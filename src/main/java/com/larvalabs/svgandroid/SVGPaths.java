package com.larvalabs.svgandroid;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by James Wilding on 07/05/2014.
 */
public class SVGPaths {
    List<PathPaintLength> mPathPaintLengths;
    RectF mBounds;

    public SVGPaths(List<PathPaintLength> pathPains, RectF bounds) {
        mPathPaintLengths = pathPains;
        mBounds = bounds;
    }

    public List<PathPaintLength> getPathPaintLengths() {
        return mPathPaintLengths;
    }

    public RectF getBounds() {
        return mBounds;
    }

    public static SVGPaths scaleBy(SVGPaths paths, float x, float y) {
        Matrix m = new Matrix();
        m.postScale(x, y);
        RectF bounds = new RectF();
        m.mapRect(bounds, paths.mBounds);
        SVGPaths out = new SVGPaths(new ArrayList<PathPaintLength>(paths.mPathPaintLengths.size()), bounds);
        for(PathPaintLength pathPaintLength : paths.mPathPaintLengths) {
            Path p = new Path(pathPaintLength.path);
            p.transform(m);
            out.mPathPaintLengths.add(new PathPaintLength(p, pathPaintLength.paint));
        }
        return out;
    }
}
