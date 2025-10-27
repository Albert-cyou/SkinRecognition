package com.example.skinrecognition;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.*;
import android.content.pm.PackageManager;

public class ShareResultActivity extends AppCompatActivity {

    private static final int REQ_READ_STORAGE = 100;
    private ImageView ivResult;
    private Bitmap  currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);   // 你的布局文件名

        ivResult = findViewById(R.id.iv_result);
        Button btnSave  = findViewById(R.id.btn_save);
        Button btnShare = findViewById(R.id.btn_share);

        /* 加载相册第一张图 */
        loadFirstPhotoFromGallery();

        /* 保存 & 分享 */
        btnSave.setOnClickListener(v  -> saveImageToGallery());
        btnShare.setOnClickListener(v -> shareImage());
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
            int idx  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            String path = cursor.getString(idx);
            cursor.close();
            currentBitmap = BitmapFactory.decodeFile(path);
            if (currentBitmap != null) ivResult.setImageBitmap(currentBitmap);
            else showEmpty();
        } else {
            showEmpty();
        }
    }

    private void showEmpty() {
        currentBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        ivResult.setImageBitmap(currentBitmap);
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
            Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show();
            showEmpty();
        }
    }

    /* ---------- 保存 ---------- */
    private void saveImageToGallery() {
        if (currentBitmap == null) return;
        String fileName = "SkinResult_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) return;
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e("ShareResult", "save error", e);
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    /* ---------- 分享 ---------- */
    private void shareImage() {
        if (currentBitmap == null) return;
        File cacheFile = new File(getCacheDir(), "share_temp.jpg");
        try (FileOutputStream out = new FileOutputStream(cacheFile)) {
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (IOException e) {
            Log.e("ShareResult", "share error", e);
            return;
        }
        Uri contentUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", cacheFile);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/jpeg");
        share.putExtra(Intent.EXTRA_STREAM, contentUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "分享结果"));
    }
}