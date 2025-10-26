package com.example.skinrecognition;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        /* 微信登录按钮点击事件 */
        Button wechatLogin = findViewById(R.id.wechatLogin);
        wechatLogin.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ShareResultActivity.class);
            startActivity(intent);
            finish();   // 可选：关闭登录页，防止返回
        });
    }
}