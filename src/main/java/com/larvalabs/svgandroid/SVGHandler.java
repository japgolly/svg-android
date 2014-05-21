package com.larvalabs.svgandroid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.RectF;

/**
 * Created by James on 02/05/2014.
 */
class SVGHandler extends DrawTextHandler {

    private Picture picture;

    public SVGHandler() {
        super();
    }

    void setPicture(Picture picture) {
        this.picture = picture;
    }

    @Override
    protected Canvas onCreateCanvas(int width, int height) {
        return picture.beginRecording(width, height);
    }

    @Override
    protected float onRect(RectF rect, Float rx, Float ry, SVGParser.Properties props, boolean fill) {
        Paint paint = getPaint(fill);

        if (rx <= 0f && ry <= 0f) {
            canvas.drawRect(rect, paint);
        }
        else {
            canvas.drawRoundRect(rect, rx, ry, paint);
        }
        return paint.getStrokeWidth();
    }

    @Override
    protected float onLine(RectF rect) {
        Paint paint = getPaint(false);
        canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint);
        return paint.getStrokeWidth();
    }

    @Override
    protected float onOval(RectF rect, boolean fill) {
        Paint paint = getPaint(fill);
        canvas.drawOval(rect, paint);
        return paint.getStrokeWidth();
    }

    @Override
    protected float onPoly(Path p, boolean fill) {
        return onPath(p, fill);
    }

    protected float onPath(Path p, boolean fill) {
        Paint paint = getPaint(fill);
        canvas.drawPath(p, paint);
        return paint.getStrokeWidth();
    }

    @Override
    protected void onEndElement() {
        picture.endRecording();
    }
}
