package com.example.shoppinglistfire;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.Intent;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;





public class QRScannerActivity extends AppCompatActivity {


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startQRScanner();
    }

    private void startQRScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scanează un cod QR");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            String scannedListId = result.getContents();
            Toast.makeText(this, "Listă accesată: " + scannedListId, Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(QRScannerActivity.this, ShoppingListActivity.class);
            intent.putExtra("LIST_ID", scannedListId);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Scanare anulată", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}