package com.example.skinrecognition;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class ShareResultActivity extends AppCompatActivity {

    private static final int REQ_READ_STORAGE = 100;
    private ImageView ivResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.share);   // 你的布局文件名

        ivResult = findViewById(R.id.iv_result);
        loadFirstPhotoFromGallery();
    }

    /* 读取系统相册第一张图 */
    private void loadFirstPhotoFromGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ_STORAGE);
            return;
        }
        queryFirstPhoto();
    }

    private void queryFirstPhoto() {
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, proj, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1");
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            String path = cursor.getString(idx);
            cursor.close();
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp != null) ivResult.setImageBitmap(bmp);
            else showEmpty();
        } else {
            showEmpty();
        }
    }

    private void showEmpty() {
        ivResult.setImageResource(android.R.color.darker_gray); // 无图时灰色
        Toast.makeText(this, "相册为空", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            queryFirstPhoto();
        } else {
            showEmpty();
        }
    }
}