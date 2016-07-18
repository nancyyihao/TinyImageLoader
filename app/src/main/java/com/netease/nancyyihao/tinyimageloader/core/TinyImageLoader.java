package com.netease.nancyyihao.tinyimageloader.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by nancyyihao on 16/7/18.
 */
public class TinyImageLoader {
    private static final String TAG = TinyImageLoader.class.getSimpleName();
    private static final int CPU_COUNT =Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1 ;
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1 ;
    private static final long KEEP_ALIVE = 10L;

    private static final int DISK_CACHE_SIZE = 50 * 1024 * 1024;  // default size is 50MB
    private static final int IO_BUFFER_SIZE = 8 * 1024 ;
    private static final int DISK_CACHE_INDEX = 0 ;
    private static final int TAG_KEY_URL = 0x0af23d ;//R.id.tinyimageloader_url;
    private boolean mIsDIskLruCacheCreated = false ;

    private static final ThreadFactory sThreadFatory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1) ;
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }
    };

    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(),
            sThreadFatory
    ) ;

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            // TODO handler message
        }
    } ;

    private ImageResizer mImageResizer = new ImageResizer();
    private LruCache<String, Bitmap> mMemoryCache ;
    private DiskLruCache mDiskLruCache ;
    private Context mContext ;



    private TinyImageLoader(Context context) {
        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8 ;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024 ;
            }
        };

        File diskCacheDir = getDiskCacheDir(mContext, "bitmap") ;
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }

        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDiskLruCache = mDiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE) ;
                mIsDIskLruCacheCreated = true ;
            } catch ( IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static TinyImageLoader build(Context context) {
        return new TinyImageLoader(context) ;
    }

    public File getDiskCacheDir(Context context, String uniqueName) {
        boolean externStorageAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ;
        final String cachePath ;
        if (externStorageAvailable) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName) ;
    }

    public void bindBitmap(final String url, final ImageView imageView) {
        bindBitmap(url, imageView, 0, 0);
    }

    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHieght) {
        imageView.setTag(TAG_KEY_URL, url);
        Bitmap bitmap = loadBitmapFromMemCache(url) ;
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        final Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, reqWidth, reqHieght) ;
                if (bitmap != null) {
                    // TODO handler result
                }
            }
        } ;
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    private Bitmap loadBitmapFromMemCache(String url) {
        return getBitMapFromCache(url) ;
    }

    private Bitmap loadBitmap(final String url, final int reqWidth, final int reqHieght) {
        Bitmap bitmap = loadBitmapFromMemCache(url) ;
        if (bitmap != null) {
            return bitmap ;
        }

        try {
            bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHieght) ;
            if (bitmap != null) {
                return bitmap ;
            }
            bitmap = loadBitmapFromHttp(url, reqWidth, reqHieght) ;
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bitmap == null && !mIsDIskLruCacheCreated) {
            bitmap = downloadBitmapFromUrl(url) ;
        }
        return bitmap ;
    }

    private Bitmap loadBitmapFromDiskCache(final String url, final int reqWidth, final int reqHieght) throws IOException {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            throw new RuntimeException("can not visit network from UI Thread.");
        }
        if (mDiskLruCache == null) {
            return null ;
        }

        Bitmap bitmap = null ;
        String key = hashKeyFromUrl(url) ;
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key) ;
        if (snapshot != null) {
            FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor fileDescriptor = fileInputStream.getFD();
            bitmap = mImageResizer.decodeSampleBitmapFromFileDesscritor(fileDescriptor, reqWidth, reqHieght) ;
            if (bitmap != null) {
                addBitmapToCache(key, bitmap);
            }
        }

        return bitmap ;
    }

    private Bitmap loadBitmapFromHttp(final String url, final int reqWidth, final int reqHieght) throws IOException{
        if (Looper.getMainLooper() == Looper.myLooper()) {
            throw new RuntimeException("can not visit network from UI Thread.");
        }
        if (mDiskLruCache == null) {
            return null ;
        }

        String key = hashKeyFromUrl(url) ;
        DiskLruCache.Editor editor = mDiskLruCache.edit(key) ;
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX) ;
            if (downloadImageFromUrlToSteam(url, outputStream)) {
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }

        return  loadBitmapFromDiskCache(url, reqWidth, reqHieght) ;
    }

    private boolean downloadImageFromUrlToSteam(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null ;
        BufferedOutputStream bos = null ;
        BufferedInputStream bis = null ;
        try {
            final URL url = new URL(urlString) ;
            urlConnection = (HttpURLConnection) url.openConnection();
            bis = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            bos = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE) ;

            int b ;
            while ((b = bis.read()) != -1) {
                bos.write(b);
            }
            return true ;
        } catch (IOException e) {
            Log.e(TAG, "download bitmap failed." + e) ;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            closeQuietly(bis);
            closeQuietly(bos);
        }

        return false ;
    }

    private Bitmap downloadBitmapFromUrl(final String urlString) {
        Bitmap bitmap = null ;
        HttpURLConnection urlConnection = null ;
        BufferedInputStream bis = null ;
        try {
            final URL url = new URL(urlString) ;
            bis = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(bis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            closeQuietly(bis);
        }
        return bitmap;
    }

    private void addBitmapToCache(String key, Bitmap bitmap) {
        if (getBitMapFromCache(key) == null) {
            mMemoryCache.put(key, bitmap) ;
        }
    }

    private Bitmap getBitMapFromCache(String key) {
        return mMemoryCache.get(key) ;
    }

    private long getUsableSpace(File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath()) ;
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks() ;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i=0 ; i<bytes.length ; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]) ;
            if (hex.length() == 1) {
                sb.append("0") ;
            }
            sb.append(hex) ;
        }
        return sb.toString() ;
    }

    private String hashKeyFromUrl(String url) {
        String cacheKey ;
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5") ;
            md.update(url.getBytes());
            cacheKey = bytesToHexString(md.digest()) ;
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode()) ;
        }
        return cacheKey ;
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
