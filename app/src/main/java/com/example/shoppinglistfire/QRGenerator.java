package com.example.shoppinglistfire;


import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class QRGenerator extends AppCompatActivity {
    private static final String DYNAMIC_LINK_DOMAIN = "https://listadecumparaturi.page.link";
    private ImageView qrCodeImage;
    private String listId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_generator);

        //hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        qrCodeImage = findViewById(R.id.qrCodeImage);
        listId = getIntent().getStringExtra("LIST_ID");
        String ownerId = FirebaseAuth.getInstance().getUid();

        if (ownerId != null && listId != null) {
            String deepLink = DYNAMIC_LINK_DOMAIN
                    + "?OWNER_ID=" + ownerId
                    + "&LIST_ID=" + listId;
            try {
                Bitmap bmp = new BarcodeEncoder()
                        .encodeBitmap(deepLink,
                                BarcodeFormat.QR_CODE, 400, 400);
                qrCodeImage.setImageBitmap(bmp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}