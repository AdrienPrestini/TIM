package com.example.adrien.projetmusique;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class MainActivity extends ListActivity {
    private static final int UPDATE_FREQUENCY = 500;
    private static final int STEP_VALUE = 4000;

    private TextView selectedfile = null;
    private SeekBar seekBar = null;
    private MediaPlayer player = null;
    private ImageButton prev = null;
    private ImageButton play = null;
    private ImageButton next = null;
    private MediaCursorAdapter adapter = null;

    private boolean isStarted = false;
    private String currentFile = "";
    private ArrayList<String> fileList;
    private boolean isMovingSeekBar = false;
    private final Handler handler = new Handler();
    private Cursor musicCursor;

    public static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123;

    public boolean checkPermissionREAD_EXTERNAL_STORAGE(final Context context) {
        int currentAPIVersion = Build.VERSION.SDK_INT;
        if (currentAPIVersion >= android.os.Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        (Activity) context,
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    showDialog("External storage", context,
                            Manifest.permission.READ_EXTERNAL_STORAGE);

                } else {
                    ActivityCompat
                            .requestPermissions(
                                    (Activity) context,
                                    new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                }
                return false;
            } else {
                return true;
            }

        } else {
            return true;
        }
    }
    public void showDialog(final String msg, final Context context, final String permission) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle("Permission necessary");
        alertBuilder.setMessage(msg + " permission is necessary");
        alertBuilder.setPositiveButton(android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions((Activity) context,
                                new String[] { permission },
                                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                    }
                });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    private final Runnable updatePositinRunnable = new Runnable() {
        @Override
        public void run() {
            updatePosition();
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        selectedfile = (TextView) findViewById(R.id.selecteditem);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        prev = (ImageButton) findViewById(R.id.previous);
        play = (ImageButton) findViewById(R.id.play);
        next = (ImageButton) findViewById(R.id.next);

        player = new MediaPlayer();
        player.setOnCompletionListener(onCompletion);
        player.setOnErrorListener(onError);
        seekBar.setOnSeekBarChangeListener(seekBarChanged);

        if (checkPermissionREAD_EXTERNAL_STORAGE(this)) {
            try
            {
                fileList = getAllSongs();
                getSongList();
                Log.i("Info","Tous les titres de musiques sont chargés");
            }catch (Exception e){
                Log.i("ERREUR : ", e.toString()+":"+e.getMessage());
            }
        }
    }

    private ArrayList<String> getAllSongs(){
        ArrayList<String> res = new ArrayList<>();
        File sdCardRoot = Environment.getExternalStorageDirectory();
        File yourDir = new File(sdCardRoot, "/Music");
        for (File f : yourDir.listFiles()) {
            if (f.isFile())
                res.add(f.getName());
        }
        return res;
    }
    public void getSongList() {
        //retrieve song info
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = android.provider.MediaStore.Audio.Media.getContentUriForPath("/storage/emulated/0/Music/");
        musicCursor = musicResolver.query(musicUri, null, null, null, null);
        //iterate over results if valid
        if(musicCursor!=null && musicCursor.moveToFirst()){
            //get columns
            adapter = new MediaCursorAdapter(this, R.layout.item, musicCursor);
            setListAdapter(adapter);
            prev.setOnClickListener(OnButtonClick);
            play.setOnClickListener(OnButtonClick);
            next.setOnClickListener(OnButtonClick);
        }

    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        currentFile = (String) v.getTag();
        startPlay(currentFile);
    }

    private void startPlay(String file) {
        if (currentFile == "" || file == "" || file == null){
            currentFile = "/storage/emulated/0/Music/" + fileList.get(0);
            file = currentFile;
        }

        Log.i("Selected: ", file);
        selectedfile.setText(file.split("/storage/emulated/0/Music/")[1]);
        seekBar.setProgress(0);
        player.stop();
        player.reset();

        try {
            player.setDataSource(file);
            player.prepare();
            player.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        seekBar.setMax(player.getDuration());
        play.setImageResource(android.R.drawable.ic_media_pause);
        updatePosition();
        isStarted = true;
    }

    private void stopPlay() {
        player.stop();
        player.reset();
        play.setImageResource(android.R.drawable.ic_media_play);
        handler.removeCallbacks(updatePositinRunnable);
        seekBar.setProgress(0);
        isStarted = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updatePositinRunnable);
        player.stop();
        player.reset();
        player.release();
        player = null;
    }

    private void updatePosition() {
        handler.removeCallbacks(updatePositinRunnable);
        seekBar.setProgress(player.getCurrentPosition());
        handler.postDelayed(updatePositinRunnable, UPDATE_FREQUENCY);
    }

    private int getPositionSong(String currFile){
        for(int i = 0 ; i < fileList.size(); i++){
            String[] title = currFile.split("/storage/emulated/0/Music/");
            if(fileList.get(i).equals(title[1]))
                return i;
        }

        return 0;
    }

    private View.OnClickListener OnButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.play: {
                    if (player.isPlaying()) {
                        handler.removeCallbacks(updatePositinRunnable);
                        player.pause();
                        play.setImageResource(android.R.drawable.ic_media_play);
                    } else {
                        if (isStarted) {
                            player.start();
                            play.setImageResource(android.R.drawable.ic_media_pause);
                            updatePosition();
                        } else {
                            startPlay(currentFile);
                        }
                    }
                    break;
                }

                case R.id.next: {
                    int position = getPositionSong(currentFile);

                    currentFile = "/storage/emulated/0/Music/"+fileList.get(position+1 % fileList.size());

                    startPlay(currentFile);

                    break;
                }

                case R.id.previous: {
                    int duree = player.getDuration();
                    int seekto;
                    if(player.getCurrentPosition() < duree/2)//on passe au précédent
                        seekto =  0;
                    else
                        seekto =  0;
                    player.pause();
                    player.seekTo(seekto);
                    player.start();
                    break;
                }
            }
        }
    };
    private MediaPlayer.OnCompletionListener onCompletion = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            stopPlay();
        }

        ;
    };
    private MediaPlayer.OnErrorListener onError = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            return false;
        }
    };

    private SeekBar.OnSeekBarChangeListener seekBarChanged = new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (isMovingSeekBar) {
                        player.seekTo(progress);
                        Log.i("OnSeekBarChangeListener", "OnProgressChanged");
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isMovingSeekBar = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isMovingSeekBar = false;
                }
            };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // do your stuff
                } else {
                    Toast.makeText(this, "GET_ACCOUNTS Denied",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions,
                        grantResults);
        }
    }
}
