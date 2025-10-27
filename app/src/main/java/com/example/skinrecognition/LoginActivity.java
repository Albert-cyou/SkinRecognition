 package com.example.skinrecognition;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoginActivity extends AppCompatActivity {

    Button wx_login, qq_login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        wx_login = findViewById(R.id.wechatLogin);
        qq_login = findViewById(R.id.qqLogin);

        wx_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(LoginActivity.this, "微信登录成功", Toast.LENGTH_SHORT).show();
                Log.e("LOGIN","use wx login successfully");
                goToMain("微信用户");
            }
        });

        qq_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(LoginActivity.this, "QQ登录成功", Toast.LENGTH_SHORT).show();
                Log.e("LOGIN","use qq login successfully");
                goToMain("QQ用户");
            }
        });
    }

    // 跳转到主界面
    private void goToMain(String username) {
        Intent intent = new Intent(LoginActivity.this, PredictActivity.class);
        intent.putExtra("username", username); // 传递用户名
        startActivity(intent);
    }
}