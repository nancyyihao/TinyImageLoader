package com.netease.nancyyihao.tinyimageloader.core;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by nancyyihao on 16/7/18.
 */
public class ImageResizer {
    private static final String TAG = ImageResizer.class.getSimpleName();

    public Bitmap decodeSampleBitmapFromResources(Resources res, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true ;
        BitmapFactory.decodeResource(res, resId, options);
        options.inSampleSize = calcInSampleSize(options, reqWidth, reqHeight) ;
        //
        options.inJustDecodeBounds = false ;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public int calcInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (reqWidth == 0 || reqHeight ==0) {
            return 1 ;
        }

        int width = options.outWidth ;
        int height = options.outHeight ;
        int inSampleSize = 1 ;

        if (width > reqWidth || height > reqHeight) {
            int halfWidth = width / 2 ;
            int halfHeight = height / 2 ;

            while ((halfWidth / inSampleSize >= reqWidth) && (halfHeight / inSampleSize >= reqHeight )) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize ;
    }

    public Bitmap decodeSampleBitmapFromFileDesscritor(FileDescriptor fileDescriptor, int reqWidth, int reqHeight) {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true ;
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options) ;

        options.inSampleSize = calcInSampleSize(options, reqWidth, reqHeight) ;
        //
        options.inJustDecodeBounds = false ;
        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options) ;
    }
}
