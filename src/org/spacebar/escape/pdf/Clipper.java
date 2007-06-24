package org.spacebar.escape.pdf;

import com.lowagie.text.pdf.PdfContentByte;

public abstract class Clipper {
    public void clip(PdfContentByte cb, int tx, int ty) {
        clipImpl(cb, tx, ty);
        cb.clip();
        cb.newPath();
    }

    protected abstract void clipImpl(PdfContentByte cb, int tx, int ty);
}
