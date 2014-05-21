package com.larvalabs.svgandroid;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * Created by James on 02/05/2014.
 */
public abstract class DrawTextHandler extends CanvasHandler {

    private Paint textPaint;
    private Float textX;
    private Float textY;
    private int newLineCount;
    private Matrix font_matrix;
    private boolean drawCharacters;
    private Float textSize;

    public DrawTextHandler() {
        super();
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onStartElement() {
        super.onStartElement();
        textPaint.setAlpha(255);
        drawCharacters = false;
    }

    @Override
    protected void onTextConfig(Float textX, Float textY, Float fontSize, Matrix font_matrix, SVGParser.Properties props) {
        drawCharacters = true;
        if (fontSize != null) {
            textSize = fontSize;
            if (textX != null && textY != null) {
                this.textX = textX;
                this.textY = textY;
            }
            else if (font_matrix != null) {
                this.font_matrix = font_matrix;
            }

            Integer color = props.getColor("fill");
            if (color != null) {
                doColor(props, color, true, textPaint);
            }
            else {
                textPaint.setColor(Color.BLACK);
            }
            this.newLineCount = 0;
            textPaint.setTextSize(textSize);
            canvas.save();
        }
    }

    @Override
    public void characters(char ch[], int start, int length) {
        if (this.drawCharacters) {
            if (length == 1 && ch[0] == '\n') {
                canvas.restore();
                canvas.save();

                newLineCount += 1;
                canvas.translate(0, newLineCount * textSize);
            }
            else {
                String text = new String(ch, start, length);
                if (this.textX != null && this.textY != null) {
                    canvas.drawText(text, this.textX, this.textY, textPaint);
                }
                else {
                    canvas.setMatrix(font_matrix);
                    canvas.drawText(text, 0, 0, textPaint);
                }
                Float delta = textPaint.measureText(text);

                canvas.translate(delta, 0);
            }
        }
    }

    @Override
    protected void onEndText() {
        if (drawCharacters) {
            canvas.restore();
        }
        drawCharacters = false;
    }
}
