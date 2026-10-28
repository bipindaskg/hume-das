package com.petrobot.hume;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * User-chosen thumbnails for app cards. A picture picked from the TV's storage is centre-cropped
 * to the card's 5:3 shape, scaled to 640x384 (the size of the built-in banners) and saved in the
 * launcher's private storage, one file per profile and app. Nothing is read from the original
 * picture after it has been imported, so it can be deleted or moved afterwards.
 */
final class CustomThumbs {
    static final int W = 640, H = 384;

    private CustomThumbs() {}

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "thumbs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** File for this profile's thumbnail of the app with the given key. */
    static File file(Context c, String profileId, String appKey) {
        String safe = (profileId + "_" + appKey).replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(dir(c), safe + ".png");
    }

    static boolean has(Context c, String profileId, String appKey) {
        return file(c, profileId, appKey).isFile();
    }

    /** The saved thumbnail, or null when none is set or it can't be read. */
    static Bitmap load(Context c, String profileId, String appKey) {
        File f = file(c, profileId, appKey);
        if (!f.isFile()) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable t) {
            return null;
        }
    }

    static void remove(Context c, String profileId, String appKey) {
        file(c, profileId, appKey).delete();
    }

    /** Imports the picture at {@code src} as the thumbnail; false if it isn't a readable image. */
    static boolean save(Context c, String profileId, String appKey, Uri src) {
        Bitmap out = null;
        try {
            ContentResolver cr = c.getContentResolver();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(src)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= W && o.outHeight / (sample * 2) >= H) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            Bitmap full;
            try (InputStream in = cr.openInputStream(src)) {
                if (in == null) return false;
                full = BitmapFactory.decodeStream(in, null, d);
            }
            if (full == null) return false;

            // Centre-crop to the card's aspect ratio, then scale to the banner size.
            float target = (float) W / H;
            int sw = full.getWidth(), sh = full.getHeight();
            Rect crop;
            if ((float) sw / sh > target) {
                int cw = Math.round(sh * target);
                crop = new Rect((sw - cw) / 2, 0, (sw - cw) / 2 + cw, sh);
            } else {
                int ch = Math.round(sw / target);
                crop = new Rect(0, (sh - ch) / 2, sw, (sh - ch) / 2 + ch);
            }
            out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            new Canvas(out).drawBitmap(full, crop, new Rect(0, 0, W, H), p);
            full.recycle();

            File f = file(c, profileId, appKey);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) out.recycle();
        }
    }
}
