package com.example.imageloadertest;

/**
 * 只利用DiskCache方法缓冲图片
 * Created by Administrator on 2016/5/8.
 */

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;

import com.example.imageloadertest.Utils.DiskLruUtils;
import com.example.imageloadertest.Utils.ImageCompress;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import libcore.io.DiskLruCache;


public class DiskCacheTest extends Activity {

    private static final String TAG = "MainActivity";
    private DiskLruCache mDiskCache;
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    private static final String URL = "http://192.168.0.102:8080/april.jpg";
    private static final int DISK_CACHE_INDEX = 0;
    private String mKey;
    private ProgressDialog mPd;
    private Bitmap bitmapImage;

    ImageCompress imageCompress;
    private ImageView ivBitmap;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                boolean isSuccess = (boolean) msg.obj;
                if (isSuccess) {
                    DiskLruCache.Snapshot snapshot = null;
                    try {
                        snapshot = mDiskCache.get(mKey);
                        if (snapshot != null) {
                            FileInputStream inputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                            Bitmap bitmap = imageCompress.decodeBitMapFromFd(inputStream.getFD(), 200, 200);
                            if (bitmap == null) {
                                System.out.println("下载的bitmap为空-------------------！");
                            }
                            //ivBitmap.setImageBitmap(bitmap);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                if (mPd != null && mPd.isShowing()) {
                    mPd.dismiss();
                }
            } else if (msg.what == 2) {
                ivImage.setImageBitmap(bitmapImage);
            }
        }
    };
    private ImageView ivImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //ivBitmap = (ImageView) findViewById(R.id.iv_bitmap);
        //ivImage = (ImageView) findViewById(R.id.iv_image);
        mPd = new ProgressDialog(this);
        //拿到目录
        File diskCacheDir = DiskLruUtils.getDiskCacheDir(this, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdir();
        }
        //初始化磁盘缓存
        try {
            mDiskCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
            if (mDiskCache != null) {
                mKey = DiskLruUtils.hashKeyFromUrl(URL);
                //输入流通过key值拿到
                DiskLruCache.Snapshot snapshot = mDiskCache.get(mKey);
                if (snapshot != null) {
                    System.out.println("snapshot不为空" + snapshot);
                    System.out.println("从缓存区中读取数据---------");
                    FileInputStream fis = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                    FileDescriptor fd = fis.getFD();
                    //压缩处理图片
                    Bitmap bitmap = imageCompress.decodeBitMapFromFd(fd, 200, 200);
                    if (bitmap != null) {
                        ivBitmap.setImageBitmap(bitmap);
                        System.out.println("压缩后的高度：" + bitmap.getHeight() + "压缩后的宽度：" + bitmap.getWidth());
                    } else {
                        System.out.println("读出的bitmap为空-------------------！");
                    }
                } else {
                    mPd.show();
                    new Thread() {
                        @Override
                        public void run() {
                            mPd.setMessage("玩命加载中....");
                            downloadImageFromUrl();
                        }
                    }.start();

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 点击访问网络下载图片
     *
     * @param v
     */
    public void download(View v) {
        new Thread() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    java.net.URL url = new URL(URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    if (conn.getResponseCode() == 200) {
                        InputStream inputStream = conn.getInputStream();
                        bitmapImage = BitmapFactory.decodeStream(inputStream);
                        Message msg = mHandler.obtainMessage();
                        msg.what = 2;
                        mHandler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * 将图片下载到本地缓存中
     */
    public void downloadImageFromUrl() {
        try {
            System.out.println("从URL中开始下载图片！！！！！");
            mKey = DiskLruUtils.hashKeyFromUrl(URL);
            DiskLruCache.Editor editor = mDiskCache.edit(mKey);
            if (editor != null) {
                OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
                if (DiskLruUtils.downloadUrlToStream(URL, outputStream)) {
                    //图片下载完成
                    editor.commit();
                    Message msg = mHandler.obtainMessage();

                    msg.what = 1;
                    msg.obj = true;
                    mHandler.sendMessage(msg);
                    ivBitmap.setTag(1,URL);

                } else {
                    editor.abort();
                }
            }
            mDiskCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (mDiskCache != null) {
                mDiskCache.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }
}

