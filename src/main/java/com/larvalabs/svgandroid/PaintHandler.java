package com.larvalabs.svgandroid;

import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;

import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * Created by James on 02/05/2014.
 */
public abstract class PaintHandler extends BaseHandler {

    private Paint strokePaint;
    private boolean strokeSet = false;
    private final LinkedList<Paint> strokePaintStack = new LinkedList<Paint>();
    private final LinkedList<Boolean> strokeSetStack = new LinkedList<Boolean>();

    private Paint fillPaint;
    private boolean fillSet = false;
    private final LinkedList<Paint> fillPaintStack = new LinkedList<Paint>();
    private final LinkedList<Boolean> fillSetStack = new LinkedList<Boolean>();

    Integer searchColor = null;
    Integer replaceColor = null;
    Float opacityMultiplier = null;

    private final Matrix gradMatrix = new Matrix();

    private boolean whiteMode = false;

    public PaintHandler() {
        super();
        strokePaint = new Paint();
        strokePaint.setAntiAlias(true);
        strokePaint.setStyle(Paint.Style.STROKE);
        fillPaint = new Paint();
        fillPaint.setAntiAlias(true);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStrokeWidth(0);
    }

    protected Paint getPaint(boolean fill) {
        return fill ? fillPaint : strokePaint;
    }

    private static float toFloat(String s, float dflt) {
        float result = dflt;
        try {
            result = Float.parseFloat(s);
        }
        catch (NumberFormatException e) {
            // ignore
        }
        return result;
    }

    /**
     * set the path style (if any) stroke-dasharray="n1,n2,..." stroke-dashoffset=n
     */
    private void pathStyleHelper(String style, String offset) {
        if (style == null) {
            return;
        }

        if (style.equals("none")) {
            strokePaint.setPathEffect(null);
            return;
        }

        StringTokenizer st = new StringTokenizer(style, " ,");
        int count = st.countTokens();
        float[] intervals = new float[(count & 1) == 1 ? count * 2 : count];
        float max = 0;
        float current = 1f;
        int i = 0;
        while (st.hasMoreTokens()) {
            intervals[i++] = current = toFloat(st.nextToken(), current);
            max += current;
        }

        // in svg speak, we double the intervals on an odd count
        for (int start = 0; i < intervals.length; i++, start++) {
            max += intervals[i] = intervals[start];
        }

        float off = 0f;
        if (offset != null) {
            try {
                off = Float.parseFloat(offset) % max;
            }
            catch (NumberFormatException e) {
                // ignore
            }
        }

        strokePaint.setPathEffect(new DashPathEffect(intervals, off));
    }

    public void setColorSwap(Integer searchColor, Integer replaceColor, boolean overideOpacity) {
        this.searchColor = searchColor;
        this.replaceColor = replaceColor;
        if (replaceColor != null && overideOpacity) {
            opacityMultiplier = ((replaceColor >> 24) & 0x000000FF) / 255f;
        }
        else {
            opacityMultiplier = null;
        }
    }

    protected void doColor(SVGParser.Properties atts, Integer color, boolean fillMode, Paint paint) {
        int c = (0xFFFFFF & color) | 0xFF000000;
        if (searchColor != null && searchColor.intValue() == c) {
            c = replaceColor;
        }
        paint.setShader(null);
        paint.setColor(c);
        Float opacityAttr = atts.getFloat("opacity");
        if (opacityAttr == null) {
            opacityAttr = atts.getFloat(fillMode ? "fill-opacity" : "stroke-opacity");
        }

        float opacity = opacityAttr != null ? opacityAttr : 1f;
        opacity *= currentLayerAttributes().opacity;
        if (opacityMultiplier != null) {
            opacity *= opacityMultiplier;
        }
        paint.setAlpha((int) (255f * opacity));
    }

    @Override
    protected boolean onFill(SVGParser.Properties atts, RectF bounding_box) {
        if ("none".equals(atts.getString("display"))) {
            return false;
        }
        if (whiteMode) {
            fillPaint.setShader(null);
            fillPaint.setColor(Color.WHITE);
            return true;
        }
        String fillString = atts.getString("fill");
        if (fillString == null && SVG_FILL != null) {
            fillString = SVG_FILL;
        }
        if (fillString != null) {
            if (fillString.startsWith("url(#")) {

                // It's a gradient fill, look it up in our map
                String id = fillString.substring("url(#".length(), fillString.length() - 1);
                SVGParser.Gradient g = getGradient(id);
                Shader shader = null;
                if (g != null) {
                    shader = g.shader;
                }
                if (shader != null) {
                    // Util.debug("Found shader!");
                    fillPaint.setShader(shader);
                    gradMatrix.set(g.matrix);
                    if (g.boundingBox && bounding_box != null) {
                        // Log.d("svg", "gradient is bounding box");
                        gradMatrix.preTranslate(bounding_box.left, bounding_box.top);
                        gradMatrix.preScale(bounding_box.width(), bounding_box.height());
                    }
                    shader.setLocalMatrix(gradMatrix);
                    return true;
                }
                else {
                    Log.w(SVGParser.TAG, "Didn't find shader, using black: " + id);
                    fillPaint.setShader(null);
                    doColor(atts, Color.BLACK, true, fillPaint);
                    return true;
                }
            }
            else if (fillString.equalsIgnoreCase("none")) {
                fillPaint.setShader(null);
                fillPaint.setColor(Color.TRANSPARENT);
                return true;
            }
            else {
                fillPaint.setShader(null);
                Integer color = atts.getColor(fillString);
                if (color != null) {
                    doColor(atts, color, true, fillPaint);
                    return true;
                }
                else {
                    Log.w(SVGParser.TAG, "Unrecognized fill color, using black: " + fillString);
                    doColor(atts, Color.BLACK, true, fillPaint);
                    return true;
                }
            }
        }
        else {
            if (fillSet) {
                // If fill is set, inherit from parent
                return fillPaint.getColor() != Color.TRANSPARENT; // optimization
            }
            else {
                // Default is black fill
                fillPaint.setShader(null);
                fillPaint.setColor(Color.BLACK);
                return true;
            }
        }
    }

    @Override
    protected boolean onStroke(SVGParser.Properties atts) {
        if (whiteMode) {
            // Never stroke in white mode
            return false;
        }
        if ("none".equals(atts.getString("display"))) {
            return false;
        }

        // Check for other stroke attributes
        Float width = atts.getFloat("stroke-width");
        if (width != null) {
            strokePaint.setStrokeWidth(width);
        }

        String linecap = atts.getString("stroke-linecap");
        if ("round".equals(linecap)) {
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
        }
        else if ("square".equals(linecap)) {
            strokePaint.setStrokeCap(Paint.Cap.SQUARE);
        }
        else if ("butt".equals(linecap)) {
            strokePaint.setStrokeCap(Paint.Cap.BUTT);
        }

        String linejoin = atts.getString("stroke-linejoin");
        if ("miter".equals(linejoin)) {
            strokePaint.setStrokeJoin(Paint.Join.MITER);
        }
        else if ("round".equals(linejoin)) {
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
        }
        else if ("bevel".equals(linejoin)) {
            strokePaint.setStrokeJoin(Paint.Join.BEVEL);
        }

        pathStyleHelper(atts.getString("stroke-dasharray"), atts.getString("stroke-dashoffset"));

        String strokeString = atts.getAttr("stroke");
        if (strokeString != null) {
            if (strokeString.equalsIgnoreCase("none")) {
                strokePaint.setColor(Color.TRANSPARENT);
                return false;
            }
            else {
                Integer color = atts.getColor(strokeString);
                if (color != null) {
                    doColor(atts, color, false, strokePaint);
                    return true;
                }
                else {
                    Log.w(SVGParser.TAG, "Unrecognized stroke color, using none: " + strokeString);
                    strokePaint.setColor(Color.TRANSPARENT);
                    return false;
                }
            }
        }
        else {
            if (strokeSet) {
                // Inherit from parent
                return strokePaint.getColor() != Color.TRANSPARENT; // optimization
            }
            else {
                // Default is none
                strokePaint.setColor(Color.TRANSPARENT);
                return false;
            }
        }
    }

    @Override
    protected void onStartElement() {
        // Reset paint opacity
        strokePaint.setAlpha(255);
        fillPaint.setAlpha(255);
    }

    @Override
    protected void onNewLayer(SVGParser.Properties props) {
        fillPaintStack.addLast(new Paint(fillPaint));
        strokePaintStack.addLast(new Paint(strokePaint));
        fillSetStack.addLast(fillSet);
        strokeSetStack.addLast(strokeSet);

        onFill(props, null); // Added by mrn but a boundingBox is now required by josef.
        onStroke(props);

        fillSet |= (props.getString("fill") != null);
        strokeSet |= (props.getString("stroke") != null);
    }

    @Override
    protected void onEndLayer() {
        fillPaint = fillPaintStack.removeLast();
        fillSet = fillSetStack.removeLast();
        strokePaint = strokePaintStack.removeLast();
        strokeSet = strokeSetStack.removeLast();
    }

    public void setColorFilter(ColorFilter colorFilter, boolean fill) {
        getPaint(fill).setColorFilter(colorFilter);
    }

    public void setWhiteMode(boolean whiteMode) {
        this.whiteMode = whiteMode;
    }
}
