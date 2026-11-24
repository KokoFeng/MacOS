package com.macos.launcher;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Photo Booth: live preview, photo, video, front/back, flash, timer, effects, gallery, saves to Photos. */
public class CameraApp {
    static CameraApp cur;
    static final String[] FX = {"Normal", "Black & White", "Sepia", "X-Ray", "Vivid", "Cool", "Warm", "Fade", "Noir"};

    final MainActivity a;
    MacWindow win;
    FrameLayout body, previewBox, overlay, effectsPage, galleryPage, flashView, countdownHolder;
    TextureView tex;
    LinearLayout bar;
    TextView shutterIcon;
    ImageView flashBtnIcon, timerBtnIcon;
    TextView timerLabel, recLabel;
    View modeGrid, modePhoto, modeVideo;
    CameraManager cm;
    CameraDevice dev;
    CameraCaptureSession sess;
    CaptureRequest.Builder pb;
    HandlerThread bt;
    Handler bh;
    final Handler ui = new Handler(Looper.getMainLooper());
    String camId;
    boolean front, videoMode, recording, flashOn, flashSupported, closed;
    int timerSec, effect, sensorOri;
    Size pSize;
    MediaRecorder rec;
    File vfile;
    long recStart;
    Surface previewSurface;
    final MediaActionSound sound = new MediaActionSound();
    Bitmap fxBmp;
    final List<ImageView> fxTiles = new ArrayList<>();

    public static void open(MainActivity a) { cur = new CameraApp(a); }

    public static void onPermission(int req, int[] res) {
        if (req == 9 && cur != null && !cur.closed) cur.build();
    }

    CameraApp(MainActivity act) {
        a = act;
        body = new FrameLayout(a);
        body.setBackgroundColor(0xFF000000);
        win = new MacWindow(a, "Photo Booth", null, "Photo Booth", body);
        win.onClose = this::release;
        a.openWindow(win, 560, 380);
        build();
    }

    boolean has(String p) { return a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    // ---------- ui ----------
    void build() {
        body.removeAllViews();
        if (!has(Manifest.permission.CAMERA)) {
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.addView(Sf.img(a, "camera.fill", 0xFFFFFFFF), new LinearLayout.LayoutParams(a.dp(44), a.dp(44)));
            TextView t = Ui.tv(a, "Photo Booth needs access to your camera.", 14, Color.WHITE, false);
            t.setPadding(0, a.dp(10), 0, a.dp(10));
            c.addView(t);
            TextView b = Ui.tv(a, "Allow Camera", 14, Color.WHITE, true);
            b.setPadding(a.dp(18), a.dp(8), a.dp(18), a.dp(8));
            b.setBackground(Ui.rr(Theme.accent, a.dp(16)));
            b.setOnClickListener(v -> a.requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 9));
            c.addView(b);
            body.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            return;
        }
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        previewBox = new FrameLayout(a);
        previewBox.setBackgroundColor(0xFF000000);
        previewBox.setClipChildren(true);
        tex = new TextureView(a);
        previewBox.addView(tex, new FrameLayout.LayoutParams(-1, -1));
        tex.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { openCamera(); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { transform(w, h); }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        buildOverlay();
        col.addView(previewBox, new LinearLayout.LayoutParams(-1, 0, 1f));
        buildBar();
        col.addView(bar, new LinearLayout.LayoutParams(-1, a.dp(56)));
        body.addView(col, new FrameLayout.LayoutParams(-1, -1));
        buildEffectsPage();
        buildGalleryPage();
        applyEffect();
        applyMirror();
    }

    ImageView ovBtn(String sym, final Runnable r) {
        ImageView v = Sf.img(a, sym, 0xFFFFFFFF);
        v.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x66000000);
        v.setBackground(g);
        v.setOnClickListener(x -> r.run());
        return v;
    }

    void buildOverlay() {
        overlay = new FrameLayout(a);
        LinearLayout tools = new LinearLayout(a);
        tools.setOrientation(LinearLayout.VERTICAL);
        flashBtnIcon = ovBtn("bolt.slash.fill", this::toggleFlash);
        timerBtnIcon = ovBtn("timer", this::cycleTimer);
        ImageView sw = ovBtn("arrow.trianglehead.2.clockwise.rotate.90.camera", this::switchCamera);
        ImageView gal = ovBtn("photo.on.rectangle", this::showGallery);
        for (View v : new View[]{flashBtnIcon, timerBtnIcon, sw, gal}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(34), a.dp(34));
            lp.bottomMargin = a.dp(8);
            tools.addView(v, lp);
        }
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        tp.topMargin = a.dp(10); tp.rightMargin = a.dp(10);
        overlay.addView(tools, tp);
        timerLabel = Ui.tv(a, "", 11, Color.WHITE, true);
        timerLabel.setGravity(Gravity.CENTER);
        timerLabel.setBackground(Ui.rr(0x99000000, a.dp(9)));
        timerLabel.setPadding(a.dp(7), a.dp(2), a.dp(7), a.dp(2));
        timerLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        tl.topMargin = a.dp(14); tl.leftMargin = a.dp(12);
        overlay.addView(timerLabel, tl);
        recLabel = Ui.tv(a, "\u25CF 00:00", 12, Color.WHITE, true);
        recLabel.setBackground(Ui.rr(0xCCFF3B30, a.dp(10)));
        recLabel.setPadding(a.dp(9), a.dp(3), a.dp(9), a.dp(3));
        recLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rl.topMargin = a.dp(12);
        overlay.addView(recLabel, rl);
        countdownHolder = new FrameLayout(a);
        overlay.addView(countdownHolder, new FrameLayout.LayoutParams(-1, -1));
        flashView = new FrameLayout(a);
        flashView.setBackgroundColor(0xFFFFFFFF);
        flashView.setAlpha(0f);
        flashView.setVisibility(View.GONE);
        overlay.addView(flashView, new FrameLayout.LayoutParams(-1, -1));
        previewBox.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    void buildBar() {
        bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Theme.dark ? 0xFF2A2A30 : 0xFFDDE6F5);
        bar.setPadding(a.dp(12), 0, a.dp(12), 0);
        LinearLayout seg = new LinearLayout(a);
        seg.setBackground(Ui.rr(Theme.dark ? 0x33FFFFFF : 0x22000000, a.dp(7)));
        seg.setPadding(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
        modeGrid = segBtn("rectangle.grid.2x2", () -> showEffects());
        modePhoto = segBtn("person.crop.rectangle", () -> setVideoMode(false));
        modeVideo = segBtn("video.fill", () -> setVideoMode(true));
        for (View v : new View[]{modeGrid, modePhoto, modeVideo}) seg.addView(v, new LinearLayout.LayoutParams(a.dp(30), a.dp(26)));
        bar.addView(seg, new LinearLayout.LayoutParams(-2, -2));
        bar.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        FrameLayout sh = new FrameLayout(a);
        shutterIcon = new TextView(a);
        sh.addView(shutterIcon, new FrameLayout.LayoutParams(-1, -1));
        styleShutter(sh);
        sh.setOnClickListener(v -> onShutter());
        bar.addView(sh, new LinearLayout.LayoutParams(a.dp(44), a.dp(44)));
        bar.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView fx = Ui.tv(a, "Effects", 12.5f, Theme.fg(), false);
        fx.setGravity(Gravity.CENTER);
        fx.setBackground(Ui.rr(Theme.dark ? 0x33FFFFFF : 0x22000000, a.dp(7)));
        fx.setOnClickListener(v -> showEffects());
        bar.addView(fx, new LinearLayout.LayoutParams(a.dp(76), a.dp(26)));
        markMode();
    }

    View segBtn(String sym, final Runnable r) {
        FrameLayout f = new FrameLayout(a);
        f.addView(Sf.img(a, sym, Theme.fg()), new FrameLayout.LayoutParams(a.dp(16), a.dp(14), Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        return f;
    }

    FrameLayout shutterHolder() { return (FrameLayout) shutterIcon.getParent(); }

    void styleShutter(FrameLayout sh) {
        sh.removeAllViews();
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (videoMode) {
            g.setColor(0xFFFFFFFF);
            g.setStroke(a.dp(2), 0xFF8E8E93);
            sh.setBackground(g);
            View dot = new View(a);
            GradientDrawable d = new GradientDrawable();
            d.setShape(recording ? GradientDrawable.RECTANGLE : GradientDrawable.OVAL);
            d.setCornerRadius(a.dp(4));
            d.setColor(0xFFE0322F);
            dot.setBackground(d);
            int s = a.dp(recording ? 16 : 30);
            sh.addView(dot, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        } else {
            g.setColor(0xFFE0322F);
            sh.setBackground(g);
            sh.addView(Sf.img(a, "camera.fill", 0xFFFFFFFF), new FrameLayout.LayoutParams(a.dp(22), a.dp(18), Gravity.CENTER));
        }
        shutterIcon = new TextView(a);
        sh.addView(shutterIcon, new FrameLayout.LayoutParams(0, 0));
    }

    void markMode() {
        modeGrid.setBackground(null);
        modePhoto.setBackground(!videoMode ? Ui.rr(Theme.dark ? 0x66FFFFFF : 0x55000000, a.dp(5)) : null);
        modeVideo.setBackground(videoMode ? Ui.rr(Theme.dark ? 0x66FFFFFF : 0x55000000, a.dp(5)) : null);
    }

    void setVideoMode(boolean v) {
        if (recording) return;
        videoMode = v;
        hideEffects(); hideGallery();
        markMode();
        styleShutter(shutterHolder());
    }

    // ---------- toggles ----------
    void toggleFlash() {
        if (!flashSupported) { Toast.makeText(a, front ? "Screen flash on" : "Flash isn't supported on this camera", Toast.LENGTH_SHORT).show(); if (!front) return; }
        flashOn = !flashOn;
        setIcon(flashBtnIcon, flashOn ? "bolt.fill" : "bolt.slash.fill", flashOn ? 0xFFFFD60A : 0xFFFFFFFF);
        if (videoMode && recording && !front) setTorch(flashOn);
    }

    void setIcon(ImageView v, String sym, int color) { v.setImageDrawable(Sf.get(a, sym, color)); }

    void cycleTimer() {
        timerSec = timerSec == 0 ? 3 : timerSec == 3 ? 10 : 0;
        timerLabel.setVisibility(timerSec == 0 ? View.GONE : View.VISIBLE);
        timerLabel.setText("\u23F1 " + timerSec + "s");
        setIcon(timerBtnIcon, "timer", timerSec == 0 ? 0xFFFFFFFF : 0xFFFFD60A);
    }

    void switchCamera() {
        if (recording) return;
        front = !front;
        applyMirror();
        openCamera();
    }

    void applyMirror() { if (tex != null) tex.setScaleX(front ? -1f : 1f); }

    // ---------- effects ----------
    ColorMatrix matrixFor(int i) {
        ColorMatrix m = new ColorMatrix();
        switch (i) {
            case 1: m.setSaturation(0); break;
            case 2: m.set(new float[]{.393f, .769f, .189f, 0, 0, .349f, .686f, .168f, 0, 0, .272f, .534f, .131f, 0, 0, 0, 0, 0, 1, 0}); break;
            case 3: m.setSaturation(0); m.postConcat(new ColorMatrix(new float[]{-1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0})); break;
            case 4: m.setSaturation(1.9f); m.postConcat(new ColorMatrix(new float[]{1.12f, 0, 0, 0, -8, 0, 1.12f, 0, 0, -8, 0, 0, 1.12f, 0, -8, 0, 0, 0, 1, 0})); break;
            case 5: m.set(new float[]{.88f, 0, 0, 0, 0, 0, 1f, 0, 0, 4, 0, 0, 1.18f, 0, 8, 0, 0, 0, 1, 0}); break;
            case 6: m.set(new float[]{1.18f, 0, 0, 0, 6, 0, 1.02f, 0, 0, 0, 0, 0, .86f, 0, 0, 0, 0, 0, 1, 0}); break;
            case 7: m.setSaturation(.65f); m.postConcat(new ColorMatrix(new float[]{.82f, 0, 0, 0, 34, 0, .82f, 0, 0, 34, 0, 0, .82f, 0, 34, 0, 0, 0, 1, 0})); break;
            case 8: m.setSaturation(0); m.postConcat(new ColorMatrix(new float[]{1.6f, 0, 0, 0, -70, 0, 1.6f, 0, 0, -70, 0, 0, 1.6f, 0, -70, 0, 0, 0, 1, 0})); break;
            default: return null;
        }
        return m;
    }

    ColorMatrixColorFilter filterFor(int i) {
        ColorMatrix m = matrixFor(i);
        return m == null ? null : new ColorMatrixColorFilter(m);
    }

    void applyEffect() {
        if (tex == null) return;
        ColorMatrixColorFilter f = filterFor(effect);
        if (f == null) tex.setLayerType(View.LAYER_TYPE_NONE, null);
        else { Paint p = new Paint(); p.setColorFilter(f); tex.setLayerType(View.LAYER_TYPE_HARDWARE, p); }
    }

    void buildEffectsPage() {
        effectsPage = new FrameLayout(a);
        effectsPage.setBackgroundColor(0xFF1B1B1F);
        effectsPage.setVisibility(View.GONE);
        effectsPage.setClickable(true);
        GridLayout g = new GridLayout(a);
        g.setColumnCount(3);
        g.setRowCount(3);
        fxTiles.clear();
        for (int i = 0; i < FX.length; i++) {
            final int k = i;
            FrameLayout t = new FrameLayout(a);
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setColorFilter(filterFor(i));
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            TextView lb = Ui.tv(a, FX[i], 13, Color.WHITE, false);
            lb.setShadowLayer(a.dp(3), 0, 0, 0xCC000000);
            lb.setGravity(Gravity.CENTER);
            t.addView(lb, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            lb.setPadding(0, 0, 0, a.dp(6));
            if (i == effect) t.setForeground(ring());
            t.setOnClickListener(v -> { effect = k; applyEffect(); hideEffects(); });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / 3, 1f), GridLayout.spec(i % 3, 1f));
            lp.width = 0; lp.height = 0;
            lp.setMargins(a.dp(1), a.dp(1), a.dp(1), a.dp(1));
            g.addView(t, lp);
            fxTiles.add(iv);
        }
        effectsPage.addView(g, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, -1);
        ep.bottomMargin = 0;
        previewBox.addView(effectsPage, ep);
    }

    android.graphics.drawable.Drawable ring() {
        GradientDrawable g = new GradientDrawable();
        g.setStroke(a.dp(3), Theme.accent);
        g.setColor(0);
        return g;
    }

    final Runnable fxTick = new Runnable() {
        @Override public void run() {
            if (closed || effectsPage == null || effectsPage.getVisibility() != View.VISIBLE) return;
            try {
                if (tex != null && tex.isAvailable()) {
                    if (fxBmp == null) fxBmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
                    tex.getBitmap(fxBmp);
                    for (ImageView iv : fxTiles) { iv.setImageBitmap(null); iv.setImageBitmap(fxBmp); }
                }
            } catch (Throwable t) { }
            ui.postDelayed(this, 140);
        }
    };

    void showEffects() {
        if (recording) return;
        for (int i = 0; i < fxTiles.size(); i++) ((FrameLayout) fxTiles.get(i).getParent()).setForeground(i == effect ? ring() : null);
        effectsPage.setVisibility(View.VISIBLE);
        ui.post(fxTick);
    }

    void hideEffects() { if (effectsPage != null) effectsPage.setVisibility(View.GONE); }

    // ---------- camera ----------
    String pickCamera() throws CameraAccessException {
        int want = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        for (String id : cm.getCameraIdList()) {
            Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (f != null && f == want) return id;
        }
        String[] ids = cm.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    void startThread() {
        if (bt != null) return;
        bt = new HandlerThread("PhotoBooth");
        bt.start();
        bh = new Handler(bt.getLooper());
    }

    @android.annotation.SuppressLint("MissingPermission")
    void openCamera() {
        if (closed || !has(Manifest.permission.CAMERA) || tex == null || !tex.isAvailable()) return;
        closeCamera();
        startThread();
        try {
            cm = (CameraManager) a.getSystemService(android.content.Context.CAMERA_SERVICE);
            camId = pickCamera();
            if (camId == null) { Toast.makeText(a, "No camera found", Toast.LENGTH_SHORT).show(); return; }
            CameraCharacteristics ch = cm.getCameraCharacteristics(camId);
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOri = so == null ? 90 : so;
            flashSupported = Boolean.TRUE.equals(ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            pSize = choose(map.getOutputSizes(SurfaceTexture.class));
            cm.openCamera(camId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) { dev = d; ui.post(() -> startPreview()); }
                @Override public void onDisconnected(CameraDevice d) { d.close(); dev = null; }
                @Override public void onError(CameraDevice d, int e) { d.close(); dev = null; }
            }, bh);
        } catch (Exception e) {
            Toast.makeText(a, "Can't open the camera", Toast.LENGTH_SHORT).show();
        }
    }

    Size choose(Size[] sizes) {
        Size best = null;
        for (Size s : sizes) {
            if (s.getWidth() * 9 != s.getHeight() * 16 && s.getWidth() * 3 != s.getHeight() * 4) continue;
            if (s.getWidth() > 1920) continue;
            if (best == null || Math.abs(s.getWidth() - 1280) < Math.abs(best.getWidth() - 1280)) best = s;
        }
        return best != null ? best : sizes[0];
    }

    void startPreview() {
        if (closed || dev == null || !tex.isAvailable()) return;
        try {
            SurfaceTexture st = tex.getSurfaceTexture();
            st.setDefaultBufferSize(pSize.getWidth(), pSize.getHeight());
            previewSurface = new Surface(st);
            List<Surface> out = new ArrayList<>();
            out.add(previewSurface);
            Surface rs = null;
            if (recording && rec != null) { rs = rec.getSurface(); out.add(rs); }
            pb = dev.createCaptureRequest(recording ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
            pb.addTarget(previewSurface);
            if (rs != null) pb.addTarget(rs);
            pb.set(CaptureRequest.CONTROL_AF_MODE, recording ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            final boolean startRec = recording;
            dev.createCaptureSession(out, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (dev == null) return;
                    sess = s;
                    try {
                        s.setRepeatingRequest(pb.build(), null, bh);
                        if (startRec && rec != null) rec.start();
                    } catch (Exception e) { }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) { }
            }, bh);
            transform(tex.getWidth(), tex.getHeight());
        } catch (Exception e) { }
    }

    void transform(int vw, int vh) {
        if (tex == null || pSize == null || vw == 0 || vh == 0) return;
        int rot = a.getWindowManager().getDefaultDisplay().getRotation();
        Matrix m = new Matrix();
        RectF view = new RectF(0, 0, vw, vh);
        float cx = view.centerX(), cy = view.centerY();
        RectF buf = new RectF(0, 0, pSize.getHeight(), pSize.getWidth());
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
            buf.offset(cx - buf.centerX(), cy - buf.centerY());
            m.setRectToRect(view, buf, Matrix.ScaleToFit.FILL);
            float s = Math.max(vh / (float) pSize.getHeight(), vw / (float) pSize.getWidth());
            m.postScale(s, s, cx, cy);
            m.postRotate(90 * (rot - 2), cx, cy);
        } else if (rot == Surface.ROTATION_180) {
            m.postRotate(180, cx, cy);
        }
        tex.setTransform(m);
    }

    int displayDegrees() {
        switch (a.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }

    void setTorch(boolean on) {
        try {
            if (pb == null || sess == null) return;
            pb.set(CaptureRequest.FLASH_MODE, on ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            sess.setRepeatingRequest(pb.build(), null, bh);
        } catch (Exception e) { }
    }

    void closeCamera() {
        try { if (sess != null) { sess.close(); sess = null; } } catch (Exception e) { }
        try { if (dev != null) { dev.close(); dev = null; } } catch (Exception e) { }
    }

    void release() {
        closed = true;
        ui.removeCallbacksAndMessages(null);
        if (recording) stopRecording(false);
        closeCamera();
        if (bt != null) { bt.quitSafely(); bt = null; }
        try { sound.release(); } catch (Exception e) { }
        if (cur == this) cur = null;
    }

    // ---------- shutter ----------
    void onShutter() {
        if (galleryPage.getVisibility() == View.VISIBLE || effectsPage.getVisibility() == View.VISIBLE) return;
        if (videoMode) {
            if (recording) stopRecording(true); else countdownThen(this::startRecording);
        } else countdownThen(this::capture);
    }

    void countdownThen(final Runnable go) {
        if (timerSec <= 0) { go.run(); return; }
        countdownHolder.removeAllViews();
        final int[] n = {timerSec};
        final TextView t = Ui.tv(a, String.valueOf(n[0]), 96, Color.WHITE, true);
        t.setShadowLayer(a.dp(8), 0, 0, 0x99000000);
        countdownHolder.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (closed) return;
                if (n[0] <= 0) { countdownHolder.removeAllViews(); go.run(); return; }
                t.setText(String.valueOf(n[0]));
                t.setScaleX(1.5f); t.setScaleY(1.5f); t.setAlpha(1f);
                t.animate().scaleX(1f).scaleY(1f).alpha(.35f).setDuration(900).start();
                n[0]--;
                ui.postDelayed(this, 1000);
            }
        };
        tick.run();
    }

    void capture() {
        if (tex == null || !tex.isAvailable()) return;
        final boolean torch = flashOn && flashSupported && !front;
        final boolean screenFlash = flashOn && front;
        if (torch) setTorch(true);
        if (screenFlash) { flashView.setVisibility(View.VISIBLE); flashView.setAlpha(1f); }
        ui.postDelayed(() -> {
            if (closed) return;
            Bitmap raw = null;
            try { raw = tex.getBitmap(); } catch (Throwable t) { }
            if (torch) setTorch(false);
            try { sound.play(MediaActionSound.SHUTTER_CLICK); } catch (Throwable t) { }
            flashView.setVisibility(View.VISIBLE);
            flashView.setAlpha(screenFlash ? 1f : .9f);
            flashView.animate().alpha(0f).setDuration(350).withEndAction(() -> flashView.setVisibility(View.GONE)).start();
            if (raw == null) return;
            final Bitmap src = raw;
            final int fx = effect;
            final boolean mirror = front;
            new Thread(() -> {
                Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                p.setColorFilter(filterFor(fx));
                Matrix m = new Matrix();
                if (mirror) m.postScale(-1, 1, src.getWidth() / 2f, src.getHeight() / 2f);
                c.drawBitmap(src, m, p);
                Uri u = saveImage(out);
                ui.post(() -> {
                    if (u != null) { addItem(u, false); Toast.makeText(a, "Saved to Photos", Toast.LENGTH_SHORT).show(); }
                    else Toast.makeText(a, "Couldn't save the photo", Toast.LENGTH_SHORT).show();
                });
            }).start();
        }, torch ? 450 : (screenFlash ? 300 : 0));
    }

    // ---------- video ----------
    void startRecording() {
        if (dev == null || recording) return;
        try {
            rec = new MediaRecorder();
            boolean audio = has(Manifest.permission.RECORD_AUDIO);
            if (audio) rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            vfile = new File(a.getCacheDir(), "pb_" + System.currentTimeMillis() + ".mp4");
            rec.setOutputFile(vfile.getAbsolutePath());
            rec.setVideoEncodingBitRate(8000000);
            rec.setVideoFrameRate(30);
            rec.setVideoSize(pSize.getWidth(), pSize.getHeight());
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (audio) rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            int deg = displayDegrees();
            rec.setOrientationHint(front ? (sensorOri + deg) % 360 : (sensorOri - deg + 360) % 360);
            rec.prepare();
            recording = true;
            recStart = System.currentTimeMillis();
            styleShutter(shutterHolder());
            recLabel.setVisibility(View.VISIBLE);
            ui.post(recTick);
            try { sound.play(MediaActionSound.START_VIDEO_RECORDING); } catch (Throwable t) { }
            try { sess.close(); } catch (Exception e) { }
            sess = null;
            startPreview();
            if (flashOn && !front) ui.postDelayed(() -> setTorch(true), 600);
        } catch (Exception e) {
            recording = false;
            try { rec.release(); } catch (Exception x) { }
            rec = null;
            Toast.makeText(a, "Can't start recording", Toast.LENGTH_SHORT).show();
        }
    }

    final Runnable recTick = new Runnable() {
        @Override public void run() {
            if (!recording || closed) return;
            long s = (System.currentTimeMillis() - recStart) / 1000;
            recLabel.setText(String.format(Locale.US, "\u25CF %02d:%02d", s / 60, s % 60));
            ui.postDelayed(this, 500);
        }
    };

    void stopRecording(boolean save) {
        if (!recording) return;
        recording = false;
        try { if (sess != null) sess.stopRepeating(); } catch (Exception e) { }
        try { rec.stop(); } catch (Exception e) { save = false; }
        try { rec.reset(); rec.release(); } catch (Exception e) { }
        rec = null;
        if (!closed) {
            try { sound.play(MediaActionSound.STOP_VIDEO_RECORDING); } catch (Throwable t) { }
            recLabel.setVisibility(View.GONE);
            styleShutter(shutterHolder());
            try { if (sess != null) sess.close(); } catch (Exception e) { }
            sess = null;
            startPreview();
        }
        final File f = vfile;
        if (save && f != null && f.exists()) {
            new Thread(() -> {
                final Uri u = saveVideo(f);
                f.delete();
                ui.post(() -> {
                    if (u != null) { addItem(u, true); Toast.makeText(a, "Saved to Photos", Toast.LENGTH_SHORT).show(); }
                });
            }).start();
        }
    }

    // ---------- saving ----------
    String stamp() { return new SimpleDateFormat("yyyy-MM-dd 'at' HH.mm.ss", Locale.US).format(new Date()); }

    Uri saveImage(Bitmap bm) {
        try {
            ContentValues v = new ContentValues();
            String name = "Photo Booth " + stamp() + ".jpg";
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            File legacy = null;
            if (Build.VERSION.SDK_INT >= 29) {
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photo Booth");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Photo Booth");
                dir.mkdirs();
                legacy = new File(dir, name);
                v.put(MediaStore.Images.Media.DATA, legacy.getAbsolutePath());
            }
            Uri u = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            try (OutputStream os = a.getContentResolver().openOutputStream(u)) { bm.compress(Bitmap.CompressFormat.JPEG, 95, os); }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues d = new ContentValues();
                d.put(MediaStore.Images.Media.IS_PENDING, 0);
                a.getContentResolver().update(u, d, null, null);
            }
            return u;
        } catch (Exception e) { return null; }
    }

    Uri saveVideo(File f) {
        try {
            ContentValues v = new ContentValues();
            String name = "Photo Booth " + stamp() + ".mp4";
            v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT >= 29) {
                v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Photo Booth");
                v.put(MediaStore.Video.Media.IS_PENDING, 1);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Photo Booth");
                dir.mkdirs();
                v.put(MediaStore.Video.Media.DATA, new File(dir, name).getAbsolutePath());
            }
            Uri u = a.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            try (OutputStream os = a.getContentResolver().openOutputStream(u); FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues d = new ContentValues();
                d.put(MediaStore.Video.Media.IS_PENDING, 0);
                a.getContentResolver().update(u, d, null, null);
            }
            return u;
        } catch (Exception e) { return null; }
    }

    // ---------- gallery ----------
    List<String[]> items() {
        List<String[]> l = new ArrayList<>();
        try {
            JSONArray ar = new JSONArray(a.prefs.getString("pb_items", "[]"));
            for (int i = 0; i < ar.length(); i++) {
                JSONObject o = ar.getJSONObject(i);
                l.add(new String[]{o.getString("u"), o.optBoolean("v") ? "1" : "0"});
            }
        } catch (Exception e) { }
        return l;
    }

    void saveItems(List<String[]> l) {
        try {
            JSONArray ar = new JSONArray();
            for (String[] s : l) ar.put(new JSONObject().put("u", s[0]).put("v", s[1].equals("1")));
            a.prefs.edit().putString("pb_items", ar.toString()).apply();
        } catch (Exception e) { }
    }

    void addItem(Uri u, boolean video) {
        List<String[]> l = items();
        l.add(0, new String[]{u.toString(), video ? "1" : "0"});
        saveItems(l);
    }

    void buildGalleryPage() {
        galleryPage = new FrameLayout(a);
        galleryPage.setBackgroundColor(Theme.dark ? 0xFF1B1B1F : 0xFFF2F2F7);
        galleryPage.setVisibility(View.GONE);
        galleryPage.setClickable(true);
        previewBox.addView(galleryPage, new FrameLayout.LayoutParams(-1, -1));
    }

    void showGallery() {
        if (recording) return;
        galleryPage.removeAllViews();
        galleryPage.setVisibility(View.VISIBLE);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(6));
        TextView title = Ui.tv(a, "Camera Gallery", 15, Theme.fg(), true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView done = Ui.tv(a, "Done", 13, Color.WHITE, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Ui.rr(Theme.accent, a.dp(14)));
        done.setOnClickListener(v -> hideGallery());
        top.addView(done, new LinearLayout.LayoutParams(a.dp(62), a.dp(28)));
        col.addView(top);
        final List<String[]> its = items();
        if (its.isEmpty()) {
            TextView e = Ui.tv(a, "No photos or videos yet", 14, Theme.sub(), false);
            e.setGravity(Gravity.CENTER);
            col.addView(e, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            ScrollView sv = new ScrollView(a);
            GridLayout g = new GridLayout(a);
            g.setColumnCount(4);
            g.setPadding(a.dp(6), 0, a.dp(6), a.dp(8));
            int cell = Math.max(a.dp(70), (previewBox.getWidth() - a.dp(12)) / 4);
            for (final String[] it : its) {
                FrameLayout f = new FrameLayout(a);
                final ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundColor(0xFF333338);
                f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                if (it[1].equals("1")) {
                    ImageView pl = Sf.img(a, "video.fill", 0xFFFFFFFF);
                    f.addView(pl, new FrameLayout.LayoutParams(a.dp(18), a.dp(14), Gravity.BOTTOM | Gravity.LEFT) {{ leftMargin = a.dp(6); bottomMargin = a.dp(6); }});
                }
                final Uri u = Uri.parse(it[0]);
                new Thread(() -> {
                    final Bitmap b = thumb(u, it[1].equals("1"));
                    ui.post(() -> { if (b != null) iv.setImageBitmap(b); });
                }).start();
                f.setOnClickListener(v -> openItem(u, it[1].equals("1")));
                f.setOnLongClickListener(v -> {
                    a.confirm("Delete this item?", () -> {
                        try { a.getContentResolver().delete(u, null, null); } catch (Exception e) { }
                        List<String[]> cur2 = items();
                        for (int i = cur2.size() - 1; i >= 0; i--) if (cur2.get(i)[0].equals(it[0])) cur2.remove(i);
                        saveItems(cur2);
                        showGallery();
                    });
                    return true;
                });
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = cell - a.dp(4); lp.height = cell - a.dp(4);
                lp.setMargins(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
                g.addView(f, lp);
            }
            sv.addView(g);
            col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        galleryPage.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    Bitmap thumb(Uri u, boolean video) {
        try {
            if (Build.VERSION.SDK_INT >= 29) return a.getContentResolver().loadThumbnail(u, new Size(320, 320), null);
            long id = android.content.ContentUris.parseId(u);
            return video ? MediaStore.Video.Thumbnails.getThumbnail(a.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                    : MediaStore.Images.Thumbnails.getThumbnail(a.getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
        } catch (Throwable t) { return null; }
    }

    void openItem(Uri u, boolean video) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        i.setDataAndType(u, video ? "video/mp4" : "image/jpeg");
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startExternal(i);
    }

    void hideGallery() { if (galleryPage != null) galleryPage.setVisibility(View.GONE); }
}
