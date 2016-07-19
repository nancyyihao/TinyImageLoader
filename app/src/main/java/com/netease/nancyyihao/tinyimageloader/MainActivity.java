package com.netease.nancyyihao.tinyimageloader;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;

import com.netease.nancyyihao.tinyimageloader.core.TinyImageLoader;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private List<String> mUrlList = new ArrayList<>();
    private TinyImageLoader mImageLoader ;
    private boolean mIsGridViewIdle = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageLoader = TinyImageLoader.build(this) ;

        for (int i=0 ; i<1000 ; i++) {
            mUrlList.add("http://img.tupianzj.com/uploads/allimg/160717/9-160GH14254.jpg");
            mUrlList.add("http://img.tupianzj.com/uploads/allimg/160717/9-160GH14256.jpg");
            mUrlList.add("http://img.tupianzj.com/uploads/allimg/160717/9-160GH14253.jpg");
            mUrlList.add("http://img.tupianzj.com/uploads/allimg/160717/9-160GH14257.jpg");
        }

        GridView gridView = (GridView) findViewById(R.id.gridview);
        gridView.setAdapter(new ImageAdapter(this, mUrlList));
        ListView listView = (ListView) findViewById(R.id.listview);

        listView.setAdapter(new ImageAdapter(this, mUrlList));
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    mIsGridViewIdle = true ;
                } else {
                    mIsGridViewIdle = false ;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            }
        });
    }

    private class ImageAdapter extends BaseAdapter {
        private Context mContext ;
        private List<String> mList ;

        public ImageAdapter(Context context, List<String> list) {
            mList = list ;
            mContext = context ;
        }

        @Override
        public int getCount() {
            return mList.size();
        }

        @Override
        public Object getItem(int position) {
            return mList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder = null ;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.gridview_item, parent, false) ;
                viewHolder = new ViewHolder();
                viewHolder.imageView = (ImageView) convertView.findViewById(R.id.square_image_view);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            ImageView imageView = viewHolder.imageView ;
            String tag = (String) imageView.getTag();
            String url = (String) getItem(position);
            if ( !url.equals(tag) ) {
                //default drawable
                imageView.setImageDrawable(null);
            }

            // Do not load image from network when scrolling
            //if (mIsGridViewIdle) {
                // set tag to image view
                imageView.setTag(url);
                // load bitmap
                mImageLoader.bindBitmap(url, imageView, 200, 200);
            //}

            return convertView;
        }

        private class ViewHolder {
                public ImageView imageView ;
        }
    }
}
