package com.larvalabs.svgandroid;

import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.FloatMath;
import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by James on 02/05/2014.
 */
public abstract class BaseHandler extends DefaultHandler {

    private Float limitsAdjustmentX, limitsAdjustmentY;

    private boolean boundsMode = false;

    // Scratch rect (so we aren't constantly making new ones)
    private final RectF rect = new RectF();
    protected RectF bounds = null;
    protected final RectF limits = new RectF(
        Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

    protected String SVG_FILL = null;

    private final HashMap<String, SVGParser.Gradient> gradientMap = new HashMap<String, SVGParser.Gradient>();
    private SVGParser.Gradient gradient = null;
    private final LinkedList<SVGParser.LayerAttributes> layerAttributeStack = new LinkedList<SVGParser.LayerAttributes>();

    private boolean hidden = false;
    private int hiddenLevel = 0;

    private final LinkedList<Boolean> transformStack = new LinkedList<Boolean>();
    private final LinkedList<Matrix> matrixStack = new LinkedList<Matrix>();

    private final RectF tmpLimitRect = new RectF();

    public BaseHandler() {
        matrixStack.addFirst(new Matrix());
        layerAttributeStack.addFirst(new SVGParser.LayerAttributes(1f));
    }

    protected SVGParser.Gradient getGradient(String id) {
        return gradientMap.get(id);
    }

    protected Collection<SVGParser.Gradient> getGradients() {
        return gradientMap.values();
    }

    private void finishGradients() {
        for (SVGParser.Gradient gradient : getGradients()) {
            if (gradient.xlink != null) {
                SVGParser.Gradient parent = getGradient(gradient.xlink);
                if (parent != null) {
                    gradient.inherit(parent);
                }
            }
            int[] colors = new int[gradient.colors.size()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = gradient.colors.get(i);
            }
            float[] positions = new float[gradient.positions.size()];
            for (int i = 0; i < positions.length; i++) {
                positions[i] = gradient.positions.get(i);
            }
            if (colors.length == 0) {
                Log.d("BAD", "BAD gradient, id=" + gradient.id);
            }
            if (gradient.isLinear) {
                gradient.shader = new LinearGradient(gradient.x1, gradient.y1, gradient.x2, gradient.y2, colors, positions, gradient.tilemode);
            }
            else {
                gradient.shader = new RadialGradient(gradient.x, gradient.y, gradient.radius, colors, positions, gradient.tilemode);
            }
        }
    }

    private void doLimits2(float x, float y) {
        if (x < limits.left) {
            limits.left = x;
        }
        if (x > limits.right) {
            limits.right = x;
        }
        if (y < limits.top) {
            limits.top = y;
        }
        if (y > limits.bottom) {
            limits.bottom = y;
        }
    }

    private void doLimits(RectF box, float strokeWidth) {
        Matrix m = matrixStack.getLast();
        m.mapRect(tmpLimitRect, box);
        float width2 = strokeWidth / 2;
        doLimits2(tmpLimitRect.left - width2, tmpLimitRect.top - width2);
        doLimits2(tmpLimitRect.right + width2, tmpLimitRect.bottom + width2);
    }

    private void doLimits(RectF box) {
        doLimits(box, 0);
    }

    public SVGParser.LayerAttributes currentLayerAttributes() {
        return layerAttributeStack.getLast();
    }

    private void pushTransform(Attributes atts) {
        final String transform = SVGParser.getStringAttr("transform", atts);
        boolean pushed = transform != null;
        transformStack.addLast(pushed);
        if (pushed) {
            final Matrix matrix = SVGParser.parseTransform(transform);
            pushMatrix(matrix);
            matrix.postConcat(matrixStack.getLast());
            matrixStack.addLast(matrix);
        }
    }

    private void popTransform() {
        if (transformStack.removeLast()) {
            popMatrix();
            matrixStack.removeLast();
        }
    }

    private SVGParser.Gradient doGradient(boolean isLinear, Attributes atts) {
        SVGParser.Gradient gradient = new SVGParser.Gradient();
        gradient.id = SVGParser.getStringAttr("id", atts);
        gradient.isLinear = isLinear;
        if (isLinear) {
            gradient.x1 = SVGParser.getFloatAttr("x1", atts, 0f);
            gradient.x2 = SVGParser.getFloatAttr("x2", atts, 1f);
            gradient.y1 = SVGParser.getFloatAttr("y1", atts, 0f);
            gradient.y2 = SVGParser.getFloatAttr("y2", atts, 0f);
        }
        else {
            gradient.x = SVGParser.getFloatAttr("cx", atts, 0f);
            gradient.y = SVGParser.getFloatAttr("cy", atts, 0f);
            gradient.radius = SVGParser.getFloatAttr("r", atts, 0f);
        }
        String transform = SVGParser.getStringAttr("gradientTransform", atts);
        if (transform != null) {
            gradient.matrix = SVGParser.parseTransform(transform);
        }
        String spreadMethod = SVGParser.getStringAttr("spreadMethod", atts);
        if (spreadMethod == null) {
            spreadMethod = "pad";
        }

        gradient.tilemode =
            (spreadMethod.equals("reflect")) ? Shader.TileMode.MIRROR
                : (spreadMethod.equals("repeat")) ? Shader.TileMode.REPEAT : Shader.TileMode.CLAMP;

        String unit = SVGParser.getStringAttr("gradientUnits", atts);
        if (unit == null) {
            unit = "objectBoundingBox";
        }
        gradient.boundingBox = !unit.equals("userSpaceOnUse");

        String xlink = SVGParser.getStringAttr("href", atts);
        if (xlink != null) {
            if (xlink.startsWith("#")) {
                xlink = xlink.substring(1);
            }
            gradient.xlink = xlink;
        }
        return gradient;
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
        throws SAXException {
        onStartElement();

        // Ignore everything but rectangles in bounds mode
        if (boundsMode) {
            if (localName.equals("rect")) {
                Float x = SVGParser.getFloatAttr("x", atts);
                if (x == null) {
                    x = 0f;
                }
                Float y = SVGParser.getFloatAttr("y", atts);
                if (y == null) {
                    y = 0f;
                }
                Float width = SVGParser.getFloatAttr("width", atts);
                Float height = SVGParser.getFloatAttr("height", atts);
                bounds = new RectF(x, y, x + width, y + height);
            }
            return;
        }
        if (localName.equals("svg")) {
            onSvg();
            SVG_FILL = SVGParser.getStringAttr("fill", atts);
            String viewboxStr = SVGParser.getStringAttr("viewBox", atts);
            if (viewboxStr != null) {
                String[] dims = viewboxStr.replace(',', ' ').split("\\s+");
                if (dims.length == 4) {
                    Float x1 = SVGParser.parseFloatValue(dims[0], null);
                    Float y1 = SVGParser.parseFloatValue(dims[1], null);
                    Float x2 = SVGParser.parseFloatValue(dims[2], null);
                    Float y2 = SVGParser.parseFloatValue(dims[3], null);
                    if (x1 != null && x2 != null && y1 != null && y2 != null) {
                        x2 += x1;
                        y2 += y1;

                        float width = FloatMath.ceil(x2 - x1);
                        float height = FloatMath.ceil(y2 - y1);
                        limitsAdjustmentX = -x1;
                        limitsAdjustmentY = -y1;

                        onViewBox((int) -x1, (int) -y1, (int) width, (int) height);
                    }
                }
            }

            // No viewbox
            if (checkViewbox()) {
                int width = (int) FloatMath.ceil(SVGParser.getFloatAttr("width", atts));
                int height = (int) FloatMath.ceil(SVGParser.getFloatAttr("height", atts));
                onNoViewbox(width, height);
            }

        }
        else if (localName.equals("defs")) {
            // Ignore
        }
        else if (localName.equals("linearGradient")) {
            gradient = doGradient(true, atts);
        }
        else if (localName.equals("radialGradient")) {
            gradient = doGradient(false, atts);
        }
        else if (localName.equals("stop")) {
            if (gradient != null) {
                final SVGParser.Properties props = new SVGParser.Properties(atts);

                final int colour;
                final Integer stopColour = props.getColor(props.getAttr("stop-color"));
                if (stopColour == null) {
                    colour = 0;
                }
                else {
                    float alpha = props.getFloat("stop-opacity", 1) * currentLayerAttributes().opacity;
                    int alphaInt = Math.round(255 * alpha);
                    colour = stopColour.intValue() | (alphaInt << 24);
                }
                gradient.colors.add(colour);

                float offset = props.getFloat("offset", 0);
                gradient.positions.add(offset);
            }
        }
        else if (localName.equals("g")) {
            final SVGParser.Properties props = new SVGParser.Properties(atts);

            // Check to see if this is the "bounds" layer
            if ("bounds".equalsIgnoreCase(SVGParser.getStringAttr("id", atts))) {
                boundsMode = true;
            }
            if (hidden) {
                hiddenLevel++;
                // Util.debug("Hidden up: " + hiddenLevel);
            }
            // Go in to hidden mode if display is "none"
            if ("none".equals(SVGParser.getStringAttr("display", atts)) || "none".equals(props.getString("display"))) {
                if (!hidden) {
                    hidden = true;
                    hiddenLevel = 1;
                    // Util.debug("Hidden up: " + hiddenLevel);
                }
            }

            // Create layer attributes
            final float opacity = props.getFloat("opacity", 1f);
            SVGParser.LayerAttributes curLayerAttr = currentLayerAttributes();
            SVGParser.LayerAttributes newLayerAttr = new SVGParser.LayerAttributes(curLayerAttr.opacity * opacity);
            layerAttributeStack.addLast(newLayerAttr);

            pushTransform(atts);
            onNewLayer(props);

        }
        else if (!hidden && localName.equals("rect")) {
            Float x = SVGParser.getFloatAttr("x", atts);
            if (x == null) {
                x = 0f;
            }
            Float y = SVGParser.getFloatAttr("y", atts);
            if (y == null) {
                y = 0f;
            }
            Float width = SVGParser.getFloatAttr("width", atts);
            Float height = SVGParser.getFloatAttr("height", atts);
            Float rx = SVGParser.getFloatAttr("rx", atts, 0f);
            Float ry = SVGParser.getFloatAttr("ry", atts, 0f);
            SVGParser.Properties props = new SVGParser.Properties(atts);

            pushTransform(atts);
            rect.set(x, y, x + width, y + height);
            if (onFill(props, rect)) {
                float strokeWidth = onRect(rect, rx, ry, props, true);
                doLimits(rect, strokeWidth);
            }

            if (onStroke(props)) {
                float strokeWidth = onRect(rect, rx, ry, props, false);
                doLimits(rect, strokeWidth);
            }

            popTransform();
        }
        else if (!hidden && localName.equals("line")) {
            Float x1 = SVGParser.getFloatAttr("x1", atts);
            Float x2 = SVGParser.getFloatAttr("x2", atts);
            Float y1 = SVGParser.getFloatAttr("y1", atts);
            Float y2 = SVGParser.getFloatAttr("y2", atts);
            SVGParser.Properties props = new SVGParser.Properties(atts);
            if (onStroke(props)) {
                pushTransform(atts);
                rect.set(x1, y1, x2, y2);
                float strokeWidth = onLine(rect);
                if (strokeWidth >= 0) {
                    doLimits(rect, strokeWidth);
                }
                popTransform();
            }
        }
        else if (!hidden && localName.equals("text")) {
            Float textX = SVGParser.getFloatAttr("x", atts);
            Float textY = SVGParser.getFloatAttr("y", atts);
            Float fontSize = SVGParser.getFloatAttr("font-size", atts);
            Matrix font_matrix = SVGParser.parseTransform(SVGParser.getStringAttr("transform",
                atts));
            pushTransform(atts);
            SVGParser.Properties props = new SVGParser.Properties(atts);
            onTextConfig(textX, textY, fontSize, font_matrix, props);
            popTransform();
        }
        else if (!hidden && (localName.equals("circle") || localName.equals("ellipse"))) {
            Float centerX, centerY, radiusX, radiusY;

            centerX = SVGParser.getFloatAttr("cx", atts);
            centerY = SVGParser.getFloatAttr("cy", atts);
            if (localName.equals("ellipse")) {
                radiusX = SVGParser.getFloatAttr("rx", atts);
                radiusY = SVGParser.getFloatAttr("ry", atts);

            }
            else {
                radiusX = radiusY = SVGParser.getFloatAttr("r", atts);
            }
            if (centerX != null && centerY != null && radiusX != null && radiusY != null) {
                pushTransform(atts);
                SVGParser.Properties props = new SVGParser.Properties(atts);
                rect.set(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY);
                if (onFill(props, rect)) {
                    float strokeWidth = onOval(rect, true);
                    doLimits(rect, strokeWidth);
                }
                if (onStroke(props)) {
                    float strokeWidth = onOval(rect, false);
                    doLimits(rect, strokeWidth);
                }
                popTransform();
            }
        }
        else if (!hidden && (localName.equals("polygon") || localName.equals("polyline"))) {
            SVGParser.NumberParse numbers = SVGParser.getNumberParseAttr("points", atts);
            if (numbers != null) {
                Path p = new Path();
                ArrayList<Float> points = numbers.numbers;
                if (points.size() > 1) {
                    pushTransform(atts);
                    SVGParser.Properties props = new SVGParser.Properties(atts);
                    p.moveTo(points.get(0), points.get(1));
                    for (int i = 2; i < points.size(); i += 2) {
                        float x = points.get(i);
                        float y = points.get(i + 1);
                        p.lineTo(x, y);
                    }
                    // Don't close a polyline
                    if (localName.equals("polygon")) {
                        p.close();
                    }
                    p.computeBounds(rect, false);
                    if (onFill(props, rect)) {
                        float strokeWidth = onPoly(p, true);
                        doLimits(rect, strokeWidth);
                    }
                    if (onStroke(props)) {
                        float strokeWidth = onPoly(p, false);
                        doLimits(rect, strokeWidth);
                    }
                    popTransform();
                }
            }
        }
        else if (!hidden && localName.equals("path")) {
            Path p = SVGParser.parsePath(SVGParser.getStringAttr("d", atts));
            pushTransform(atts);
            SVGParser.Properties props = new SVGParser.Properties(atts);
            p.computeBounds(rect, false);
            if (onFill(props, rect)) {
                onPath(p, true);
                doLimits(rect);
            }
            if (onStroke(props)) {
                float strokeWidth = onPath(p, false);
                doLimits(rect, strokeWidth);
            }
            popTransform();
        }
        else if (!hidden) {
            Log.w(SVGParser.TAG, "UNRECOGNIZED SVG COMMAND: " + localName);
        }
    }


    @Override
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        if (localName.equals("svg")) {
            onEndSvg();
            if (limitsAdjustmentX != null) {
                limits.left += limitsAdjustmentX;
                limits.right += limitsAdjustmentX;
            }
            if (limitsAdjustmentY != null) {
                limits.top += limitsAdjustmentY;
                limits.bottom += limitsAdjustmentY;
            }

            onEndElement();
        }
        else if (localName.equals("linearGradient") || localName.equals("radialGradient")) {
            if (gradient.id != null) {
                gradientMap.put(gradient.id, gradient);
            }
        }
        else if (localName.equals("defs")) {
            finishGradients();
        }
        else if (localName.equals("g")) {
            if (boundsMode) {
                boundsMode = false;
            }
            // Break out of hidden mode
            if (hidden) {
                hiddenLevel--;
                // Util.debug("Hidden down: " + hiddenLevel);
                if (hiddenLevel == 0) {
                    hidden = false;
                }
            }
            // // Clear gradient map
            // gradientRefMap.clear();
            popTransform();
            onEndLayer();

            if (!layerAttributeStack.isEmpty()) {
                layerAttributeStack.removeLast();
            }
        }
        else if (localName.equals("text")) {
            onEndText();
        }
    }

    protected abstract void pushMatrix(Matrix matrix);

    protected abstract void popMatrix();

    protected abstract void onStartElement();

    protected abstract void onSvg();

    protected abstract void onViewBox(int x, int y, int width, int height);

    protected abstract boolean checkViewbox();

    protected abstract void onNoViewbox(int width, int height);

    protected abstract void onNewLayer(SVGParser.Properties props);

    protected abstract boolean onFill(SVGParser.Properties props, RectF bounding);

    protected abstract void onTextConfig(Float textX, Float textY, Float fontSize, Matrix font_matrix, SVGParser.Properties props);

    protected abstract boolean onStroke(SVGParser.Properties props);

    protected abstract float onRect(RectF rect, Float rx, Float ry, SVGParser.Properties props, boolean fill);

    protected abstract float onLine(RectF rect);

    protected abstract float onOval(RectF rect, boolean fill);

    protected abstract float onPoly(Path p, boolean fill);

    protected abstract float onPath(Path p, boolean fill);

    protected abstract void onEndLayer();

    protected abstract void onEndText();

    protected abstract void onEndSvg();

    protected abstract void onEndElement();

    @Override
    public void startDocument() throws SAXException {
        // Set up prior to parsing a doc
    }

    @Override
    public void endDocument() throws SAXException {
        // Clean up after parsing a doc
    }

}
