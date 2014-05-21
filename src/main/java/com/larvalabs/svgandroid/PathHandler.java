package com.larvalabs.svgandroid;

import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by James on 02/05/2014.
 */
public class PathHandler extends PaintHandler {
    List<PathPaintLength> mPaths = new ArrayList<PathPaintLength>();
    Path mPath;
    Matrix mMatrix = new Matrix();
    float mMatrixX;
    float mMatrixY;

    List<PathPaintLength> getPaths() {
        return mPaths;
    }

    PathHandler() {
        super();
    }

    @Override
    protected void pushMatrix(Matrix matrix) {
//        mPath = new Path();
        mMatrix.postConcat(matrix);
        float[] values = new float[16];
        matrix.getValues(values);
        mMatrixX = values[2];
        mMatrixY = values[5];
    }

    @Override
    protected void popMatrix() {
//        List<>
        mMatrix.reset();
    }

    @Override
    protected void onSvg() {

    }

    @Override
    protected void onViewBox(int x, int y, int width, int height) {

    }

    @Override
    protected boolean checkViewbox() {
        return false;
    }

    @Override
    protected void onNoViewbox(int width, int height) {

    }

    @Override
    protected void onTextConfig(Float textX, Float textY, Float fontSize, Matrix font_matrix, SVGParser.Properties props) {

    }

    @Override
    protected float onRect(RectF rect, Float rx, Float ry, SVGParser.Properties props, boolean fill) {
        Path p = new Path();
        Paint paint = getPaint(fill);
        p.addRect(rect, Path.Direction.CCW);
        addPath(p, paint, true);
        return paint.getStrokeWidth();
    }

    @Override
    protected float onLine(RectF rect) {
        Path p = new Path();
        Paint paint = getPaint(false);
        p.moveTo(rect.left, rect.top);
        p.lineTo(rect.right, rect.bottom);
        addPath(p, paint, true);
        return paint.getStrokeWidth();
    }

    @Override
    protected float onOval(RectF rect, boolean fill) {
        Path p = new Path();
        Paint paint = getPaint(fill);
        p.addCircle(rect.centerX(), rect.centerY(), rect.width() / 2, Path.Direction.CCW);
        addPath(p, paint, true);
        return paint.getStrokeWidth();
    }

    @Override
    protected float onPoly(Path p, boolean fill) {
        return onPath(p, fill);
    }

    @Override
    protected float onPath(Path p, boolean fill) {
        Paint paint = getPaint(fill);
        addPath(p, paint, false);
        return paint.getStrokeWidth();
    }

    @Override
    protected void onEndText() {
    }

    @Override
    protected void onEndSvg() {
    }

    @Override
    protected void onEndElement() {
    }

    private void addPath(Path p, Paint paint, boolean pathIsNew) {
        if(!pathIsNew) {
            p = new Path(p);
        }

        p.offset(mMatrixX, mMatrixY);
        paint = new Paint(paint);
        mPaths.add(new PathPaintLength(p, paint));
    }
}
