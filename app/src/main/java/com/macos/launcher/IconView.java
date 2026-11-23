package com.macos.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small vector glyphs drawn with Canvas (menu bar + Control Center). */
public class IconView extends View {
    public static final int APPLE = 0, WIFI = 1, BATT = 2, SEARCH = 3, CC = 4, BT = 5, AIRDROP = 6, MOON = 7,
            CONTRAST = 8, SHOT = 9, STAGE = 10, MIRROR = 11, PLAY = 12, PREV = 13, NEXT = 14, TRASH = 15,
            SIDEBAR = 16, GRID = 17, DOCK = 18;
    private final int type, color;
    private float level = 0.8f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public IconView(Context c, int type, int color) { super(c); this.type = type; this.color = color; }
    public void setLevel(float l) { level = l; invalidate(); }

    private android.graphics.drawable.Drawable sfD;
    private String sfN;

    private String sfName() {
        switch (type) {
            case APPLE: return "apple.logo";
            case WIFI: return "wifi";
            case BATT: return level > .875f ? "battery.100percent" : level > .625f ? "battery.75percent" : level > .375f ? "battery.50percent" : level > .125f ? "battery.25percent" : "battery.0percent";
            case SEARCH: return "magnifyingglass";
            case CC: return "switch.2";
            case MOON: return "moon.fill";
            case CONTRAST: return "circle.lefthalf.filled";
            case SHOT: return "camera.viewfinder";
            case MIRROR: return "rectangle.on.rectangle";
            case PLAY: return "play.fill";
            case PREV: return "backward.fill";
            case NEXT: return "forward.fill";
            case TRASH: return "trash";
            case SIDEBAR: return "sidebar.left";
            case GRID: case STAGE: return "square.grid.3x3.fill";
            case DOCK: return "dock.rectangle";
            default: return null;
        }
    }

    private void stroke(float w) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    }
    private void fill() { p.setStyle(Paint.Style.FILL); }

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), s = Math.min(W, H), cx = W / 2f, cy = H / 2f;
        p.reset(); p.setAntiAlias(true); p.setColor(color);
        String sn = sfName();
        if (sn != null) {
            if (!sn.equals(sfN)) { sfN = sn; sfD = Sf.get(getContext(), sn, color); }
            if (sfD != null) {
                float iw = Math.max(1, sfD.getIntrinsicWidth()), ih = Math.max(1, sfD.getIntrinsicHeight());
                float k = Math.min(W / iw, H / ih) * .94f;
                int l = Math.round(cx - iw * k / 2f), t = Math.round(cy - ih * k / 2f);
                sfD.setBounds(l, t, l + Math.round(iw * k), t + Math.round(ih * k));
                sfD.draw(c);
                return;
            }
        }
        switch (type) {
            case APPLE: {
                int sc = c.saveLayer(new RectF(0, 0, W, H), null);
                fill();
                c.drawCircle(cx - .17f * s, cy + .1f * s, .27f * s, p);
                c.drawCircle(cx + .17f * s, cy + .1f * s, .27f * s, p);
                c.drawRoundRect(new RectF(cx - .3f * s, cy - .1f * s, cx + .3f * s, cy + .4f * s), .2f * s, .2f * s, p);
                Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
                q.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawCircle(cx + .47f * s, cy + .02f * s, .14f * s, q);
                c.restoreToCount(sc);
                c.save(); c.rotate(-35, cx + .06f * s, cy - .3f * s);
                c.drawOval(new RectF(cx - .02f * s, cy - .52f * s, cx + .2f * s, cy - .36f * s), p);
                c.restore();
                break;
            }
            case WIFI: {
                stroke(s * .09f);
                float by = cy + .3f * s;
                for (int i = 1; i <= 3; i++) {
                    float r = i * .2f * s;
                    c.drawArc(new RectF(cx - r, by - r, cx + r, by + r), 225, 90, false, p);
                }
                fill(); c.drawCircle(cx, by, .055f * s, p);
                break;
            }
            case BATT: {
                float bh = H * .5f, bw = W * .82f, l = (W - bw) / 2f - W * .04f, t = cy - bh / 2f;
                stroke(H * .07f); p.setAlpha(150);
                c.drawRoundRect(new RectF(l, t, l + bw, t + bh), bh * .3f, bh * .3f, p);
                p.setAlpha(255); fill();
                c.drawRoundRect(new RectF(l + H * .07f, t + H * .07f, l + H * .07f + (bw - H * .14f) * level, t + bh - H * .07f), bh * .2f, bh * .2f, p);
                p.setAlpha(150);
                c.drawRoundRect(new RectF(l + bw + W * .02f, cy - bh * .18f, l + bw + W * .05f, cy + bh * .18f), 2, 2, p);
                break;
            }
            case SEARCH:
                stroke(s * .09f);
                c.drawCircle(cx - .07f * s, cy - .07f * s, .25f * s, p);
                c.drawLine(cx + .12f * s, cy + .12f * s, cx + .36f * s, cy + .36f * s, p);
                break;
            case CC:
                stroke(s * .07f);
                c.drawRoundRect(new RectF(cx - .4f * s, cy - .38f * s, cx + .4f * s, cy - .08f * s), s * .15f, s * .15f, p);
                c.drawRoundRect(new RectF(cx - .4f * s, cy + .08f * s, cx + .4f * s, cy + .38f * s), s * .15f, s * .15f, p);
                fill();
                c.drawCircle(cx - .25f * s, cy - .23f * s, .09f * s, p);
                c.drawCircle(cx + .25f * s, cy + .23f * s, .09f * s, p);
                break;
            case BT: {
                stroke(s * .08f);
                Path b = new Path();
                b.moveTo(cx - .22f * s, cy - .2f * s); b.lineTo(cx + .22f * s, cy + .2f * s); b.lineTo(cx, cy + .4f * s);
                b.lineTo(cx, cy - .4f * s); b.lineTo(cx + .22f * s, cy - .2f * s); b.lineTo(cx - .22f * s, cy + .2f * s);
                c.drawPath(b, p);
                break;
            }
            case AIRDROP:
                stroke(s * .07f);
                for (int i = 1; i <= 3; i++) {
                    float r = (.1f + i * .12f) * s;
                    c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 135, 270, false, p);
                }
                fill(); c.drawCircle(cx, cy, .06f * s, p);
                break;
            case MOON: {
                Path a = new Path(), b = new Path();
                a.addCircle(cx, cy, .36f * s, Path.Direction.CW);
                b.addCircle(cx + .18f * s, cy - .14f * s, .32f * s, Path.Direction.CW);
                a.op(b, Path.Op.DIFFERENCE);
                fill(); c.drawPath(a, p);
                break;
            }
            case CONTRAST: {
                float r = .36f * s;
                stroke(s * .07f);
                c.drawCircle(cx, cy, r, p);
                fill();
                c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 90, 180, true, p);
                break;
            }
            case SHOT: {
                stroke(s * .08f);
                float d = .38f * s, k = .16f * s;
                c.drawLine(cx - d, cy - d + k, cx - d, cy - d, p); c.drawLine(cx - d, cy - d, cx - d + k, cy - d, p);
                c.drawLine(cx + d - k, cy - d, cx + d, cy - d, p); c.drawLine(cx + d, cy - d, cx + d, cy - d + k, p);
                c.drawLine(cx - d, cy + d - k, cx - d, cy + d, p); c.drawLine(cx - d, cy + d, cx - d + k, cy + d, p);
                c.drawLine(cx + d - k, cy + d, cx + d, cy + d, p); c.drawLine(cx + d, cy + d, cx + d, cy + d - k, p);
                fill(); c.drawCircle(cx, cy, .1f * s, p);
                break;
            }
            case STAGE:
                stroke(s * .07f);
                c.drawRoundRect(new RectF(cx - .1f * s, cy - .3f * s, cx + .42f * s, cy + .3f * s), s * .08f, s * .08f, p);
                fill();
                for (int i = -1; i <= 1; i++) c.drawCircle(cx - .32f * s, cy + i * .2f * s, .055f * s, p);
                break;
            case MIRROR:
                stroke(s * .07f);
                c.drawRoundRect(new RectF(cx - .4f * s, cy - .18f * s, cx + .18f * s, cy + .36f * s), s * .06f, s * .06f, p);
                c.drawRoundRect(new RectF(cx - .18f * s, cy - .36f * s, cx + .4f * s, cy + .18f * s), s * .06f, s * .06f, p);
                break;
            case PLAY: {
                fill();
                Path t = new Path();
                t.moveTo(cx - .25f * s, cy - .32f * s); t.lineTo(cx + .32f * s, cy); t.lineTo(cx - .25f * s, cy + .32f * s); t.close();
                c.drawPath(t, p);
                break;
            }
            case PREV: case NEXT: {
                fill();
                float d = type == NEXT ? 1 : -1;
                for (int i = 0; i < 2; i++) {
                    float x0 = cx - d * .3f * s + d * i * .3f * s;
                    Path t = new Path();
                    t.moveTo(x0, cy - .24f * s); t.lineTo(x0 + d * .3f * s, cy); t.lineTo(x0, cy + .24f * s); t.close();
                    c.drawPath(t, p);
                }
                break;
            }
            case TRASH:
                stroke(s * .06f);
                c.drawRoundRect(new RectF(cx - .22f * s, cy - .12f * s, cx + .22f * s, cy + .34f * s), s * .06f, s * .06f, p);
                c.drawLine(cx - .3f * s, cy - .18f * s, cx + .3f * s, cy - .18f * s, p);
                c.drawLine(cx - .08f * s, cy - .18f * s, cx - .08f * s, cy - .3f * s, p);
                c.drawLine(cx + .08f * s, cy - .18f * s, cx + .08f * s, cy - .3f * s, p);
                c.drawLine(cx - .08f * s, cy - .3f * s, cx + .08f * s, cy - .3f * s, p);
                break;
        }
    }
}
