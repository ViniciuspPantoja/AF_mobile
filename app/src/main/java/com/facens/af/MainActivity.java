package com.facens.af;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int CODIGO_PERMISSAO_GPS = 42;
    private static final String CHAVE_CLIMA = "e1489d591ede52714f7846700747bee4";

    private FirebaseFirestore firestore;
    private CollectionReference colecaoRegistros;

    private EditText campoNome;
    private EditText campoNota;
    private EditText campoDataVisita;
    private EditText campoTipo;
    private CheckBox checkDestacado;

    private TextView infoLat;
    private TextView infoLng;
    private TextView infoTemp;
    private TextView infoCondicao;

    private Button btnCapturarGPS;
    private Button btnVerMapa;
    private Button btnRegistrar;

    private FusedLocationProviderClient clienteGPS;

    private double coordLat = 0.0;
    private double coordLng = 0.0;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inicializarFirestore();
        vincularViews();
        configurarBotoes();

        clienteGPS = LocationServices.getFusedLocationProviderClient(this);
    }

    private void inicializarFirestore() {
        firestore = FirebaseFirestore.getInstance();
        colecaoRegistros = firestore.collection("registros");
    }

    private void vincularViews() {
        campoNome       = findViewById(R.id.etTitulo);
        campoNota       = findViewById(R.id.etDescricao);
        campoDataVisita = findViewById(R.id.etData);
        campoTipo       = findViewById(R.id.etCategoria);

        checkDestacado  = findViewById(R.id.cbFavorito);

        infoLat       = findViewById(R.id.tvLatitude);
        infoLng       = findViewById(R.id.tvLongitude);
        infoTemp      = findViewById(R.id.tvTemperatura);
        infoCondicao  = findViewById(R.id.tvClima);

        btnCapturarGPS = findViewById(R.id.btnLocalizacao);
        btnVerMapa     = findViewById(R.id.btnMapa);
        btnRegistrar   = findViewById(R.id.btnSalvar);
    }

    private void configurarBotoes() {
        btnCapturarGPS.setOnClickListener(v -> buscarPosicaoAtual());
        btnRegistrar.setOnClickListener(v -> gravarRegistro());
        btnVerMapa.setOnClickListener(v -> navegarParaMapa());
    }

    private void buscarPosicaoAtual() {
        boolean semPermissao = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED;

        if (semPermissao) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    CODIGO_PERMISSAO_GPS
            );
            return;
        }

        clienteGPS.getLastLocation().addOnSuccessListener(posicao -> {
            if (posicao != null) {
                coordLat = posicao.getLatitude();
                coordLng = posicao.getLongitude();
                infoLat.setText("Lat: " + coordLat);
                infoLng.setText("Lng: " + coordLng);
                buscarClima(coordLat, coordLng);
            } else {
                Toast.makeText(this, "Não foi possível obter a posição", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buscarClima(double lat, double lng) {
        infoTemp.setText("Temperatura: buscando...");
        infoCondicao.setText("Condição: buscando...");

        String url = "https://api.openweathermap.org/data/2.5/weather"
                + "?lat=" + lat
                + "&lon=" + lng
                + "&appid=" + CHAVE_CLIMA
                + "&units=metric"
                + "&lang=pt_br";

        executor.execute(() -> {
            Request requisicao = new Request.Builder().url(url).build();
            try (Response resposta = httpClient.newCall(requisicao).execute()) {
                if (resposta.isSuccessful() && resposta.body() != null) {
                    String json = resposta.body().string();
                    JSONObject obj = new JSONObject(json);
                    double temperatura = obj.getJSONObject("main").getDouble("temp");
                    String condicao = obj.getJSONArray("weather")
                            .getJSONObject(0).getString("description");
                    mainHandler.post(() -> {
                        infoTemp.setText("Temperatura: " + temperatura + "°C");
                        infoCondicao.setText("Condição: " + condicao);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Erro ao buscar clima: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int codigo,
                                           @NonNull String[] permissoes,
                                           @NonNull int[] resultados) {
        super.onRequestPermissionsResult(codigo, permissoes, resultados);

        if (codigo == CODIGO_PERMISSAO_GPS) {
            boolean concedida = resultados.length > 0
                    && resultados[0] == PackageManager.PERMISSION_GRANTED;

            if (concedida) {
                buscarPosicaoAtual();
            } else {
                Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void gravarRegistro() {
        String nome      = campoNome.getText().toString().trim();
        String nota      = campoNota.getText().toString().trim();
        String data      = campoDataVisita.getText().toString().trim();
        String tipo      = campoTipo.getText().toString().trim();
        boolean destaque = checkDestacado.isChecked();

        if (nome.isEmpty()) {
            campoNome.setError("Informe um nome para o destino");
            return;
        }

        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", nome);
        dados.put("nota", nota);
        dados.put("data", data);
        dados.put("tipo", tipo);
        dados.put("lat", coordLat);
        dados.put("lng", coordLng);
        dados.put("temperatura", infoTemp.getText().toString());
        dados.put("condicao", infoCondicao.getText().toString());
        dados.put("destaque", destaque);

        colecaoRegistros.add(dados)
                .addOnSuccessListener(ref ->
                        Toast.makeText(this, "Destino registrado!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Falha ao registrar: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void navegarParaMapa() {
        if (coordLat == 0.0 && coordLng == 0.0) {
            Toast.makeText(this, "Capture a localização antes de abrir o mapa", Toast.LENGTH_SHORT).show();
            return;
        }

        String geoUri = "geo:" + coordLat + "," + coordLng
                + "?q=" + coordLat + "," + coordLng;

        Intent intencaoMapa = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
        intencaoMapa.setPackage("com.google.android.apps.maps");

        if (intencaoMapa.resolveActivity(getPackageManager()) != null) {
            startActivity(intencaoMapa);
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + coordLat + "," + coordLng)));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}