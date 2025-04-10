package com.example.shoppinglistfire;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.ShortDynamicLink;

import java.util.ArrayList;
import java.util.List;

public class ShoppingListActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference database;
    private List<ShoppingItem> items;
    private ListView listView;
    private EditText itemNameInput, itemDescriptionInput;
    private Button addButton, generateQRButton, scanQRButton, logoutButton, shareButton;
    private String listId;
    private ShoppingListAdapter adapter;
    private static final String DYNAMIC_LINK_DOMAIN = "https://listadecumparaturi.page.link";
    private LinearLayout menuListContainer;
    private SharedPreferences sharedPreferences ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);

        mAuth = FirebaseAuth.getInstance();

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);
        Button menuButton = findViewById(R.id.menuButton);

        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));



        String userId = mAuth.getUid();
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
        shareButton = findViewById(R.id.shareButton);

        View headerView = navigationView.getHeaderView(0);
        ImageView profileImage = headerView.findViewById(R.id.profileImage);
        Button changeProfileButton = headerView.findViewById(R.id.changeProfileButton);
        menuListContainer = headerView.findViewById(R.id.menuListContainer);

        SharedPreferences sharedPreferences = getSharedPreferences("ShoppingAppPrefs", MODE_PRIVATE);
        boolean inputsVisible = sharedPreferences.getBoolean("inputsVisible", false);
        listId = sharedPreferences.getString("LIST_ID", null);

        navigationView.setNavigationItemSelectedListener(menuItem -> {


            int id = menuItem.getItemId();

            if (id == R.id.my_lists) {
                Toast.makeText(this, "Listele mele", Toast.LENGTH_SHORT).show();
                loadUserShoppingLists();
            } else if (id == R.id.create_list) {
                Toast.makeText(this, "Creează o listă nouă", Toast.LENGTH_SHORT).show();
            }else if (id == R.id.create_list) {
                String newListId = database.push().getKey();
                if (newListId == null) return false;

                String listName = "Listă " + System.currentTimeMillis(); // Sau cere un nume de la user
                database.child(newListId).child("name").setValue(listName);

                listId = newListId;
                sharedPreferences.edit().putString("LIST_ID", listId).apply();

                items.clear();  // Golește lista veche de produse
                adapter.notifyDataSetChanged();

                Toast.makeText(this, "Listă creată: " + listName, Toast.LENGTH_SHORT).show();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });




        String profileUri = sharedPreferences.getString("PROFILE_URI", null);
        if (profileUri != null) {
            profileImage.setImageURI(Uri.parse(profileUri));
        }

        itemNameInput.setVisibility(inputsVisible ? View.VISIBLE : View.GONE);
        itemDescriptionInput.setVisibility(inputsVisible ? View.VISIBLE : View.GONE);

        if (listId == null) {
            listId = database.push().getKey();
            sharedPreferences.edit().putString("LIST_ID", listId).apply();
        }

        adapter = new ShoppingListAdapter(this, items, database, listId);
        listView.setAdapter(adapter);

        database.child(listId).child("items").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ShoppingItem item = child.getValue(ShoppingItem.class);
                    if (item != null) items.add(item);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea datelor.", Toast.LENGTH_SHORT).show();
            }
        });

        changeProfileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 1001);
        });

        addButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (itemNameInput.getVisibility() == View.GONE) {
                itemNameInput.setVisibility(View.VISIBLE);
                itemDescriptionInput.setVisibility(View.VISIBLE);
                editor.putBoolean("inputsVisible", true);
            } else {
                if (listId == null) {
                    Toast.makeText(this, "Listă inexistentă!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String name = itemNameInput.getText().toString().trim();
                String description = itemDescriptionInput.getText().toString().trim();

                if (!name.isEmpty()) {
                    String itemId = database.push().getKey();
                    ShoppingItem newItem = new ShoppingItem(itemId, name, description);
                    database.child(listId).child("items").child(itemId).setValue(newItem);

                    itemNameInput.setText("");
                    itemDescriptionInput.setText("");
                    itemNameInput.setVisibility(View.GONE);
                    itemDescriptionInput.setVisibility(View.GONE);
                    editor.putBoolean("inputsVisible", false);
                }
            }
            editor.apply();
        });

        generateQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRGenerator.class);
            intent.putExtra("LIST_ID", listId);
            startActivity(intent);
        });

        scanQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(ShoppingListActivity.this, QRScannerActivity.class);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        shareButton.setOnClickListener(v -> showShareOptions());
    }

    private void loadUserShoppingLists() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        DatabaseReference userListsRef = FirebaseDatabase.getInstance().getReference("shoppingLists").child(uid);

        userListsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                menuListContainer.removeAllViews();

                for (DataSnapshot listSnapshot : snapshot.getChildren()) {
                    String listName = listSnapshot.child("name").getValue(String.class);
                    String listId = listSnapshot.getKey();

                    if (listName != null) {
                        TextView listItem = new TextView(ShoppingListActivity.this);
                        listItem.setText(listName);
                        listItem.setTextSize(16);
                        listItem.setPadding(24, 16, 24, 16);
                        listItem.setTextColor(Color.BLACK);


                        listItem.setOnClickListener(v -> {
                            Toast.makeText(ShoppingListActivity.this, "Ai ales: " + listName, Toast.LENGTH_SHORT).show();
                        });

                        menuListContainer.addView(listItem);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShoppingListActivity.this, "Eroare la încărcarea listelor.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showShareOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Alege metoda de partajare")
                .setItems(new String[]{"Partajare ca text", "Partajare cu link"}, (dialog, which) -> {
                    if (which == 0) {
                        shareListAsText();
                    } else {
                        shareListWithDynamicLink();
                    }
                }).show();
    }

    private void shareListAsText() {
        if (items.isEmpty()) {
            Toast.makeText(this, "Lista este goală!", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder shoppingListText = new StringBuilder("Lista mea de cumpărături:\n");
        for (ShoppingItem item : items) {
            shoppingListText.append("• ").append(item.getName());
            if (!item.getDescription().isEmpty()) {
                shoppingListText.append(" - ").append(item.getDescription());
            }
            shoppingListText.append("\n");
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shoppingListText.toString());
        startActivity(Intent.createChooser(shareIntent, "Trimite lista prin"));
    }

    private void shareListWithDynamicLink() {
        String deepLink = DYNAMIC_LINK_DOMAIN + "?LIST_ID=" + listId;
        FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(deepLink))
                .setDomainUriPrefix(DYNAMIC_LINK_DOMAIN)
                .setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
                .buildShortDynamicLink()
                .addOnSuccessListener(shortDynamicLink -> {
                    Uri shortLink = shortDynamicLink.getShortLink();
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "Deschide lista mea de cumpărături: " + shortLink.toString());
                    startActivity(Intent.createChooser(shareIntent, "Trimite link-ul prin"));
                })
                .addOnFailureListener(e -> Log.e("DynamicLink", "Eroare generare link", e));
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, pendingDynamicLinkData -> {
                    if (pendingDynamicLinkData != null && pendingDynamicLinkData.getLink() != null) {
                        Uri deepLink = pendingDynamicLinkData.getLink();
                        String receivedListId = deepLink.getQueryParameter("LIST_ID");
                        if (receivedListId != null) {
                            listId = receivedListId;
                            SharedPreferences sharedPreferences = getSharedPreferences("ShoppingAppPrefs", MODE_PRIVATE);
                            sharedPreferences.edit().putString("LIST_ID", listId).apply();

                            String userId = FirebaseAuth.getInstance().getUid();
                            if (userId != null) {
                                database.child(listId).child("participants").child(userId).setValue(true);
                            }

                            database.child(listId).child("items").addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    items.clear();
                                    for (DataSnapshot child : snapshot.getChildren()) {
                                        ShoppingItem item = child.getValue(ShoppingItem.class);
                                        if (item != null) items.add(item);
                                    }
                                    adapter = new ShoppingListAdapter(ShoppingListActivity.this, items, database, listId);
                                    listView.setAdapter(adapter);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e("Firebase", "Eroare la citirea listei partajate", error.toException());
                                }
                            });
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri selectedImageUri = data.getData();
            ImageView profileImage = findViewById(R.id.profileImage);
            profileImage.setImageURI(selectedImageUri);
            sharedPreferences.edit().putString("PROFILE_URI", selectedImageUri.toString()).apply();
        }
    }
}
