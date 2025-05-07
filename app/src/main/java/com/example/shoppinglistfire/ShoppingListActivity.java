package com.example.shoppinglistfire;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShoppingListActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;
    private String ownerId;
    private String listId;
    private List<ShoppingItem> items;
    private ShoppingListAdapter adapter;
    private ListView listView;
    private EditText itemNameInput, itemDescriptionInput;
    private Button addButton, generateQRButton, scanQRButton, logoutButton, shareButton;
    private ImageView profileImage;
    private SharedPreferences sharedPreferences;
    private ActivityResultLauncher<String> pickImageLauncher;
    private static final String DYNAMIC_LINK_DOMAIN = "https://listadecumparaturi.page.link";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ascunde ActionBar și oprește resize la tastatură
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        setContentView(R.layout.activity_shopping_list);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("ShoppingAppPrefs", MODE_PRIVATE);
        rootRef = FirebaseDatabase.getInstance().getReference("shoppingLists");

        // Verifică autentificarea
        String currentUid = mAuth.getUid();
        if (currentUid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 1) încearcă mai întâi Dynamic Link
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, pending -> {
                    if (pending != null && pending.getLink() != null) {
                        Uri link = pending.getLink();
                        String o = link.getQueryParameter("OWNER_ID");
                        String l = link.getQueryParameter("LIST_ID");
                        if (o != null && l != null) {
                            handleNewList(o, l);
                            return;
                        }
                    }
                    // 2) dacă nu a fost dynamic link, verifică extra din Intent (QR)
                    Intent in = getIntent();
                    String o = in.getStringExtra("OWNER_ID");
                    String l = in.getStringExtra("LIST_ID");
                    if (o != null && l != null) {
                        handleNewList(o, l);
                    } else {
                        // 3) fallback la preferințe / listă implicită
                        String me = mAuth.getUid();
                        ownerId = sharedPreferences.getString("OWNER_ID", me);
                        listId  = sharedPreferences.getString("LIST_ID",  null);
                        if (listId == null) createAndInitializeNewList();
                        else initializeAdapterWithList(listId);
                    }
                })
                .addOnFailureListener(e -> {
                    // în caz de eroare la rezolvarea link-ului, fallback la același comportament
                    String me = mAuth.getUid();
                    ownerId = sharedPreferences.getString("OWNER_ID", me);
                    listId  = sharedPreferences.getString("LIST_ID",  null);
                    if (listId == null) createAndInitializeNewList();
                    else initializeAdapterWithList(listId);
                });

        // UI Binding
        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        findViewById(R.id.menuButton)
                .setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));

        listView = findViewById(R.id.listView);
        itemNameInput = findViewById(R.id.itemNameInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        addButton = findViewById(R.id.addButton);
        generateQRButton = findViewById(R.id.generateQRButton);
        scanQRButton = findViewById(R.id.scanQRButton);
        shareButton = findViewById(R.id.shareButton);
        logoutButton = findViewById(R.id.logoutButton);
        profileImage = findViewById(R.id.profileImage);
        Button changePic = findViewById(R.id.changeProfileButton);
        TextView btnMyLists = findViewById(R.id.btnMyLists);
        TextView btnCreate = findViewById(R.id.btnCreateList);
        LinearLayout listsBox = findViewById(R.id.listsContainer);

        // Ascunde câmpurile la start
        itemNameInput.setVisibility(View.GONE);
        itemDescriptionInput.setVisibility(View.GONE);

        // Logout
        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Image picker
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) saveImageLocally(uri);
                }
        );
        changePic.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Încarcă poza de profil
        String profPath = sharedPreferences.getString("PROFILE_URI", null);
        if (profPath != null && new File(profPath).exists()) {
            Glide.with(this)
                    .load(new File(profPath))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(profileImage);
        }

        // Pregătește lista de produse (adapter-ul va fi configurat de handleNewList sau de fallback)
        items = new ArrayList<>();

// 1) Încearcă Dynamic Link
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, pending -> {
                    if (pending != null && pending.getLink() != null) {
                        Uri link = pending.getLink();
                        String o = link.getQueryParameter("OWNER_ID");
                        String l = link.getQueryParameter("LIST_ID");
                        if (o != null && l != null) {
                            handleNewList(o, l);
                            return;
                        }
                    }
                    // 2) Dacă nu a fost Dynamic Link, verifică extras din Intent (QR scan)
                    Intent in2 = getIntent();
                    String o2 = in2.getStringExtra("OWNER_ID");
                    String l2 = in2.getStringExtra("LIST_ID");
                    if (o2 != null && l2 != null) {
                        handleNewList(o2, l2);
                    } else {
                        // 3) Fallback la preferințe sau creare listă implicită
                        String me = currentUid;
                        ownerId = sharedPreferences.getString("OWNER_ID", me);
                        listId  = sharedPreferences.getString("LIST_ID",  null);
                        if (listId == null) {
                            createAndInitializeNewList();
                        } else {
                            initializeAdapterWithList(listId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Dacă Dynamic Link eșuează, facem același fallback
                    String me = currentUid;
                    ownerId = sharedPreferences.getString("OWNER_ID", me);
                    listId  = sharedPreferences.getString("LIST_ID",  null);
                    if (listId == null) {
                        createAndInitializeNewList();
                    } else {
                        initializeAdapterWithList(listId);
                    }
                });

        // Adaugă produs
        addButton.setOnClickListener(v -> {
            if (itemNameInput.getVisibility() == View.GONE) {
                itemNameInput.setVisibility(View.VISIBLE);
                itemDescriptionInput.setVisibility(View.VISIBLE);
            } else {
                addProduct(ownerId);
                itemNameInput.setText("");
                itemDescriptionInput.setText("");
                itemNameInput.setVisibility(View.GONE);
                itemDescriptionInput.setVisibility(View.GONE);
            }
        });

        // Generează QR
        generateQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, QRGenerator.class);
            intent.putExtra("LIST_ID", listId);
            startActivity(intent);
        });

        // Scanează QR
        scanQRButton.setOnClickListener(v ->
                startActivity(new Intent(this, QRScannerActivity.class))
        );

        // Partajează listă
        shareButton.setOnClickListener(v -> showShareOptions());

        // Toggle dropdown “Listele mele”
        btnMyLists.setOnClickListener(v -> {
            if (listsBox.getVisibility() == View.GONE) {
                loadUserShoppingLists(listsBox, btnMyLists);
            } else {
                listsBox.setVisibility(View.GONE);
                btnMyLists.setText("Listele mele ");
            }
        });

        // Creează listă nouă
        btnCreate.setOnClickListener(v -> showCreateListDialog());
    }



    /**
     * Încarcă listele ownerId în drawer
     */
    private void loadUserShoppingLists(LinearLayout listsBox, TextView btn) {
        String currentUid = mAuth.getUid();
        if (currentUid == null) return;
        listsBox.removeAllViews();
        DrawerLayout drawer = findViewById(R.id.drawerLayout);

        rootRef.child(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.hasChildren()) {
                            listsBox.addView(makeListItem("(nici o listă)", false));
                        } else {
                            for (DataSnapshot ls : snapshot.getChildren()) {
                                String name = ls.child("name").getValue(String.class);
                                String id = ls.getKey();
                                // Dacă există sharedFrom, folosim acel owner, altfel curentul.
                                String sharedFrom = ls.child("sharedFrom").getValue(String.class);
                                String entryOwner = (sharedFrom != null ? sharedFrom : currentUid);

                                TextView it = makeListItem(name, true);
                                it.setOnClickListener(v -> {
                                    ownerId = entryOwner;
                                    listId = id;
                                    sharedPreferences.edit()
                                            .putString("OWNER_ID", ownerId)
                                            .putString("LIST_ID", listId)
                                            .apply();
                                    initializeAdapterWithList(id);
                                    drawer.closeDrawer(GravityCompat.START);
                                });
                                listsBox.addView(it);
                            }
                        }
                        listsBox.setVisibility(View.VISIBLE);
                        btn.setText("Listele mele ▲");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { /*…*/ }
                });
    }

    /**
     * Creează un TextView cu ripple și padding
     */
    private TextView makeListItem(String text, boolean enabled) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        tv.setPadding(pad, pad / 2, pad, pad / 2);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // ripple
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue, true);
        tv.setBackgroundResource(outValue.resourceId);
        @ColorInt int clr = ContextCompat.getColor(this,
                enabled ? R.color.drawerText : android.R.color.darker_gray);
        tv.setTextColor(clr);
        return tv;
    }

    /**
     * Adaugă un produs în Firebase la nodul ownerId/listId
     */
    private void addProduct(String owner) {
        if (listId == null) {
            Toast.makeText(this, "Listă inexistentă!", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = itemNameInput.getText().toString().trim();
        String desc = itemDescriptionInput.getText().toString().trim();
        if (name.isEmpty()) return;
        String itemId = rootRef.child(owner).push().getKey();
        rootRef.child(owner)
                .child(listId)
                .child("items")
                .child(itemId)
                .setValue(new ShoppingItem(itemId, name, desc));
    }

    /**
     * (Re)Initializează adapter-ul pentru listId
     */
    private void initializeAdapterWithList(String id) {
        adapter = new ShoppingListAdapter(
                this,
                items,
                rootRef.child(ownerId),
                id
        );
        listView.setAdapter(adapter);
        loadItemsFromFirebase(id);
    }

    /**
     * Încarcă produsele din Firebase sub ownerId/listId/items
     */
    private void loadItemsFromFirebase(String id) {
        rootRef.child(ownerId)
                .child(id)
                .child("items")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        items.clear();
                        for (DataSnapshot c : snap.getChildren()) {
                            ShoppingItem it = c.getValue(ShoppingItem.class);
                            if (it != null) items.add(it);
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError err) {
                        Toast.makeText(ShoppingListActivity.this,
                                "Eroare la încărcarea produselor.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Creează o listă nouă sub current user = proprietar
     */
    private void createNewList(String name) {
        String currentUid = mAuth.getUid();
        String id = rootRef.child(currentUid).push().getKey();
        if (id == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("participants/" + currentUid, true);
        rootRef.child(currentUid)
                .child(id)
                .updateChildren(data)
                .addOnSuccessListener(aVoid -> {
                    // setează proprietar și listă curentă
                    ownerId = currentUid;
                    listId = id;
                    sharedPreferences.edit()
                            .putString("OWNER_ID", ownerId)
                            .putString("LIST_ID", listId)
                            .apply();
                    initializeAdapterWithList(id);
                    Toast.makeText(this,
                            "Listă creată: " + name,
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("ShoppingList", "Eroare creare listă", e);
                    Toast.makeText(this,
                            "Nu s-a putut crea lista.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void createAndInitializeNewList() {
        createNewList("Listă implicită");
    }

    /**
     * Dialog pentru creare listă nouă
     */
    private void showCreateListDialog() {
        EditText et = new EditText(this);
        et.setHint("Nume listă");
        new AlertDialog.Builder(this)
                .setTitle("Creează listă nouă")
                .setView(et)
                .setPositiveButton("Creează", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) createNewList(n);
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    /**
     * Extrage OWNER și LIST from QR sau DL,
     * salvează prefs, marchează user-ul ca participant
     * şi iniţializează adapter-ul.
     */
    private void handleNewList(String o, String l) {
        ownerId = o;
        listId   = l;
        sharedPreferences.edit()
                .putString("OWNER_ID", o)
                .putString("LIST_ID",  l)
                .apply();

        // Adaugă-te ca participant la lista proprietarului adevărat
        String me = mAuth.getUid();
        rootRef.child(o)
                .child(l)
                .child("participants")
                .child(me)
                .setValue(true);

        // Copiază numele listei și marchează de unde a venit
        rootRef.child(o)
                .child(l)
                .child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        String name = snap.getValue(String.class);
                        if (name != null) {
                            rootRef.child(me)
                                    .child(l)
                                    .child("name")
                                    .setValue(name);
                            rootRef.child(me)
                                    .child(l)
                                    .child("sharedFrom")
                                    .setValue(o);
                        }
                        initializeAdapterWithList(l);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        // Chiar dacă nu citim numele, tot inițializăm adapter-ul
                        initializeAdapterWithList(l);
                    }
                });
    }


    /**
     * Salvează poza de profil local și o afișează
     */
    private void saveImageLocally(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            File f = new File(getFilesDir(), "profile.jpg");
            try (FileOutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            Glide.with(this)
                    .load(f)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(profileImage);
            sharedPreferences.edit()
                    .putString("PROFILE_URI", f.getAbsolutePath())
                    .apply();
        } catch (Exception e) {
            Log.e("SaveImage", "Eroare la salvarea imaginii", e);
            Toast.makeText(this,
                    "Eroare la salvarea pozei",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Partajare listă prin text sau DynamicLink
     */
    private void showShareOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Alege metoda de partajare")
                .setItems(new String[]{"Partajare ca text",
                                "Partajare cu link"},
                        (dlg, which) -> {
                            if (which == 0) shareListAsText();
                            else shareListWithDynamicLink();
                        })
                .show();
    }

    private void shareListAsText() {
        if (items.isEmpty()) {
            Toast.makeText(this,
                    "Lista este goală!",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder("Lista mea de cumpărături:\n");
        for (ShoppingItem it : items) {
            sb.append("• ").append(it.getName());
            if (!it.getDescription().isEmpty()) {
                sb.append(" - ").append(it.getDescription());
            }
            sb.append("\n");
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(share, "Trimite lista prin"));
    }

    private void shareListWithDynamicLink() {
        String deepLink = DYNAMIC_LINK_DOMAIN
                + "?OWNER_ID=" + ownerId
                + "&LIST_ID=" + listId;

        FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(deepLink))
                .setDomainUriPrefix(DYNAMIC_LINK_DOMAIN)
                .setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
                .buildShortDynamicLink()
                .addOnSuccessListener(shortDL -> {
                    Uri link = shortDL.getShortLink();
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT,
                            "Deschide lista mea de cumpărături: " + link);
                    startActivity(Intent.createChooser(share, "Trimite link-ul prin"));
                })
                .addOnFailureListener(e -> Log.e("DynamicLink", "Eroare generare link", e));
    }
}