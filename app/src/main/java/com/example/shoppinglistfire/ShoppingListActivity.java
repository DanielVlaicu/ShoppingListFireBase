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
    private List<ShoppingItem> items;
    private ListView listView;
    private EditText itemNameInput, itemDescriptionInput;
    private Button addButton, generateQRButton, scanQRButton, logoutButton;
    private String listId;
    private ShoppingListAdapter adapter; // Adapter personalizat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);

        database = FirebaseDatabase.getInstance().getReference("shoppingLists");
        items = new ArrayList<>();
        listView = findViewById(R.id.listView);
        itemNameInput = findViewById(R.id.itemNameInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        addButton = findViewById(R.id.addButton);
        generateQRButton = findViewById(R.id.generateQRButton);
        scanQRButton = findViewById(R.id.scanQRButton);
        logoutButton = findViewById(R.id.logoutButton);

        // Inițializăm adapterul și îl setăm la ListView
        adapter = new ShoppingListAdapter(this, items, database, listId);
        listView.setAdapter(adapter);

        // Obținem ID-ul listei din intent sau generăm unul nou
        listId = getIntent().getStringExtra("LIST_ID");
        if (listId == null) {
            listId = database.push().getKey();
        }

        // Ascultăm modificările din Firebase și actualizăm lista locală
        database.child(listId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ShoppingItem item = child.getValue(ShoppingItem.class);
                    if (item != null) {
                        items.add(item);
                    }
                }
                adapter.notifyDataSetChanged(); // Actualizăm UI-ul
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea datelor.", Toast.LENGTH_SHORT).show();
            }
        });

        // Adăugăm un nou item în listă
        addButton.setOnClickListener(v -> {
            String name = itemNameInput.getText().toString().trim();
            String description = itemDescriptionInput.getText().toString().trim();

            if (!name.isEmpty()) {
                String itemId = database.child(listId).push().getKey();
                ShoppingItem newItem = new ShoppingItem(itemId, name, description);
                database.child(listId).child(itemId).setValue(newItem);

                itemNameInput.setText("");
                itemDescriptionInput.setText("");
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