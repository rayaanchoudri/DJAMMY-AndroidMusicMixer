package com.example.songsequencerapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoopSettings extends Activity {
	
	public static int beatNumber;
	public static final String LOOP_ARRAY_KEY = "LOOP_ARRAY_KEY";
	public static final String LOOP_ARRAY_SIZE = "LOOP_ARRAY_SIZE";
	public static final String LOOP_ARRAY_FILE = "LOOP_ARRAY_FILE";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_loop_settings);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.loop_settings, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public void recordLoop(View view){
		EditText beats_value;
		beats_value = (EditText) findViewById(R.id.editText1);
		beatNumber = Integer.parseInt(beats_value.getText().toString()) * 4;
		if (beatNumber > 0){
			Toast t = Toast.makeText(getApplicationContext(), "Press any key to start recording", Toast.LENGTH_LONG);
			t.show();
			Intent intent = new Intent(this, LoopActivity.class);
			startActivity(intent);
		}
		else{
			Toast t = Toast.makeText(getApplicationContext(), "Beats Divisions should be greater than 0", Toast.LENGTH_LONG);
			t.show();
		}
	}
	public void setSlot(View view){
		Button b = (Button) view;
		String temp = b.getText().toString();
		if (temp.compareTo("Loop 1") == 0)
			LoopActivity.index=0;
		else if (temp.compareTo("Loop 2") == 0)
			LoopActivity.index=1;
		else if (temp.compareTo("Loop 3") == 0)
			LoopActivity.index=2;
		else if((temp.compareTo("Loop 4") == 0))
			LoopActivity.index=3;
	}
}
