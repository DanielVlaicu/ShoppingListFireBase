package com.example.shoppinglistfire;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;


public class QRScannerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        new IntentIntegrator(this)
                .setPrompt("Scanează un cod QR")
                .setBeepEnabled(true)
                .setOrientationLocked(false)
                .initiateScan();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        IntentResult r = IntentIntegrator.parseActivityResult(req, res, data);
        if (r != null && r.getContents() != null) {
            Uri uri = Uri.parse(r.getContents());
            String owner = uri.getQueryParameter("OWNER_ID");
            String list = uri.getQueryParameter("LIST_ID");
            if (owner != null && list != null) {
                Intent i = new Intent(this, ShoppingListActivity.class);
                i.putExtra("OWNER_ID", owner);
                i.putExtra("LIST_ID", list);
                startActivity(i);
            } else {
                Toast.makeText(this, "QR invalid", Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }
}