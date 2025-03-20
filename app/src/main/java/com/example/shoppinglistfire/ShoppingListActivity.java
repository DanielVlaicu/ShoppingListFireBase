package com.example.shoppinglistfire;

import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;

import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;

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

        String userId = FirebaseAuth.getInstance().getUid();
        Log.d("DEBUG", "UserId: " + userId); // Verifică id-ul utilizatorului
        if (userId == null) {
            startActivity(new Intent(ShoppingListActivity.this, LoginActivity.class));
            finish();
            return;
        }
        database = FirebaseDatabase.getInstance().getReference("shoppingLists").child(userId);
        items = new ArrayList<>();
        listView = findViewById(R.id.listView);
        itemNameInput = findViewById(R.id.itemNameInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        addButton = findViewById(R.id.addButton);
        generateQRButton = findViewById(R.id.generateQRButton);
        scanQRButton = findViewById(R.id.scanQRButton);
        logoutButton = findViewById(R.id.logoutButton);



        // Obținem ID-ul listei din intent sau generăm unul nou
        listId = getIntent().getStringExtra("LIST_ID");
        Log.d("DEBUG", "listId citit din Intent: " + listId);
        if (listId == null) {
            listId = database.push().getKey();
            Log.e("DEBUG", "listId este null! Verificați dacă este transmis corect!");
            listId = database.push().getKey(); // Generează un ID pentru listă dacă nu este specificat
            Log.d("DEBUG", "listId generat automat: " + listId); // Verifică dacă este generat corect
        }

        Log.d("DEBUG", "ListId înainte de citire: " + listId);

        // Inițializăm adapterul după ce listId este setat corect
        adapter = new ShoppingListAdapter(this, items, database, listId);
        listView.setAdapter(adapter);
        Log.d("DEBUG", "ListId dupa initializarea adapterului: " + listId);

        // Ascultăm modificările din Firebase și actualizăm lista locală
        database.child(listId).addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("DEBUG", "ListId onDataChange1" + listId);
                Log.d("DEBUG", "onDataChange triggered");
               items.clear();
                Log.d("DEBUG", "ListId onDataChange2" + listId);
                if (snapshot.exists()) {
                    Log.d("DEBUG", "Snapshot children count: " + snapshot.getChildrenCount());
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Log.d("DEBUG", "Key: " + child.getKey() + ", Value: " + child.getValue());
                        ShoppingItem item = child.getValue(ShoppingItem.class);
                        if (item != null) {
                            Log.d("DEBUG", "ListId onDataChange3" + listId);
                            Log.d("DEBUG", "Item citit: " + item.toString());
                            items.add(item);
                        }
                    }
                    Log.d("DEBUG", "Items încărcate: " + items.size());
                    adapter.notifyDataSetChanged(); // Actualizăm UI-ul
                } else {
                    Log.d("DEBUG", "Nu au fost găsite produse.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea datelor.", Toast.LENGTH_SHORT).show();
                Log.e("DEBUG", "Eroare la citirea din Firebase: " + error.getMessage());
            }
        });

        // Adăugăm un nou item în listă
        addButton.setOnClickListener(v -> {
            String name = itemNameInput.getText().toString().trim();
            String description = itemDescriptionInput.getText().toString().trim();

            if (!name.isEmpty()) {
                String itemId = database.push().getKey();
                ShoppingItem newItem = new ShoppingItem(itemId, name, description);

                Log.d("DEBUG", "Salvăm item: listId=" + listId + ", itemId=" + itemId); // Debugging

                database.child(listId).child(itemId).setValue(newItem)

                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d("DEBUG", "Item adăugat cu succes!");
                            } else {
                                Log.e("DEBUG", "Eroare la salvare", task.getException());
                            }
                        });

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