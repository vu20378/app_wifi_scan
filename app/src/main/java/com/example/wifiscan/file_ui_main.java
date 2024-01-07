package com.example.wifiscan;

import android.view.View;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class file_ui_main extends AppCompatActivity {

    private Button button_tao_file;
    private EditText editText_file;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_ui);

        button_tao_file = (Button) findViewById(R.id.button_tao_file);
        editText_file = (EditText) findViewById(R.id.ten_file_tao);

        button_tao_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Tên File quy định là ngày/tháng/năm của ngày lấy dữ liệu
                String data = editText_file.getText().toString();

                if (!data.isEmpty()) {
                    Intent intent = new Intent(file_ui_main.this, MainActivity.class);
                    intent.putExtra("data_key", data);
                    startActivity(intent);
                } else {
                    // Hiển thị thông báo lỗi nếu tên file chưa được nhập
                    Toast.makeText(file_ui_main.this, "Vui lòng nhập tên file", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}