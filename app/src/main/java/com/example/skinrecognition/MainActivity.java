package com.example.skinrecognition;

//added by hay begin
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

//added by hay end

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


public class MainActivity extends AppCompatActivity {

    //added by hay begin
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_TAKE_PHOTO = 102;
    private static final int REQUEST_SELECT_IMAGE = 103;

    private ImageView ivPhoto;
    private Button btnTakePhoto, btnSelectImage;
    private Uri imageURI;
    private String currentPhotoPath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivPhoto = findViewById(R.id.ivPhoto);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);

        btnSelectImage = findViewById(R.id.btnSelectImage);

        // ====== 拍照按钮点击事件 ======
        btnTakePhoto.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
            } else {
                takePhoto();
            }
        });

        // ====== 选取图片按钮点击事件 ======
        btnSelectImage.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            } else {
                selectImageFromGallery();
            }
        });
    }


    private void takePhoto() {
        // 自定义文件夹路径
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "SkinRecognition");

        // 若不存在则创建
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Toast.makeText(this, "无法创建保存目录", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 文件名：IMG_20251024_123456.jpg
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File photoFile = new File(storageDir, "IMG_" + timeStamp + ".jpg");

        try {
            if (photoFile.exists()) {
                photoFile.delete();
            }
            photoFile.createNewFile();
            currentPhotoPath = photoFile.getAbsolutePath();

            imageURI = FileProvider.getUriForFile(
                    this,
                    "com.example.skinrecognition.fileprovider",
                    photoFile
            );

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageURI);
            startActivityForResult(intent, REQUEST_TAKE_PHOTO);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "创建图片文件失败", Toast.LENGTH_SHORT).show();
        }
    }


    private void selectImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectImageFromGallery();
            } else {
                Toast.makeText(this, "需要存储权限才能访问图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 拍照返回
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                ivPhoto.setImageBitmap(bitmap);
                Toast.makeText(this, "照片已保存到：" + currentPhotoPath, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "加载拍照图片失败", Toast.LENGTH_SHORT).show();
            }
        }

        // 选取图片返回
        if (requestCode == REQUEST_SELECT_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                ivPhoto.setImageURI(selectedImageUri);
                Toast.makeText(this, "图片选取成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show();
            }
        }
    }
    //added by hay end
}
