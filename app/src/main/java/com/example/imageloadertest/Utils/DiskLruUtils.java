package com.example.imageloadertest.Utils;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 基于磁盘缓存的工具类
 * Created by Administrator on 2016/5/7.
 */
public class DiskLruUtils {

    private static final int IO_BUFFER_SIZE = 1024 * 10;
    private static final String TAG = "DiskLruUtils";

    /**
     * 寻找缓存路径
     * @param ctx
     * @param name
     * @return
     */
    public static File getDiskCacheDir(Context ctx, String name) {
        String path;
        //若sd卡存在
        if (Environment.getExternalStorageDirectory().equals(Environment.MEDIA_MOUNTED)) {
            //拿到sd卡路径
            path = Environment.getExternalStorageDirectory().getPath();
        } else {
            //拿到缓存路径
            path = ctx.getCacheDir().getPath();
        }
        return new File(path + "/" + name);
    }

    /**
     * 根据url特征进行MD5编码
     * @param url
     * @return
     */
    public static String hashKeyFromUrl(String url) {
        String key = null;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            byte[] bt = mDigest.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bt.length; i++) {
                //取出有效的值
                int result = bt[i] & 0xFF;
                String str = Integer.toHexString(result);
                if (str.length() == 1) {
                    str = str + "0";
                }
                sb.append(str);
            }
            key = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return key;
    }

    /**
     * 根据url将数据转化为流
     * @param urlString
     * @param ou
     * @return
     */
    public static boolean downloadUrlToStream(String urlString, OutputStream ou) {
        HttpURLConnection conn = null;
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            //打开连接
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();

            bis = new BufferedInputStream(conn.getInputStream(), IO_BUFFER_SIZE);
            bos = new BufferedOutputStream(ou, IO_BUFFER_SIZE);
            int len;
            while ((len = bis.read()) != -1) {
                bos.write(len);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
                if (bos != null) {
                    bos.close();
                }
                if (bis != null) {
                    bis.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
