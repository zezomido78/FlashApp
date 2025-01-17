package com.flash.chat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.flash.Codes;
import com.flash.R;
import com.flash.activities.intro1;
import com.flash.chat.call.CallActivity;
import com.flash.chat.call.MyCallClientListener;
import com.flash.chat.call.MySinchClientListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallClient;
import com.sinch.android.rtc.calling.CallClientListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatActivity extends AppCompatActivity {
    private static DatabaseReference databaseReference;
    private static FirebaseAuth firebaseAuth;
    private StorageReference mImageStorage;
    private String currentUserID;
    private String targetUserID;
    private String targetUserName;
    private ImageButton sendButton;
    private ImageButton addBtn;
    private EditText chatMsgText;
    private TextView targetNameText;
    private CircleImageView profileImg;
    private ImageButton callBtn;
    private RecyclerView mMessagesList;
    private final List<Message> messageList= new ArrayList<>();
    private LinearLayoutManager mLinearLayout;
    private MessageAdapter messageAdapter;
    private SwipeRefreshLayout mRefreshLayout;
    private static final int  TOTAL_ITEM_PER_PAGE = 10;
    private static final int GALLERY_PICK = 1;
    private int current_page =1;
    private int current_pos = 0;
    private String mLasKey = "";
    private String mPrevKey = "";
    private Intent intentCall;

    boolean isCallingAllowed=true;
   static CallClient callClient;
   static SinchClient sinchClient;
   static Call call;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Bundle extra = getIntent().getExtras();
        mImageStorage = FirebaseStorage.getInstance().getReference();
        /////////
        sendButton = (ImageButton) findViewById(R.id.send_button);
        messageAdapter = new MessageAdapter(messageList);
        targetNameText = (TextView) findViewById(R.id.char_name);
        addBtn = (ImageButton) findViewById(R.id.add_button);
        chatMsgText = (EditText) findViewById(R.id.messageTextView);
        profileImg = (CircleImageView) findViewById(R.id.custom_bar_image);
        mMessagesList = (RecyclerView) findViewById(R.id.messagesList);
        callBtn = (ImageButton) findViewById(R.id.CallButton);
        mRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.message_swipe_refresh);
        mMessagesList.setHasFixedSize(true);
        messageAdapter = new MessageAdapter(messageList);
        mLinearLayout = new LinearLayoutManager(this);
        mMessagesList.setLayoutManager(mLinearLayout);
        mMessagesList.setAdapter(messageAdapter);
        targetNameText = (TextView) findViewById(R.id.char_name);
        intentCall = new Intent(this,CallActivity.class);
        ////////
        if(extra != null){
            targetUserID = extra.getString("TARGET_USER_ID");
            targetUserName = extra.getString("TARGET_USER_NAME");
        }
        firebaseAuth = FirebaseAuth.getInstance();
        currentUserID=firebaseAuth.getCurrentUser().getUid();
        databaseReference =  FirebaseDatabase.getInstance().getReference("Flash");
        DatabaseReference sinchReference = databaseReference.child("SINCH_SECRET");

        loadMessages();
        databaseReference.child("Chat").child(currentUserID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(!snapshot.hasChild(targetUserID)){
                    Map chatAddMap = new HashMap();
                    Map chatUserMap = new HashMap();
                    chatUserMap.put("Chat/"+currentUserID+"/"+targetUserID,chatAddMap);
                    chatUserMap.put("Chat/"+targetUserID+"/"+currentUserID,chatAddMap);
                    databaseReference.updateChildren(chatUserMap, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                            if(error != null){
                                Log.d("CHAT_LOG",error.getMessage());
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        /////////////////////////////////////////// CALLL //////////////////////////////////////////////////////////////

        callBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(isCallingAllowed){
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {

                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_DENIED) {

                            Log.d("permission", "permission denied to SEND_SMS - requesting it");
                            String[] permissions = {Manifest.permission.RECORD_AUDIO};

                            requestPermissions(permissions,Codes.RECORD_AUDIO_PERMISSION_REQUEST_CODE);

                        }else {
                                intentCall.putExtra("isACaller",true);
                             CallActivity.call = MainActivity.callClient.callUser(targetUserID);
                             startActivity(intentCall);
                        }
                    }

                }else {
                        Log.d("CALL","CALL Not allowed");
                        Toast.makeText(ChatActivity.this,"Required the user to allow contacting",Toast.LENGTH_SHORT).show();
                    }
            }
        }
        );
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMsg();
            }
        });
        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                current_page++;
                current_pos=0;
                loadMoreMessages();

            }
        });
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent galleryintent = new Intent();
                galleryintent.setType("image/*");
                galleryintent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(galleryintent,"Select Image"),GALLERY_PICK);
            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==GALLERY_PICK && resultCode == RESULT_OK){
            Uri imageUri = data.getData();
            final String current_user_ref = "messages/"+currentUserID+"/"+targetUserID;
            final String target_uset_ref= "messages/"+targetUserID+"/"+currentUserID;
            DatabaseReference user_message_ref = databaseReference.child("messages").child(currentUserID).child(targetUserID).push();
            final String push_id = user_message_ref.getKey();
            StorageReference filepath = mImageStorage.child("message_images").child(push_id+".jpg");
            filepath.putFile(imageUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                    if(task.isSuccessful()){
                        String download_uri = task.getResult().getMetadata().getReference().getDownloadUrl().toString();
                        Map msgMap = new HashMap();
                        msgMap.put("message",download_uri);
                        msgMap.put("seen",false);
                        msgMap.put("type","image");
                        msgMap.put("time", ServerValue.TIMESTAMP);
                        msgMap.put("from",currentUserID);
                        Map messageUserMap = new HashMap();
                        messageUserMap.put(current_user_ref+"/"+push_id,msgMap);
                        messageUserMap.put(target_uset_ref+"/"+push_id,msgMap);
                        databaseReference.updateChildren(messageUserMap, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                                if(error != null){
                                    Log.d("CHAT_LOG",error.getMessage());
                                }
                            }
                        });
                    }
                }
            });


        }
    }

    private void loadMoreMessages() {
        DatabaseReference msgRef = databaseReference.child("messages").child(currentUserID).child(targetUserID);

        Query messageQuery = msgRef.orderByKey().endAt(mLasKey).limitToLast(TOTAL_ITEM_PER_PAGE);

        messageQuery.addChildEventListener(new ChildEventListener() {

            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                long a =snapshot.getChildrenCount();
                if(snapshot.getChildrenCount()==1)return;
                Message message = snapshot.getValue(Message.class);

                if(!mPrevKey.equals(snapshot.getKey())){
                    messageList.add(current_pos++,message);
                }else {
                    mPrevKey=mLasKey;
                }
                if(current_pos == 1){
                    mLasKey = snapshot.getKey();

                }
                Log.d("TotalKeys", "Last key : "+mLasKey +" | Prev Key : "+mPrevKey);
                messageAdapter.notifyDataSetChanged();
                mMessagesList.scrollToPosition(messageList.size()-1);
                mRefreshLayout.setRefreshing(false);
                mLinearLayout.scrollToPositionWithOffset(current_pos,0);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void loadMessages() {
        DatabaseReference msgRef = databaseReference.child("messages").child(currentUserID).child(targetUserID);
        Query messagesQuery = msgRef.limitToLast(TOTAL_ITEM_PER_PAGE*current_page);
        messagesQuery.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Message message = (Message)snapshot.getValue(Message.class);

                current_pos++;
                if(current_pos == 1){

                    mLasKey = snapshot.getKey();
                    mPrevKey =snapshot.getKey();
                }
                messageList.add(message);
                messageAdapter.notifyDataSetChanged();
                mMessagesList.scrollToPosition(messageList.size()-1);
                mRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void sendMsg() {
        String msg = chatMsgText.getText().toString();
        if(!TextUtils.isEmpty(msg)){
            DatabaseReference ref = databaseReference.child("messages").child(currentUserID).child(targetUserID).push();
            String push_id = ref.getKey();
            String cur_user_ref = "messages/"+currentUserID+"/"+targetUserID+"/";
            String target_uset_ref= "messages/"+targetUserID+"/"+currentUserID;
            Map msgMap = new HashMap();
            msgMap.put("message",msg);
            msgMap.put("seen",false);
            msgMap.put("type","text");
            msgMap.put("time", ServerValue.TIMESTAMP);
            msgMap.put("from",currentUserID);
            Map messageUserMap = new HashMap();
            messageUserMap.put(cur_user_ref+"/"+push_id,msgMap);
            messageUserMap.put(target_uset_ref+"/"+push_id,msgMap);
            chatMsgText.setText("");
            databaseReference.updateChildren(messageUserMap, new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                    if(error != null){
                        Log.d("CHAT_LOG",error.getMessage());
                    }
                }
            });

        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == Codes.RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (!contains(grantResults,PackageManager.PERMISSION_DENIED)) {
                intentCall.putExtra("isACaller",true);
                call = MainActivity.callClient.callUser(targetUserID);
                startActivity(intentCall);

            }
        }
    }
    private boolean contains(int[] grantResult,int permission){
        for(int i=0;i<grantResult.length;i++){
            if(grantResult[i]==permission)return true;
        }
        return false;
    }

}
