package com.larvalabs.svgandroid;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.util.FloatMath;
import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

/**
 * @author Larva Labs, LLC
 */
public class SVGParser {

    static final String TAG = "SVGAndroid";

    private static boolean DISALLOW_DOCTYPE_DECL = true;

    /**
     * Parses a single SVG path and returns it as a <code>android.graphics.Path</code> object. An example path is
     * <code>M250,150L150,350L350,350Z</code>, which draws a triangle.
     *
     * @param pathString the SVG path, see the specification <a href="http://www.w3.org/TR/SVG/paths.html">here</a>.
     */
    public static Path parsePath(String pathString) {
        return doPath(pathString);
    }

    static com.larvalabs.svgandroid.SVG parse(InputSource data, SVGHandler handler) throws SVGParseException {
        try {
            final Picture picture = new Picture();
            handler.setPicture(picture);

            processSax(data, handler);

            com.larvalabs.svgandroid.SVG result = new com.larvalabs.svgandroid.SVG(picture, handler.bounds);
            // Skip bounds if it was an empty pic
            if (!Float.isInfinite(handler.limits.top)) {
                result.setLimits(handler.limits);
            }
            return result;
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to parse SVG.", e);
            throw new SVGParseException(e);
        }
    }

    private static void processSax(InputSource data, BaseHandler handler) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp = spf.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        xr.setContentHandler(handler);
        xr.setFeature("http://xml.org/sax/features/validation", false);
        if (DISALLOW_DOCTYPE_DECL) {
            try {
                xr.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            }
            catch (SAXNotRecognizedException e) {
                DISALLOW_DOCTYPE_DECL = false;
            }
        }
        xr.parse(data);
    }

    static SVGPaths parse(InputSource data, PathHandler handler) {
        try {
            processSax(data, handler);

            List<PathPaintLength> paths = handler.getPaths();
            RectF bounds = handler.bounds;
            if(bounds == null) {
                bounds = new RectF(handler.limits);
                bounds.offsetTo(0, 0);
            }
            SVGPaths result = new SVGPaths(paths, bounds);
            return result;
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to parse SVG.", e);
            throw new SVGParseException(e);
        }
    }

    private static NumberParse parseNumbers(String s) {
        // Util.debug("Parsing numbers from: '" + s + "'");
        int n = s.length();
        int p = 0;
        ArrayList<Float> numbers = new ArrayList<Float>();
        boolean skipChar = false;
        boolean prevWasE = false;
        for (int i = 1; i < n; i++) {
            if (skipChar) {
                skipChar = false;
                continue;
            }
            char c = s.charAt(i);
            switch (c) {
                // This ends the parsing, as we are on the next element
                case 'M':
                case 'm':
                case 'Z':
                case 'z':
                case 'L':
                case 'l':
                case 'H':
                case 'h':
                case 'V':
                case 'v':
                case 'C':
                case 'c':
                case 'S':
                case 's':
                case 'Q':
                case 'q':
                case 'T':
                case 't':
                case 'a':
                case 'A':
                case ')': {
                    String str = s.substring(p, i);
                    if (str.trim().length() > 0) {
                        // Util.debug("  Last: " + str);
                        Float f = Float.parseFloat(str);
                        numbers.add(f);
                    }
                    p = i;
                    return new NumberParse(numbers, p);
                }
                case '-':
                    // Allow numbers with negative exp such as 7.23e-4
                    if (prevWasE) {
                        prevWasE = false;
                        break;
                    }
                    // fall-through
                case '\n':
                case '\t':
                case ' ':
                case ',': {
                    String str = s.substring(p, i);
                    // Just keep moving if multiple whitespace
                    if (str.trim().length() > 0) {
                        // Util.debug("  Next: " + str);
                        Float f = Float.parseFloat(str);
                        numbers.add(f);
                        if (c == '-') {
                            p = i;
                        }
                        else {
                            p = i + 1;
                            skipChar = true;
                        }
                    }
                    else {
                        p++;
                    }
                    prevWasE = false;
                    break;
                }
                case 'e':
                    prevWasE = true;
                    break;
                default:
                    prevWasE = false;
            }
        }

        String last = s.substring(p);
        if (last.length() > 0) {
            // Util.debug("  Last: " + last);
            try {
                numbers.add(Float.parseFloat(last));
            }
            catch (NumberFormatException nfe) {
                // Just white-space, forget it
            }
            p = s.length();
        }
        return new NumberParse(numbers, p);
    }

    private static final Pattern TRANSFORM_SEP = Pattern.compile("[\\s,]*");

    /**
     * Parse a list of transforms such as: foo(n,n,n...) bar(n,n,n..._ ...) Delimiters are whitespaces or commas
     */
    static Matrix parseTransform(String s) {
        Matrix matrix = new Matrix();
        while (true) {
            parseTransformItem(s, matrix);
            // Log.i(TAG, "Transformed: (" + s + ") " + matrix);
            final int rparen = s.indexOf(")");
            if (rparen > 0 && s.length() > rparen + 1) {
                s = TRANSFORM_SEP.matcher(s.substring(rparen + 1)).replaceFirst("");
            }
            else {
                break;
            }
        }
        return matrix;
    }

    private static Matrix parseTransformItem(String s, Matrix matrix) {
        if (s.startsWith("matrix(")) {
            NumberParse np = parseNumbers(s.substring("matrix(".length()));
            if (np.numbers.size() == 6) {
                Matrix mat = new Matrix();
                mat.setValues(new float[]{
                    // Row 1
                    np.numbers.get(0), np.numbers.get(2), np.numbers.get(4),
                    // Row 2
                    np.numbers.get(1), np.numbers.get(3), np.numbers.get(5),
                    // Row 3
                    0, 0, 1,});
                matrix.preConcat(mat);
            }
        }
        else if (s.startsWith("translate(")) {
            NumberParse np = parseNumbers(s.substring("translate(".length()));
            if (np.numbers.size() > 0) {
                float tx = np.numbers.get(0);
                float ty = 0;
                if (np.numbers.size() > 1) {
                    ty = np.numbers.get(1);
                }
                matrix.preTranslate(tx, ty);
            }
        }
        else if (s.startsWith("scale(")) {
            NumberParse np = parseNumbers(s.substring("scale(".length()));
            if (np.numbers.size() > 0) {
                float sx = np.numbers.get(0);
                float sy = sx;
                if (np.numbers.size() > 1) {
                    sy = np.numbers.get(1);
                }
                matrix.preScale(sx, sy);
            }
        }
        else if (s.startsWith("skewX(")) {
            NumberParse np = parseNumbers(s.substring("skewX(".length()));
            if (np.numbers.size() > 0) {
                float angle = np.numbers.get(0);
                matrix.preSkew((float) Math.tan(angle), 0);
            }
        }
        else if (s.startsWith("skewY(")) {
            NumberParse np = parseNumbers(s.substring("skewY(".length()));
            if (np.numbers.size() > 0) {
                float angle = np.numbers.get(0);
                matrix.preSkew(0, (float) Math.tan(angle));
            }
        }
        else if (s.startsWith("rotate(")) {
            NumberParse np = parseNumbers(s.substring("rotate(".length()));
            if (np.numbers.size() > 0) {
                float angle = np.numbers.get(0);
                float cx = 0;
                float cy = 0;
                if (np.numbers.size() > 2) {
                    cx = np.numbers.get(1);
                    cy = np.numbers.get(2);
                }
                matrix.preTranslate(-cx, -cy);
                matrix.preRotate(angle);
                matrix.preTranslate(cx, cy);
            }
        }
        else {
            Log.w(TAG, "Invalid transform (" + s + ")");
        }
        return matrix;
    }

    /**
     * This is where the hard-to-parse paths are handled. Uppercase rules are absolute positions, lowercase are
     * relative. Types of path rules:
     * <p/>
     * <ol>
     * <li>M/m - (x y)+ - Move to (without drawing)
     * <li>Z/z - (no params) - Close path (back to starting point)
     * <li>L/l - (x y)+ - Line to
     * <li>H/h - x+ - Horizontal ine to
     * <li>V/v - y+ - Vertical line to
     * <li>C/c - (x1 y1 x2 y2 x y)+ - Cubic bezier to
     * <li>S/s - (x2 y2 x y)+ - Smooth cubic bezier to (shorthand that assumes the x2, y2 from previous C/S is the x1,
     * y1 of this bezier)
     * <li>Q/q - (x1 y1 x y)+ - Quadratic bezier to
     * <li>T/t - (x y)+ - Smooth quadratic bezier to (assumes previous control point is "reflection" of last one w.r.t.
     * to current point)
     * </ol>
     * <p/>
     * Numbers are separate by whitespace, comma or nothing at all (!) if they are self-delimiting, (ie. begin with a -
     * sign)
     *
     * @param s the path string from the XML
     */
    private static Path doPath(String s) {
        int n = s.length();
        ParserHelper ph = new ParserHelper(s, 0);
        ph.skipWhitespace();
        Path p = new Path();
        float lastX = 0;
        float lastY = 0;
        float lastX1 = 0;
        float lastY1 = 0;
        float subPathStartX = 0;
        float subPathStartY = 0;
        char prevCmd = 0;
        while (ph.pos < n) {
            char cmd = s.charAt(ph.pos);
            switch (cmd) {
                case '-':
                case '+':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    if (prevCmd == 'm' || prevCmd == 'M') {
                        cmd = (char) ((prevCmd) - 1);
                        break;
                    }
                    else if (("lhvcsqta").indexOf(Character.toLowerCase(prevCmd)) >= 0) {
                        cmd = prevCmd;
                        break;
                    }
                default: {
                    ph.advance();
                    prevCmd = cmd;
                }
            }

            boolean wasCurve = false;
            switch (cmd) {
                case 'M':
                case 'm': {
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 'm') {
                        subPathStartX += x;
                        subPathStartY += y;
                        p.rMoveTo(x, y);
                        lastX += x;
                        lastY += y;
                    }
                    else {
                        subPathStartX = x;
                        subPathStartY = y;
                        p.moveTo(x, y);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                }
                case 'Z':
                case 'z': {
                    p.close();
                    p.moveTo(subPathStartX, subPathStartY);
                    lastX = subPathStartX;
                    lastY = subPathStartY;
                    lastX1 = subPathStartX;
                    lastY1 = subPathStartY;
                    wasCurve = true;
                    break;
                }
                case 'T':
                case 't':
                    // todo - smooth quadratic Bezier (two parameters)
                case 'L':
                case 'l': {
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 'l') {
                        p.rLineTo(x, y);
                        lastX += x;
                        lastY += y;
                    }
                    else {
                        p.lineTo(x, y);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                }
                case 'H':
                case 'h': {
                    float x = ph.nextFloat();
                    if (cmd == 'h') {
                        p.rLineTo(x, 0);
                        lastX += x;
                    }
                    else {
                        p.lineTo(x, lastY);
                        lastX = x;
                    }
                    break;
                }
                case 'V':
                case 'v': {
                    float y = ph.nextFloat();
                    if (cmd == 'v') {
                        p.rLineTo(0, y);
                        lastY += y;
                    }
                    else {
                        p.lineTo(lastX, y);
                        lastY = y;
                    }
                    break;
                }
                case 'C':
                case 'c': {
                    wasCurve = true;
                    float x1 = ph.nextFloat();
                    float y1 = ph.nextFloat();
                    float x2 = ph.nextFloat();
                    float y2 = ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 'c') {
                        x1 += lastX;
                        x2 += lastX;
                        x += lastX;
                        y1 += lastY;
                        y2 += lastY;
                        y += lastY;
                    }
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    lastX1 = x2;
                    lastY1 = y2;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'Q':
                case 'q':
                    // todo - quadratic Bezier (four parameters)
                case 'S':
                case 's': {
                    wasCurve = true;
                    float x2 = ph.nextFloat();
                    float y2 = ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        x2 += lastX;
                        x += lastX;
                        y2 += lastY;
                        y += lastY;
                    }
                    float x1 = 2 * lastX - lastX1;
                    float y1 = 2 * lastY - lastY1;
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    lastX1 = x2;
                    lastY1 = y2;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'A':
                case 'a': {
                    float rx = ph.nextFloat();
                    float ry = ph.nextFloat();
                    float theta = ph.nextFloat();
                    int largeArc = ph.nextFlag();
                    int sweepArc = ph.nextFlag();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 'a') {
                        x += lastX;
                        y += lastY;
                    }
                    drawArc(p, lastX, lastY, x, y, rx, ry, theta, largeArc, sweepArc);
                    lastX = x;
                    lastY = y;
                    break;
                }
                default:
                    Log.w(TAG, "Invalid path command: " + cmd);
                    ph.advance();
            }
            if (!wasCurve) {
                lastX1 = lastX;
                lastY1 = lastY;
            }
            ph.skipWhitespace();
        }
        return p;
    }

    private static float angle(float x1, float y1, float x2, float y2) {

        return (float) Math.toDegrees(Math.atan2(x1, y1) - Math.atan2(x2, y2)) % 360;
    }

    private static final RectF arcRectf = new RectF();
    private static final Matrix arcMatrix = new Matrix();
    private static final Matrix arcMatrix2 = new Matrix();

    private static void drawArc(Path p, float lastX, float lastY, float x, float y, float rx, float ry, float theta,
                                int largeArc, int sweepArc) {
        // Log.d("drawArc", "from (" + lastX + "," + lastY + ") to (" + x + ","+ y + ") r=(" + rx + "," + ry +
        // ") theta=" + theta + " flags="+ largeArc + "," + sweepArc);

        // http://www.w3.org/TR/SVG/implnote.html#ArcImplementationNotes

        if (rx == 0 || ry == 0) {
            p.lineTo(x, y);
            return;
        }

        if (x == lastX && y == lastY) {
            return; // nothing to draw
        }

        rx = Math.abs(rx);
        ry = Math.abs(ry);

        final float thrad = theta * (float) Math.PI / 180;
        final float st = FloatMath.sin(thrad);
        final float ct = FloatMath.cos(thrad);

        final float xc = (lastX - x) / 2;
        final float yc = (lastY - y) / 2;
        final float x1t = ct * xc + st * yc;
        final float y1t = -st * xc + ct * yc;

        final float x1ts = x1t * x1t;
        final float y1ts = y1t * y1t;
        float rxs = rx * rx;
        float rys = ry * ry;

        float lambda = (x1ts / rxs + y1ts / rys) * 1.001f; // add 0.1% to be sure that no out of range occurs due to
        // limited precision
        if (lambda > 1) {
            float lambdasr = FloatMath.sqrt(lambda);
            rx *= lambdasr;
            ry *= lambdasr;
            rxs = rx * rx;
            rys = ry * ry;
        }

        final float R =
            FloatMath.sqrt((rxs * rys - rxs * y1ts - rys * x1ts) / (rxs * y1ts + rys * x1ts))
                * ((largeArc == sweepArc) ? -1 : 1);
        final float cxt = R * rx * y1t / ry;
        final float cyt = -R * ry * x1t / rx;
        final float cx = ct * cxt - st * cyt + (lastX + x) / 2;
        final float cy = st * cxt + ct * cyt + (lastY + y) / 2;

        final float th1 = angle(1, 0, (x1t - cxt) / rx, (y1t - cyt) / ry);
        float dth = angle((x1t - cxt) / rx, (y1t - cyt) / ry, (-x1t - cxt) / rx, (-y1t - cyt) / ry);

        if (sweepArc == 0 && dth > 0) {
            dth -= 360;
        }
        else if (sweepArc != 0 && dth < 0) {
            dth += 360;
        }

        // draw
        if ((theta % 360) == 0) {
            // no rotate and translate need
            arcRectf.set(cx - rx, cy - ry, cx + rx, cy + ry);
            p.arcTo(arcRectf, th1, dth);
        }
        else {
            // this is the hard and slow part :-)
            arcRectf.set(-rx, -ry, rx, ry);

            arcMatrix.reset();
            arcMatrix.postRotate(theta);
            arcMatrix.postTranslate(cx, cy);
            arcMatrix.invert(arcMatrix2);

            p.transform(arcMatrix2);
            p.arcTo(arcRectf, th1, dth);
            p.transform(arcMatrix);
        }
    }

    static NumberParse getNumberParseAttr(String name, Attributes attributes) {
        int n = attributes.getLength();
        for (int i = 0; i < n; i++) {
            if (attributes.getLocalName(i).equals(name)) {
                return parseNumbers(attributes.getValue(i));
            }
        }
        return null;
    }

    static String getStringAttr(String name, Attributes attributes) {
        int n = attributes.getLength();
        for (int i = 0; i < n; i++) {
            if (attributes.getLocalName(i).equals(name)) {
                return attributes.getValue(i);
            }
        }
        return null;
    }

    static Float getFloatAttr(String name, Attributes attributes) {
        return getFloatAttr(name, attributes, null);
    }

    static Float getFloatAttr(String name, Attributes attributes, Float defaultValue) {
        String v = getStringAttr(name, attributes);
        return parseFloatValue(v, defaultValue);
    }

    static float getFloatAttr(String name, Attributes attributes, float defaultValue) {
        String v = getStringAttr(name, attributes);
        return parseFloatValue(v, defaultValue);
    }

    static Float parseFloatValue(String str, Float defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        else if (str.endsWith("px")) {
            str = str.substring(0, str.length() - 2);
        }
        else if (str.endsWith("%")) {
            str = str.substring(0, str.length() - 1);
            return Float.parseFloat(str) / 100;
        }
        // Log.d(TAG, "Float parsing '" + name + "=" + v + "'");
        return Float.parseFloat(str);
    }

    static class NumberParse {
        ArrayList<Float> numbers;
        private int nextCmd;

        public NumberParse(ArrayList<Float> numbers, int nextCmd) {
            this.numbers = numbers;
            this.nextCmd = nextCmd;
        }

        public int getNextCmd() {
            return nextCmd;
        }

        public float getNumber(int index) {
            return numbers.get(index);
        }

    }

    static class Gradient {
        String id;
        String xlink;
        boolean isLinear;
        float x1, y1, x2, y2;
        float x, y, radius;
        ArrayList<Float> positions = new ArrayList<Float>();
        ArrayList<Integer> colors = new ArrayList<Integer>();
        Matrix matrix = null;
        public Shader shader = null;
        public boolean boundingBox = false;
        public TileMode tilemode;

        /*
                public Gradient createChild(Gradient g) {
                    Gradient child = new Gradient();
                    child.id = g.id;
                    child.xlink = id;
                    child.isLinear = g.isLinear;
                    child.x1 = g.x1;
                    child.x2 = g.x2;
                    child.y1 = g.y1;
                    child.y2 = g.y2;
                    child.x = g.x;
                    child.y = g.y;
                    child.radius = g.radius;
                    child.positions = positions;
                    child.colors = colors;
                    child.matrix = matrix;
                    if (g.matrix != null) {
                        if (matrix == null) {
                            child.matrix = g.matrix;
                        } else {
                            Matrix m = new Matrix(matrix);
                            m.preConcat(g.matrix);
                            child.matrix = m;
                        }
                    }
                    child.boundingBox = g.boundingBox;
                    child.shader = g.shader;
                    child.tilemode = g.tilemode;
                    return child;
                }
        */
        public void inherit(Gradient parent) {
            Gradient child = this;
            child.xlink = parent.id;
            child.positions = parent.positions;
            child.colors = parent.colors;
            if (child.matrix == null) {
                child.matrix = parent.matrix;
            }
            else if (parent.matrix != null) {
                Matrix m = new Matrix(parent.matrix);
                m.preConcat(child.matrix);
                child.matrix = m;
            }
        }
    }

    private static class StyleSet {
        HashMap<String, String> styleMap = new HashMap<String, String>();

        private StyleSet(String string) {
            String[] styles = string.split(";");
            for (String s : styles) {
                String[] style = s.split(":");
                if (style.length == 2) {
                    styleMap.put(style[0], style[1]);
                }
            }
        }

        public String getStyle(String name) {
            return styleMap.get(name);
        }
    }

    static class Properties {
        StyleSet styles = null;
        Attributes atts;

        Properties(Attributes atts) {
            this.atts = atts;
            String styleAttr = getStringAttr("style", atts);
            if (styleAttr != null) {
                styles = new StyleSet(styleAttr);
            }
        }

        public String getAttr(String name) {
            String v = null;
            if (styles != null) {
                v = styles.getStyle(name);
            }
            if (v == null) {
                v = getStringAttr(name, atts);
            }
            return v;
        }

        public String getString(String name) {
            return getAttr(name);
        }

        private Integer rgb(int r, int g, int b) {
            return ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
        }

        private int parseNum(String v) throws NumberFormatException {
            if (v.endsWith("%")) {
                v = v.substring(0, v.length() - 1);
                return Math.round(Float.parseFloat(v) / 100 * 255);
            }
            return Integer.parseInt(v);
        }

        public Integer getColor(String name) {
            String v = name;
            if (v == null) {
                return null;
            }
            else if (v.startsWith("#")) {
                try { // #RRGGBB or #AARRGGBB
                    return Color.parseColor(v);
                }
                catch (IllegalArgumentException iae) {
                    return null;
                }
            }
            else if (v.startsWith("rgb(") && v.endsWith(")")) {
                String values[] = v.substring(4, v.length() - 1).split(",");
                try {
                    return rgb(parseNum(values[0]), parseNum(values[1]), parseNum(values[2]));
                }
                catch (NumberFormatException nfe) {
                    return null;
                }
                catch (ArrayIndexOutOfBoundsException e) {
                    return null;
                }
            }
            else {
                return SVGColors.mapColour(v);
            }
        }

        // convert 0xRGB into 0xRRGGBB
        private int hex3Tohex6(int x) {
            return (x & 0xF00) << 8 | (x & 0xF00) << 12 | (x & 0xF0) << 4 | (x & 0xF0) << 8 | (x & 0xF) << 4
                | (x & 0xF);
        }

        public float getFloat(String name, float defaultValue) {
            String v = getAttr(name);
            if (v == null) {
                return defaultValue;
            }
            else {
                try {
                    return Float.parseFloat(v);
                }
                catch (NumberFormatException nfe) {
                    return defaultValue;
                }
            }
        }

        public Float getFloat(String name, Float defaultValue) {
            String v = getAttr(name);
            if (v == null) {
                return defaultValue;
            }
            else {
                try {
                    return Float.parseFloat(v);
                }
                catch (NumberFormatException nfe) {
                    return defaultValue;
                }
            }
        }

        public Float getFloat(String name) {
            return getFloat(name, null);
        }
    }

    static class LayerAttributes {
        public final float opacity;

        public LayerAttributes(float opacity) {
            this.opacity = opacity;
        }
    }

}
