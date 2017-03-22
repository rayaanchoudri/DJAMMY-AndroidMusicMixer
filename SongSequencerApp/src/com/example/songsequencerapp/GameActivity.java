package com.example.songsequencerapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class GameActivity extends Activity {

	public static final byte MSG_TYPE_BROADCAST_KEYS = 1;
	public static final byte MSG_TYPE_SET_SOUND_OUT = 2;
	public static final byte MSG_TYPE_MUTE = 3;
	public static final byte MSG_TYPE_START_GAME = 10;
	public static final byte MSG_TYPE_BPM = 11;
	
	public static boolean is_master = false;
	public static boolean groupSession = true; // If false, individual session is set
	public static boolean onTouch = false;
	boolean keyPressed = false;
	
	public int[] loopArray1 = null;
	public int[] loopArray2 = null;
	public int[] loopArray3 = null;
	public int[] loopArray4 = null;
	public int playPosition1;
	public int playPosition2;
	public int playPosition3;
	public int playPosition4;
	
	public Vec72 vec72;
	public Vec216 vec216;
	public Bass bass;
	public Drums drums;

	public int bassdrum;
	public int bassdrum_timer = 0;
	SoundPool soundpool;
	float instrument_volume = 1;
	float bpm_volume =(float) 0.7;

	Timer bpm_timer;
	Timer tcp_timer;
	Timer sendmsg_timer;
	SendMsgTimerTask sendmsg_task;
	BPMTimerTask bpmTask;
	TCPReadTimerTask tcp_task;

	public int my_instrument=SettingsMenu.getInstrument();
	
	public int my_key;
	SparseIntArray tcp_instruments; // <client, instrument>
	SparseIntArray tcp_keys; // <client, instrument>
	public boolean tcp_updated = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_game);
		
		if (LoopActivity.globalLoopArray.size() >= 4 ){
			loopArray1 = new int [LoopActivity.globalLoopArray.get(0).length];
			loopArray2 = new int [LoopActivity.globalLoopArray.get(1).length];
			loopArray3 = new int [LoopActivity.globalLoopArray.get(2).length];
			loopArray4 = new int [LoopActivity.globalLoopArray.get(3).length];
			loopArray1 = LoopActivity.globalLoopArray.get(0).clone();
			loopArray2 = LoopActivity.globalLoopArray.get(1).clone();
			loopArray3 = LoopActivity.globalLoopArray.get(2).clone();
			loopArray4 = LoopActivity.globalLoopArray.get(3).clone();
		}
		
		drums = new Drums();
		vec72 = new Vec72();
		vec216 = new Vec216();
		bass = new Bass();
		
		my_instrument = SettingsMenu.getInstrument();
		initKey(MiddlemanConnection.getKey());

		bpm_timer = new Timer();
		sendmsg_timer = new Timer();
		tcp_timer = new Timer();

		tcp_instruments = new SparseIntArray();
		tcp_keys = new SparseIntArray();

		soundpool = new SoundPool(8, AudioManager.STREAM_MUSIC, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		
		vec72.load(soundpool, getApplicationContext(), 0);
		vec216.load(soundpool, getApplicationContext(), 0);
		bass.load(soundpool, getApplicationContext(), 0);
		drums.load(soundpool, getApplicationContext(), 0);
		bassdrum = soundpool.load(getApplicationContext(), R.raw.bassdrum, 1); // in 2nd param u have to pass your desire ringtone
		
		playPosition1 = 1;
		playPosition2 = 1;
		playPosition3 = 1;
		playPosition4 = 1;
		
		tcp_task = new TCPReadTimerTask();
		bpmTask = new BPMTimerTask();
		sendmsg_task = new SendMsgTimerTask();
		bpm_timer.schedule(bpmTask, 0, MiddlemanConnection.getTempo());
		tcp_timer.schedule(tcp_task, 0, MiddlemanConnection.getTempo()/2);
		sendmsg_timer.schedule(sendmsg_task, 5, MiddlemanConnection.getTempo()/2);
	}

	@Override
	protected void onPause() {
		super.onPause();
		bpmTask.cancel();
		tcp_task.cancel();
		sendmsg_task.cancel();
		
		vec72.unload(soundpool);
		vec216.unload(soundpool);
		bass.unload(soundpool);
		drums.unload(soundpool);
		soundpool.unload(bassdrum);
		
		soundpool.release();
		soundpool = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.game, menu);
		return true;
	}

	private int getKeyPosition(float y_pos) {
		Display display = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		float key_size = size.y / GameView.DIVISIONS;
		return (int) (y_pos / key_size);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float y = event.getY();
		View game_view = findViewById(R.id.gameView1);
		int key_position = getKeyPosition(y);

		switch (event.getAction()) {
		case (MotionEvent.ACTION_DOWN):
			onTouch = true;
			keyPressed = true;
			GameView.touchPosition = key_position;
			game_view.invalidate();
			my_key = key_position;
			Log.d("MyApp", "Action was DOWN: " + GameView.touchPosition);
			return true;

		case (MotionEvent.ACTION_MOVE):
			GameView.touchPosition = key_position;
			game_view.invalidate();
			my_key = key_position;
			Log.d("MyApp", "Action was MOVE: " + getKeyPosition(y));
			return true;

		case (MotionEvent.ACTION_UP):
			onTouch = false;
			game_view.invalidate();
			Log.d("MyApp", "Action was UP");
			return true;

		default:
			return super.onTouchEvent(event);
		}
	}

	// SEND the keys and notes only
	public void sendMessage(int instrument, int key) { // BROADCAST MODE!
		MyApplication app = (MyApplication) getApplication();

		byte buf[] = new byte[4];
		buf[0] = MSG_TYPE_BROADCAST_KEYS;
		buf[1] = 0; // Allocate space for the client id
		buf[2] = (byte) instrument;
		buf[3] = (byte) key;

		OutputStream out;
		try {
			out = app.sock.getOutputStream();
			try {
				out.write(buf, 0, 4);
				//out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// RECEIVE message from the middleman/DE2
	public class TCPReadTimerTask extends TimerTask {
		public void run() {
			MyApplication app = (MyApplication) getApplication();
			if (app.sock != null && app.sock.isConnected()
					&& !app.sock.isClosed()) {

				try {
					InputStream in = app.sock.getInputStream();

					// See if any bytes are available from the Middleman
					int bytes_avail = in.available();
					if (bytes_avail > 0) {

						// If so, read them in and create a sring
						byte buf[] = new byte[bytes_avail];
						in.read(buf);
						int message_type = buf[0];

						if (message_type == MSG_TYPE_BROADCAST_KEYS) {
							int client_id = buf[1];
							int received_instrument = buf[2];
							int received_key = buf[3];

							Log.d("MyRcvdMessage", "Instrument: "+ received_instrument + " Key: " + received_key);

							tcp_instruments.put(client_id, received_instrument);
							tcp_keys.put(client_id, received_key);
							tcp_updated = true;
						} else if (message_type == MSG_TYPE_SET_SOUND_OUT) {
							instrument_volume = 1;
							bpm_volume = (float) 0.7;
							is_master = true;
							Log.d("SoundThing", "Sound On!!");
						} else if (message_type == MSG_TYPE_MUTE) {
							instrument_volume = 0;
							bpm_volume = 0;
							is_master = false;
							Log.d("SoundThing", "Sound Mute!!");
						}

					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	// SEND the BPM beats to the DE2
	public void sendBPMMessage() {
		MyApplication app = (MyApplication) getApplication();

		byte buf[] = new byte[1];
		buf[0] = MSG_TYPE_BPM;

		OutputStream out;
		try {
			out = app.sock.getOutputStream();
			try {
				out.write(buf, 0, 1);
				out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// PLAY notes (timer)
	public class BPMTimerTask extends TimerTask {
		public void run() {
			Log.d("BPMTimerTask", "Playing note");
			if (bassdrum_timer == 1) {
				soundpool.play(bassdrum, bpm_volume, bpm_volume, 0, 0, 1);
				bassdrum_timer = 0;
				if (is_master){
					sendBPMMessage();
				}
			} else {
				bassdrum_timer = 1;
			}
			if (groupSession == true && tcp_updated == true) {
				for (int i = 0; i < tcp_keys.size(); i++) {
					playSound(tcp_keys.valueAt(i), tcp_instruments.valueAt(i));
				}
				tcp_keys.clear();
				tcp_instruments.clear();
				tcp_updated = false;
			}
			if (groupSession == false && (onTouch == true || keyPressed == true)) {
				playSound(GameView.touchPosition, my_instrument);
				keyPressed = false;
			}
			if (SettingsMenu.playLoop1 == true && loopArray1 != null){
				if (playPosition1 < loopArray1.length){
					playSound(loopArray1[playPosition1], LoopActivity.loopInstrument1);
					playPosition1++;
				}
				else{
					playPosition1 = 0;
					playSound(loopArray1[playPosition1], LoopActivity.loopInstrument1);
					playPosition1++;
				}
			}
			if (SettingsMenu.playLoop2 == true && loopArray2 != null){
				if (playPosition2 < loopArray2.length){
					playSound(loopArray2[playPosition2], LoopActivity.loopInstrument2);
					playPosition2++;
				}
				else{
					playPosition2 = 0;
					playSound(loopArray2[playPosition2], LoopActivity.loopInstrument2);
					playPosition2++;
				}
			}
			if (SettingsMenu.playLoop3 == true && loopArray3 != null){
				if (playPosition3 < loopArray3.length){
					playSound(loopArray3[playPosition3], LoopActivity.loopInstrument3);
					playPosition3++;
				}
				else{
					playPosition3 = 0;
					playSound(loopArray3[playPosition3], LoopActivity.loopInstrument3);
					playPosition3++;
				}
			}
			if (SettingsMenu.playLoop4 == true && loopArray4 != null){
				if (playPosition4 < loopArray4.length){
					playSound(loopArray4[playPosition4], LoopActivity.loopInstrument4);
					playPosition4++;
				}
				else{
					playPosition4 = 0;
					playSound(loopArray4[playPosition4], LoopActivity.loopInstrument4);
					playPosition4++;
				}
			}
		}
	}

	// SENDS messages from time to time
	public class SendMsgTimerTask extends TimerTask {
		public void run() {
			if (onTouch == true) {
				sendMessage(my_instrument, my_key);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	// PLAY notes depending on the instrument and key received
	public void playSound(int touchPosition, int instrument) {
		Log.d("PlaySound", "Key Pressed " + touchPosition);
		switch (instrument) {
		case Instrument.Vec72:
			pickVec72Note(touchPosition);
			break;
		case Instrument.Vec216:
			pickVec216Note(touchPosition);
			break;
		case Instrument.Bass:
			pickBassNote(touchPosition);
			break;
		case Instrument.Drums:
			pickDrumsNote(touchPosition);
			break;
		default:
			break;
		}
	}

	// PLAY Vec72 notes
	public void pickVec72Note(int touchPosition) {
		switch (touchPosition) {
		case 0:
			soundpool.play(vec72.note[10], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 1:
			soundpool.play(vec72.note[9], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 2:
			soundpool.play(vec72.note[8], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 3:
			soundpool.play(vec72.note[7], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 4:
			soundpool.play(vec72.note[6], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 5:
			soundpool.play(vec72.note[5], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 6:
			soundpool.play(vec72.note[4], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 7:
			soundpool.play(vec72.note[3], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 8:
			soundpool.play(vec72.note[2], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 9:
			soundpool.play(vec72.note[1], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 10:
			soundpool.play(vec72.note[0], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		default:
			Log.d("PlaySound", "Redundant Key Pressed "
					+ GameView.touchPosition);
			break;
		}
	}

	// PLAY Vec216 notes
	public void pickVec216Note(int touchPosition) {
		switch (touchPosition) {
		case 0:
			soundpool.play(vec216.note[10], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 1:
			soundpool.play(vec216.note[9], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 2:
			soundpool.play(vec216.note[8], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 3:
			soundpool.play(vec216.note[7], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 4:
			soundpool.play(vec216.note[6], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 5:
			soundpool.play(vec216.note[5], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 6:
			soundpool.play(vec216.note[4], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 7:
			soundpool.play(vec216.note[3], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 8:
			soundpool.play(vec216.note[2], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 9:
			soundpool.play(vec216.note[1], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 10:
			soundpool.play(vec216.note[0], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		default:
			Log.d("PlaySound", "Redundant Key Pressed "
					+ GameView.touchPosition);
			break;
		}
	}

	// PLAY bass notes
	public void pickBassNote(int touchPosition) {
		switch (touchPosition) {
		case 0:
			soundpool.play(bass.note[10], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 1:
			soundpool.play(bass.note[9], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 2:
			soundpool.play(bass.note[8], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 3:
			soundpool.play(bass.note[7], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 4:
			soundpool.play(bass.note[6], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 5:
			soundpool.play(bass.note[5], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 6:
			soundpool.play(bass.note[4], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 7:
			soundpool.play(bass.note[3], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 8:
			soundpool.play(bass.note[2], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 9:
			soundpool.play(bass.note[1], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 10:
			soundpool.play(bass.note[0], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		default:
			Log.d("PlaySound", "Redundant Key Pressed "
					+ GameView.touchPosition);
			break;
		}
	}

	// Play drums sounds
	public void pickDrumsNote(int touchPosition) {
		switch (touchPosition) {
		case 0:
			soundpool.play(drums.note[10], instrument_volume,
					instrument_volume, 0, 0, 1);
			break;
		case 1:
			soundpool.play(drums.note[9], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 2:
			soundpool.play(drums.note[8], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 3:
			soundpool.play(drums.note[7], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 4:
			soundpool.play(drums.note[6], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 5:
			soundpool.play(drums.note[5], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 6:
			soundpool.play(drums.note[4], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 7:
			soundpool.play(drums.note[3], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 8:
			soundpool.play(drums.note[2], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 9:
			soundpool.play(drums.note[1], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		case 10:
			soundpool.play(drums.note[0], instrument_volume, instrument_volume,
					0, 0, 1);
			break;
		default:
			Log.d("PlaySound", "Redundant Key Pressed "
					+ GameView.touchPosition);
			break;
		}
	}

	public void initKey(String key) {

		if (key.compareTo("G#/Ab") == 0) {
			vec72.init(vec72.KEY_OF_GSHARP);
			vec216.init(vec216.KEY_OF_GSHARP);
			bass.init(bass.KEY_OF_GSHARP);
		}

		else if (key.compareTo("A") == 0) {
			vec72.init(vec72.KEY_OF_A);
			vec216.init(vec216.KEY_OF_A);
			bass.init(bass.KEY_OF_A);
		}

		else if (key.compareTo("A#/Bb") == 0) {
			vec72.init(vec72.KEY_OF_ASHARP);
			vec216.init(vec216.KEY_OF_ASHARP);
			bass.init(bass.KEY_OF_ASHARP);
		}

		else if (key.compareTo("B") == 0) {
			vec72.init(vec72.KEY_OF_B);
			vec216.init(vec216.KEY_OF_B);
			bass.init(bass.KEY_OF_B);
		}

		else if (key.compareTo("C") == 0) {
			vec72.init(vec72.KEY_OF_C);
			vec216.init(vec216.KEY_OF_C);
			bass.init(bass.KEY_OF_C);
		}

		else if (key.compareTo("C#/Db") == 0) {
			vec72.init(vec72.KEY_OF_CSHARP);
			vec216.init(vec216.KEY_OF_CSHARP);
			bass.init(bass.KEY_OF_CSHARP);
		}

		else if (key.compareTo("D") == 0) {
			vec72.init(vec72.KEY_OF_D);
			vec216.init(vec216.KEY_OF_D);
			bass.init(bass.KEY_OF_D);
		}

		else if (key.compareTo("D#/Eb") == 0) {
			vec72.init(vec72.KEY_OF_DSHARP);
			vec216.init(vec216.KEY_OF_DSHARP);
			bass.init(bass.KEY_OF_DSHARP);
		}

		else if (key.compareTo("E") == 0) {
			vec72.init(vec72.KEY_OF_E);
			vec216.init(vec216.KEY_OF_E);
			bass.init(bass.KEY_OF_E);
		}

		else if (key.compareTo("F") == 0) {
			vec72.init(vec72.KEY_OF_F);
			vec216.init(vec216.KEY_OF_F);
			bass.init(bass.KEY_OF_F);
		}

		else if (key.compareTo("F#/Gb") == 0) {
			vec72.init(vec72.KEY_OF_FSHARP);
			vec216.init(vec216.KEY_OF_FSHARP);
			bass.init(bass.KEY_OF_FSHARP);
		}

		else if (key.compareTo("G") == 0) {
			vec72.init(vec72.KEY_OF_G);
			vec216.init(vec216.KEY_OF_G);
			bass.init(bass.KEY_OF_G);
		}

		else {
			Log.d("Key", "The Key " + MiddlemanConnection.getKey());

		}
	}
}
