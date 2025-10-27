package com.example.skinrecognition;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class PredictActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_TAKE_PHOTO = 102;
    private static final int REQUEST_SELECT_IMAGE = 103;

    private ImageView ivPhoto;
    private Button btnTakePhoto, btnSelectImage;
    private TextView tvResult;
    private Uri imageURI;
    private String currentPhotoPath;

    // 模型相关变量
    private List<TreeNode> treeNodes = new ArrayList<>();
    private float[] scalerMean;
    private float[] scalerStd;
    private final String[] skinTypes = {"油性", "干性", "中性"};
    private Button btnShare;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict);

        // 初始化控件
        ivPhoto = findViewById(R.id.ivPhoto);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        tvResult = findViewById(R.id.tvResult);
        btnShare = findViewById(R.id.btnShare);

        // 加载模型参数
        loadModelParams();

        // 拍照按钮事件
        btnTakePhoto.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
            } else {
                takePhoto();
            }
        });

        // 选择图片按钮事件 - 使用新方法避免权限问题
        btnSelectImage.setOnClickListener(v -> {
            selectImageFromGallery();
        });

        // 点击分享按钮事件，跳转到分享页面
        btnShare.setOnClickListener(v -> {
            Intent intent = new Intent(PredictActivity.this, ShareResultActivity.class);
            // 传递识别结果文字
            intent.putExtra("result_text", tvResult.getText().toString());

            // 传递当前显示的图片路径
            if (currentPhotoPath != null) {
                intent.putExtra("result_image_path", currentPhotoPath);
            } else if (imageURI != null) {
                intent.putExtra("result_image_uri", imageURI.toString());
            }

            startActivity(intent);
        });
    }

    // 加载模型参数
    private void loadModelParams() {
        try {
            // 1. 加载归一化参数
            scalerMean = loadFloatArrayFromAssets("scaler_mean.txt");
            scalerStd = loadFloatArrayFromAssets("scaler_std.txt");

            // 2. 加载决策树模型
            InputStream is = getAssets().open("tree_model.txt");
            Scanner scanner = new Scanner(is);
            int lineNum = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (lineNum == 0) {
                    lineNum++;
                    continue;
                }
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length != 6) {
                    continue;
                }
                TreeNode node = new TreeNode(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Float.parseFloat(parts[2]),
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5])
                );
                treeNodes.add(node);
                lineNum++;
            }
            scanner.close();
            is.close();

            if (treeNodes.isEmpty()) {
                Toast.makeText(this, "模型节点数据为空", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "模型加载成功，节点数: " + treeNodes.size(), Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "模型文件读取失败", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            e.printStackTrace();
            Toast.makeText(this, "模型数据格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    // 从assets读取浮点数组
    private float[] loadFloatArrayFromAssets(String filename) throws IOException {
        InputStream is = getAssets().open(filename);
        Scanner scanner = new Scanner(is);
        List<Float> list = new ArrayList<>();
        while (scanner.hasNextFloat()) {
            list.add(scanner.nextFloat());
        }
        scanner.close();
        is.close();
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    // 提取图像特征
    private float[] extractFeatures(Bitmap bitmap) {
        // 为了性能，可以缩放图片
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true);

        int width = resizedBitmap.getWidth();
        int height = resizedBitmap.getHeight();

        // 1. 计算HSV特征
        float hSum = 0, sSum = 0, vSum = 0;
        float hSqSum = 0, sSqSum = 0, vSqSum = 0;
        int pixelCount = 0;

        // 2. 计算LBP特征
        int lbpSum = 0;
        int lbpCount = 0;

        for (int i = 1; i < height - 1; i++) {
            for (int j = 1; j < width - 1; j++) {
                // 获取RGB值
                int pixel = resizedBitmap.getPixel(j, i);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // 转换为HSV
                float[] hsv = new float[3];
                Color.RGBToHSV(r, g, b, hsv);
                float h = hsv[0];
                float s = hsv[1];
                float v = hsv[2];

                hSum += h;
                sSum += s;
                vSum += v;
                hSqSum += h * h;
                sSqSum += s * s;
                vSqSum += v * v;
                pixelCount++;

                // 计算LBP值
                int center = (r + g + b) / 3;
                int code = 0;

                int p1 = (Color.red(resizedBitmap.getPixel(j-1, i-1)) + Color.green(resizedBitmap.getPixel(j-1, i-1)) + Color.blue(resizedBitmap.getPixel(j-1, i-1))) / 3;
                code |= (p1 >= center) ? 1 << 7 : 0;

                int p2 = (Color.red(resizedBitmap.getPixel(j, i-1)) + Color.green(resizedBitmap.getPixel(j, i-1)) + Color.blue(resizedBitmap.getPixel(j, i-1))) / 3;
                code |= (p2 >= center) ? 1 << 6 : 0;

                int p3 = (Color.red(resizedBitmap.getPixel(j+1, i-1)) + Color.green(resizedBitmap.getPixel(j+1, i-1)) + Color.blue(resizedBitmap.getPixel(j+1, i-1))) / 3;
                code |= (p3 >= center) ? 1 << 5 : 0;

                int p4 = (Color.red(resizedBitmap.getPixel(j+1, i)) + Color.green(resizedBitmap.getPixel(j+1, i)) + Color.blue(resizedBitmap.getPixel(j+1, i))) / 3;
                code |= (p4 >= center) ? 1 << 4 : 0;

                int p5 = (Color.red(resizedBitmap.getPixel(j+1, i+1)) + Color.green(resizedBitmap.getPixel(j+1, i+1)) + Color.blue(resizedBitmap.getPixel(j+1, i+1))) / 3;
                code |= (p5 >= center) ? 1 << 3 : 0;

                int p6 = (Color.red(resizedBitmap.getPixel(j, i+1)) + Color.green(resizedBitmap.getPixel(j, i+1)) + Color.blue(resizedBitmap.getPixel(j, i+1))) / 3;
                code |= (p6 >= center) ? 1 << 2 : 0;

                int p7 = (Color.red(resizedBitmap.getPixel(j-1, i+1)) + Color.green(resizedBitmap.getPixel(j-1, i+1)) + Color.blue(resizedBitmap.getPixel(j-1, i+1))) / 3;
                code |= (p7 >= center) ? 1 << 1 : 0;

                int p8 = (Color.red(resizedBitmap.getPixel(j-1, i)) + Color.green(resizedBitmap.getPixel(j-1, i)) + Color.blue(resizedBitmap.getPixel(j-1, i))) / 3;
                code |= (p8 >= center) ? 1 << 0 : 0;

                lbpSum += code;
                lbpCount++;
            }
        }

        // 计算HSV均值和标准差
        float hMean = hSum / pixelCount;
        float sMean = sSum / pixelCount;
        float vMean = vSum / pixelCount;
        float hStd = (float) Math.sqrt((hSqSum / pixelCount) - hMean * hMean);
        float sStd = (float) Math.sqrt((sSqSum / pixelCount) - sMean * sMean);
        float vStd = (float) Math.sqrt((vSqSum / pixelCount) - vMean * vMean);

        // 计算LBP均值
        float lbpMean = (float) lbpSum / lbpCount;

        return new float[]{hMean, hStd, sMean, sStd, vMean, vStd, lbpMean};
    }

    // 特征归一化
    private float[] normalizeFeatures(float[] features) {
        float[] normalized = new float[features.length];
        for (int i = 0; i < features.length; i++) {
            normalized[i] = (features[i] - scalerMean[i]) / scalerStd[i];
        }
        return normalized;
    }

    // 预测皮肤类型
    private String predict(float[] features) {
        if (treeNodes.isEmpty() || scalerMean == null || scalerStd == null) {
            return "模型未加载";
        }

        try {
            float[] normalized = normalizeFeatures(features);
            int currentNodeIdx = 0;

            while (true) {
                TreeNode node = treeNodes.get(currentNodeIdx);
                if (node.type == 0) {
                    return skinTypes[node.label];
                } else {
                    if (normalized[node.featureIndex] <= node.threshold) {
                        currentNodeIdx = node.leftChild;
                    } else {
                        currentNodeIdx = node.rightChild;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "预测失败";
        }
    }

    // 处理图片并显示结果
    private void processAndDisplayImage(Bitmap bitmap) {
        if (bitmap == null) {
            tvResult.setText("图片加载失败");
            return;
        }

        try {
            // 显示处理中状态
            tvResult.setText("分析中...");

            // 在后台线程处理以避免界面卡顿
            new Thread(() -> {
                try {
                    // 提取特征
                    float[] features = extractFeatures(bitmap);

                    // 在主线程更新UI
                    runOnUiThread(() -> {
                        String result = predict(features);
                        tvResult.setText("识别结果：" + result);

                        // 显示特征值（可选，用于调试）
                        String featureText = String.format(
                                "\nHSV均值: H=%.1f S=%.2f V=%.1f",
                                features[0], features[2], features[4]
                        );
                        tvResult.append(featureText);
//                        显示分享按钮
                        btnShare.setVisibility(View.VISIBLE);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> tvResult.setText("特征提取失败"));
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            tvResult.setText("处理失败");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_TAKE_PHOTO) {
            // 处理拍照图片
            handleCapturedPhoto();
        }

        if (requestCode == REQUEST_SELECT_IMAGE && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                // Android 10+ 必须持久化读取权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                        takeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    }
                    try {
                        getContentResolver().takePersistableUriPermission(selectedImageUri, takeFlags);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }


                try {
                    // 使用更安全的方式加载图片
                    InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();

                    ivPhoto.setImageURI(selectedImageUri);
                    imageURI = selectedImageUri;
                    currentPhotoPath = null;

                    // 调用分析预测
                    processAndDisplayImage(bitmap);

                } catch (IOException e) {
                    e.printStackTrace();
                    tvResult.setText("加载相册图片失败");
                }
            }
        }
    }

    // 单独封装拍照结果处理（更清晰）
    private void handleCapturedPhoto() {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
            if (bitmap != null) {
                ivPhoto.setImageBitmap(bitmap);
                processAndDisplayImage(bitmap);

                // 通知系统扫描图片，让它显示在相册中
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(new File(currentPhotoPath));
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);
            } else {
                tvResult.setText("拍照图片加载失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            tvResult.setText("处理拍照图片失败");
        }
    }

    // 拍照功能
    private void takePhoto() {
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "SkinRecognition");

        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Toast.makeText(this, "无法创建保存目录", Toast.LENGTH_SHORT).show();
                return;
            }
        }

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

    // 选择图片功能 - 使用新方法避免权限问题
    private void selectImageFromGallery() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }

        startActivityForResult(Intent.createChooser(intent, "选择皮肤照片"), REQUEST_SELECT_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 决策树节点类
    private static class TreeNode {
        int type;
        int featureIndex;
        float threshold;
        int leftChild;
        int rightChild;
        int label;

        TreeNode(int type, int featureIndex, float threshold, int leftChild, int rightChild, int label) {
            this.type = type;
            this.featureIndex = featureIndex;
            this.threshold = threshold;
            this.leftChild = leftChild;
            this.rightChild = rightChild;
            this.label = label;
        }
    }
}