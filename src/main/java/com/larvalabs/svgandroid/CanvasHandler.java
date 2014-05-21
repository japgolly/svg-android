package com.larvalabs.svgandroid;

import android.graphics.Canvas;
import android.graphics.Matrix;

/**
 * Created by James on 02/05/2014.
 */
public abstract class CanvasHandler extends PaintHandler {

    protected Canvas canvas;
    Integer canvasRestoreCount;

    public CanvasHandler() {
        super();
    }

    @Override
    protected void pushMatrix(Matrix matrix) {
        canvas.save();
        canvas.concat(matrix);
    }

    @Override
    protected void popMatrix() {
        canvas.restore();
    }

    @Override
    protected void onSvg() {
        canvas = null;
    }

    @Override
    protected void onViewBox(int x, int y, int width, int height) {
        canvas = onCreateCanvas(width, height);
        canvasRestoreCount = canvas.save();
        canvas.clipRect(0f, 0f, width, height);
        canvas.translate(x, y);
    }

    @Override
    protected boolean checkViewbox() {
        return canvas == null;
    }

    @Override
    protected void onNoViewbox(int width, int height) {
        canvas = onCreateCanvas(width, height);
        canvasRestoreCount = null;
    }

    @Override
    protected void onEndSvg() {
        if (canvasRestoreCount != null) {
            canvas.restoreToCount(canvasRestoreCount);
        }
    }

    protected abstract Canvas onCreateCanvas(int width, int height);
}

