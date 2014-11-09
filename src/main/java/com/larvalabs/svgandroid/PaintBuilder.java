package com.larvalabs.svgandroid;

import android.graphics.ColorFilter;

/**
 * Created by James Wilding on 03/05/2014.
 */
public class PaintBuilder<T> extends BaseBuilder {

    private Integer searchColor = null;
    private Integer replaceColor = null;
    private ColorFilter strokeColorFilter = null, fillColorFilter = null;
    private boolean whiteMode = false;
    private boolean overideOpacity = false;


    public T clearColorSwap() {
        searchColor = replaceColor = null;
        return (T) this;
    }

    /**
     * Replaces a single colour with another.
     *
     * @param searchColor  The colour in the SVG.
     * @param replaceColor The desired colour.
     */
    public T setColorSwap(int searchColor, int replaceColor) {
        return setColorSwap(searchColor, replaceColor, false);
    }

    /**
     * Replaces a single colour with another, affecting the opacity.
     *
     * @param searchColor    The colour in the SVG.
     * @param replaceColor   The desired colour.
     * @param overideOpacity If true, combines the opacity defined in the SVG resource with the alpha of replaceColor.
     */
    public T setColorSwap(int searchColor, int replaceColor, boolean overideOpacity) {
        this.searchColor = searchColor;
        this.replaceColor = replaceColor;
        this.overideOpacity = overideOpacity;
        return (T) this;
    }

    /**
     * In white-mode, fills are drawn in white and strokes are not drawn at all.
     */
    public T setWhiteMode(boolean whiteMode) {
        this.whiteMode = whiteMode;
        return (T) this;
    }

    /**
     * Applies a {@link android.graphics.ColorFilter} to the paint objects used to render the SVG.
     */
    public T setColorFilter(ColorFilter colorFilter) {
        this.strokeColorFilter = this.fillColorFilter = colorFilter;
        return (T) this;
    }

    /**
     * Applies a {@link android.graphics.ColorFilter} to strokes in the SVG.
     */
    public T setStrokeColorFilter(ColorFilter colorFilter) {
        this.strokeColorFilter = colorFilter;
        return (T) this;
    }

    /**
     * Applies a {@link android.graphics.ColorFilter} to fills in the SVG.
     */
    public T setFillColorFilter(ColorFilter colorFilter) {
        this.fillColorFilter = colorFilter;
        return (T) this;
    }

    protected void applyPaintSettings(PaintHandler handler) {
        handler.setColorSwap(searchColor, replaceColor, overideOpacity);
        handler.setWhiteMode(whiteMode);
        if (fillColorFilter != null) {
            handler.setColorFilter(fillColorFilter, true);
        }
        if (strokeColorFilter != null) {
            handler.setColorFilter(strokeColorFilter, false);
        }
    }
}
