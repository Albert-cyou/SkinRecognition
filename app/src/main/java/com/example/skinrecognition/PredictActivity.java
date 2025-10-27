package com.example.skinrecognition;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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

    // 原有变量
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_TAKE_PHOTO = 102;
    private static final int REQUEST_SELECT_IMAGE = 103;
    private ImageView ivPhoto;
    private Button btnTakePhoto, btnSelectImage;
    private TextView tvResult; // 新增：显示识别结果
    private Uri imageURI;
    private String currentPhotoPath;

    // 新增：模型和特征参数
    private List<TreeNode> treeNodes = new ArrayList<>();
    private float[] scalerMean;
    private float[] scalerStd;
    private final String[] skinTypes = {"油性", "干性", "中性"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件（新增结果显示）
        ivPhoto = findViewById(R.id.ivPhoto);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        tvResult = findViewById(R.id.tvResult); // 需要在布局中添加此控件

        // 加载模型和特征参数
        loadModelParams();

        // 按钮事件（保持原有逻辑）
        btnTakePhoto.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                takePhoto();
            }
        });

        btnSelectImage.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            } else {
                selectImageFromGallery();
            }
        });
    }

    // 加载模型参数（从assets读取）
    private void loadModelParams() {
        try {
            // 1. 加载归一化参数（这部分不变）
            scalerMean = loadFloatArrayFromAssets("scaler_mean.txt");
            scalerStd = loadFloatArrayFromAssets("scaler_std.txt");

            // 2. 加载决策树模型（关键修改：跳过第一行头信息）
            InputStream is = getAssets().open("tree_model.txt");
            Scanner scanner = new Scanner(is);
            int lineNum = 0; // 记录当前读取的行数
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim(); // 去除换行和空格
                // 跳过第一行（头信息：node_count:xxx）
                if (lineNum == 0) {
                    lineNum++;
                    continue; // 不处理头信息，直接进入下一行
                }
                // 跳过空行（防止文件末尾有空行）
                if (line.isEmpty()) {
                    continue;
                }
                // 解析节点数据（原逻辑不变）
                String[] parts = line.split(",");
                // 安全校验：确保分割后有6个字段（防止格式错误）
                if (parts.length != 6) {
                    continue;
                }
                TreeNode node = new TreeNode(
                        Integer.parseInt(parts[0]), // 节点类型（0=叶节点，1=内部节点）
                        Integer.parseInt(parts[1]), // 特征索引
                        Float.parseFloat(parts[2]), // 阈值
                        Integer.parseInt(parts[3]), // 左子节点
                        Integer.parseInt(parts[4]), // 右子节点
                        Integer.parseInt(parts[5])  // 叶节点类别
                );
                treeNodes.add(node);
                lineNum++;
            }
            scanner.close();
            is.close();

            // 校验：确保加载到节点数据
            if (treeNodes.isEmpty()) {
                Toast.makeText(this, "模型节点数据为空", Toast.LENGTH_SHORT).show();
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

    // 核心：提取图像特征（纯Java实现，不依赖OpenCV）
    private float[] extractFeatures(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 1. 计算HSV特征（均值和方差）
        float hSum = 0, sSum = 0, vSum = 0;
        float hSqSum = 0, sSqSum = 0, vSqSum = 0;
        int pixelCount = 0;

        // 2. 计算LBP特征（均值）
        int lbpSum = 0;
        int lbpCount = 0;

        for (int i = 1; i < height - 1; i++) {
            for (int j = 1; j < width - 1; j++) {
                // 获取RGB值
                int pixel = bitmap.getPixel(j, i);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // 转换为HSV
                float[] hsv = new float[3];
                Color.RGBToHSV(r, g, b, hsv);
                float h = hsv[0];
                float s = hsv[1];
                float v = hsv[2];

                // 累加HSV值（用于计算均值和方差）
                hSum += h;
                sSum += s;
                vSum += v;
                hSqSum += h * h;
                sSqSum += s * s;
                vSqSum += v * v;
                pixelCount++;

                // 计算LBP值（8邻域）
                int center = (r + g + b) / 3; // 灰度值
                int code = 0;
                // 上左
                int p1 = (Color.red(bitmap.getPixel(j-1, i-1)) + Color.green(bitmap.getPixel(j-1, i-1)) + Color.blue(bitmap.getPixel(j-1, i-1))) / 3;
                code |= (p1 >= center) ? 1 << 7 : 0;
                // 上中
                int p2 = (Color.red(bitmap.getPixel(j, i-1)) + Color.green(bitmap.getPixel(j, i-1)) + Color.blue(bitmap.getPixel(j, i-1))) / 3;
                code |= (p2 >= center) ? 1 << 6 : 0;
                // 上右
                int p3 = (Color.red(bitmap.getPixel(j+1, i-1)) + Color.green(bitmap.getPixel(j+1, i-1)) + Color.blue(bitmap.getPixel(j+1, i-1))) / 3;
                code |= (p3 >= center) ? 1 << 5 : 0;
                // 右中
                int p4 = (Color.red(bitmap.getPixel(j+1, i)) + Color.green(bitmap.getPixel(j+1, i)) + Color.blue(bitmap.getPixel(j+1, i))) / 3;
                code |= (p4 >= center) ? 1 << 4 : 0;
                // 下右
                int p5 = (Color.red(bitmap.getPixel(j+1, i+1)) + Color.green(bitmap.getPixel(j+1, i+1)) + Color.blue(bitmap.getPixel(j+1, i+1))) / 3;
                code |= (p5 >= center) ? 1 << 3 : 0;
                // 下中
                int p6 = (Color.red(bitmap.getPixel(j, i+1)) + Color.green(bitmap.getPixel(j, i+1)) + Color.blue(bitmap.getPixel(j, i+1))) / 3;
                code |= (p6 >= center) ? 1 << 2 : 0;
                // 下左
                int p7 = (Color.red(bitmap.getPixel(j-1, i+1)) + Color.green(bitmap.getPixel(j-1, i+1)) + Color.blue(bitmap.getPixel(j-1, i+1))) / 3;
                code |= (p7 >= center) ? 1 << 1 : 0;
                // 左中
                int p8 = (Color.red(bitmap.getPixel(j-1, i)) + Color.green(bitmap.getPixel(j-1, i)) + Color.blue(bitmap.getPixel(j-1, i))) / 3;
                code |= (p8 >= center) ? 1 << 0 : 0;

                lbpSum += code;
                lbpCount++;
            }
        }

        // 计算HSV均值和方差
        float hMean = hSum / pixelCount;
        float sMean = sSum / pixelCount;
        float vMean = vSum / pixelCount;
        float hStd = (float) Math.sqrt((hSqSum / pixelCount) - hMean * hMean);
        float sStd = (float) Math.sqrt((sSqSum / pixelCount) - sMean * sMean);
        float vStd = (float) Math.sqrt((vSqSum / pixelCount) - vMean * vMean);

        // 计算LBP均值
        float lbpMean = (float) lbpSum / lbpCount;

        // 特征数组（7个特征）
        return new float[]{hMean, hStd, sMean, sStd, vMean, vStd, lbpMean};
    }

    // 特征归一化（使用Python保存的均值和标准差）
    private float[] normalizeFeatures(float[] features) {
        float[] normalized = new float[features.length];
        for (int i = 0; i < features.length; i++) {
            normalized[i] = (features[i] - scalerMean[i]) / scalerStd[i];
        }
        return normalized;
    }

    // 预测皮肤类型（手动遍历决策树）
    private String predict(float[] features) {
        if (treeNodes.isEmpty() || scalerMean == null || scalerStd == null) {
            return "模型未加载";
        }
        // 特征归一化
        float[] normalized = normalizeFeatures(features);
        // 从根节点开始遍历决策树
        int currentNodeIdx = 0;
        while (true) {
            TreeNode node = treeNodes.get(currentNodeIdx);
            if (node.type == 0) { // 叶节点
                return skinTypes[node.label];
            } else { // 内部节点：根据特征值判断左/右子树
                if (normalized[node.featureIndex] <= node.threshold) {
                    currentNodeIdx = node.leftChild;
                } else {
                    currentNodeIdx = node.rightChild;
                }
            }
        }
    }

    // 重写onActivityResult，添加预测逻辑
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                ivPhoto.setImageBitmap(bitmap);
                // 提取特征并预测
                float[] features = extractFeatures(bitmap);
                String result = predict(features);
                tvResult.setText("识别结果：" + result);
            } catch (Exception e) {
                e.printStackTrace();
                tvResult.setText("处理失败");
            }
        }

        if (requestCode == REQUEST_SELECT_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                    ivPhoto.setImageURI(selectedImageUri);
                    // 提取特征并预测
                    float[] features = extractFeatures(bitmap);
                    String result = predict(features);
                    tvResult.setText("识别结果：" + result);
                } catch (Exception e) {
                    e.printStackTrace();
                    tvResult.setText("处理失败");
                }
            }
        }
    }

    // 决策树节点类
    private static class TreeNode {
        int type; // 0=叶节点, 1=内部节点
        int featureIndex; // 特征索引（内部节点用）
        float threshold; // 阈值（内部节点用）
        int leftChild; // 左子节点索引
        int rightChild; // 右子节点索引
        int label; // 类别（叶节点用）

        TreeNode(int type, int featureIndex, float threshold, int leftChild, int rightChild, int label) {
            this.type = type;
            this.featureIndex = featureIndex;
            this.threshold = threshold;
            this.leftChild = leftChild;
            this.rightChild = rightChild;
            this.label = label;
        }
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
    //added by hay end
}
