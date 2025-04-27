package com.example.ai_chatbot;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    EditText message_text_text;
    ImageView send_btn;
    Button logout_btn; // <-- Thêm dòng này
    List<Message> messageList = new ArrayList<>();
    MessageAdapter messageAdapter;

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //====================================
        message_text_text = findViewById(R.id.message_text_text);
        send_btn = findViewById(R.id.send_btn);
        recyclerView = findViewById(R.id.recyclerView);
        logout_btn = findViewById(R.id.logout_btn); // <-- Gán nút logout

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        //====================================

        // Xử lý khi ấn logout
        logout_btn.setOnClickListener(view -> {
            logout();
        });

        if(!isConnected(MainActivity.this)) {
            buildDialog(MainActivity.this).show();
        }

        message_text_text.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if(s.toString().trim().length()==0){
                    send_btn.setEnabled(false);
                } else {
                    send_btn.setEnabled(true);
                    send_btn.setOnClickListener(view -> {
                        String question = message_text_text.getText().toString().trim();
                        addToChat(question,Message.SEND_BY_ME);
                        message_text_text.setText("");
                        callAPI(question);
                    });
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable s) { }
        });

    } // OnCreate End

    //=================== Các hàm cũ giữ nguyên =======================

    void addToChat (String message, String sendBy){
        runOnUiThread(() -> {
            messageList.add(new Message(message, sendBy));
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
        });
    }

    void addResponse(String response){
        messageList.remove(messageList.size()-1);
        addToChat(response, Message.SEND_BY_BOT);
    }

    void callAPI(String question){
        messageList.add(new Message("Typing...", Message.SEND_BY_BOT));

        JSONObject jsonBody = new JSONObject();
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", "You are a helpful AI assistant."));
            messages.put(new JSONObject().put("role", "user").put("content", question));

            jsonBody.put("model","deepseek/deepseek-r1:free");
            jsonBody.put("messages", messages);
            jsonBody.put("max_tokens",300);
            jsonBody.put("temperature",0);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        RequestBody requestBody = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(API.API_URL)
                .header("Authorization", "Bearer " + API.API)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                addResponse("Failed to load response due to " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray("choices");
                        String result;

                        JSONObject messageObject = jsonArray.getJSONObject(0).optJSONObject("message");
                        if (messageObject != null) {
                            result = messageObject.getString("content");
                        } else {
                            result = jsonArray.getJSONObject(0).getString("text"); // fallback
                        }

                        addResponse(result.trim());
                    } catch (JSONException e) {
                        addResponse("Response parsing error: " + e.getMessage());
                    }
                } else {
                    addResponse("Error: " + response.code());
                }

            }
        });
    }

    public boolean isConnected(Context context){
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
        NetworkInfo info= manager.getActiveNetworkInfo();
        if(info!= null && info.isConnectedOrConnecting()){
            NetworkInfo wifi= manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            NetworkInfo mobile= manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            return (mobile != null && mobile.isConnectedOrConnecting()) || (wifi != null && wifi.isConnectedOrConnecting());
        } else
            return false;
    }

    public AlertDialog.Builder buildDialog(Context context){
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("No Internet Connection");
        builder.setMessage("Please check your internet connection.");
        builder.setPositiveButton("OK", (dialog, which) -> finishAffinity());
        return builder;
    }

    //=============== Hàm logout =================
    private void logout() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear(); // Xoá toàn bộ thông tin đăng nhập
        editor.apply();

        Intent intent = new Intent(MainActivity.this, SignInActivity.class);
        startActivity(intent);
        finish(); // đóng MainActivity luôn
    }
    //============================================

} // Public Class End
