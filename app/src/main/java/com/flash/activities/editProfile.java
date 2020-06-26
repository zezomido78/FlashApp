package com.flash.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.flash.DataBase.DataBase;
import com.flash.R;
import com.flash.person.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

public class editProfile extends AppCompatActivity implements View.OnClickListener{
    private Button save;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser user;
    private EditText textViewEmail;
    private DatabaseReference databaseReference;
    private EditText editTextName;
    private EditText editTextPhoneNo;
    private EditText postalAddress;
    private ImageView profileImageView;
    private static int PICK_IMAGE = 123;
    private Uri imagePath;
    private StorageReference storageReference;
    private DataBase dataBase;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        editTextName = findViewById(R.id.UserNameText);
        editTextPhoneNo = findViewById(R.id.editTextPhone);
        save =findViewById(R.id.save_data);
        textViewEmail= findViewById(R.id.EmailTextup);
        profileImageView = findViewById(R.id.photoPicker);
        postalAddress = findViewById(R.id.editTextTextPostalAddress);
        save.setOnClickListener(this);

        firebaseAuth=FirebaseAuth.getInstance();
        user = firebaseAuth.getCurrentUser();

        if (user == null){
            startActivity(new Intent(getApplicationContext(),LogIn.class));
            finish();
        }
        dataBase = DataBase.getInstance();
        storageReference = FirebaseStorage.getInstance().getReference();
        databaseReference = FirebaseDatabase.getInstance().getReference().
                child("Flash").child("Users").child(user.getUid());

        storageReference.child(Objects.requireNonNull(firebaseAuth.getUid())).child("Images").
                child("Profile Pic").getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                Picasso.get().load(uri).fit().centerInside().into(profileImageView);
            }
        });


        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User userProfile = snapshot.getValue(User.class);
                    assert userProfile != null;
                    editTextName.setText(userProfile.getUsername(), TextView.BufferType.EDITABLE);
                    postalAddress.setText(userProfile.getPostalCode(), TextView.BufferType.EDITABLE);
                    editTextPhoneNo.setText(userProfile.getPhone(), TextView.BufferType.EDITABLE);
                    textViewEmail.setText(userProfile.getEmail(), TextView.BufferType.EDITABLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(editProfile.this, error.getCode(), Toast.LENGTH_SHORT).show();
            }
        });


        profileImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent profileIntent = new Intent();
                profileIntent.setType("image/*");
                profileIntent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(profileIntent, "Select Image."), PICK_IMAGE);
            }
        });
    }

    @Override
    public void onClick(View view) {
        if ( view == save ){
//            if (imagePath == null) {
//                Drawable drawable = this.getResources().getDrawable(R.drawable.avatar);
//                Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.avatar);
//                imagePath = getImageUri(this,bitmap);
//              //  openSelectProfilePictureDialog();
//            }
            userInformation();
            sendUserData();
        }
    }

    private void userInformation(){
        String name = editTextName.getText().toString().trim();
        String mail = textViewEmail.getText().toString().trim();
        String phone = editTextPhoneNo.getText().toString().trim();
        String postal = postalAddress.getText().toString().trim();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        User data = new User(mail,name,phone,postal);
        data.setImgPath(imagePath != null ? imagePath.toString() : Uri.parse("src/main/res/drawable/avatar.png").toString());
        assert user != null;
        data.setUserId(user.getUid());
        dataBase.addUser(data);
        Toast.makeText(getApplicationContext(),"User information updated",Toast.LENGTH_LONG).show();
    }


    private void sendUserData() {
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        StorageReference imageReference = storageReference.child(Objects.requireNonNull(firebaseAuth.getUid())).child("Images").child("Profile Pic"); //User id/Images/Profile Pic.jpg
        if(imagePath==null) imagePath = Uri.parse("src/main/res/drawable/avatar.png");

        // show progress dialog until the pic is uploaded
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Upload a photo");
        progressDialog.setMessage("please wait, we are working to upload your Photo");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        UploadTask uploadTask = imageReference.putFile(imagePath);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                progressDialog.dismiss();
                Toast.makeText(editProfile.this, "Error: Uploading profile picture, Try Again", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(editProfile.this, userPofile.class));
                finish();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                progressDialog.dismiss();
                Toast.makeText(editProfile.this, "Profile picture uploaded", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(editProfile.this, userPofile.class));
                finish();
            }
        });
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data.getData() != null) {
            imagePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imagePath);
                profileImageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}