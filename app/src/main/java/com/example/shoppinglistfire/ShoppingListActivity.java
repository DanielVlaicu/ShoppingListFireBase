package com.example.shoppinglistfire;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.List;


public class ShoppingListActivity extends AppCompatActivity {

    private DatabaseReference database;
    private List<String> items;
    private ListView listView;
    private EditText itemInput;
    private Button addButton, generateQRButton, scanQRButton, logoutButton;
    private String listId;
    private ArrayAdapter<String> adapter; // Adapter pentru ListView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);

        database = FirebaseDatabase.getInstance().getReference("shoppingLists");
        items = new ArrayList<>();
        listView = findViewById(R.id.listView);
        itemInput = findViewById(R.id.itemInput);
        addButton = findViewById(R.id.addButton);
        generateQRButton = findViewById(R.id.generateQRButton);
        scanQRButton = findViewById(R.id.scanQRButton);
        logoutButton = findViewById(R.id.logoutButton);

        // Configurăm adapterul pentru ListView
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter); // Setăm adapterul

        // Obținem ID-ul listei din intent sau generăm unul nou
        listId = getIntent().getStringExtra("LIST_ID");
        if (listId == null) {
            listId = database.push().getKey();
            database.child(listId).setValue(new ArrayList<>()); // Inițializăm lista în Firebase
        }

        // Ascultăm modificările din Firebase și actualizăm lista locală
        database.child(listId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    // Adăugăm produsul în listă
                    String item = child.getValue(String.class);
                    if (item != null) {
                        items.add(item);
                    }
                }
                // Actualizăm UI-ul
                adapter.notifyDataSetChanged(); // Notify pentru a reîmprospăta ListView
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Poți adăuga un mesaj de eroare dacă vrei
                Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea datelor.", Toast.LENGTH_SHORT).show();
            }
        });

        // Adăugăm un nou item în listă
        addButton.setOnClickListener(v -> {
            String item = itemInput.getText().toString();
            if (!item.isEmpty()) {
                database.child(listId).push().setValue(item);
                itemInput.setText(""); // Golim câmpul după adăugare
            }
        });

        // Generare QR Code pentru partajare
        generateQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRGenerator.class);
            intent.putExtra("LIST_ID", listId);
            startActivity(intent);
        });

        // Scanare QR Code pentru a prelua o listă
        scanQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRScannerActivity.class);
            startActivity(intent);
        });

        // Logout
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}