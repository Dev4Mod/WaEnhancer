package com.wmods.wppenhacer.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ContactPickerActivity extends BaseActivity {

    private String mKey;
    private ListView contactListView;
    private ArrayAdapter<String> adapter;
    private HashSet<String> selectedNumbers = new HashSet<>();
    private final List<Contact> allContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_picker);
        mKey = getIntent().getStringExtra("key");

        contactListView = findViewById(R.id.contactListView);
        Button selectButton = findViewById(R.id.selectButton);
        EditText searchBar = findViewById(R.id.searchBar);

        ArrayList<String> selectedNumbersInIntent = getIntent().getStringArrayListExtra("selectedNumbers");
        if (selectedNumbersInIntent != null) {
            selectedNumbers = new HashSet<>(selectedNumbersInIntent);
        }

        loadAllContacts();
        insertContactsInList(allContacts);

        selectButton.setOnClickListener(view -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selectedNumbers", new ArrayList<>(selectedNumbers));
            resultIntent.putExtra("key", mKey);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Não precisa implementar
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String searchText = charSequence.toString().toLowerCase();
                List<Contact> filteredContacts = allContacts.stream()
                        .filter(contact -> contact.name.toLowerCase().contains(searchText) || contact.number.contains(searchText))
                        .collect(Collectors.toList());
                insertContactsInList(filteredContacts);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // Não precisa implementar
            }
        });
    }

    @SuppressLint("Range")
    private void loadAllContacts() {
        allContacts.clear();
        Set<String> uniqueNumbers = new HashSet<>();

        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.STARRED + " DESC, " + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String nome = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String numero = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                int starred = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED));

                String numeroLimpo = numero.replaceAll("[^0-9]", "");

                if (!uniqueNumbers.contains(numeroLimpo)) {
                    uniqueNumbers.add(numeroLimpo);
                    allContacts.add(new Contact(nome, numeroLimpo, starred == 1));
                }
            }
            cursor.close();
        }
    }

    @SuppressLint("Range")
    private void insertContactsInList(List<Contact> contacts) {
        List<String> contactList = contacts.stream().map(contact -> contact.name + " - " + contact.number).collect(Collectors.toList());

        // Ordena a lista colocando os contatos selecionados no topo
        contactList.sort((contact1, contact2) -> {
            String number1 = contact1.substring(contact1.lastIndexOf("-") + 1).trim();
            String number2 = contact2.substring(contact2.lastIndexOf("-") + 1).trim();
            boolean isSelected1 = selectedNumbers.contains(number1);
            boolean isSelected2 = selectedNumbers.contains(number2);
            if (isSelected1 && !isSelected2) {
                return -1; // coloca contact1 antes de contact2
            } else if (!isSelected1 && isSelected2) {
                return 1; // coloca contact2 antes de contact1
            } else {
                return contact1.compareToIgnoreCase(contact2); // mantém a ordem original
            }
        });

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, contactList);
        contactListView.setAdapter(adapter);
        contactListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        for (int i = 0; i < contactList.size(); i++) {
            String contactInfo = contactList.get(i);
            String number = contactInfo.substring(contactInfo.lastIndexOf("-") + 1).trim();
            if (selectedNumbers.contains(number)) {
                contactListView.setItemChecked(i, true);
            }
        }

        contactListView.setOnItemClickListener((adapterView, view, i, l) -> {
            String contactInfo = adapter.getItem(i);
            String number = contactInfo.substring(contactInfo.lastIndexOf("-") + 1).trim();
            if (selectedNumbers.contains(number)) {
                selectedNumbers.remove(number);
            } else {
                selectedNumbers.add(number);
            }
        });
    }

    public static class Contact {
        public String name;
        public String number;
        public boolean isStarred;

        public Contact(String name, String number, boolean isStarred) {
            this.name = name;
            this.number = number;
            this.isStarred = isStarred;
        }
    }
}
