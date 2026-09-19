package com.pVq.KaneKone;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.activity.*;
import androidx.activity.ktx.*;
import androidx.annotation.*;
import androidx.annotation.experimental.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.*;
import androidx.autofill.*;
import androidx.compose.animation.*;
import androidx.compose.animation.core.*;
import androidx.compose.foundation.*;
import androidx.compose.foundation.layout.*;
import androidx.compose.runtime.*;
import androidx.compose.runtime.saveable.*;
import androidx.compose.ui.*;
import androidx.compose.ui.geometry.*;
import androidx.compose.ui.graphics.*;
import androidx.compose.ui.text.*;
import androidx.compose.ui.unit.*;
import androidx.compose.ui.util.*;
import androidx.core.*;
import androidx.core.ktx.*;
import androidx.customview.*;
import androidx.customview.poolingcontainer.*;
import androidx.fragment.*;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.ktx.*;
import androidx.lifecycle.extensions.*;
import androidx.lifecycle.ktx.*;
import androidx.lifecycle.livedata.*;
import androidx.lifecycle.livedata.core.*;
import androidx.lifecycle.livedata.core.ktx.*;
import androidx.lifecycle.process.*;
import androidx.lifecycle.runtime.*;
import androidx.lifecycle.service.*;
import androidx.lifecycle.viewmodel.*;
import androidx.lifecycle.viewmodel.ktx.*;
import androidx.lifecycle.viewmodel.savedstate.*;
import androidx.loader.*;
import androidx.profileinstaller.*;
import androidx.savedstate.*;
import androidx.savedstate.ktx.*;
import androidx.startup.*;
import androidx.tracing.*;
import androidx.versionedparcelable.*;
import androidx.viewpager.*;
import com.github.kittinunf.fuel.android.*;
import com.google.android.filament.*;
import com.google.android.filament.gltfio.*;
import com.google.android.filament.utils.*;
import com.pVq.KaneKone.databinding.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import com.pVq.KaneKone.game.GameView;
import com.pVq.KaneKone.game.LobbyState;
import com.pVq.KaneKone.game.WorldManager;

public class MainActivity extends AppCompatActivity {
	
	private MainBinding binding;
	private GameView gameEngine;
	
	private RequestNetwork net;
	private RequestNetwork.RequestListener _net_request_listener;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		binding = MainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		initialize(_savedInstanceState);
		initializeLogic();
	}
	
	private void initialize(Bundle _savedInstanceState) {
		net = new RequestNetwork(this);
		
		_net_request_listener = new RequestNetwork.RequestListener() {
			@Override
			public void onResponse(String _param1, String _param2, HashMap<String, Object> _param3) {
				final String _tag = _param1;
				final String _response = _param2;
				final HashMap<String, Object> _responseHeaders = _param3;
				
			}
			
			@Override
			public void onErrorResponse(String _param1, String _param2) {
				final String _tag = _param1;
				final String _message = _param2;
				
			}
		};
	}
	
	private void initializeLogic() {
		// --- 1. SETTING LAYAR & ACTION BAR (ANTI CRASH) ---
		if (getSupportActionBar() != null) {
			getSupportActionBar().hide();
		} else if (getActionBar() != null) {
			getActionBar().hide();
		}
		
		final android.widget.FrameLayout layarUtama = new android.widget.FrameLayout(this);
		setContentView(layarUtama);
		
		
		// --- 2. EKSEKUSI LOADING STATE ---
		// Cukup tulis "LoadingState" saja (tanpa com.pVq...)
		LoadingState loading = new LoadingState(this, new Runnable() {
			@Override
			public void run() {
				
				// --- [CALLBACK] LOADING SELESAI, MASUK LOBBY ---
				layarUtama.removeAllViews();
				
				// 2. Panggil LOBBY
				// [FIX] Hapus "com.pVq.KaneKone." -> Cukup "LobbyState"
				// Karena kita sudah import com.pVq.KaneKone.game.LobbyState di atas!
				LobbyState lobby = new LobbyState(MainActivity.this, new LobbyState.OnWorldSelected() {
					@Override
					public void onPlay(final WorldManager.WorldData worldData) {
						
						// --- [CALLBACK] USER MEMILIH WORLD ---
						android.widget.Toast.makeText(getApplicationContext(), "Traveling to: " + worldData.name, android.widget.Toast.LENGTH_SHORT).show();
						
						layarUtama.removeAllViews();
						
						// 2. Panggil ENGINE GAME
						// [FIX] Cukup "GameView", tidak perlu panjang lebar
						final GameView gameEngine = new GameView(MainActivity.this, worldData);
						
						// 3. Tampilkan Game 3D
						layarUtama.addView(gameEngine.getView());
					}
				});
				
				// Tampilkan Lobby ke layar
				layarUtama.addView(lobby.getView());
			}
		});
		
		// --- 3. TAMPILAN AWAL ---
		layarUtama.addView(loading.getView());
	}
	
	
	@Override
	public void onPause() {
		super.onPause();
		// Di onPause
		if (gameEngine != null) {
			gameEngine.onPause();
		}
		
		// Di onResume
		if (gameEngine != null) {
			gameEngine.onResume();
		}
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (gameEngine != null) {
			gameEngine.onResume();
		}
		
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
	}
}