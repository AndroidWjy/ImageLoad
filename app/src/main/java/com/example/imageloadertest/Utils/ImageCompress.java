package com.example.imageloadertest.Utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileDescriptor;

/**
 * 高效的加载BitMap
 * Created by Administrator on 2016/5/6.
 */
public class ImageCompress {
    private static final String TAG = "ImageCompress";

    //加载位图到资源文件
    public static Bitmap decodeBitMapFromRes(Resources res, int resId, int reqWidth, int reqHeight) {
        //有内部类创建对象
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        options.inSampleSize = calculateInSampleSize(reqWidth, reqHeight, options);
        options.inJustDecodeBounds = false;
        //Log.e(TAG, "采样压缩" + options.inSampleSize);
        return BitmapFactory.decodeResource(res, resId, options);
    }

    //根据文件描述构建压缩的位图
    public static Bitmap decodeBitMapFromFd(FileDescriptor fd, int reqWidth, int reqHeigh) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        //根据文件的描述进行编码
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        options.inSampleSize = calculateInSampleSize(reqWidth, reqHeigh, options);
        //System.out.println("压缩采样率："+options.inSampleSize);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    /**
     * 计算采样率达到
     *
     * @param reqWidth
     * @param reqHeight
     * @param options
     * @return
     */
    private static int calculateInSampleSize(int reqWidth, int reqHeight, BitmapFactory.Options options) {
        if (reqWidth == 0 || reqHeight == 0) {
            return 1;
        }
        //拿到图片的高宽
        int outWidth = options.outWidth;
        int outHeight = options.outHeight;
        //System.out.println("原宽度：" + outWidth + "原高度：" + outHeight);
        //都是2的幂次方
        int inSampleSize = 1;

        if (outHeight > reqHeight || outWidth > reqWidth) {
            //都折半处理
            int halfHeight = outHeight / 2;
            int halfWidth = outWidth / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                //扩大采样值
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    //第二种计算方法，上面更好
    private int calculateInSampleSize1(int reqWidth, int reqHeight, BitmapFactory.Options options) {
        //拿到图片的高宽
        int outWidth = options.outWidth;
        int outHeight = options.outHeight;
        Log.e(TAG, "原宽度：" + outWidth + "原高度：" + outHeight);

        //都是2的幂次方
        int inSampleSize = 1;
        inSampleSize = ((outWidth > outHeight ? outHeight : outWidth) / 100);
        if (inSampleSize <= 0) {
            inSampleSize = 1;
        }
        return inSampleSize;
    }

}
