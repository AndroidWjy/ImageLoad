package com.example.imageloadertest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import com.example.imageloadertest.Utils.DiskLruUtils;
import com.example.imageloadertest.Utils.ImageCompress;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import libcore.io.DiskLruCache;

/**
 * 加载图片的类
 * Created by Administrator on 2016/5/7.
 */
public class ImageLoad {
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskCache;
    private Context mContext;
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    private static final int DISK_CACHE_INDEX = 0;

    private static final int TAG_KET_URL = 2;
    //handler发送消息成功
    private static final int RESULT_OK = 1;
    private boolean mIsDiskCreated = false;
    //CPU核心数
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //核心线程数
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    //非核心线程超时等待时间
    private static final Long KEEP_ALIVE = 10L;
    //线程工厂创造线程
    private static ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            //返回线程以及线程的名字
            return new Thread(r, "ImageLoad＃" + mCount.getAndIncrement());
        }
    };
    //创建一个线程池
    private static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(), sThreadFactory);

    /**
     * 构造方法，创建缓存对象
     *
     * @param ctx
     */
    private ImageLoad(Context ctx) {
        mContext = ctx.getApplicationContext();
        //最大缓存为1/8
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        //初始化lru
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                //对bitmap的计算
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
        //拿到目录
        File diskCacheDir = DiskLruUtils.getDiskCacheDir(mContext, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdir();
        }
        //初始化磁盘缓存
        try {
            mDiskCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
            mIsDiskCreated = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 实例化对象，内部类实现
     *
     * @param ctx
     * @return
     */
    public static ImageLoad build(Context ctx) {
        return new ImageLoad(ctx);
    }

    //利用looper构造handler对象
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            String tag = (String) imageView.getTag();
            System.out.println("tag---------------"+tag);
            String url = result.url;
            if (url.equals(tag)) {
                imageView.setImageBitmap(result.bitmap);
            }else{
                System.out.println("url改变！");
            }
        }
    };

    /**
     * 内存中的添加和查找
     *
     * @param key
     * @param bitmap
     */
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemory(key) == null) {
            //将对应key值的bitmap放入
            mMemoryCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemory(String key) {
        //返回的对象和泛型有关
        return mMemoryCache.get(key);
    }

    private Bitmap loadBitmapFromMemoryCache(String url) {
        final String key = DiskLruUtils.hashKeyFromUrl(url);
        Bitmap bitmap = getBitmapFromMemory(key);
        return bitmap;
    }

    /**
     * 在本地缓存中读取数据，并将数据放到内存中
     *
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     * @throws IOException
     */
    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight) throws IOException {
        //判读当前线程是不是在子线程运行
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can out visit network from UI Thread.");
        }
        if (mDiskCache == null) {
            return null;
        }

        String key = DiskLruUtils.hashKeyFromUrl(url);
        Bitmap bitmap = null;
        DiskLruCache.Snapshot snapshot = mDiskCache.get(key);
        if (snapshot != null) {
            FileInputStream inputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            //用file的摘要
            bitmap = ImageCompress.decodeBitMapFromFd(inputStream.getFD(), reqWidth, reqHeight);
            if (bitmap != null) {
                //添加到内存
                addBitmapToMemoryCache(key, bitmap);
            }
        }
        return bitmap;
    }

    /**
     * 从网络上获取数据，并将数据写入到磁盘，返回缓存中的内容
     *
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     * @throws IOException
     */
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can out visit network from UI Thread.");
        }

        if (mDiskCache == null) {
            return null;
        }
        String key = DiskLruUtils.hashKeyFromUrl(url);
        //拿到编辑对象
        DiskLruCache.Editor editor = mDiskCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            //获取网络数据，并转换成流
            if (DiskLruUtils.downloadUrlToStream(url, outputStream)) {
                //将数据提交到磁盘上，此时snapshot已经有值了
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskCache.flush();
        }
        return loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    /**
     * 直接返回一张图片，通过数据流
     *
     * @param urlString
     * @return
     */
    private Bitmap downloadBitmapFromUrl(String urlString) {
        HttpURLConnection conn = null;
        Bitmap bitmap = null;
        BufferedInputStream in = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(conn.getInputStream());
            //直接通过数据流
            bitmap = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bitmap;
    }

    /**
     * 同步加载图片，同步接口
     *
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private Bitmap loadBitmap(String url, int reqWidth, int reqHeight) {
        //从内存中获取
        Bitmap bitmap = loadBitmapFromMemoryCache(url);
        if (bitmap != null) {
            System.out.println("在缓存中读取照片------url=" + url);
            return bitmap;
        }
        try {
            //从磁盘中获取
            bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight);
            if (bitmap != null) {
                System.out.println("在磁盘中读取照片------url=" + url);
                return bitmap;
            }
            //从网络中获取
            bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight);
            if (bitmap != null) {
                System.out.println("从网络中拉取照片-----url" + url);
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (bitmap == null && !mIsDiskCreated) {
            System.out.println("磁盘缓存对象无法创建");
            //直接通过url下载
            bitmap = downloadBitmapFromUrl(url);

        }
        return bitmap;
    }

    /**
     * 异步加载图片，异步接口，即对传入的ImageView进行设置
     *
     * @param url
     * @param imageView
     * @param reqWidth
     * @param reqHeight
     */
    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHeight) {
        Bitmap bitmap = loadBitmapFromMemoryCache(url);
        if (bitmap != null) {
            //给控件设置图片
            imageView.setImageBitmap(bitmap);
            System.out.println("缓存中读出-------------");
            return;
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                //通过loadBitmap的方法
                Bitmap bitmap = loadBitmap(url, reqWidth, reqHeight);
                LoaderResult result = new LoaderResult(imageView, url, bitmap);
                //将封装的对象发送出去
                Message message = mHandler.obtainMessage(RESULT_OK, result);
                message.sendToTarget();
            }
        };
        //执行线程
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    /**
     * 对象封装类
     */
    private static class LoaderResult {
        ImageView imageView;
        Bitmap bitmap;
        String url;

        public LoaderResult(ImageView imageView, String url, Bitmap bitmap) {
            this.imageView = imageView;
            this.url = url;
            this.bitmap = bitmap;
        }
    }


}
